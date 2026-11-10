package com.example.xvideodownload

import com.example.xvideodownload.core.MarkdownEntry
import com.example.xvideodownload.core.ResolvedTweet
import com.example.xvideodownload.core.SaveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEntryTest {

    private val tweet = ResolvedTweet(
        tweetId = "123456",
        author = "NatGeo",
        text = "  Hello penguins  ",
        createdAt = "Mon Nov 09 20:00:00 +0000 2026",
        postDate = "2026-11-09",
        sourceUrl = "https://x.com/NatGeo/status/123456",
        items = emptyList(),
        photos = emptyList(),
    )

    @Test
    fun `body contains author text and links`() {
        val body = MarkdownEntry.body(tweet)
        assertTrue(body.startsWith("## @NatGeo"))
        assertTrue(body.contains("Hello penguins"))
        assertTrue(body.contains("https://x.com/NatGeo/status/123456"))
        assertTrue(body.contains("推文 ID：123456"))
    }

    @Test
    fun `first append writes header without separator`() {
        val result = MarkdownEntry.append("", "2026-11-10", MarkdownEntry.body(tweet))
        assertTrue(result.startsWith("# X 推文存档 · 2026-11-10"))
        assertEquals(0, result.split("\n---\n").size - 1)
    }

    @Test
    fun `second append is separated by horizontal rule`() {
        val first = MarkdownEntry.append("", "2026-11-10", MarkdownEntry.body(tweet))
        val second = MarkdownEntry.append(first, "2026-11-10", MarkdownEntry.body(tweet.copy(text = "second")))
        assertEquals(1, second.split("\n\n---\n\n").size - 1)
        assertTrue(second.contains("Hello penguins"))
        assertTrue(second.contains("second"))
        // 头部只出现一次
        assertEquals(1, Regex("# X 推文存档").findAll(second).count())
    }

    @Test
    fun `append keeps existing content and trims trailing blank lines`() {
        val existing = "# X 推文存档 · 2026-11-10\n\nold\n\n\n"
        val result = MarkdownEntry.append(existing, "2026-11-10", "new")
        assertTrue(result.startsWith("# X 推文存档 · 2026-11-10\n\nold"))
        assertTrue(result.endsWith("---\n\nnew\n"))
    }

    @Test
    fun `media refs embed images and link videos`() {
        val result = MarkdownEntry.withMedia(
            MarkdownEntry.body(tweet),
            listOf(
                MarkdownEntry.MediaRef(SaveKind.PHOTO, "a_1378x917.jpg", "图片 1 · 1378x917"),
                MarkdownEntry.MediaRef(SaveKind.VIDEO, "a_1280x720.mp4", "视频 1 · 1280x720"),
            ),
        )
        assertTrue(result.contains("![图片 1 · 1378x917](a_1378x917.jpg)"))
        assertTrue(result.contains("🎬 [视频 1 · 1280x720](a_1280x720.mp4)"))
    }

    @Test
    fun `no media refs leaves body unchanged`() {
        val body = MarkdownEntry.body(tweet)
        assertEquals(body, MarkdownEntry.withMedia(body, emptyList()))
    }

    @Test
    fun `archive date picks today or post date`() {
        assertEquals("2026-11-10", MarkdownEntry.archiveDate(tweet, false, "2026-11-10"))
        assertEquals("2026-11-09", MarkdownEntry.archiveDate(tweet, true, "2026-11-10"))
        assertEquals("2026-11-10", MarkdownEntry.archiveDate(
            tweet.copy(postDate = null), true, "2026-11-10",
        ))
        assertEquals("2026-11-10.md", MarkdownEntry.fileName("2026-11-10"))
    }
}
