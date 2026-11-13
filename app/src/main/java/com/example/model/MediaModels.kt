package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import kotlinx.serialization.Serializable

enum class AppThemeMode(val label: String) {
    DARK("Dark Obsidian"),
    LIGHT("Light Pearl"),
    SYSTEM("Follow System")
}

enum class MediaPlatform(
    val displayName: String,
    val folderName: String,
    val accentColor: Color,
    val endpointHint: String
) {
    YOUTUBE("YouTube", "Videos", YouTubeRed, "ytdown"),
    YOUTUBE_PLAYLIST("YouTube Playlist", "Videos", YouTubeRed, "ytsearch/playlist"),
    TIKTOK("TikTok", "TikTok", TikTokTeal, "tikdown"),
    INSTAGRAM("Instagram", "Instagram", InstagramPink, "instagram"),
    FACEBOOK("Facebook", "Facebook", FacebookBlue, "fbdown2"),
    TWITTER("Twitter / X", "Twitter", TwitterSky, "twitterdown"),
    THREADS("Threads", "Threads", ThreadsWhite, "threads"),
    SPOTIFY("Spotify", "Spotify", SpotifyGreen, "spotifyDl"),
    SOUNDCLOUD("SoundCloud", "Music", SoundCloudOrange, "soundcloud"),
    PINTEREST("Pinterest", "Pinterest", PinterestRed, "pintarest"),
    LIKEE("Likee", "Videos", LikeeCoral, "likee"),
    TERABOX("Terabox", "Downloads", TeraboxAzure, "teraboxv2"),
    GOOGLE_DRIVE("Google Drive", "Downloads", DriveEmerald, "GDLink"),
    WHATSAPP_STATUS("WhatsApp Status", "Favorites", EmeraldMint, "local"),
    UNIVERSAL("Universal", "Downloads", ElectricCyan, "alldown-v2");

    companion object {
        fun fromString(value: String): MediaPlatform =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNIVERSAL
    }
}

enum class MediaType(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    IMAGE("Image");

    companion object {
        fun fromString(value: String): MediaType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VIDEO
    }
}

enum class DownloadStatus(val label: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed");

    companion object {
        fun fromString(value: String): DownloadStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: QUEUED
    }
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest Size"),
    TYPE("Media Type")
}

enum class LibraryFilter(val label: String) {
    ALL("All Media"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    IMAGES("Images"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    PLAYLISTS("Playlists")
}

enum class SearchPlatformScope(val label: String) {
    ALL_LOCAL("SaveBox Library"),
    YOUTUBE("YouTube"),
    YOUTUBE_PLAYLIST("YT Playlists"),
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud")
}

data class MediaFormatOption(
    val id: String,
    val label: String,
    val quality: String,
    val mediaType: MediaType,
    val downloadUrl: String,
    val extension: String,
    val estimatedSizeBytes: Long? = null,
    val sizeDisplay: String? = null
)

data class AnalyzedMedia(
    val sourceUrl: String,
    val platform: MediaPlatform,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationText: String?,
    val durationMs: Long = 0L,
    val formats: List<MediaFormatOption>,
    val suggestedFileName: String
)

data class SearchResultItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val durationText: String?,
    val url: String,
    val platform: MediaPlatform
)

data class WhatsAppStatusItem(
    val uriString: String,
    val name: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val sourceLabel: String,
    val isAlreadySaved: Boolean = false
)

data class StorageFolderBreakdown(
    val folderName: String,
    val fileCount: Int,
    val sizeBytes: Long
)

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val downloads: List<BackupDownloadRecord> = emptyList(),
    val playlists: List<BackupPlaylistRecord> = emptyList()
)

@Serializable
data class BackupDownloadRecord(
    val title: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val platform: String,
    val mediaType: String,
    val qualityLabel: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val folderCategory: String,
    val isFavorite: Boolean,
    val createdAt: Long
)

@Serializable
data class BackupPlaylistRecord(
    val name: String,
    val description: String,
    val createdAt: Long
)
