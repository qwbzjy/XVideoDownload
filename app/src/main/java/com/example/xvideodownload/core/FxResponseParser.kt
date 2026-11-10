package com.example.xvideodownload.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * 解析 FxTwitter / FxEmbed 的 JSON 响应。
 *
 * v2 (GET /2/status/{id}): {"code":200, "status":{...,"media":{"videos":[...],"photos":[...]}}}
 * v1 (GET /i/status/{id}): {"code":200, "tweet":{...,"media":{"all":[...]}}}
 *
 * 一条推文可以同时有正文文字、图片和视频，这里全部提取出来。
 * 视频项里的 formats[] 直接给出各清晰度 mp4 直链，无需处理 m3u8 分片。
 */
object FxResponseParser {

    class NotFoundException : Exception()
    class NoContentException : Exception()
    class MalformedException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String, tweetId: String): ResolvedTweet {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw MalformedException("解析服务返回内容不是合法 JSON") }

        if (root.int("code") == 404) throw NotFoundException()

        val status = root.obj("status") ?: root.obj("tweet")
            ?: throw MalformedException("解析服务返回里没有推文内容")

        val authorObj = status.obj("author")
        val author = authorObj?.string("screen_name") ?: authorObj?.string("name") ?: ""
        val text = status.string("text")
            ?: status.obj("raw_text")?.string("text")
            ?: ""
        val createdAt = status.string("created_at")
        val postDate = runCatching {
            status.long("created_timestamp")?.let { epoch ->
                // X 的时间戳是 UTC；统一用 UTC 换算，避免手机/开发机时区不同导致归档日期漂移
                java.time.Instant.ofEpochSecond(epoch)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString()
            }
        }.getOrNull()
        val sourceUrl = status.string("url")

        val media = status.obj("media")

        // ---- 图片 ----
        val photoObjects: List<JsonObject> = media?.array("photos")
            ?.takeIf { it.isNotEmpty() }
            ?: media?.array("all")?.filter { it.string("type") == "photo" }
            ?: emptyList()

        val photos = photoObjects.mapNotNull { o ->
            val url = o.string("url") ?: return@mapNotNull null
            PhotoItem(
                id = o.string("id") ?: url.hashCode().toString(),
                url = url,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
                altText = o.string("altText") ?: o.string("alt_text"),
            )
        }

        // ---- 视频 ----
        val videoObjects: List<JsonObject> = media?.array("videos")
            ?.takeIf { it.isNotEmpty() }
            ?: media?.array("all")?.filter {
                it.string("type") == "video" || it.string("type") == "animated_gif"
            }
            ?: emptyList()

        val items = videoObjects.mapNotNull { item -> parseVideoItem(item) }

        val tweet = ResolvedTweet(
            tweetId = tweetId,
            author = author,
            text = text,
            createdAt = createdAt,
            postDate = postDate,
            sourceUrl = sourceUrl,
            items = items,
            photos = photos,
        )
        if (!tweet.hasContent) throw NoContentException()
        return tweet
    }

    private fun parseVideoItem(item: JsonObject): VideoItem? {
        val duration = item.double("duration") ?: 0.0
        val thumbnail = item.string("thumbnail_url")

        val variants = mutableListOf<VideoVariant>()
        item.array("formats").forEach { f ->
            val url = f.string("url") ?: return@forEach
            val container = f.string("container")
            val isMp4 = container.equals("mp4", ignoreCase = true) ||
                url.substringBefore("?").endsWith(".mp4", ignoreCase = true)
            if (!isMp4) return@forEach
            val dims = parseDimensions(url)
            variants += VideoVariant(
                url,
                f.int("width") ?: dims.first,
                f.int("height") ?: dims.second,
                f.long("bitrate") ?: 0L,
            )
        }

        // 有些响应没有 formats，只有顶层 url，兜底加进去
        if (variants.isEmpty()) {
            item.string("url")?.let { top ->
                val dims = parseDimensions(top)
                variants += VideoVariant(
                    top,
                    item.int("width") ?: dims.first,
                    item.int("height") ?: dims.second,
                    0L,
                )
            }
        }

        val distinct = variants.distinctBy { it.url }
        if (distinct.isEmpty()) return null

        return VideoItem(
            id = item.string("id") ?: distinct.first().url.hashCode().toString(),
            thumbnailUrl = thumbnail,
            durationSec = duration,
            variants = distinct,
        )
    }

    private val dimRegex = Regex("""/(\d{3,5})x(\d{3,5})/""")

    private fun parseDimensions(url: String): Pair<Int, Int> {
        val m = dimRegex.find(url) ?: return 0 to 0
        return (m.groupValues[1].toIntOrNull() ?: 0) to (m.groupValues[2].toIntOrNull() ?: 0)
    }

    // ---- 宽松取值的小工具：字段缺失或类型不符时返回 null，而不是抛异常 ----

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.string(key: String): String? = primitive(key)?.content

    // 部分版本对非数字 JsonPrimitive 取值会抛异常，统一兜住
    private fun JsonObject.int(key: String): Int? =
        runCatching { primitive(key)?.intOrNull }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { primitive(key)?.longOrNull }.getOrNull()

    private fun JsonObject.double(key: String): Double? =
        runCatching { primitive(key)?.doubleOrNull }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): List<JsonObject> =
        runCatching { this[key]?.jsonArray?.mapNotNull { it as? JsonObject } }.getOrNull().orEmpty()
}
