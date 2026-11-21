package com.traxxiontech.savebox.model

import kotlinx.serialization.Serializable

enum class AppThemeMode(val label: String) {
    DARK("Dark Obsidian"),
    LIGHT("Light Pearl"),
    SYSTEM("Follow System")
}

enum class AppThemePalette(val label: String) {
    OBSIDIAN_CYAN("Obsidian Cyan"),
    AURORA_VIOLET("Aurora Violet"),
    EMERALD_NEON("Emerald Neon"),
    MIDNIGHT_ROSE("Midnight Rose"),
    SOLAR_AMBER("Solar Amber")
}

enum class VisualizerStyle(val label: String) {
    SPECTRUM_BARS("Spectrum Bars"),
    ORBITAL_RING("Orbital Pulse"),
    DUAL_WAVE("Liquid Wave")
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
    IMAGES("Photos"),
    VIDEOS("Videos"),
    MUSIC("Songs"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    PLAYLISTS("Playlists")
}

enum class SearchScope(val label: String) {
    YOUTUBE("YouTube"),
    SAVED_VAULT("Saved Library")
}

data class MediaFormatOption(
    val id: String,
    val label: String,
    val quality: String,
    val mediaType: MediaType,
    val downloadUrl: String,
    val extension: String,
    val estimatedSizeBytes: Long? = null,
    val sizeDisplay: String? = null,
    val resolution: String? = null
)

data class AnalyzedMedia(
    val sourceUrl: String,
    val sourceHost: String,
    val mediaType: MediaType,
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
    val channel: String?,
    val viewCountText: String?,
    val thumbnailUrl: String?,
    val durationText: String?,
    val url: String
)

data class LocalFolderMediaItem(
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
    val sourceHost: String,
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
