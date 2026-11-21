package com.traxxiontech.savebox.data.remote

import android.util.Patterns
import com.traxxiontech.savebox.model.AnalyzedMedia
import com.traxxiontech.savebox.model.MediaFormatOption
import com.traxxiontech.savebox.model.MediaType
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
}

/**
 * Multi-platform URL media extractor for SaveBox.
 * Supports YouTube, TikTok, Instagram, Facebook, X/Twitter, Reddit, Vimeo,
 * Pinterest, SoundCloud, direct media URLs, and OpenGraph/HTML5 web pages.
 */
class UrlMediaBot(
    val okHttpClient: OkHttpClient
) {
    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "ts")
        private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

        fun sanitizeFileName(raw: String): String {
            return raw.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)
        }

        fun formatBytes(bytes: Long?): String? {
            if (bytes == null || bytes <= 0L) return null
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$bytes B"
            }
        }

        fun formatSecondsToText(seconds: Long): String? {
            if (seconds <= 0L) return null
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun validateAndNormalizeUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.removePrefix("http://")
            else -> "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        return if (Patterns.WEB_URL.matcher(parsed.toString()).matches()) parsed.toString() else null
    }

    suspend fun fetchMediaFromUrl(rawUrl: String): ApiResult<AnalyzedMedia> = withContext(Dispatchers.IO) {
        val validUrl = validateAndNormalizeUrl(rawUrl)
            ?: return@withContext ApiResult.Error("Please enter a valid web or media URL (e.g. https://...).")

        val parsedUrl = validUrl.toHttpUrlOrNull()
            ?: return@withContext ApiResult.Error("Invalid URL format.")
        val host = parsedUrl.host.lowercase().removePrefix("www.").removePrefix("m.")

        // 1. YouTube (watch, shorts, music.youtube, youtu.be)
        val ytVideoId = extractYouTubeVideoId(parsedUrl)
        if (ytVideoId != null) {
            val ytResult = fetchYouTubeMedia(ytVideoId, validUrl, host)
            if (ytResult != null && ytResult.formats.any { it.mediaType != MediaType.IMAGE }) {
                return@withContext ApiResult.Success(ytResult)
            }
        }

        // 2. TikTok (tiktok.com, vm.tiktok.com, vt.tiktok.com)
        if (host.contains("tiktok.com")) {
            val tiktokResult = fetchTikTokMedia(validUrl, host)
            if (tiktokResult != null && tiktokResult.formats.isNotEmpty()) {
                return@withContext ApiResult.Success(tiktokResult)
            }
        }

        // 3. X / Twitter (x.com, twitter.com)
        if (host == "x.com" || host == "twitter.com" || host.endsWith(".x.com") || host.endsWith(".twitter.com")) {
            val twitterResult = fetchTwitterXMedia(parsedUrl, validUrl, host)
            if (twitterResult != null && twitterResult.formats.isNotEmpty()) {
                return@withContext ApiResult.Success(twitterResult)
            }
        }

        // 4. Reddit (reddit.com, v.redd.it, redd.it)
        if (host.contains("reddit.com") || host.contains("redd.it")) {
            val redditResult = fetchRedditMedia(validUrl, host)
            if (redditResult != null && redditResult.formats.isNotEmpty()) {
                return@withContext ApiResult.Success(redditResult)
            }
        }

        // 5. Vimeo
        if (host.contains("vimeo.com")) {
            val vimeoResult = fetchVimeoMedia(parsedUrl, validUrl, host)
            if (vimeoResult != null && vimeoResult.formats.isNotEmpty()) {
                return@withContext ApiResult.Success(vimeoResult)
            }
        }

        // 6. Direct media probe (mp4, mp3, m4a, jpg, png, webp, webm, etc.)
        val directProbe = probeMediaUrl(validUrl)
        if (directProbe != null && !directProbe.contentType.contains("html", ignoreCase = true)) {
            val type = inferTypeFromMimeOrUrl(directProbe.contentType, validUrl)
            if (type != null) {
                val ext = inferExtension(directProbe.contentType, validUrl, type)
                val title = deriveTitleFromUrl(parsedUrl, type)
                val qualityLabel = when (type) {
                    MediaType.VIDEO -> "Original Video (${ext.uppercase()})"
                    MediaType.IMAGE -> "Original Image (${ext.uppercase()})"
                    MediaType.AUDIO -> "Original Audio (${ext.uppercase()})"
                }
                val option = MediaFormatOption(
                    id = UUID.randomUUID().toString(),
                    label = qualityLabel,
                    quality = qualityLabel,
                    mediaType = type,
                    downloadUrl = directProbe.resolvedUrl,
                    extension = ext,
                    estimatedSizeBytes = directProbe.contentLength,
                    sizeDisplay = formatBytes(directProbe.contentLength)
                )
                return@withContext ApiResult.Success(
                    AnalyzedMedia(
                        sourceUrl = validUrl,
                        sourceHost = host,
                        mediaType = type,
                        title = title,
                        author = host,
                        thumbnailUrl = if (type == MediaType.IMAGE) directProbe.resolvedUrl else null,
                        durationText = null,
                        durationMs = 0L,
                        formats = listOf(option),
                        suggestedFileName = sanitizeFileName(title).ifBlank { "savebox_${System.currentTimeMillis()}" }
                    )
                )
            }
        }

        // 7. Social & Streaming platforms resolver fallback (Instagram, Facebook, Threads, SoundCloud, Spotify, Pinterest, etc.)
        val socialPlatforms = listOf(
            "instagram.com", "facebook.com", "fb.watch", "threads.net",
            "soundcloud.com", "spotify.com", "pinterest.com", "pin.it",
            "twitch.tv", "dailymotion.com", "bilibili.com", "snapchat.com",
            "tiktok.com", "youtube.com", "youtu.be"
        )
        if (socialPlatforms.any { host.contains(it) }) {
            val cobaltResult = fetchViaCobaltResolver(validUrl, host)
            if (cobaltResult != null && cobaltResult.formats.isNotEmpty()) {
                return@withContext ApiResult.Success(cobaltResult)
            }
        }

        // 8. Generic HTML / OpenGraph / JSON-LD / HTML5 media extraction
        return@withContext try {
            val htmlRequest = Request.Builder()
                .url(validUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            okHttpClient.newCall(htmlRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val cobaltFallback = fetchViaCobaltResolver(validUrl, host)
                    if (cobaltFallback != null && cobaltFallback.formats.isNotEmpty()) {
                        return@withContext ApiResult.Success(cobaltFallback)
                    }
                    return@withContext ApiResult.Error("Unable to reach URL (HTTP ${response.code}).")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext ApiResult.Error("The page returned an empty response.")
                }
                val analyzed = parseHtmlForMedia(
                    html = body,
                    baseUrl = response.request.url,
                    originalUrl = validUrl,
                    host = host
                )
                if (analyzed == null || analyzed.formats.isEmpty()) {
                    val cobaltFallback = fetchViaCobaltResolver(validUrl, host)
                    if (cobaltFallback != null && cobaltFallback.formats.isNotEmpty()) {
                        ApiResult.Success(cobaltFallback)
                    } else {
                        ApiResult.Error("No downloadable video, audio, or image was found in this URL.")
                    }
                } else {
                    ApiResult.Success(analyzed)
                }
            }
        } catch (t: Throwable) {
            ApiResult.Error(t.localizedMessage ?: "Network error while fetching media from URL.", t)
        }
    }

    // ==================== TIKTOK EXTRACTOR ====================

    private suspend fun fetchTikTokMedia(originalUrl: String, host: String): AnalyzedMedia? {
        // 1. Try TikWM public JSON extractor (returns HD no-watermark, SD, MP3 audio, cover, & slides)
        val tikWm = fetchTikTokFromTikWm(originalUrl, host)
        if (tikWm != null && tikWm.formats.isNotEmpty()) return tikWm

        // 2. Resolve short link (vm.tiktok.com / vt.tiktok.com) and parse HTML rehydration JSON
        val htmlResult = fetchTikTokFromHtml(originalUrl, host)
        if (htmlResult != null && htmlResult.formats.isNotEmpty()) return htmlResult

        // 3. Fallback to Cobalt
        return fetchViaCobaltResolver(originalUrl, host)
    }

    private fun fetchTikTokFromTikWm(originalUrl: String, host: String): AnalyzedMedia? {
        return runCatching {
            val encoded = URLEncoder.encode(originalUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.tikwm.com/api/?url=$encoded&hd=1")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                if (root.get("code")?.asInt != 0) return@use null
                val data = root.getAsJsonObject("data") ?: return@use null

                val title = data.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "TikTok Video"
                val authorObj = data.getAsJsonObject("author")
                val author = authorObj?.get("nickname")?.asString
                    ?: authorObj?.get("unique_id")?.asString
                    ?: "@tiktok"
                val durationSec = data.get("duration")?.asLong ?: 0L
                val coverUrl = data.get("origin_cover")?.asString?.takeIf { it.startsWith("http") }
                    ?: data.get("cover")?.asString?.takeIf { it.startsWith("http") }

                val options = mutableListOf<MediaFormatOption>()

                val hdPlay = data.get("hdplay")?.asString?.takeIf { it.startsWith("http") }
                val hdSize = data.get("hd_size")?.asLong?.takeIf { it > 0L }
                if (hdPlay != null) {
                    options.add(
                        MediaFormatOption(
                            id = "tt_hd_${hdPlay.hashCode()}",
                            label = "HD No Watermark (MP4)",
                            quality = "1080p HD",
                            mediaType = MediaType.VIDEO,
                            downloadUrl = hdPlay,
                            extension = "mp4",
                            estimatedSizeBytes = hdSize,
                            sizeDisplay = formatBytes(hdSize),
                            resolution = "1080p"
                        )
                    )
                }

                val sdPlay = data.get("play")?.asString?.takeIf { it.startsWith("http") }
                val sdSize = data.get("size")?.asLong?.takeIf { it > 0L }
                if (sdPlay != null && sdPlay != hdPlay) {
                    options.add(
                        MediaFormatOption(
                            id = "tt_sd_${sdPlay.hashCode()}",
                            label = "Standard No Watermark (MP4)",
                            quality = "720p SD",
                            mediaType = MediaType.VIDEO,
                            downloadUrl = sdPlay,
                            extension = "mp4",
                            estimatedSizeBytes = sdSize,
                            sizeDisplay = formatBytes(sdSize),
                            resolution = "720p"
                        )
                    )
                }

                val musicUrl = data.get("music")?.asString?.takeIf { it.startsWith("http") }
                if (musicUrl != null) {
                    val musicTitle = data.getAsJsonObject("music_info")?.get("title")?.asString
                    options.add(
                        MediaFormatOption(
                            id = "tt_mp3_${musicUrl.hashCode()}",
                            label = if (!musicTitle.isNullOrBlank()) "Original Sound • $musicTitle" else "Original Sound (MP3)",
                            quality = "Audio MP3",
                            mediaType = MediaType.AUDIO,
                            downloadUrl = musicUrl,
                            extension = "mp3",
                            estimatedSizeBytes = null,
                            sizeDisplay = null
                        )
                    )
                }

                val imagesArr = data.getAsJsonArray("images")
                if (imagesArr != null && imagesArr.size() > 0) {
                    imagesArr.forEachIndexed { idx, imgEl ->
                        val imgUrl = imgEl.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.startsWith("http") }
                        if (imgUrl != null) {
                            options.add(
                                MediaFormatOption(
                                    id = "tt_img_$idx",
                                    label = "Slide Photo #${idx + 1} (JPG)",
                                    quality = "HD Photo",
                                    mediaType = MediaType.IMAGE,
                                    downloadUrl = imgUrl,
                                    extension = "jpg"
                                )
                            )
                        }
                    }
                } else if (coverUrl != null && options.isNotEmpty()) {
                    options.add(
                        MediaFormatOption(
                            id = "tt_cover",
                            label = "Cover Artwork (JPG)",
                            quality = "Cover Image",
                            mediaType = MediaType.IMAGE,
                            downloadUrl = coverUrl,
                            extension = "jpg"
                        )
                    )
                }

                if (options.isEmpty()) return@use null

                AnalyzedMedia(
                    sourceUrl = originalUrl,
                    sourceHost = host,
                    mediaType = options.first().mediaType,
                    title = title,
                    author = author,
                    thumbnailUrl = coverUrl,
                    durationText = formatSecondsToText(durationSec),
                    durationMs = durationSec * 1000L,
                    formats = options,
                    suggestedFileName = sanitizeFileName(title).ifBlank { "tiktok_${System.currentTimeMillis()}" }
                )
            }
        }.getOrNull()
    }

    private fun fetchTikTokFromHtml(originalUrl: String, host: String): AnalyzedMedia? {
        return runCatching {
            val req = Request.Builder()
                .url(originalUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string().orEmpty()
                val jsonObj = extractJsonObjectAfterMarker(html, """id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">""")
                    ?: extractJsonObjectAfterMarker(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
                    ?: return@use null
                val defaultScope = jsonObj.getAsJsonObject("__DEFAULT_SCOPE__") ?: return@use null
                val detail = defaultScope.getAsJsonObject("webapp.video-detail")
                    ?.getAsJsonObject("itemInfo")
                    ?.getAsJsonObject("itemStruct")
                    ?: return@use null

                val desc = detail.get("desc")?.asString?.takeIf { it.isNotBlank() } ?: "TikTok Video"
                val author = detail.getAsJsonObject("author")?.get("nickname")?.asString
                val video = detail.getAsJsonObject("video")
                val playAddr = video?.get("playAddr")?.asString?.takeIf { it.startsWith("http") }
                    ?: video?.get("downloadAddr")?.asString?.takeIf { it.startsWith("http") }
                val cover = video?.get("originCover")?.asString ?: video?.get("cover")?.asString
                val durationSec = video?.get("duration")?.asLong ?: 0L
                val musicUrl = detail.getAsJsonObject("music")?.get("playUrl")?.asString?.takeIf { it.startsWith("http") }

                val options = mutableListOf<MediaFormatOption>()
                if (playAddr != null) {
                    options.add(
                        MediaFormatOption(
                            id = "tt_html_mp4",
                            label = "TikTok Video (MP4)",
                            quality = "HD Video",
                            mediaType = MediaType.VIDEO,
                            downloadUrl = playAddr,
                            extension = "mp4"
                        )
                    )
                }
                if (musicUrl != null) {
                    options.add(
                        MediaFormatOption(
                            id = "tt_html_mp3",
                            label = "Original Audio (MP3)",
                            quality = "Audio MP3",
                            mediaType = MediaType.AUDIO,
                            downloadUrl = musicUrl,
                            extension = "mp3"
                        )
                    )
                }
                if (options.isEmpty()) return@use null

                AnalyzedMedia(
                    sourceUrl = originalUrl,
                    sourceHost = host,
                    mediaType = MediaType.VIDEO,
                    title = desc,
                    author = author,
                    thumbnailUrl = cover,
                    durationText = formatSecondsToText(durationSec),
                    durationMs = durationSec * 1000L,
                    formats = options,
                    suggestedFileName = sanitizeFileName(desc).ifBlank { "tiktok_${System.currentTimeMillis()}" }
                )
            }
        }.getOrNull()
    }

    // ==================== X / TWITTER EXTRACTOR ====================

    private fun fetchTwitterXMedia(parsedUrl: HttpUrl, originalUrl: String, host: String): AnalyzedMedia? {
        val path = parsedUrl.encodedPath.trimStart('/')
        if (path.isBlank()) return null
        val endpoints = listOf("https://api.fxtwitter.com/$path", "https://api.vxtwitter.com/$path")
        for (ep in endpoints) {
            val result = runCatching {
                val req = Request.Builder()
                    .url(ep)
                    .header("User-Agent", DESKTOP_UA)
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val str = resp.body?.string() ?: return@use null
                    val root = JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null

                    val tweet = root.getAsJsonObject("tweet")
                    val text = tweet?.get("text")?.asString
                        ?: root.get("text")?.asString
                        ?: "X Post Media"
                    val author = tweet?.getAsJsonObject("author")?.get("name")?.asString
                        ?: root.get("user_name")?.asString
                        ?: host

                    val options = mutableListOf<MediaFormatOption>()
                    var thumb: String? = null

                    // fxtwitter schema
                    val fxVideos = tweet?.getAsJsonObject("media")?.getAsJsonArray("videos")
                    fxVideos?.forEachIndexed { vIdx, vEl ->
                        val vObj = vEl.asJsonObject
                        thumb = thumb ?: vObj.get("thumbnail_url")?.asString
                        val variants = vObj.getAsJsonArray("variants")
                        variants?.forEachIndexed { varIdx, varEl ->
                            val vo = varEl.asJsonObject
                            val url = vo.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                            val ct = vo.get("content_type")?.asString.orEmpty()
                            if (ct.contains("mp4", true) || url.contains(".mp4")) {
                                val br = vo.get("bitrate")?.asLong ?: 0L
                                val qLabel = if (br > 1_500_000) "1080p HD" else if (br > 600_000) "720p HD" else "480p SD"
                                options.add(
                                    MediaFormatOption(
                                        id = "x_v_${vIdx}_$varIdx",
                                        label = "$qLabel (MP4)",
                                        quality = qLabel,
                                        mediaType = MediaType.VIDEO,
                                        downloadUrl = url,
                                        extension = "mp4"
                                    )
                                )
                            }
                        }
                    }

                    val fxPhotos = tweet?.getAsJsonObject("media")?.getAsJsonArray("photos")
                    fxPhotos?.forEachIndexed { idx, pEl ->
                        val url = pEl.asJsonObject.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                        thumb = thumb ?: url
                        options.add(
                            MediaFormatOption(
                                id = "x_p_$idx",
                                label = "HD Photo #${idx + 1}",
                                quality = "Original Photo",
                                mediaType = MediaType.IMAGE,
                                downloadUrl = url,
                                extension = "jpg"
                            )
                        )
                    }

                    // vxtwitter schema fallback
                    if (options.isEmpty()) {
                        root.getAsJsonArray("media_extended")?.forEachIndexed { idx, mEl ->
                            val mo = mEl.asJsonObject
                            val url = mo.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                            val typeStr = mo.get("type")?.asString.orEmpty()
                            thumb = thumb ?: mo.get("thumbnail_url")?.asString ?: url
                            val isVid = typeStr.contains("video", true) || typeStr.contains("gif", true)
                            options.add(
                                MediaFormatOption(
                                    id = "vx_$idx",
                                    label = if (isVid) "Video (MP4)" else "Photo (JPG)",
                                    quality = if (isVid) "HD Video" else "Original Image",
                                    mediaType = if (isVid) MediaType.VIDEO else MediaType.IMAGE,
                                    downloadUrl = url,
                                    extension = if (isVid) "mp4" else "jpg"
                                )
                            )
                        }
                    }

                    if (options.isEmpty()) return@use null
                    val sorted = options.distinctBy { it.downloadUrl }
                        .sortedByDescending { extractResolutionScore(it.quality) }

                    AnalyzedMedia(
                        sourceUrl = originalUrl,
                        sourceHost = host,
                        mediaType = sorted.first().mediaType,
                        title = text.take(90),
                        author = author,
                        thumbnailUrl = thumb,
                        durationText = null,
                        durationMs = 0L,
                        formats = sorted,
                        suggestedFileName = sanitizeFileName(text).ifBlank { "x_media_${System.currentTimeMillis()}" }
                    )
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    // ==================== REDDIT EXTRACTOR ====================

    private fun fetchRedditMedia(originalUrl: String, host: String): AnalyzedMedia? {
        return runCatching {
            val cleanBase = originalUrl.substringBefore("?").trimEnd('/')
            val req = Request.Builder()
                .url("$cleanBase.json")
                .header("User-Agent", "android:com.traxxiontech.savebox:1.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val arr = JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray ?: return@use null
                val postData = arr.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("data")
                    ?.getAsJsonArray("children")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("data")
                    ?: return@use null

                val title = postData.get("title")?.asString ?: "Reddit Media"
                val author = postData.get("author")?.asString?.let { "u/$it" } ?: "Reddit"
                val thumb = postData.get("thumbnail")?.asString?.takeIf { it.startsWith("http") }

                val options = mutableListOf<MediaFormatOption>()
                val redditVideo = postData.getAsJsonObject("secure_media")?.getAsJsonObject("reddit_video")
                    ?: postData.getAsJsonObject("media")?.getAsJsonObject("reddit_video")
                val fallbackUrl = redditVideo?.get("fallback_url")?.asString?.takeIf { it.startsWith("http") }
                if (fallbackUrl != null) {
                    val height = redditVideo.get("height")?.asInt ?: 720
                    options.add(
                        MediaFormatOption(
                            id = "reddit_vid",
                            label = "${height}p Video (MP4)",
                            quality = "${height}p",
                            mediaType = MediaType.VIDEO,
                            downloadUrl = unescapeJsonUrl(fallbackUrl),
                            extension = "mp4"
                        )
                    )
                }
                val directImg = postData.get("url_overridden_by_dest")?.asString
                    ?: postData.get("url")?.asString
                if (directImg != null && inferTypeFromMimeOrUrl("", directImg) == MediaType.IMAGE) {
                    options.add(
                        MediaFormatOption(
                            id = "reddit_img",
                            label = "Original Image",
                            quality = "HD Image",
                            mediaType = MediaType.IMAGE,
                            downloadUrl = directImg,
                            extension = inferExtension("", directImg, MediaType.IMAGE)
                        )
                    )
                }

                if (options.isEmpty()) return@use null
                AnalyzedMedia(
                    sourceUrl = originalUrl,
                    sourceHost = host,
                    mediaType = options.first().mediaType,
                    title = title,
                    author = author,
                    thumbnailUrl = thumb ?: directImg,
                    durationText = null,
                    durationMs = 0L,
                    formats = options,
                    suggestedFileName = sanitizeFileName(title).ifBlank { "reddit_${System.currentTimeMillis()}" }
                )
            }
        }.getOrNull()
    }

    // ==================== VIMEO EXTRACTOR ====================

    private fun fetchVimeoMedia(parsedUrl: HttpUrl, originalUrl: String, host: String): AnalyzedMedia? {
        val vimeoId = parsedUrl.pathSegments.lastOrNull { it.all { ch -> ch.isDigit() } && it.length >= 5 }
            ?: return null
        return runCatching {
            val req = Request.Builder()
                .url("https://player.vimeo.com/video/$vimeoId/config")
                .header("User-Agent", DESKTOP_UA)
                .header("Referer", "https://vimeo.com/")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                val videoObj = root.getAsJsonObject("video")
                val title = videoObj?.get("title")?.asString ?: "Vimeo Video ($vimeoId)"
                val author = videoObj?.getAsJsonObject("owner")?.get("name")?.asString ?: "Vimeo"
                val durationSec = videoObj?.get("duration")?.asLong ?: 0L
                val thumb = videoObj?.getAsJsonObject("thumbs")?.entrySet()?.lastOrNull()?.value?.asString

                val progressive = root.getAsJsonObject("request")
                    ?.getAsJsonObject("files")
                    ?.getAsJsonArray("progressive")
                    ?: return@use null

                val options = progressive.mapIndexedNotNull { idx, el ->
                    val o = el.asJsonObject
                    val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@mapIndexedNotNull null
                    val q = o.get("quality")?.asString ?: "HD"
                    val w = o.get("width")?.asInt
                    val h = o.get("height")?.asInt
                    MediaFormatOption(
                        id = "vimeo_$idx",
                        label = "$q (MP4)",
                        quality = q,
                        mediaType = MediaType.VIDEO,
                        downloadUrl = url,
                        extension = "mp4",
                        resolution = if (w != null && h != null) "${w}x${h}" else null
                    )
                }.sortedByDescending { extractResolutionScore(it.quality) }

                if (options.isEmpty()) return@use null
                AnalyzedMedia(
                    sourceUrl = originalUrl,
                    sourceHost = host,
                    mediaType = MediaType.VIDEO,
                    title = title,
                    author = author,
                    thumbnailUrl = thumb,
                    durationText = formatSecondsToText(durationSec),
                    durationMs = durationSec * 1000L,
                    formats = options,
                    suggestedFileName = sanitizeFileName(title).ifBlank { "vimeo_$vimeoId" }
                )
            }
        }.getOrNull()
    }

    // ==================== COBALT MULTI-PLATFORM RESOLVER ====================

    private suspend fun fetchViaCobaltResolver(originalUrl: String, host: String): AnalyzedMedia? {
        val instances = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt-api.kwiatekmiki.com/"
        )
        for (apiBase in instances) {
            val videoResult = queryCobalt(apiBase, originalUrl, audioOnly = false, host = host)
            if (videoResult != null) {
                // Also try querying audio-only so user can choose Video OR Audio MP3
                val audioResult = queryCobalt(apiBase, originalUrl, audioOnly = true, host = host)
                val mergedFormats = buildList {
                    addAll(videoResult.formats)
                    audioResult?.formats?.filter { it.mediaType == MediaType.AUDIO }?.forEach { aFmt ->
                        if (none { it.downloadUrl == aFmt.downloadUrl }) add(aFmt)
                    }
                }
                return videoResult.copy(formats = mergedFormats)
            }
        }
        return null
    }

    private fun queryCobalt(
        endpoint: String,
        originalUrl: String,
        audioOnly: Boolean,
        host: String
    ): AnalyzedMedia? {
        return runCatching {
            val payload = JSONObject().apply {
                put("url", originalUrl)
                put("downloadMode", if (audioOnly) "audio" else "auto")
                put("videoQuality", "1080")
                put("audioFormat", "mp3")
                put("filenameStyle", "pretty")
            }
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "SaveBox/1.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val str = resp.body?.string() ?: return@use null
                val root = JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                val status = root.get("status")?.asString.orEmpty()
                val options = mutableListOf<MediaFormatOption>()
                var thumb: String? = null

                when (status) {
                    "tunnel", "redirect" -> {
                        val dlUrl = root.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@use null
                        val filename = root.get("filename")?.asString.orEmpty()
                        val mType = if (audioOnly) MediaType.AUDIO else (inferTypeFromMimeOrUrl("", filename.ifBlank { dlUrl }) ?: MediaType.VIDEO)
                        val ext = if (audioOnly) "mp3" else inferExtension("", filename.ifBlank { dlUrl }, mType)
                        options.add(
                            MediaFormatOption(
                                id = "cb_${dlUrl.hashCode()}",
                                label = if (audioOnly) "Audio Track (MP3)" else "Best Quality (${ext.uppercase()})",
                                quality = if (audioOnly) "Audio MP3" else "1080p HD",
                                mediaType = mType,
                                downloadUrl = dlUrl,
                                extension = ext
                            )
                        )
                    }
                    "picker" -> {
                        val picker = root.getAsJsonArray("picker") ?: return@use null
                        picker.forEachIndexed { idx, el ->
                            val o = el.asJsonObject
                            val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                            val type = o.get("type")?.asString.orEmpty()
                            thumb = thumb ?: o.get("thumb")?.asString
                            val mType = when {
                                type.equals("photo", true) -> MediaType.IMAGE
                                audioOnly -> MediaType.AUDIO
                                else -> MediaType.VIDEO
                            }
                            val ext = when (mType) {
                                MediaType.IMAGE -> "jpg"
                                MediaType.AUDIO -> "mp3"
                                MediaType.VIDEO -> "mp4"
                            }
                            options.add(
                                MediaFormatOption(
                                    id = "cb_p_$idx",
                                    label = "${mType.label} #${idx + 1} (${ext.uppercase()})",
                                    quality = "HD ${mType.label}",
                                    mediaType = mType,
                                    downloadUrl = url,
                                    extension = ext
                                )
                            )
                        }
                        root.get("audio")?.asString?.takeIf { it.startsWith("http") }?.let { audioUrl ->
                            options.add(
                                MediaFormatOption(
                                    id = "cb_picker_audio",
                                    label = "Original Audio (MP3)",
                                    quality = "Audio MP3",
                                    mediaType = MediaType.AUDIO,
                                    downloadUrl = audioUrl,
                                    extension = "mp3"
                                )
                            )
                        }
                    }
                    else -> return@use null
                }

                if (options.isEmpty()) return@use null
                val filenameTitle = root.get("filename")?.asString
                    ?.substringBeforeLast('.')
                    ?.replace('_', ' ')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Media from $host"

                AnalyzedMedia(
                    sourceUrl = originalUrl,
                    sourceHost = host,
                    mediaType = options.first().mediaType,
                    title = filenameTitle,
                    author = host,
                    thumbnailUrl = thumb,
                    durationText = null,
                    durationMs = 0L,
                    formats = options,
                    suggestedFileName = sanitizeFileName(filenameTitle)
                )
            }
        }.getOrNull()
    }

    // ==================== YOUTUBE EXTRACTOR (MULTI-CLIENT + PIPED + INVIDIOUS + COBALT) ====================

    private suspend fun fetchYouTubeMedia(
        videoId: String,
        originalUrl: String,
        host: String
    ): AnalyzedMedia? {
        // 1. Try YouTube Innertube mobile/VR/TV clients first (they return direct stream URLs without cipher!)
        val innertubeJson = fetchYouTubePlayerJsonViaInnertube(videoId)
        if (innertubeJson != null) {
            val parsed = parseYouTubePlayerResponse(innertubeJson, videoId, originalUrl, host)
            if (parsed != null && parsed.formats.any { it.mediaType != MediaType.IMAGE }) {
                return parsed
            }
        }

        // 2. Try watch-page ytInitialPlayerResponse
        val htmlPlayerJson = fetchYouTubePlayerJsonFromWatchPage(videoId)
        if (htmlPlayerJson != null && hasUsableStreamUrls(htmlPlayerJson)) {
            val parsed = parseYouTubePlayerResponse(htmlPlayerJson, videoId, originalUrl, host)
            if (parsed != null && parsed.formats.any { it.mediaType != MediaType.IMAGE }) {
                return parsed
            }
        }

        // 3. Try Piped public API instances (returns clean MP4/WebM/M4A stream URLs + sizes + cover art)
        val piped = fetchYouTubeFromPiped(videoId, originalUrl, host)
        if (piped != null && piped.formats.any { it.mediaType != MediaType.IMAGE }) {
            return piped
        }

        // 4. Try Invidious public API instances
        val invidious = fetchYouTubeFromInvidious(videoId, originalUrl, host)
        if (invidious != null && invidious.formats.any { it.mediaType != MediaType.IMAGE }) {
            return invidious
        }

        // 5. Fallback to Cobalt resolver enriched with YouTube oEmbed metadata & cover art
        val cobalt = fetchViaCobaltResolver(originalUrl, host)
        if (cobalt != null && cobalt.formats.isNotEmpty()) {
            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val oembedTitle = fetchYouTubeOEmbedTitle(videoId)
            val finalFormats = cobalt.formats + MediaFormatOption(
                id = "yt_thumb_$videoId",
                label = "Cover Artwork (HD JPG)",
                quality = "Cover Art",
                mediaType = MediaType.IMAGE,
                downloadUrl = thumb,
                extension = "jpg"
            )
            return cobalt.copy(
                title = oembedTitle ?: cobalt.title,
                thumbnailUrl = cobalt.thumbnailUrl ?: thumb,
                formats = finalFormats,
                suggestedFileName = sanitizeFileName(oembedTitle ?: cobalt.title)
            )
        }

        return null
    }

    private fun fetchYouTubeOEmbedTitle(videoId: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
                .header("User-Agent", DESKTOP_UA)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val str = resp.body?.string() ?: return@use null
                JsonParser.parseString(str).asJsonObject.get("title")?.asString
            }
        }.getOrNull()
    }

    private fun fetchYouTubePlayerJsonFromWatchPage(videoId: String): JsonObject? {
        return runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string().orEmpty()
                extractJsonObjectAfterMarker(html, "ytInitialPlayerResponse = ")
                    ?: extractJsonObjectAfterMarker(html, "var ytInitialPlayerResponse = ")
            }
        }.getOrNull()
    }

    private data class InnertubeClientConfig(
        val clientIdHeader: String,
        val clientVersion: String,
        val userAgent: String,
        val clientJson: JSONObject
    )

    private fun fetchYouTubePlayerJsonViaInnertube(videoId: String): JsonObject? {
        val clients = listOf(
            InnertubeClientConfig(
                clientIdHeader = "28",
                clientVersion = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                clientJson = JSONObject().apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.60.19")
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("androidSdkVersion", 32)
                    put("hl", "en")
                    put("gl", "US")
                }
            ),
            InnertubeClientConfig(
                clientIdHeader = "5",
                clientVersion = "19.45.4",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                clientJson = JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.45.4")
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.1.0.22B83")
                    put("hl", "en")
                    put("gl", "US")
                }
            ),
            InnertubeClientConfig(
                clientIdHeader = "3",
                clientVersion = "19.44.38",
                userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip",
                clientJson = JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.44.38")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                }
            ),
            InnertubeClientConfig(
                clientIdHeader = "85",
                clientVersion = "2.0",
                userAgent = DESKTOP_UA,
                clientJson = JSONObject().apply {
                    put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                    put("clientVersion", "2.0")
                    put("hl", "en")
                    put("gl", "US")
                }
            )
        )

        for (cfg in clients) {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", cfg.clientJson)
                    if (cfg.clientIdHeader == "85") {
                        put("thirdParty", JSONObject().apply {
                            put("embedUrl", "https://www.youtube.com/")
                        })
                    }
                })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val parsed = runCatching {
                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", cfg.userAgent)
                    .header("X-YouTube-Client-Name", cfg.clientIdHeader)
                    .header("X-YouTube-Client-Version", cfg.clientVersion)
                    .header("Origin", "https://www.youtube.com")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val str = resp.body?.string() ?: return@use null
                    JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject
                }
            }.getOrNull()

            if (parsed != null && hasUsableStreamUrls(parsed)) {
                return parsed
            }
        }
        return null
    }

    private fun fetchYouTubeFromPiped(
        videoId: String,
        originalUrl: String,
        host: String
    ): AnalyzedMedia? {
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.leptons.xyz"
        )
        for (base in pipedInstances) {
            val parsed = runCatching {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", DESKTOP_UA)
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val str = resp.body?.string() ?: return@use null
                    val root = JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null

                    val title = root.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "YouTube Video ($videoId)"
                    val author = root.get("uploader")?.asString
                    val durationSec = root.get("duration")?.asLong ?: 0L
                    val thumbUrl = root.get("thumbnailUrl")?.asString?.takeIf { it.startsWith("http") }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val options = mutableListOf<MediaFormatOption>()

                    root.getAsJsonArray("videoStreams")?.forEachIndexed { idx, el ->
                        val o = el.asJsonObject
                        val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                        val videoOnly = o.get("videoOnly")?.asBoolean ?: false
                        val quality = o.get("quality")?.asString ?: "720p"
                        val mime = o.get("mimeType")?.asString.orEmpty()
                        val ext = if (mime.contains("webm", true)) "webm" else "mp4"
                        val sizeBytes = o.get("contentLength")?.asLong?.takeIf { it > 0L }
                        val label = if (!videoOnly) "$quality (${ext.uppercase()})" else "$quality Video (${ext.uppercase()})"
                        options.add(
                            MediaFormatOption(
                                id = "piped_v_$idx",
                                label = label,
                                quality = quality,
                                mediaType = MediaType.VIDEO,
                                downloadUrl = url,
                                extension = ext,
                                estimatedSizeBytes = sizeBytes,
                                sizeDisplay = formatBytes(sizeBytes)
                            )
                        )
                    }

                    root.getAsJsonArray("audioStreams")?.forEachIndexed { idx, el ->
                        val o = el.asJsonObject
                        val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                        val bitrateKbps = (o.get("bitrate")?.asLong ?: 128000L) / 1000L
                        val mime = o.get("mimeType")?.asString.orEmpty()
                        val ext = if (mime.contains("mp4", true) || mime.contains("m4a", true)) "m4a" else "webm"
                        val sizeBytes = o.get("contentLength")?.asLong?.takeIf { it > 0L }
                        options.add(
                            MediaFormatOption(
                                id = "piped_a_$idx",
                                label = "Audio ${bitrateKbps}kbps (${ext.uppercase()})",
                                quality = "Audio ${bitrateKbps}kbps",
                                mediaType = MediaType.AUDIO,
                                downloadUrl = url,
                                extension = ext,
                                estimatedSizeBytes = sizeBytes,
                                sizeDisplay = formatBytes(sizeBytes)
                            )
                        )
                    }

                    if (options.isEmpty()) return@use null
                    options.add(
                        MediaFormatOption(
                            id = "yt_thumb_$videoId",
                            label = "Cover Artwork (HD JPG)",
                            quality = "Cover Art",
                            mediaType = MediaType.IMAGE,
                            downloadUrl = thumbUrl,
                            extension = "jpg"
                        )
                    )

                    val sorted = options
                        .distinctBy { "${it.mediaType}_${it.quality}_${it.extension}" }
                        .sortedWith(
                            compareBy<MediaFormatOption> {
                                when (it.mediaType) {
                                    MediaType.VIDEO -> 0
                                    MediaType.AUDIO -> 1
                                    MediaType.IMAGE -> 2
                                }
                            }.thenByDescending { extractResolutionScore(it.quality) }
                        )

                    AnalyzedMedia(
                        sourceUrl = originalUrl,
                        sourceHost = host,
                        mediaType = MediaType.VIDEO,
                        title = title,
                        author = author,
                        thumbnailUrl = thumbUrl,
                        durationText = formatSecondsToText(durationSec),
                        durationMs = durationSec * 1000L,
                        formats = sorted,
                        suggestedFileName = sanitizeFileName(title).ifBlank { "youtube_$videoId" }
                    )
                }
            }.getOrNull()

            if (parsed != null && parsed.formats.any { it.mediaType != MediaType.IMAGE }) {
                return parsed
            }
        }
        return null
    }

    private fun fetchYouTubeFromInvidious(
        videoId: String,
        originalUrl: String,
        host: String
    ): AnalyzedMedia? {
        val instances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://iv.datura.network"
        )
        for (base in instances) {
            val parsed = runCatching {
                val req = Request.Builder()
                    .url("$base/api/v1/videos/$videoId")
                    .header("User-Agent", DESKTOP_UA)
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val str = resp.body?.string() ?: return@use null
                    val root = JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                    val title = root.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "YouTube Video ($videoId)"
                    val author = root.get("author")?.asString
                    val durationSec = root.get("lengthSeconds")?.asLong ?: 0L
                    val thumbUrl = root.getAsJsonArray("videoThumbnails")
                        ?.firstOrNull()?.asJsonObject?.get("url")?.asString
                        ?.takeIf { it.startsWith("http") }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val options = mutableListOf<MediaFormatOption>()
                    root.getAsJsonArray("formatStreams")?.forEachIndexed { idx, el ->
                        val o = el.asJsonObject
                        val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                        val q = o.get("qualityLabel")?.asString ?: o.get("quality")?.asString ?: "720p"
                        val container = o.get("container")?.asString ?: "mp4"
                        options.add(
                            MediaFormatOption(
                                id = "inv_v_$idx",
                                label = "$q (${container.uppercase()})",
                                quality = q,
                                mediaType = MediaType.VIDEO,
                                downloadUrl = url,
                                extension = container
                            )
                        )
                    }
                    root.getAsJsonArray("adaptiveFormats")?.forEachIndexed { idx, el ->
                        val o = el.asJsonObject
                        val url = o.get("url")?.asString?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                        val typeStr = o.get("type")?.asString.orEmpty()
                        val isAudio = typeStr.startsWith("audio/", true)
                        val isVideo = typeStr.startsWith("video/", true)
                        if (!isAudio && !isVideo) return@forEachIndexed
                        val ext = when {
                            typeStr.contains("webm", true) -> "webm"
                            isAudio -> "m4a"
                            else -> "mp4"
                        }
                        val q = if (isAudio) {
                            val br = (o.get("bitrate")?.asString?.toLongOrNull() ?: 128000L) / 1000L
                            "Audio ${br}kbps"
                        } else {
                            o.get("qualityLabel")?.asString ?: "Video"
                        }
                        val clen = o.get("clen")?.asString?.toLongOrNull()
                        options.add(
                            MediaFormatOption(
                                id = "inv_adp_$idx",
                                label = if (isAudio) "$q (${ext.uppercase()})" else q,
                                quality = q,
                                mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO,
                                downloadUrl = url,
                                extension = ext,
                                estimatedSizeBytes = clen,
                                sizeDisplay = formatBytes(clen)
                            )
                        )
                    }
                    if (options.isEmpty()) return@use null
                    options.add(
                        MediaFormatOption(
                            id = "yt_thumb_$videoId",
                            label = "Cover Artwork (HD JPG)",
                            quality = "Cover Art",
                            mediaType = MediaType.IMAGE,
                            downloadUrl = thumbUrl,
                            extension = "jpg"
                        )
                    )
                    val sorted = options.distinctBy { "${it.mediaType}_${it.quality}_${it.extension}" }
                        .sortedWith(
                            compareBy<MediaFormatOption> {
                                when (it.mediaType) {
                                    MediaType.VIDEO -> 0
                                    MediaType.AUDIO -> 1
                                    MediaType.IMAGE -> 2
                                }
                            }.thenByDescending { extractResolutionScore(it.quality) }
                        )
                    AnalyzedMedia(
                        sourceUrl = originalUrl,
                        sourceHost = host,
                        mediaType = MediaType.VIDEO,
                        title = title,
                        author = author,
                        thumbnailUrl = thumbUrl,
                        durationText = formatSecondsToText(durationSec),
                        durationMs = durationSec * 1000L,
                        formats = sorted,
                        suggestedFileName = sanitizeFileName(title).ifBlank { "youtube_$videoId" }
                    )
                }
            }.getOrNull()
            if (parsed != null && parsed.formats.any { it.mediaType != MediaType.IMAGE }) {
                return parsed
            }
        }
        return null
    }

    internal fun parseYouTubePlayerResponse(
        root: JsonObject,
        videoId: String,
        originalUrl: String,
        host: String
    ): AnalyzedMedia? {
        val details = root.getAsJsonObject("videoDetails")
        val title = details?.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "YouTube Video ($videoId)"
        val author = details?.get("author")?.asString?.takeIf { it.isNotBlank() }
        val durationSec = details?.get("lengthSeconds")?.asString?.toLongOrNull() ?: 0L
        val durationMs = durationSec * 1000L
        val durationText = formatSecondsToText(durationSec)

        val thumbUrl = details?.getAsJsonObject("thumbnail")
            ?.getAsJsonArray("thumbnails")
            ?.lastOrNull()
            ?.asJsonObject
            ?.get("url")?.asString
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val streamingData = root.getAsJsonObject("streamingData")
        val options = mutableListOf<MediaFormatOption>()

        fun processArray(arr: com.google.gson.JsonArray?, isMuxed: Boolean) {
            if (arr == null) return
            for (el in arr) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val directUrl = obj.get("url")?.asString?.takeIf { it.startsWith("http") } ?: continue
                val mime = obj.get("mimeType")?.asString.orEmpty()
                val isAudio = mime.startsWith("audio/", ignoreCase = true)
                val isVideo = mime.startsWith("video/", ignoreCase = true)
                if (!isAudio && !isVideo) continue

                val mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO
                val ext = when {
                    mime.contains("webm", true) -> "webm"
                    isAudio && mime.contains("mp4", true) -> "m4a"
                    isAudio -> "m4a"
                    else -> "mp4"
                }

                val qualityLabel = obj.get("qualityLabel")?.asString?.takeIf { it.isNotBlank() }
                    ?: if (isAudio) {
                        val abr = (obj.get("averageBitrate")?.asLong ?: obj.get("bitrate")?.asLong ?: 128000L) / 1000L
                        "Audio ${abr}kbps"
                    } else {
                        val h = obj.get("height")?.asInt ?: 0
                        if (h > 0) "${h}p" else obj.get("quality")?.asString ?: "Video"
                    }

                val width = obj.get("width")?.asInt
                val height = obj.get("height")?.asInt
                val resolution = if (width != null && height != null && width > 0 && height > 0) "${width}x${height}" else null

                val contentLength = obj.get("contentLength")?.asString?.toLongOrNull()
                    ?: obj.get("bitrate")?.asLong?.let { br ->
                        val approxMs = obj.get("approxDurationMs")?.asString?.toLongOrNull() ?: durationMs
                        if (approxMs > 0L) (br * (approxMs / 1000.0) / 8.0).toLong() else null
                    }

                val displayLabel = if (isMuxed && !isAudio) "$qualityLabel (${ext.uppercase()})" else qualityLabel

                options.add(
                    MediaFormatOption(
                        id = "yt_${obj.get("itag")?.asString ?: UUID.randomUUID()}",
                        label = displayLabel,
                        quality = qualityLabel,
                        mediaType = mediaType,
                        downloadUrl = directUrl,
                        extension = ext,
                        estimatedSizeBytes = contentLength,
                        sizeDisplay = formatBytes(contentLength),
                        resolution = resolution
                    )
                )
            }
        }

        processArray(streamingData?.getAsJsonArray("formats"), isMuxed = true)
        processArray(streamingData?.getAsJsonArray("adaptiveFormats"), isMuxed = false)

        // IMPORTANT: Do NOT return a cover-art-only result if no actual video/audio streams were extracted!
        if (options.isEmpty()) return null

        // Include thumbnail/cover artwork as a bonus alongside playable streams
        options.add(
            MediaFormatOption(
                id = "yt_thumb_$videoId",
                label = "Cover Thumbnail (HD JPG)",
                quality = "HD Image",
                mediaType = MediaType.IMAGE,
                downloadUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg".takeIf {
                    thumbUrl.contains("maxres")
                } ?: thumbUrl,
                extension = "jpg",
                estimatedSizeBytes = null,
                sizeDisplay = null
            )
        )

        val deduped = options
            .distinctBy { "${it.mediaType}_${it.quality}_${it.extension}" }
            .sortedWith(
                compareBy<MediaFormatOption> {
                    when (it.mediaType) {
                        MediaType.VIDEO -> 0
                        MediaType.AUDIO -> 1
                        MediaType.IMAGE -> 2
                    }
                }.thenByDescending { extractResolutionScore(it.quality) }
                    .thenByDescending { it.estimatedSizeBytes ?: 0L }
            )

        return AnalyzedMedia(
            sourceUrl = originalUrl,
            sourceHost = host,
            mediaType = MediaType.VIDEO,
            title = title,
            author = author,
            thumbnailUrl = thumbUrl,
            durationText = durationText,
            durationMs = durationMs,
            formats = deduped,
            suggestedFileName = sanitizeFileName(title).ifBlank { "youtube_$videoId" }
        )
    }

    private fun hasUsableStreamUrls(root: JsonObject): Boolean {
        val sd = root.getAsJsonObject("streamingData") ?: return false
        val formats = sd.getAsJsonArray("formats")
        val adaptive = sd.getAsJsonArray("adaptiveFormats")
        val hasFmt = formats?.any {
            it.isJsonObject && it.asJsonObject.get("url")?.asString?.startsWith("http") == true
        } == true
        val hasAdp = adaptive?.any {
            it.isJsonObject && it.asJsonObject.get("url")?.asString?.startsWith("http") == true
        } == true
        return hasFmt || hasAdp
    }

    // ==================== HTML / OPENGRAPH PARSER ====================

    internal suspend fun parseHtmlForMedia(
        html: String,
        baseUrl: HttpUrl,
        originalUrl: String,
        host: String
    ): AnalyzedMedia? = coroutineScope {
        val ogTitle = extractMetaContent(html, "og:title")
            ?: extractMetaContent(html, "twitter:title")
            ?: Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.let { decodeHtmlEntities(it).trim() }
        val title = ogTitle?.takeIf { it.isNotBlank() } ?: "Media from $host"

        val author = extractMetaContent(html, "author")
            ?: extractMetaContent(html, "og:site_name")
            ?: host

        val posterImage = resolveAbsoluteUrl(
            baseUrl,
            extractMetaContent(html, "og:image:secure_url")
                ?: extractMetaContent(html, "og:image")
                ?: extractMetaContent(html, "twitter:image:src")
                ?: extractMetaContent(html, "twitter:image")
                ?: Regex("""<video[^>]*\bposter=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)
        )

        val candidates = linkedMapOf<String, CandidateMedia>()

        fun addCandidate(rawCandidate: String?, typeHint: MediaType, qualityHint: String? = null) {
            val resolved = resolveAbsoluteUrl(baseUrl, rawCandidate) ?: return
            if (isLikelyNoiseAsset(resolved)) return
            val existing = candidates[resolved]
            if (existing == null || (existing.qualityHint == null && qualityHint != null)) {
                candidates[resolved] = CandidateMedia(resolved, typeHint, qualityHint)
            }
        }

        // OpenGraph / Twitter video & audio tags
        listOf("og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream").forEach { prop ->
            addCandidate(extractMetaContent(html, prop), MediaType.VIDEO, inferQualityLabelFromUrl(extractMetaContent(html, prop), MediaType.VIDEO))
        }
        listOf("og:audio:secure_url", "og:audio:url", "og:audio").forEach { prop ->
            addCandidate(extractMetaContent(html, prop), MediaType.AUDIO, "Audio Stream")
        }

        // HTML5 <video>, <audio>, and <source>
        Regex("""<(?:video|audio|source)\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).forEach { tagMatch ->
            val tag = tagMatch.value
            val src = extractAttr(tag, "src") ?: extractAttr(tag, "data-src")
            val label = extractAttr(tag, "label") ?: extractAttr(tag, "data-quality") ?: extractAttr(tag, "res")
            val typeAttr = extractAttr(tag, "type").orEmpty()
            val mediaType = when {
                tag.startsWith("<audio", true) || typeAttr.contains("audio", true) -> MediaType.AUDIO
                else -> MediaType.VIDEO
            }
            addCandidate(src, mediaType, label)
        }

        // JSON-LD & embedded script video/audio URLs
        Regex(""""(?:contentUrl|video_url|playable_url_quality_hd|playable_url|browser_native_hd_url|browser_native_sd_url)"\s*:\s*"([^"]+)"""")
            .findAll(html)
            .take(12)
            .forEach { m ->
                val raw = unescapeJsonUrl(m.groupValues[1])
                val t = inferTypeFromMimeOrUrl("", raw) ?: MediaType.VIDEO
                addCandidate(raw, t, inferQualityLabelFromUrl(raw, t))
            }

        // Inline direct video/audio URLs inside scripts/JSON
        Regex("""https?:\\/\\/[^"'\s<>]+\.(?:mp4|webm|mov|m4v|mp3|m4a)(?:\?[^"'\s<>]*)?|https?://[^"'\s<>]+\.(?:mp4|webm|mov|m4v|mp3|m4a)(?:\?[^"'\s<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .take(12)
            .forEach { m ->
                val raw = unescapeJsonUrl(m.value)
                val t = inferTypeFromMimeOrUrl("", raw) ?: MediaType.VIDEO
                addCandidate(raw, t, inferQualityLabelFromUrl(raw, t))
            }

        // High-res Images: OpenGraph + <img> tags + srcset
        if (posterImage != null) {
            addCandidate(posterImage, MediaType.IMAGE, "Featured Image")
        }

        Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).take(25).forEach { imgMatch ->
            val tag = imgMatch.value
            val srcset = extractAttr(tag, "srcset") ?: extractAttr(tag, "data-srcset")
            if (!srcset.isNullOrBlank()) {
                parseSrcSet(srcset).forEach { (url, widthDesc) ->
                    addCandidate(url, MediaType.IMAGE, widthDesc)
                }
            }
            val src = extractAttr(tag, "data-src") ?: extractAttr(tag, "src")
            addCandidate(src, MediaType.IMAGE, null)
        }

        if (candidates.isEmpty()) return@coroutineScope null

        val probedOptions = candidates.values.take(10).mapIndexed { index, candidate ->
            async {
                val probe = runCatching { probeMediaUrl(candidate.url) }.getOrNull()
                val actualMime = probe?.contentType.orEmpty()
                if (actualMime.contains("html", ignoreCase = true)) {
                    return@async null
                }
                val finalType = if (actualMime.isNotBlank()) {
                    inferTypeFromMimeOrUrl(actualMime, candidate.url) ?: candidate.typeHint
                } else {
                    candidate.typeHint
                }
                val ext = inferExtension(actualMime, candidate.url, finalType)
                val sizeBytes = probe?.contentLength?.takeIf { it > 0L }
                if (finalType == MediaType.IMAGE && sizeBytes != null && sizeBytes < 4096L && candidates.size > 1) {
                    return@async null
                }
                val qualityText = candidate.qualityHint
                    ?: inferQualityLabelFromUrl(candidate.url, finalType)
                    ?: when (finalType) {
                        MediaType.VIDEO -> "Video Stream #${index + 1}"
                        MediaType.IMAGE -> "Image (${ext.uppercase()})"
                        MediaType.AUDIO -> "Audio (${ext.uppercase()})"
                    }

                MediaFormatOption(
                    id = "${finalType.name}_${index}_${candidate.url.hashCode()}",
                    label = qualityText,
                    quality = qualityText,
                    mediaType = finalType,
                    downloadUrl = probe?.resolvedUrl ?: candidate.url,
                    extension = ext,
                    estimatedSizeBytes = sizeBytes,
                    sizeDisplay = formatBytes(sizeBytes)
                )
            }
        }.awaitAll().filterNotNull()

        val sorted = probedOptions
            .distinctBy { it.downloadUrl }
            .sortedWith(
                compareBy<MediaFormatOption> {
                    when (it.mediaType) {
                        MediaType.VIDEO -> 0
                        MediaType.AUDIO -> 1
                        MediaType.IMAGE -> 2
                    }
                }.thenByDescending { extractResolutionScore(it.label) }
                    .thenByDescending { it.estimatedSizeBytes ?: 0L }
            )

        if (sorted.isEmpty()) return@coroutineScope null

        val primaryType = sorted.first().mediaType
        val thumb = posterImage ?: sorted.firstOrNull { it.mediaType == MediaType.IMAGE }?.downloadUrl

        AnalyzedMedia(
            sourceUrl = originalUrl,
            sourceHost = host,
            mediaType = primaryType,
            title = title,
            author = author,
            thumbnailUrl = thumb,
            durationText = null,
            durationMs = 0L,
            formats = sorted,
            suggestedFileName = sanitizeFileName(title).ifBlank { "savebox_${System.currentTimeMillis()}" }
        )
    }

    private data class ProbeResult(
        val resolvedUrl: String,
        val contentType: String,
        val contentLength: Long?
    )

    private fun probeMediaUrl(url: String): ProbeResult? {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", DESKTOP_UA)
            .build()
        runCatching {
            okHttpClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val mime = resp.header("Content-Type").orEmpty()
                    val len = resp.header("Content-Length")?.toLongOrNull()
                    return ProbeResult(resp.request.url.toString(), mime, len)
                }
            }
        }

        val rangeReq = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DESKTOP_UA)
            .header("Range", "bytes=0-0")
            .build()
        return runCatching {
            okHttpClient.newCall(rangeReq).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 206) {
                    val mime = resp.header("Content-Type").orEmpty()
                    val totalFromRange = resp.header("Content-Range")
                        ?.substringAfter("/")
                        ?.toLongOrNull()
                    val len = totalFromRange ?: resp.header("Content-Length")?.toLongOrNull()
                    ProbeResult(resp.request.url.toString(), mime, len)
                } else null
            }
        }.getOrNull()
    }

    internal fun extractYouTubeVideoId(url: HttpUrl): String? {
        val host = url.host.lowercase()
        return when {
            host == "youtu.be" -> url.pathSegments.firstOrNull()?.takeIf { it.length >= 6 }
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
                url.queryParameter("v")?.takeIf { it.length >= 6 }
                    ?: run {
                        val segs = url.pathSegments
                        when {
                            segs.size >= 2 && segs[0] in setOf("shorts", "embed", "live", "v") -> segs[1]
                            else -> null
                        }
                    }
            }
            else -> null
        }
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val escaped = Regex.escape(key)
        val p1 = Regex(
            """<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val p2 = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$escaped["']""",
            RegexOption.IGNORE_CASE
        )
        val raw = p1.find(html)?.groupValues?.getOrNull(1)
            ?: p2.find(html)?.groupValues?.getOrNull(1)
            ?: return null
        return decodeHtmlEntities(raw).trim().takeIf { it.isNotBlank() }
    }

    private fun extractAttr(tag: String, attr: String): String? {
        val escaped = Regex.escape(attr)
        val match = Regex("""\b$escaped\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)
        return match?.groupValues?.getOrNull(1)?.let { decodeHtmlEntities(it).trim() }?.takeIf { it.isNotBlank() }
    }

    private fun parseSrcSet(srcset: String): List<Pair<String, String?>> {
        return srcset.split(",").mapNotNull { entry ->
            val parts = entry.trim().split(Regex("\\s+"))
            val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val descriptor = parts.getOrNull(1)?.let { d ->
                when {
                    d.endsWith("w", true) -> "${d.dropLast(1)}px Width"
                    d.endsWith("x", true) -> "$d Scale"
                    else -> d
                }
            }
            url to descriptor
        }
    }

    private fun resolveAbsoluteUrl(base: HttpUrl, raw: String?): String? {
        val cleaned = raw?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("javascript:") }
            ?: return null
        return base.resolve(cleaned)?.toString()
    }

    private fun isLikelyNoiseAsset(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".svg") ||
            lower.endsWith(".ico") ||
            lower.contains("favicon") ||
            lower.contains("pixel.") ||
            lower.contains("1x1") ||
            lower.contains("doubleclick") ||
            lower.contains("analytics")
    }

    private fun inferTypeFromMimeOrUrl(mime: String, url: String): MediaType? {
        val m = mime.lowercase()
        if (m.startsWith("video/")) return MediaType.VIDEO
        if (m.startsWith("image/")) return MediaType.IMAGE
        if (m.startsWith("audio/")) return MediaType.AUDIO

        val pathExt = url.substringBefore("?").substringBefore("#").substringAfterLast(".", "").lowercase()
        return when {
            pathExt in VIDEO_EXTS -> MediaType.VIDEO
            pathExt in IMAGE_EXTS -> MediaType.IMAGE
            pathExt in AUDIO_EXTS -> MediaType.AUDIO
            else -> null
        }
    }

    private fun inferExtension(mime: String, url: String, type: MediaType): String {
        val m = mime.lowercase()
        val fromMime = when {
            m.contains("mp4") -> "mp4"
            m.contains("webm") -> "webm"
            m.contains("quicktime") -> "mov"
            m.contains("matroska") -> "mkv"
            m.contains("jpeg") || m.contains("jpg") -> "jpg"
            m.contains("png") -> "png"
            m.contains("webp") -> "webp"
            m.contains("gif") -> "gif"
            m.contains("mpeg") && type == MediaType.AUDIO -> "mp3"
            m.contains("audio/mp4") -> "m4a"
            else -> null
        }
        if (fromMime != null) return fromMime

        val fromPath = url.substringBefore("?").substringBefore("#").substringAfterLast(".", "").lowercase()
        val validExts = VIDEO_EXTS + AUDIO_EXTS + IMAGE_EXTS
        if (fromPath in validExts) return fromPath

        return when (type) {
            MediaType.VIDEO -> "mp4"
            MediaType.IMAGE -> "jpg"
            MediaType.AUDIO -> "mp3"
        }
    }

    private fun inferQualityLabelFromUrl(url: String?, type: MediaType): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") -> "2160p 4K"
            lower.contains("1440") -> "1440p QHD"
            lower.contains("1080") -> "1080p Full HD"
            lower.contains("720") -> "720p HD"
            lower.contains("480") -> "480p SD"
            lower.contains("360") -> "360p"
            lower.contains("hd") && type == MediaType.VIDEO -> "HD Video"
            lower.contains("sd") && type == MediaType.VIDEO -> "SD Video"
            else -> null
        }
    }

    private fun extractResolutionScore(label: String): Int {
        return when {
            label.contains("2160") || label.contains("4K", true) -> 2160
            label.contains("1440") -> 1440
            label.contains("1080") -> 1080
            label.contains("720") -> 720
            label.contains("480") -> 480
            label.contains("360") -> 360
            label.contains("240") -> 240
            label.contains("HD", true) -> 720
            else -> Regex("""(\d{3,4})p""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 100
        }
    }

    private fun deriveTitleFromUrl(url: HttpUrl, type: MediaType): String {
        val last = url.pathSegments.lastOrNull { it.isNotBlank() }?.substringBeforeLast(".")
        val decoded = last?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.replace('_', ' ')?.replace('-', ' ')?.trim()
        return decoded?.takeIf { it.length >= 3 } ?: "${type.label} from ${url.host.removePrefix("www.")}"
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("&amp;", "&")
    }

    private fun decodeHtmlEntities(input: String): String {
        return input.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    internal fun extractJsonObjectAfterMarker(source: String, marker: String): JsonObject? {
        val idx = source.indexOf(marker)
        if (idx == -1) return null
        val start = source.indexOf('{', idx + marker.length)
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val jsonStr = source.substring(start, i + 1)
                        return runCatching {
                            val el: JsonElement = JsonParser.parseString(jsonStr)
                            if (el.isJsonObject) el.asJsonObject else null
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private data class CandidateMedia(
        val url: String,
        val typeHint: MediaType,
        val qualityHint: String?
    )
}
