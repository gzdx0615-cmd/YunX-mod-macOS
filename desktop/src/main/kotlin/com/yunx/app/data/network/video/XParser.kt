package com.yunx.app.data.network.video

import com.yunx.app.data.network.HttpClients
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Public X/Twitter posts through X's own syndication endpoint. No third-party parsing service. */
object XParser : VideoParser, QualitySelectableParser {
    override val platform = VideoPlatform.X
    private const val ENDPOINT = "https://cdn.syndication.twimg.com/tweet-result"
    private val headers = mapOf("User-Agent" to VideoHttp.UA_CHROME, "Referer" to "https://x.com/")

    override suspend fun parse(link: DetectedVideoLink, cookie: String): VideoParseResult =
        parseWithQuality(link, cookie, null)

    override suspend fun parseWithQuality(link: DetectedVideoLink, cookie: String, qn: Int?): VideoParseResult =
        fetch(link, qn)

    internal suspend fun fetch(link: DetectedVideoLink, qn: Int?, endpoint: String = ENDPOINT,
        clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }): VideoParseResult {
        if (link.platform != platform || !link.id.matches(Regex("[0-9]{1,25}")))
            throw VideoParseException(VideoParseErrorKind.UNSUPPORTED, "请粘贴 X 帖子链接（/status/数字），暂不支持用户主页或直播")
        // The public syndication endpoint currently requires a nonempty token marker (0 is accepted).
        // Do not forward account cookies to the syndication or media hosts.
        val url = endpoint.toHttpUrl().newBuilder().addQueryParameter("id", link.id)
            .addQueryParameter("lang", "en").addQueryParameter("token", "0").build()
        val response = VideoHttp.request(url.toString(), headers = headers, clientProvider = clientProvider)
        if (response.status == 401)
            throw VideoParseException(VideoParseErrorKind.UNSUPPORTED, "该 X 帖子需要登录，当前公开解析接口无法读取")
        VideoHttp.requireSuccess(response, "X 帖子")
        val json = try { JSONObject(response.body) } catch (e: Exception) {
            throw VideoParseException(VideoParseErrorKind.UNKNOWN, "X 返回的数据格式已变化，请稍后重试", e)
        }
        return decode(json, link.id, qn)
    }

    internal fun decode(json: JSONObject, id: String, qn: Int? = null): VideoParseResult {
        if (json.optString("id_str") != id) {
            val kind = if (json.length() == 0 || json.optString("__typename").contains("Tombstone"))
                VideoParseErrorKind.NOT_FOUND else VideoParseErrorKind.UNKNOWN
            throw VideoParseException(kind, "X 帖子不可见，可能已删除、设为私密或受到访问限制")
        }
        val details = json.optJSONArray("mediaDetails")?.objects().orEmpty().ifEmpty {
            json.optJSONObject("extended_entities")?.optJSONArray("media")?.objects().orEmpty()
        }.toMutableList()
        // Some public posts expose attached video only in the unified card, not mediaDetails.
        if (details.isEmpty()) {
            val card = json.optJSONObject("card")?.optJSONObject("binding_values")
                ?.optJSONObject("unified_card")?.optString("string_value").orEmpty()
            val entities = runCatching { JSONObject(card).optJSONObject("media_entities") }.getOrNull()
            entities?.keys()?.forEach { key -> entities.optJSONObject(key)?.let(details::add) }
        }
        // Older syndication responses use a top-level video and/or photos collection.
        if (details.isEmpty()) {
            json.optJSONArray("photos")?.objects()?.forEach {
                details += JSONObject().put("type", "photo").put("media_url_https", it.optString("url"))
            }
            json.optJSONObject("video")?.let {
                details += JSONObject().put("type", "video").put("media_url_https", it.optString("poster"))
                    .put("video_info", JSONObject().put("variants", it.optJSONArray("variants") ?: JSONArray()))
            }
        }
        val heights = details.flatMap { variants(it) }.filter { !it.hls && it.height > 0 }
            .map { it.height }.distinct().sortedDescending()
        val medias = details.mapIndexedNotNull { index, detail ->
            when (detail.optString("type")) {
                "photo" -> imageUrl(detail.optString("media_url_https")).takeIf { it.isNotBlank() }?.let {
                    VideoMedia(it, VideoMediaKind.IMAGE, "x_${id}_${index + 1}.${it.toHttpUrl().queryParameter("format") ?: "jpg"}",
                        headers = headers, quality = "原图")
                }
                "video", "animated_gif" -> {
                    val options = variants(detail)
                    val mp4 = options.filter { !it.hls }
                    val eligible = if (qn != null && qn > 0) mp4.filter { it.height in 1..qn }.ifEmpty { mp4 } else mp4
                    val chosen = eligible.maxWithOrNull(compareBy<Variant> { it.bitrate }.thenBy { it.height })
                        ?: options.firstOrNull { it.hls }
                    chosen?.let {
                        VideoMedia(it.url, VideoMediaKind.VIDEO, "x_${id}_${index + 1}.mp4", isHls = it.hls,
                            headers = headers, quality = when { it.hls -> "HLS"; it.height > 0 -> "${it.height}P"; else -> "MP4" })
                    }
                }
                else -> null
            }
        }.distinctBy { it.url }
        if (medias.isEmpty()) throw VideoParseException(VideoParseErrorKind.UNSUPPORTED,
            "此 X 帖子没有可公开下载的图片或视频；私密/登录受限内容、直播及外部视频暂不支持")
        val user = json.optJSONObject("user") ?: JSONObject()
        val text = json.optString("text").ifBlank { "X 帖子 $id" }
        val selected = medias.firstOrNull { it.kind == VideoMediaKind.VIDEO && !it.isHls }?.quality
            ?.removeSuffix("P")?.toIntOrNull() ?: 0
        val skipped = details.size - medias.size
        return VideoParseResult(platform, title = text.lineSequence().first().take(120), author = user.optString("name"),
            avatar = cdnUrl(user.optString("profile_image_url_https")),
            cover = details.firstNotNullOfOrNull { cdnUrl(it.optString("media_url_https")).takeIf(String::isNotBlank) }.orEmpty(),
            text = text, timestampSeconds = runCatching { Instant.parse(json.optString("created_at")).epochSecond }.getOrDefault(0),
            url = "https://x.com/i/status/$id", medias = medias,
            notice = if (skipped > 0) "有 $skipped 项媒体未通过公开接口取得，已显示可用内容" else "",
            qualities = if (heights.size > 1) heights.map { VideoQuality(it, "${it}P") } else emptyList(), selectedQn = selected)
    }

    private data class Variant(val url: String, val bitrate: Long, val height: Int, val hls: Boolean)
    private fun variants(detail: JSONObject): List<Variant> =
        detail.optJSONObject("video_info")?.optJSONArray("variants")?.objects().orEmpty().mapNotNull {
            val url = cdnUrl(it.optString("url").ifBlank { it.optString("src") })
            val parsed = url.toHttpUrlOrNull() ?: return@mapNotNull null
            val type = it.optString("content_type").ifBlank { it.optString("type") }
            val hls = type.equals("application/x-mpegURL", true) || parsed.encodedPath.endsWith(".m3u8")
            if (!hls && type != "video/mp4" && !parsed.encodedPath.endsWith(".mp4")) return@mapNotNull null
            val dimensions = Regex("/(\\d+)x(\\d+)/").find(parsed.encodedPath)
            val height = dimensions?.groupValues?.drop(1)?.mapNotNull(String::toIntOrNull)?.minOrNull() ?: 0
            Variant(url, it.optLong("bitrate", 0), height, hls)
        }

    private fun imageUrl(value: String): String {
        val url = cdnUrl(value).toHttpUrlOrNull() ?: return ""
        val path = url.encodedPath
        val extension = path.substringAfterLast('.', "jpg").substringBefore(':').lowercase()
        val format = (url.queryParameter("format") ?: extension).takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val cleanPath = path.substringBeforeLast(':', path)
        return url.newBuilder().encodedPath(cleanPath).setQueryParameter("format", format)
            .setQueryParameter("name", "orig").build().toString()
    }
    private fun cdnUrl(value: String): String {
        val url = VideoHttp.secureUrl(value).toHttpUrlOrNull() ?: return ""
        return if (url.host == "twimg.com" || url.host.endsWith(".twimg.com")) url.toString() else ""
    }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
