package com.yunx.app.data.network

import com.yunx.app.data.network.video.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XParserTest {
    private val id = "1234567890123456789"
    private fun fixture(media: String) = JSONObject("""{"id_str":"$id","text":"Public post","created_at":"2026-11-27T00:00:00Z","user":{"name":"Example"},"mediaDetails":$media}""")
    private val video = """{"type":"video","media_url_https":"https://pbs.twimg.com/media/cover.jpg","video_info":{"variants":[
      {"content_type":"video/mp4","bitrate":256000,"url":"https://video.twimg.com/vid/640x360/small.mp4"},
      {"content_type":"video/mp4","bitrate":2176000,"url":"https://video.twimg.com/vid/1280x720/large.mp4"},
      {"content_type":"application/x-mpegURL","url":"https://video.twimg.com/pl/master.m3u8"}]}}"""

    @Test fun recognizesAliasesSuffixesAndSharedText() {
        for (url in listOf("https://x.com/user/status/$id/video/1?s=20", "https://twitter.com/user/status/$id/photo/2",
            "https://mobile.twitter.com/user/status/$id", "https://x.com/i/web/status/$id", "https://x.com/i/status/$id",
            "https://twitter.com/i/videos/tweet/$id")) {
            val result = VideoLinkParser.parse("看看这个 $url。")!!
            assertEquals(VideoPlatform.X, result.platform); assertEquals(id, result.id)
        }
        assertTrue(VideoLinkParser.parse("https://t.co/AbC123")!!.isShortLink)
        for (url in listOf("https://evilx.com/user/status/$id", "https://x.com.evil.com/user/status/$id",
            "https://example.com/x.com/user/status/$id", "https://x.com/user", "https://x.com/i/broadcasts/abc"))
            assertNull(url, VideoLinkParser.parse(url))
    }
    @Test fun selectsBestMp4AndReparsesRequestedQuality() {
        val json = fixture("[$video]")
        val best = XParser.decode(json, id)
        assertTrue(best.medias.single().url.endsWith("large.mp4"))
        assertEquals(listOf(720, 360), best.qualities.map { it.qn })
        assertEquals(720, best.selectedQn)
        assertTrue(XParser.decode(json, id, 360).medias.single().url.endsWith("small.mp4"))
        assertEquals("Example", best.author)
        assertTrue(best.timestampSeconds > 0)
    }
    @Test fun preservesMixedMediaAndAnimatedGifWithoutDuplicateQualities() {
        val json = fixture("""[$video,{"type":"photo","media_url_https":"https://pbs.twimg.com/media/photo.jpg:large"},
            {"type":"animated_gif","video_info":{"variants":[{"content_type":"video/mp4","url":"https://video.twimg.com/vid/480x270/gif.mp4"}]}}]""")
        val result = XParser.decode(json, id)
        assertEquals(3, result.medias.size)
        assertEquals(2, result.videoCount); assertEquals(1, result.imageCount)
        assertTrue(result.medias[1].url.contains("name=orig"))
        assertFalse(result.medias[1].url.contains(":large"))
        assertTrue(result.medias.all { "Cookie" !in it.headers })
    }
    @Test fun supportsPublicUnifiedCardResponse() {
        val text = javaClass.getResourceAsStream("/x/unified-card.json")!!.bufferedReader().use { it.readText() }
        val result = XParser.decode(JSONObject(text), "1349794411333394432")
        assertEquals(1, result.videoCount)
        assertEquals(720, result.selectedQn)
    }
    @Test fun usesHlsWhenNoMp4IsAvailable() {
        val result = XParser.decode(fixture("""[{"type":"video","video_info":{"variants":[
          {"content_type":"application/x-mpegURL","url":"https://video.twimg.com/pl/stream.m3u8"}]}}]"""), id)
        assertTrue(result.medias.single().isHls)
    }
    @Test fun neverSubstitutesQuotedPostAndRejectsForeignMediaHosts() {
        val json = fixture("[]").put("quoted_tweet", fixture("[$video]"))
        try { XParser.decode(json, id); fail("Quoted media must not silently replace requested post") }
        catch (e: VideoParseException) { assertEquals(VideoParseErrorKind.UNSUPPORTED, e.kind) }
        try { XParser.decode(fixture("""[{"type":"photo","media_url_https":"https://example.com/photo.jpg"}]"""), id); fail() }
        catch (e: VideoParseException) { assertEquals(VideoParseErrorKind.UNSUPPORTED, e.kind) }
    }
    @Test fun classifiesUnavailableAndMismatchedPosts() {
        for (json in listOf(JSONObject(), JSONObject("""{"__typename":"TweetTombstone"}"""))) {
            try { XParser.decode(json, id); fail() }
            catch (e: VideoParseException) { assertEquals(VideoParseErrorKind.NOT_FOUND, e.kind) }
        }
        try { XParser.decode(fixture("[$video]"), "999"); fail() }
        catch (e: VideoParseException) { assertEquals(VideoParseErrorKind.UNKNOWN, e.kind) }
    }
    @Test fun requestsOfficialDataAndClassifiesRateLimitsWithoutSendingCookies() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = OkHttpClient()
        try {
            server.enqueue(MockResponse().setBody(fixture("[$video]").toString()))
            val link = DetectedVideoLink(VideoPlatform.X, "status", id, "https://x.com/u/status/$id")
            assertEquals(1, XParser.fetch(link, null, server.url("/tweet-result").toString()) { client }.videoCount)
            val request = server.takeRequest()
            assertEquals(id, request.requestUrl!!.queryParameter("id"))
            assertNull(request.getHeader("Cookie"))
            server.enqueue(MockResponse().setResponseCode(429))
            try { XParser.fetch(link, null, server.url("/tweet-result").toString()) { client }; fail() }
            catch (e: VideoParseException) { assertEquals(VideoParseErrorKind.BLOCKED, e.kind) }
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
