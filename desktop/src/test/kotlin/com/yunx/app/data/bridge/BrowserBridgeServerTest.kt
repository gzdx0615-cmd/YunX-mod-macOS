package com.yunx.app.data.bridge

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BrowserBridgeServerTest {
    @Test fun extensionCanDiscoverBridgeButWebsitesAndUnauthenticatedDownloadsAreRejected() {
        BrowserBridgeServer.start()
        val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        try {
            assertTrue(BrowserBridgeServer.isRunning)
            val base = "http://127.0.0.1:${BrowserBridgeServer.port}"
            fun request(path: String, origin: String, post: Boolean = false) =
                client.newCall(Request.Builder().url(base + path).header("Origin", origin).apply {
                    if (post) post("{}".toRequestBody())
                }.build()).execute()
            request("/ping", "chrome-extension://test-extension").use {
                assertEquals(200, it.code)
                val json = JSONObject(it.body!!.string())
                assertEquals("yunx", json.getString("app"))
                assertTrue(json.getString("token").length >= 32)
            }
            request("/ping", "https://example.com").use { assertEquals(403, it.code) }
            request("/hook", "chrome-extension://test-extension", true).use { assertEquals(401, it.code) }
        } finally { BrowserBridgeServer.stop(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
