package com.example.xvideodownload.download

import android.content.Context
import android.net.Uri
import com.example.xvideodownload.core.MarkdownEntry
import com.example.xvideodownload.core.SaveKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 把网络资源（视频 / 图片）写入存档目录，或把一条帖子追加到当天的 Markdown 文件。
 */
class MediaDownloader(context: Context) {

    private val storage = ArchiveStorage(context.applicationContext)

    sealed interface Result {
        data class Success(val displayPath: String, val uri: Uri?, val bytes: Long) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun download(
        url: String,
        fileName: String,
        kind: SaveKind,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        repeat(MAX_ATTEMPTS) { attempt ->
            val target = storage.create(fileName, mimeOf(fileName, kind))
                ?: return@withContext Result.Failure("无法创建保存位置，请检查存档目录或存储空间")

            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://x.com/")
                    setRequestProperty("Accept", "*/*")
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    storage.discard(target)
                    if (code in 400..499) {
                        return@withContext Result.Failure(
                            "资源直链已失效或被拒绝（HTTP $code），请重新粘贴链接再解析一次",
                        )
                    }
                    if (attempt == MAX_ATTEMPTS - 1) {
                        return@withContext Result.Failure("服务器返回 HTTP $code，请稍后重试")
                    }
                    return@repeat
                }

                val total = conn.getHeaderFieldLong("Content-Length", -1L)
                onProgress(0L, total)
                val output = storage.open(target)
                if (output == null) {
                    storage.discard(target)
                    return@withContext Result.Failure("无法写入文件，请检查存档目录权限")
                }

                var downloaded = 0L
                var lastEmit = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                conn.inputStream.use { input ->
                    output.use { out ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastEmit >= EMIT_INTERVAL_BYTES) {
                                lastEmit = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                        out.flush()
                    }
                }
                onProgress(downloaded, total)
                val finalPath = storage.publish(target)
                return@withContext Result.Success(finalPath, target.uri, downloaded)
            } catch (e: CancellationException) {
                storage.discard(target)
                throw e
            } catch (e: Exception) {
                storage.discard(target)
                if (attempt == MAX_ATTEMPTS - 1) {
                    return@withContext Result.Failure(
                        buildString {
                            append("下载失败，请确认手机能访问 pbs.twimg.com / video.twimg.com（X 媒体 CDN）。")
                            e.localizedMessage?.let { append("\n\n$it") }
                        },
                    )
                }
            } finally {
                conn?.disconnect()
            }
        }

        return@withContext Result.Failure("下载失败，请重试")
    }

    /**
     * 追加一条帖子到当天 md：读出已有内容 → 拼接新片段（用 --- 分隔）→ 整体覆写。
     * 覆写而不是 "wa" 追加，是为了兼容所有 SAF provider 和 MediaStore。
     */
    suspend fun appendMarkdown(
        fileName: String,
        date: String,
        entry: String,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val target = storage.find(fileName)
            ?: storage.create(fileName, ArchiveStorage.MARKDOWN_MIME)
            ?: return@withContext Result.Failure("无法创建 Markdown 文件，请检查存档目录")

        val existing = storage.readText(target)
        val content = MarkdownEntry.append(existing, date, entry)
        val bytes = content.toByteArray(Charsets.UTF_8)

        val output = storage.open(target)
            ?: return@withContext Result.Failure("无法写入 Markdown 文件，请检查存档目录权限")
        return@withContext try {
            onProgress(0L, bytes.size.toLong())
            output.use { it.write(bytes) }
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            val finalPath = storage.publish(target)
            Result.Success(finalPath, target.uri, bytes.size.toLong())
        } catch (e: Exception) {
            Result.Failure("写入 Markdown 失败：${e.localizedMessage ?: e::class.java.simpleName}")
        }
    }

    private fun mimeOf(fileName: String, kind: SaveKind): String = when (kind) {
        SaveKind.VIDEO -> "video/mp4"
        SaveKind.PHOTO -> when {
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
        SaveKind.MARKDOWN -> ArchiveStorage.MARKDOWN_MIME
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val BUFFER_SIZE = 64 * 1024
        const val EMIT_INTERVAL_BYTES = 128 * 1024L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
    }
}
