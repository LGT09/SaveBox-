package com.example.data.remote

import android.util.Patterns
import com.example.model.AnalyzedMedia
import com.example.model.MediaPlatform
import com.example.model.SearchPlatformScope
import com.example.model.SearchResultItem
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
}

class SaveBoxApiManager(
    val okHttpClient: OkHttpClient,
    private val apiService: MediaApiService
) {

    // Extensible service registry allowing additional endpoints to be plugged in easily
    private val customHandlers = mutableMapOf<MediaPlatform, suspend (String) -> Response<JsonElement>>()

    fun registerCustomEndpoint(
        platform: MediaPlatform,
        handler: suspend (String) -> Response<JsonElement>
    ) {
        customHandlers[platform] = handler
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

    fun detectPlatform(url: String): MediaPlatform {
        val lower = url.lowercase().trim()
        val host = runCatching {
            val withScheme = if (lower.startsWith("http://") || lower.startsWith("https://")) lower else "https://$lower"
            withScheme.toHttpUrlOrNull()?.host.orEmpty()
        }.getOrDefault("")

        fun hostMatches(domain: String): Boolean =
            host == domain || host.endsWith(".$domain") || lower.contains("://$domain") || lower.contains(".$domain")

        return when {
            (hostMatches("youtube.com") && (lower.contains("/playlist") || lower.contains("list="))) -> MediaPlatform.YOUTUBE_PLAYLIST
            hostMatches("youtube.com") || hostMatches("youtu.be") -> MediaPlatform.YOUTUBE
            hostMatches("tiktok.com") -> MediaPlatform.TIKTOK
            hostMatches("instagram.com") || hostMatches("instagr.am") -> MediaPlatform.INSTAGRAM
            hostMatches("facebook.com") || hostMatches("fb.watch") || hostMatches("fb.com") -> MediaPlatform.FACEBOOK
            hostMatches("twitter.com") || hostMatches("x.com") || hostMatches("t.co") -> MediaPlatform.TWITTER
            hostMatches("threads.net") -> MediaPlatform.THREADS
            hostMatches("spotify.com") || hostMatches("spotify.link") -> MediaPlatform.SPOTIFY
            hostMatches("soundcloud.com") -> MediaPlatform.SOUNDCLOUD
            host.contains("pinterest.") || hostMatches("pin.it") -> MediaPlatform.PINTEREST
            hostMatches("likee.video") || hostMatches("likee.com") -> MediaPlatform.LIKEE
            host.contains("terabox") -> MediaPlatform.TERABOX
            hostMatches("drive.google.com") || hostMatches("docs.google.com") -> MediaPlatform.GOOGLE_DRIVE
            else -> MediaPlatform.UNIVERSAL
        }
    }

    suspend fun analyzeUrl(rawUrl: String): ApiResult<AnalyzedMedia> = withContext(Dispatchers.IO) {
        val validUrl = validateAndNormalizeUrl(rawUrl)
            ?: return@withContext ApiResult.Error("Please enter a valid HTTPS media URL (e.g. https://youtu.be/...).")

        val platform = detectPlatform(validUrl)
        val endpointAttempts: List<suspend () -> Response<JsonElement>> = buildList {
            customHandlers[platform]?.let { custom -> add { custom(validUrl) } }
            when (platform) {
                MediaPlatform.YOUTUBE, MediaPlatform.YOUTUBE_PLAYLIST -> {
                    add { apiService.downloadYouTube(validUrl) }
                    add { apiService.fetchYouTubeMedia(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                    add { apiService.downloadUniversal(validUrl) }
                }
                MediaPlatform.TIKTOK -> {
                    add { apiService.downloadTikTok(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                    add { apiService.downloadUniversal(validUrl) }
                }
                MediaPlatform.INSTAGRAM -> {
                    add { apiService.downloadInstagram(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                    add { apiService.downloadUniversal(validUrl) }
                }
                MediaPlatform.FACEBOOK -> {
                    add { apiService.downloadFacebook(validUrl) }
                    add { apiService.downloadNDown(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.TWITTER -> {
                    add { apiService.downloadTwitter(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.THREADS -> {
                    add { apiService.downloadThreads(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.SPOTIFY -> {
                    add { apiService.downloadSpotify(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.SOUNDCLOUD -> {
                    add { apiService.downloadSoundCloud(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.PINTEREST -> {
                    add { apiService.downloadPinterest(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.LIKEE -> {
                    add { apiService.downloadLikee(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.TERABOX -> {
                    add { apiService.downloadTeraboxV2(validUrl) }
                    add { apiService.downloadTerabox(validUrl) }
                }
                MediaPlatform.GOOGLE_DRIVE -> {
                    add { apiService.downloadGoogleDrive(validUrl) }
                    add { apiService.downloadUniversalV2(validUrl) }
                }
                MediaPlatform.UNIVERSAL, MediaPlatform.WHATSAPP_STATUS -> {
                    add { apiService.downloadUniversalV2(validUrl) }
                    add { apiService.downloadUniversal(validUrl) }
                }
            }
        }

        var lastErrorMsg = "Could not extract downloadable media from this URL."
        for (attempt in endpointAttempts) {
            try {
                val response = attempt()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val parsed = MediaResponseParser.parseAnalyzedMedia(body, validUrl, platform)
                        if (parsed != null && parsed.formats.isNotEmpty()) {
                            return@withContext ApiResult.Success(parsed)
                        }
                    }
                } else {
                    lastErrorMsg = "Server responded with HTTP ${response.code()} for ${platform.displayName}."
                }
            } catch (t: Throwable) {
                lastErrorMsg = t.localizedMessage ?: "Network error while analyzing URL."
            }
        }
        ApiResult.Error(lastErrorMsg)
    }

    suspend fun searchMedia(query: String, scope: SearchPlatformScope): ApiResult<List<SearchResultItem>> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext ApiResult.Error("Enter keywords to search.")
            }
            try {
                val (response, platform) = when (scope) {
                    SearchPlatformScope.YOUTUBE -> apiService.searchYouTube(cleanQuery) to MediaPlatform.YOUTUBE
                    SearchPlatformScope.YOUTUBE_PLAYLIST -> apiService.searchYouTubePlaylists(cleanQuery) to MediaPlatform.YOUTUBE_PLAYLIST
                    SearchPlatformScope.SPOTIFY -> apiService.searchSpotify(cleanQuery) to MediaPlatform.SPOTIFY
                    SearchPlatformScope.SOUNDCLOUD -> apiService.searchSoundCloud(cleanQuery) to MediaPlatform.SOUNDCLOUD
                    SearchPlatformScope.ALL_LOCAL -> return@withContext ApiResult.Success(emptyList())
                }
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Error("Search failed with HTTP ${response.code()}.")
                }
                val body = response.body() ?: return@withContext ApiResult.Success(emptyList())
                ApiResult.Success(MediaResponseParser.parseSearchResults(body, platform))
            } catch (t: Throwable) {
                ApiResult.Error(t.localizedMessage ?: "Unable to reach search service.", t)
            }
        }
}
