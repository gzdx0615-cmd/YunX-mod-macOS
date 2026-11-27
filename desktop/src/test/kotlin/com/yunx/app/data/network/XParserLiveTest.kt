package com.yunx.app.data.network

import com.yunx.app.data.network.video.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

/** Explicit opt-in smoke test against a public post and its actual media CDN. */
class XParserLiveTest {
    @Test fun publicPostResolvesAndMediaBytesAreReadable() = runBlocking {
        assumeTrue(System.getenv("YUNX_X_LIVE_TEST") == "1")
        val builder = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let {
            val uri = URI(it)
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, if (uri.port > 0) uri.port else 80)))
        }
        val client = builder.build()
        try {
            val link = VideoLinkParser.parse("https://x.com/BrooklynNets/status/1349794411333394432")!!
            val result = XParser.fetch(link, null, clientProvider = { client })
            assertEquals(VideoPlatform.X, result.platform)
            val media = result.medias.first { it.kind == VideoMediaKind.VIDEO }
            assertFalse(media.isHls)
            val request = Request.Builder().url(media.url).apply {
                media.headers.forEach { (key, value) -> header(key, value) }
                header("Range", "bytes=0-1023")
            }.build()
            client.newCall(request).execute().use {
                assertTrue("Media HTTP ${it.code}", it.code == 206 || it.code == 200)
                val bytes = it.body!!.byteStream().readNBytes(1024)
                assertTrue(bytes.size > 32)
                assertTrue(String(bytes.copyOfRange(0, 32), Charsets.ISO_8859_1).contains("ftyp"))
            }
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
