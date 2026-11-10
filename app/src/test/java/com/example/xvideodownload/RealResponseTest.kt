package com.example.xvideodownload

import com.example.xvideodownload.core.FxResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用从 api.fxtwitter.com/2/status/2119441731846639971 真实抓取的响应做回归，
 * 确保解析逻辑跟线上 JSON 结构保持一致（而不是只跟手写的样本一致）。
 */
class RealResponseTest {

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "缺少测试样本 $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `parse real v2 response`() {
        val tweet = FxResponseParser.parse(load("video_tweet_v2.json"), "2119441731846639971")
        assertEquals("NASA", tweet.author)
        assertTrue(tweet.text.isNotBlank())
        assertEquals(1, tweet.items.size)

        val item = tweet.items.first()
        assertEquals(5, item.variants.size) // 1 个 m3u8 被排除，剩 5 档 mp4
        assertEquals("3840x2160", item.variants.maxBy { it.bitrate }.resolutionLabel)
        assertEquals("1920x1080", item.best()!!.resolutionLabel) // 默认封顶 1080p
        assertEquals("https://pbs.twimg.com/amplify_video_thumb/2119441256052932608/img/eUdGd7NaWSonBfvm.jpg", item.thumbnailUrl)
        assertTrue(item.variants.all { it.url.startsWith("https://video.twimg.com/") })
    }
}
