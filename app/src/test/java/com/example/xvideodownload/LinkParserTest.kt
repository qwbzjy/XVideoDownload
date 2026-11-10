package com.example.xvideodownload

import com.example.xvideodownload.core.LinkParser
import com.example.xvideodownload.core.ParsedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    private fun id(input: String): String? =
        (LinkParser.parse(input) as? LinkParser.Result.Success)?.link
            ?.let { (it as? ParsedLink.Tweet)?.id }

    @Test
    fun `standard x com link`() {
        assertEquals("1234567890123456789", id("https://x.com/NASA/status/1234567890123456789"))
    }

    @Test
    fun `link with query and trailing punctuation`() {
        assertEquals("1234567890123456789", id("看这个 https://x.com/NASA/status/1234567890123456789?s=20 太好笑了。"))
    }

    @Test
    fun `twitter com and i status variants`() {
        assertEquals("1234567890", id("https://twitter.com/i/status/1234567890"))
        assertEquals("1234567890", id("https://mobile.twitter.com/user/status/1234567890/photo/1"))
    }

    @Test
    fun `fxtwitter and fixupx variants`() {
        assertEquals("1234567890", id("https://fxtwitter.com/user/status/1234567890"))
        assertEquals("1234567890", id("https://fixupx.com/user/status/1234567890"))
    }

    @Test
    fun `short legacy tweet id`() {
        assertEquals("20", id("https://x.com/jack/status/20"))
    }

    @Test
    fun `bare url without scheme`() {
        assertEquals("1234567890", id("x.com/user/status/1234567890"))
        assertEquals("1234567890", id("看这个 x.com/user/status/1234567890 链接，很好笑。"))
    }

    @Test
    fun `direct mp4 link`() {
        val url = "https://video.twimg.com/amplify_video/1/vid/avc1/1280x720/abc.mp4?tag=29"
        val result = LinkParser.parse(url)
        assertTrue(result is LinkParser.Result.Success)
        assertEquals(url, ((result as LinkParser.Result.Success).link as ParsedLink.DirectVideo).url)
    }

    @Test
    fun `short link needs expansion`() {
        assertTrue(LinkParser.parse("https://t.co/abc123") is LinkParser.Result.NeedExpand)
    }

    @Test
    fun `empty and non x input`() {
        assertTrue(LinkParser.parse("") is LinkParser.Result.Error)
        assertTrue(LinkParser.parse("随便一段文字") is LinkParser.Result.Error)
        assertTrue(LinkParser.parse("https://example.com/a/b") is LinkParser.Result.Error)
    }
}
