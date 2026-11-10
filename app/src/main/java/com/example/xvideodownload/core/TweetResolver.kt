package com.example.xvideodownload.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把用户粘贴的链接变成一组可下载的视频。
 *
 * 责任链：主地址 /2/status → 主地址 /i/status → 备用地址（自动降级）。
 * 错误按可操作的原因分类，UI 直接据此给提示。
 */
class TweetResolver {

    sealed interface Result {
        data class Success(val tweet: ResolvedTweet) : Result
        data class Failure(val title: String, val detail: String) : Result
    }

    suspend fun resolve(input: String): Result = resolve(input, allowExpand = true)

    private suspend fun resolve(input: String, allowExpand: Boolean): Result = withContext(Dispatchers.IO) {
        when (val parsed = LinkParser.parse(input)) {
            is LinkParser.Result.Error -> Result.Failure("链接无法识别", parsed.message)

            is LinkParser.Result.NeedExpand -> {
                if (!allowExpand) {
                    Result.Failure("短链跳转失败", "请打开短链后复制浏览器地址栏里的完整 X 推文链接")
                } else {
                    val finalUrl = expand(parsed.url)
                    if (finalUrl == null) {
                        Result.Failure(
                            "短链跳转失败",
                            "无法跟随短链，请复制完整的 x.com/用户/status/数字 链接",
                        )
                    } else {
                        resolve(finalUrl, allowExpand = false)
                    }
                }
            }

            is LinkParser.Result.Success -> when (val link = parsed.link) {
                is ParsedLink.DirectVideo -> Result.Success(ResolvedTweet.direct(link.url))
                is ParsedLink.Tweet -> fetchStatus(link.id)
            }
        }
    }

    private fun fetchStatus(id: String): Result {
        val chain = SettingsStore.settings.value.resolverChain
        var sawNotFound = false
        var sawNoContent = false
        var lastError: Exception? = null

        for (base in chain) {
            for (path in listOf("2/status/$id", "i/status/$id")) {
                val url = "$base/$path"
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = ResolverConfig.CONNECT_TIMEOUT_MS
                        readTimeout = ResolverConfig.READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Accept", "application/json")
                    }
                    when (conn.responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            when (val parsed = parseBody(body, id)) {
                                is BodyResult.Success -> return Result.Success(parsed.tweet)
                                is BodyResult.NotFound -> sawNotFound = true
                                is BodyResult.NoContent -> sawNoContent = true
                                is BodyResult.Malformed -> lastError = Exception(parsed.detail)
                            }
                        }

                        HttpURLConnection.HTTP_NOT_FOUND -> sawNotFound = true
                    }
                } catch (e: Exception) {
                    lastError = e
                } finally {
                    conn?.disconnect()
                }
            }
        }

        return when {
            sawNoContent -> Result.Failure(
                "这条推文没有可保存的内容",
                "解析服务返回了一条空推文，可能已被删除或不可见",
            )

            sawNotFound -> Result.Failure(
                "找不到这条推文",
                "推文可能已删除、仅粉丝可见或需要登录查看；本版本只处理公开推文",
            )

            else -> Result.Failure(
                "解析服务不可用",
                buildString {
                    append("连不上解析服务，请检查网络（需要能访问 api.fxtwitter.com），或在设置里换/添加备用解析地址。")
                    lastError?.localizedMessage?.let { append("\n\n$it") }
                },
            )
        }
    }

    private sealed interface BodyResult {
        data class Success(val tweet: ResolvedTweet) : BodyResult
        data object NotFound : BodyResult
        data object NoContent : BodyResult
        data class Malformed(val detail: String) : BodyResult
    }

    private fun parseBody(body: String, id: String): BodyResult = try {
        BodyResult.Success(FxResponseParser.parse(body, id))
    } catch (e: FxResponseParser.NotFoundException) {
        BodyResult.NotFound
    } catch (e: FxResponseParser.NoContentException) {
        BodyResult.NoContent
    } catch (e: FxResponseParser.MalformedException) {
        BodyResult.Malformed(e.message ?: "解析服务返回格式不符合预期")
    } catch (e: Exception) {
        BodyResult.Malformed(e.localizedMessage ?: e::class.java.simpleName)
    }

    /** t.co 等短链：跟随 302 拿到最终 URL 再解析。 */
    private fun expand(shortUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(shortUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = ResolverConfig.CONNECT_TIMEOUT_MS
                readTimeout = ResolverConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.responseCode // 触发重定向链
            conn.url.toString()
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
    }
}
