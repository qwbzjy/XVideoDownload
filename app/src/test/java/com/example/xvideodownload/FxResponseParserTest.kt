package com.example.xvideodownload

import com.example.xvideodownload.core.FxResponseParser
import com.example.xvideodownload.core.MarkdownEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxResponseParserTest {

    /** 按真实 api.fxtwitter.com/2/status 响应结构裁剪的样本。 */
    private val v2VideoResponse = """
        {
          "code": 200,
          "status": {
            "url": "https://x.com/NASA/status/2119441731846639971",
            "id": "2119441731846639971",
            "text": "A video from NASA",
            "author": {"screen_name": "NASA", "name": "NASA"},
            "media": {
              "videos": [
                {
                  "id": "2119441256052932608",
                  "url": "https://video.twimg.com/amplify_video/2119441256052932608/vid/avc1/1280x720/highest.mp4?tag=29",
                  "thumbnail_url": "https://pbs.twimg.com/thumb.jpg",
                  "duration": 52.82,
                  "width": 1280,
                  "height": 720,
                  "type": "video",
                  "formats": [
                    {"url": "https://video.twimg.com/x/pl/a.m3u8", "container": "m3u8"},
                    {"url": "https://video.twimg.com/x/vid/avc1/640x360/a.mp4?tag=29", "bitrate": 832000, "container": "mp4", "codec": "h264"},
                    {"url": "https://video.twimg.com/x/vid/avc1/1280x720/b.mp4?tag=29", "bitrate": 2176000, "container": "mp4", "codec": "h264"},
                    {"url": "https://video.twimg.com/x/vid/avc1/1920x1080/c.mp4?tag=29", "bitrate": 10368000, "container": "mp4", "codec": "h264"},
                    {"url": "https://video.twimg.com/x/vid/avc1/3840x2160/d.mp4?tag=29", "bitrate": 25128000, "container": "mp4", "codec": "h264"}
                  ]
                }
              ]
            }
          }
        }
    """.trimIndent()

    private val v1TweetResponse = """
        {"code":200,"tweet":{"id":"999","text":"hi","author":{"screen_name":"jack"},"media":{"all":[
          {"type":"photo","id":"p1","url":"https://pbs.twimg.com/media/a.jpg?name=orig","width":1200,"height":800},
          {"type":"video","id":"v1","url":"https://video.twimg.com/x/vid/avc1/1280x720/z.mp4","duration":12.5,"width":1280,"height":720,
           "formats":[{"container":"mp4","bitrate":2176000,"url":"https://video.twimg.com/x/vid/avc1/1280x720/z.mp4"}]}
        ]}}}
    """.trimIndent()

    private val multiVideoResponse = """
        {"code":200,"status":{"id":"888","text":"two videos","author":{"screen_name":"nasa"},"media":{"videos":[
          {"id":"a","duration":10.0,"thumbnail_url":"https://pbs.twimg.com/a.jpg","formats":[
             {"container":"mp4","bitrate":832000,"url":"https://video.twimg.com/x/vid/avc1/640x360/a.mp4"}]},
          {"id":"b","duration":20.0,"thumbnail_url":"https://pbs.twimg.com/b.jpg","formats":[
             {"container":"mp4","bitrate":2176000,"url":"https://video.twimg.com/x/vid/avc1/1280x720/b.mp4"}]}
        ]}}}
    """.trimIndent()

    private val photoOnlyResponse = """
        {"code":200,"status":{"id":"777","text":"penguins","author":{"screen_name":"NatGeo"},"media":{"photos":[
          {"type":"photo","id":"ph1","url":"https://pbs.twimg.com/media/HWtIjRGXMAAkZ5X.jpg?name=orig","width":1378,"height":917,"altText":"Emperor penguins"},
          {"type":"photo","id":"ph2","url":"https://pbs.twimg.com/media/HWtIjRGXMAAkZ5Y.png?name=orig","width":800,"height":600}
        ]}}}
    """.trimIndent()

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "缺少测试样本 $name" }
            .bufferedReader().use { it.readText() }

    private fun first(body: String) = FxResponseParser.parse(body, "1").items.first()

    @Test
    fun `parse v2 video response`() {
        val tweet = FxResponseParser.parse(v2VideoResponse, "2119441731846639971")
        assertEquals("NASA", tweet.author)
        assertEquals("A video from NASA", tweet.text)
        assertEquals(1, tweet.items.size)
        assertEquals(0, tweet.photos.size)
        val item = tweet.items.first()
        assertEquals(52.82, item.durationSec, 0.001)
        assertEquals(4, item.variants.size) // m3u8 被排除
        assertEquals("0:52", item.durationLabel)
    }

    @Test
    fun `default quality caps at 1080p`() {
        val best = first(v2VideoResponse).best()!!
        assertEquals("1920x1080", best.resolutionLabel)
        assertEquals(10_368_000L, best.bitrate)
    }

    @Test
    fun `custom max height falls back correctly`() {
        val item = first(v2VideoResponse)
        assertEquals("1280x720", item.best(maxHeight = 720)!!.resolutionLabel)
        assertEquals("640x360", item.best(maxHeight = 360)!!.resolutionLabel)
    }

    @Test
    fun `selectable variants are sorted by resolution desc`() {
        val options = first(v2VideoResponse).selectableVariants().map { it.resolutionLabel }
        assertEquals(listOf("3840x2160", "1920x1080", "1280x720", "640x360"), options)
    }

    @Test
    fun `v1 response yields both photo and video`() {
        val tweet = FxResponseParser.parse(v1TweetResponse, "999")
        assertEquals("jack", tweet.author)
        assertEquals(1, tweet.items.size)
        assertEquals(1, tweet.photos.size)
        assertEquals("1200x800", tweet.photos.first().sizeLabel)
        assertEquals("1280x720", tweet.items.first().best()!!.resolutionLabel)
    }

    @Test
    fun `photo only tweet`() {
        val tweet = FxResponseParser.parse(photoOnlyResponse, "777")
        assertEquals(0, tweet.items.size)
        assertEquals(2, tweet.photos.size)
        assertEquals("1378x917", tweet.photos[0].sizeLabel)
        assertEquals("Emperor penguins", tweet.photos[0].altText)
        assertTrue(tweet.photos[0].url.endsWith("?name=orig"))
        assertTrue(tweet.hasContent)
    }

    @Test
    fun `pure text tweet is valid content`() {
        val tweet = FxResponseParser.parse(load("text_tweet.json"), "20")
        assertEquals("jack", tweet.author)
        assertEquals("just setting up my twttr", tweet.text)
        assertEquals(0, tweet.items.size)
        assertEquals(0, tweet.photos.size)
        assertTrue(tweet.hasContent)
        assertTrue(MarkdownEntry.body(tweet).contains("@jack"))
        assertTrue(MarkdownEntry.body(tweet).contains("just setting up my twttr"))
        assertTrue(tweet.createdAt!!.contains("2006"))
        assertEquals("2006-03-21", tweet.postDate)
    }

    @Test
    fun `real photo tweet has original image url`() {
        val tweet = FxResponseParser.parse(load("photo_tweet.json"), "2119882719270162776")
        assertEquals("NatGeo", tweet.author)
        assertTrue(tweet.text.startsWith("The Antarctic climate"))
        assertEquals(1, tweet.photos.size)
        assertEquals("1378x917", tweet.photos.first().sizeLabel)
        assertTrue(tweet.photos.first().url.startsWith("https://pbs.twimg.com/media/"))
        assertTrue(tweet.photos.first().url.contains("name=orig"))
    }

    @Test
    fun `multiple videos are all kept`() {
        val tweet = FxResponseParser.parse(multiVideoResponse, "888")
        assertEquals(2, tweet.items.size)
        assertEquals("a", tweet.items[0].id)
        assertEquals("b", tweet.items[1].id)
    }

    @Test(expected = FxResponseParser.NoContentException::class)
    fun `empty tweet throws no content`() {
        FxResponseParser.parse("""{"code":200,"status":{"media":{"all":[]}}}""", "1")
    }

    @Test(expected = FxResponseParser.NotFoundException::class)
    fun `404 code throws not found`() {
        FxResponseParser.parse("""{"code":404,"message":"NOT_FOUND"}""", "1")
    }

    @Test(expected = FxResponseParser.MalformedException::class)
    fun `invalid json throws malformed`() {
        FxResponseParser.parse("not json", "1")
    }

    @Test
    fun `unknown fields are ignored`() {
        val body = v2VideoResponse.replace(
            "\"code\": 200,",
            "\"code\": 200, \"brand_new_field\": {\"a\": 1},",
        )
        assertTrue(FxResponseParser.parse(body, "1").items.isNotEmpty())
    }
}
