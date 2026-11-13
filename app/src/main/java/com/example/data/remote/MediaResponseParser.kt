package com.example.data.remote

import com.example.model.AnalyzedMedia
import com.example.model.MediaFormatOption
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.SearchResultItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

object MediaResponseParser {

    fun parseAnalyzedMedia(
        root: JsonElement,
        sourceUrl: String,
        platform: MediaPlatform
    ): AnalyzedMedia? {
        val primaryObj = unwrapPrimaryObject(root) ?: return null

        val title = firstNonBlankString(
            primaryObj,
            listOf("title", "name", "caption", "desc", "description", "videoTitle", "song", "music_title")
        ) ?: "${platform.displayName} Media"

        val author = firstNonBlankString(
            primaryObj,
            listOf("author", "channel", "creator", "username", "owner", "artist", "singer", "channelName")
        )

        val thumbnail = firstValidUrl(
            primaryObj,
            listOf("thumbnail", "thumb", "cover", "image", "avatar", "poster", "picture", "artwork")
        )

        val durationText = firstNonBlankString(
            primaryObj,
            listOf("duration", "duration_text", "timestamp", "length", "time")
        )

        val formats = mutableListOf<MediaFormatOption>()
        collectFormatOptions(root, platform, formats)

        val deduplicated = formats
            .filter { it.downloadUrl.startsWith("http://") || it.downloadUrl.startsWith("https://") }
            .distinctBy { it.downloadUrl }

        if (deduplicated.isEmpty()) return null

        val safeName = sanitizeFileName(title).ifBlank { "savebox_${System.currentTimeMillis()}" }

        return AnalyzedMedia(
            sourceUrl = sourceUrl,
            platform = platform,
            title = title.trim(),
            author = author?.trim(),
            thumbnailUrl = thumbnail,
            durationText = durationText,
            durationMs = parseDurationToMs(durationText),
            formats = deduplicated,
            suggestedFileName = safeName
        )
    }

    fun parseSearchResults(
        root: JsonElement,
        platform: MediaPlatform
    ): List<SearchResultItem> {
        val array = findPrimaryArray(root) ?: return emptyList()
        val results = mutableListOf<SearchResultItem>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val title = firstNonBlankString(
                obj,
                listOf("title", "name", "playlistName", "trackName", "song")
            ) ?: continue
            val url = firstValidUrl(
                obj,
                listOf("url", "link", "videoUrl", "playlistUrl", "permalink_url", "spotifyUrl", "external_urls")
            ) ?: continue
            val subtitle = firstNonBlankString(
                obj,
                listOf("author", "channel", "artist", "artists", "user", "owner", "description", "views")
            )
            val thumbnail = firstValidUrl(
                obj,
                listOf("thumbnail", "thumb", "image", "cover", "artwork", "artwork_url", "avatar")
            )
            val duration = firstNonBlankString(
                obj,
                listOf("duration", "timestamp", "duration_ms", "videoCount", "tracks")
            )
            results.add(
                SearchResultItem(
                    id = firstNonBlankString(obj, listOf("id", "videoId", "playlistId")) ?: UUID.randomUUID().toString(),
                    title = title.trim(),
                    subtitle = subtitle?.trim(),
                    thumbnailUrl = thumbnail,
                    durationText = duration,
                    url = url,
                    platform = platform
                )
            )
        }
        return results
    }

    private fun unwrapPrimaryObject(element: JsonElement): JsonObject? {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (wrapperKey in listOf("data", "result", "media", "response", "videoInfo")) {
                val child = obj.get(wrapperKey)
                if (child != null && child.isJsonObject) {
                    return child.asJsonObject
                }
                if (child != null && child.isJsonArray && child.asJsonArray.size() > 0) {
                    val first = child.asJsonArray[0]
                    if (first.isJsonObject) return first.asJsonObject
                }
            }
            return obj
        }
        if (element.isJsonArray && element.asJsonArray.size() > 0) {
            val first = element.asJsonArray[0]
            if (first.isJsonObject) return first.asJsonObject
        }
        return null
    }

    private fun findPrimaryArray(element: JsonElement): JsonArray? {
        if (element.isJsonArray) return element.asJsonArray
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (key in listOf("data", "result", "results", "videos", "playlists", "tracks", "items", "collection")) {
                val child = obj.get(key)
                if (child != null) {
                    if (child.isJsonArray) return child.asJsonArray
                    if (child.isJsonObject) {
                        val nested = findPrimaryArray(child)
                        if (nested != null) return nested
                    }
                }
            }
        }
        return null
    }

    private fun collectFormatOptions(
        element: JsonElement,
        platform: MediaPlatform,
        out: MutableList<MediaFormatOption>,
        hintKey: String = ""
    ) {
        when {
            element.isJsonArray -> {
                element.asJsonArray.forEachIndexed { idx, child ->
                    collectFormatOptions(child, platform, out, "${hintKey}_$idx")
                }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                // Check if this object itself represents a single format/media entry
                val directUrl = firstValidUrl(
                    obj,
                    listOf("url", "downloadUrl", "download_url", "link", "src", "videoUrl", "audioUrl")
                )
                if (directUrl != null && !isThumbnailOrPageUrl(directUrl, hintKey)) {
                    val label = firstNonBlankString(obj, listOf("quality", "resolution", "label", "format", "type", "ext"))
                        ?: inferLabelFromKey(hintKey, directUrl)
                    val ext = firstNonBlankString(obj, listOf("ext", "extension", "format"))
                        ?: inferExtension(directUrl, label, platform)
                    val type = inferMediaType(ext, label, firstNonBlankString(obj, listOf("type", "mimeType")), platform)
                    val sizeStr = firstNonBlankString(obj, listOf("size", "filesize", "fileSize", "formattedSize", "clen"))
                    val sizeBytes = parseSizeBytes(sizeStr)
                    out.add(
                        MediaFormatOption(
                            id = UUID.randomUUID().toString(),
                            label = label,
                            quality = label,
                            mediaType = type,
                            downloadUrl = directUrl,
                            extension = ext,
                            estimatedSizeBytes = sizeBytes,
                            sizeDisplay = sizeStr
                        )
                    )
                }

                // Also inspect common explicit quality keys on the object
                val explicitKeys = listOf(
                    "hd" to "HD Video",
                    "High" to "HD Video",
                    "high" to "HD Video",
                    "1080p" to "1080p Full HD",
                    "720p" to "720p HD",
                    "480p" to "480p SD",
                    "360p" to "360p",
                    "sd" to "SD Video",
                    "Low" to "SD Video",
                    "low" to "SD Video",
                    "Normal video" to "Standard Video",
                    "HD video" to "HD Video",
                    "video" to "Video",
                    "video_url" to "Video",
                    "nowm" to "No Watermark HD",
                    "wm" to "Watermark Video",
                    "mp4" to "MP4 Video",
                    "mp3" to "MP3 Audio",
                    "audio" to "Audio Track",
                    "audio_url" to "Audio Track",
                    "music" to "Audio Track",
                    "image" to "Image",
                    "download" to "Download",
                    "downloadLink" to "Direct File"
                )
                for ((key, defaultLabel) in explicitKeys) {
                    val value = obj.get(key) ?: continue
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        val candidate = value.asString.trim()
                        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                            val ext = inferExtension(candidate, defaultLabel, platform)
                            val type = inferMediaType(ext, defaultLabel, key, platform)
                            out.add(
                                MediaFormatOption(
                                    id = UUID.randomUUID().toString(),
                                    label = defaultLabel,
                                    quality = defaultLabel,
                                    mediaType = type,
                                    downloadUrl = candidate,
                                    extension = ext
                                )
                            )
                        }
                    } else if (value.isJsonObject || value.isJsonArray) {
                        collectFormatOptions(value, platform, out, key)
                    }
                }

                // Recurse into nested collection keys
                for (nestedKey in listOf("data", "result", "results", "medias", "urls", "formats", "links", "items", "media", "video", "audio")) {
                    val child = obj.get(nestedKey)
                    if (child != null && (child.isJsonObject || child.isJsonArray)) {
                        collectFormatOptions(child, platform, out, nestedKey)
                    }
                }
            }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val url = element.asString.trim()
                if ((url.startsWith("http://") || url.startsWith("https://")) && !isThumbnailOrPageUrl(url, hintKey)) {
                    val label = inferLabelFromKey(hintKey, url)
                    val ext = inferExtension(url, label, platform)
                    val type = inferMediaType(ext, label, hintKey, platform)
                    out.add(
                        MediaFormatOption(
                            id = UUID.randomUUID().toString(),
                            label = label,
                            quality = label,
                            mediaType = type,
                            downloadUrl = url,
                            extension = ext
                        )
                    )
                }
            }
        }
    }

    private fun firstNonBlankString(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val el = obj.get(key) ?: continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty() && !s.equals("null", ignoreCase = true)) return s
            } else if (el.isJsonObject) {
                // Support e.g. author: { name: "..." }
                val nested = firstNonBlankString(el.asJsonObject, listOf("name", "username", "title", "nickname"))
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun firstValidUrl(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val el = obj.get(key) ?: continue
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val s = el.asString.trim()
                if (s.startsWith("http://") || s.startsWith("https://")) return s
            } else if (el.isJsonObject) {
                val nested = firstValidUrl(el.asJsonObject, listOf("url", "spotify", "href", "src"))
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun isThumbnailOrPageUrl(url: String, hintKey: String): Boolean {
        val lowerKey = hintKey.lowercase()
        if (lowerKey.contains("thumb") || lowerKey.contains("avatar") || lowerKey.contains("cover") || lowerKey.contains("artwork")) {
            return true
        }
        val lower = url.lowercase()
        return lower.contains("youtube.com/watch") || lower.contains("youtu.be/") ||
            lower.contains("open.spotify.com/track") || lower.contains("soundcloud.com/") && !lower.contains("sndcdn")
    }

    private fun inferLabelFromKey(hintKey: String, url: String): String {
        val lower = hintKey.lowercase()
        return when {
            lower.contains("hd") || lower.contains("1080") -> "HD Video"
            lower.contains("720") -> "720p HD"
            lower.contains("sd") || lower.contains("low") -> "SD Video"
            lower.contains("audio") || lower.contains("mp3") || lower.contains("music") -> "Audio (MP3)"
            lower.contains("image") || lower.contains("photo") -> "Image"
            url.contains(".mp3", ignoreCase = true) || url.contains(".m4a", ignoreCase = true) -> "Audio Track"
            url.contains(".jpg", ignoreCase = true) || url.contains(".png", ignoreCase = true) || url.contains(".webp", ignoreCase = true) -> "Image"
            else -> "Best Quality"
        }
    }

    private fun inferExtension(url: String, label: String, platform: MediaPlatform): String {
        val cleanUrl = url.substringBefore('?').lowercase()
        return when {
            cleanUrl.endsWith(".mp3") || label.contains("mp3", true) || platform == MediaPlatform.SPOTIFY || platform == MediaPlatform.SOUNDCLOUD -> "mp3"
            cleanUrl.endsWith(".m4a") -> "m4a"
            cleanUrl.endsWith(".aac") -> "aac"
            cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") -> "jpg"
            cleanUrl.endsWith(".png") -> "png"
            cleanUrl.endsWith(".webp") -> "webp"
            cleanUrl.endsWith(".webm") -> "webm"
            else -> "mp4"
        }
    }

    private fun inferMediaType(ext: String, label: String, typeHint: String?, platform: MediaPlatform): MediaType {
        val combined = "$ext $label ${typeHint.orEmpty()}".lowercase()
        return when {
            combined.contains("mp3") || combined.contains("m4a") || combined.contains("audio") ||
                platform == MediaPlatform.SPOTIFY || platform == MediaPlatform.SOUNDCLOUD -> MediaType.AUDIO
            combined.contains("jpg") || combined.contains("jpeg") || combined.contains("png") ||
                combined.contains("webp") || combined.contains("image") -> MediaType.IMAGE
            else -> MediaType.VIDEO
        }
    }

    private fun parseDurationToMs(durationText: String?): Long {
        if (durationText.isNullOrBlank()) return 0L
        val trimmed = durationText.trim()
        trimmed.toLongOrNull()?.let { seconds ->
            return if (seconds > 100_000L) seconds else seconds * 1000L
        }
        val parts = trimmed.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun parseSizeBytes(sizeText: String?): Long? {
        if (sizeText.isNullOrBlank()) return null
        val normalized = sizeText.trim().uppercase()
        normalized.toLongOrNull()?.let { return it }
        val num = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(normalized)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return when {
            normalized.contains("GB") -> (num * 1024 * 1024 * 1024).toLong()
            normalized.contains("MB") -> (num * 1024 * 1024).toLong()
            normalized.contains("KB") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("""[\\/:*?"<>|\r\n]+"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)
    }
}
