package com.yunx.app.data.preview

import com.yunx.app.data.network.GitHubMirror
import com.yunx.app.data.network.HttpClients
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Native macOS FFmpeg, kept in the application's private tools directory. */
internal object MacFfmpegInstaller {
    private const val RELEASE = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1"

    private val sha256 = mapOf(
        "ffmpeg-darwin-arm64" to "a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584",
        "ffmpeg-darwin-x64" to "ebdddc936f61e14049a2d4b549a412b8a40deeff6540e58a9f2a2da9e6b18894"
    )

    internal fun assetName(arch: String): String = when (arch.lowercase()) {
        "aarch64", "arm64" -> "ffmpeg-darwin-arm64"
        "x86_64", "amd64" -> "ffmpeg-darwin-x64"
        else -> throw IOException("不支持的 Mac 架构：$arch")
    }

    suspend fun download(installDir: File, progress: (Long, Long) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val asset = assetName(System.getProperty("os.arch"))
            val direct = "$RELEASE/$asset"
            val prefix = GitHubMirror.activePrefix()
            val candidates = listOf(prefix + direct, direct).distinct()
            val bin = File(installDir, "bin").apply { mkdirs() }
            var last: Exception? = null
            for (url in candidates) {
                val tmp = File.createTempFile("ffmpeg-", ".download", bin)
                try {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val call = HttpClients.downloadClient().newCall(Request.Builder().url(url).build())
                    coroutineScope {
                        val watchdog = launch { try { awaitCancellation() } finally { call.cancel() } }
                        try {
                            call.execute().use { response ->
                                if (!response.isSuccessful) throw IOException("FFmpeg 下载失败 HTTP ${response.code}")
                                val body = response.body ?: throw IOException("下载响应为空")
                                val total = body.contentLength()
                                var received = 0L
                                body.byteStream().use { input -> tmp.outputStream().use { output ->
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer, 0, n); digest.update(buffer, 0, n); received += n
                                        progress(received, total)
                                    }
                                } }
                                if (total > 0 && received != total) throw IOException("FFmpeg 下载不完整")
                            }
                        } finally { watchdog.cancel() }
                    }
                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    if (checksum != sha256[asset]) throw IOException("FFmpeg SHA-256 校验失败，现有引擎保持不变")
                    // Reject HTML/proxy error pages and the wrong architecture before replacing an existing engine.
                    val header = tmp.inputStream().use { it.readNBytes(8) }
                    val cpu = if (asset.endsWith("arm64")) 12 else 7
                    if (header.size != 8 || !header.take(4).map { it.toInt() and 255 }.equals(listOf(207, 250, 237, 254)) ||
                        (header[4].toInt() and 255) != cpu || header[7].toInt() != 1)
                        throw IOException("FFmpeg 文件格式或架构不匹配")
                    if (!tmp.setExecutable(true, true)) throw IOException("无法设置 FFmpeg 执行权限")
                    val target = File(bin, "ffmpeg")
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    val licenseName = "ffmpeg-darwin-${if (asset.endsWith("arm64")) "arm64" else "x64"}.LICENSE"
                    javaClass.classLoader.getResourceAsStream("licenses/$licenseName")?.use { input ->
                        File(installDir, "LICENSE").outputStream().use { input.copyTo(it) }
                    }
                    return@withContext Result.success(target)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    last = e
                } finally { tmp.delete() }
            }
            Result.failure(last ?: IOException("FFmpeg 下载失败"))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
