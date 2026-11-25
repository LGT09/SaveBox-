package com.traxxiontech.savebox.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room entity managing persistent downloaded media metadata:
 * title, URL, local file path, download timestamp, quality, size, and status.
 */
@Entity(
    tableName = "downloads",
    indices = [Index(value = ["createdAt"]), Index(value = ["status"])]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "fileName") val fileName: String,
    @ColumnInfo(name = "sourceUrl") val sourceUrl: String,
    @ColumnInfo(name = "downloadUrl") val downloadUrl: String,
    @ColumnInfo(name = "sourceHost") val sourceHost: String = "web",
    @ColumnInfo(name = "mediaType") val mediaType: String,
    @ColumnInfo(name = "qualityLabel") val qualityLabel: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    @ColumnInfo(name = "localFilePath") val localFilePath: String? = null,
    val localUri: String? = null,
    val folderCategory: String = "Downloads",
    @ColumnInfo(name = "status") val status: String = "QUEUED",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val playbackPositionMs: Long = 0L,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    val workId: String? = null,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (fileSizeBytes > 0L) {
            (downloadedBytes.toFloat() / fileSizeBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == "COMPLETED") 1f else 0f
}

/**
 * Room entity for downloaded or device-scanned media files (Photos, Videos, Songs)
 * storing file path, cover art path, title, type, and timestamp.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["mediaType"]),
        Index(value = ["timestamp"])
    ]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "filePath") val filePath: String,
    @ColumnInfo(name = "coverArtPath") val coverArtPath: String? = null,
    @ColumnInfo(name = "mediaType") val mediaType: String,
    @ColumnInfo(name = "mimeType") val mimeType: String = "video/mp4",
    @ColumnInfo(name = "author") val author: String? = null,
    @ColumnInfo(name = "qualityLabel") val qualityLabel: String = "Original",
    @ColumnInfo(name = "durationMs") val durationMs: Long = 0L,
    @ColumnInfo(name = "fileSizeBytes") val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

/**
 * Room entity tracking active/queued background downloads and live byte progress state.
 */
@Entity(
    tableName = "download_queue",
    indices = [
        Index(value = ["downloadId"], unique = true),
        Index(value = ["status"])
    ]
)
data class DownloadQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "downloadId") val downloadId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "downloadUrl") val downloadUrl: String,
    @ColumnInfo(name = "filePath") val filePath: String? = null,
    @ColumnInfo(name = "status") val status: String = "QUEUED",
    @ColumnInfo(name = "downloadedBytes") val downloadedBytes: Long = 0L,
    @ColumnInfo(name = "totalBytes") val totalBytes: Long = 0L,
    @ColumnInfo(name = "progressPercent") val progressPercent: Int = 0,
    @ColumnInfo(name = "workId") val workId: String? = null,
    @ColumnInfo(name = "errorMessage") val errorMessage: String? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity storing analyzed media metadata:
 * URL, title, file path, download status, quality, size, and timestamp.
 */
@Entity(
    tableName = "media_metadata",
    indices = [Index(value = ["url"], unique = true)]
)
data class MediaMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "filePath") val filePath: String? = null,
    @ColumnInfo(name = "downloadStatus") val downloadStatus: String = "ANALYZED",
    @ColumnInfo(name = "sourceHost") val sourceHost: String = "web",
    @ColumnInfo(name = "mediaType") val mediaType: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val durationText: String? = null,
    val qualityLabel: String? = null,
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchScope: String,
    val isUrl: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "downloadId"],
    indices = [Index(value = ["downloadId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val downloadId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "downloadId"
        )
    )
    val tracks: List<DownloadEntity>
)

/**
 * Dedicated Room entity storing bookmarked Favorites for fast access across the app.
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["downloadId"], unique = true),
        Index(value = ["bookmarkedAt"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "downloadId") val downloadId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "filePath") val filePath: String? = null,
    @ColumnInfo(name = "mediaType") val mediaType: String,
    @ColumnInfo(name = "sourceHost") val sourceHost: String = "web",
    @ColumnInfo(name = "thumbnailUrl") val thumbnailUrl: String? = null,
    @ColumnInfo(name = "durationMs") val durationMs: Long = 0L,
    @ColumnInfo(name = "fileSizeBytes") val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "bookmarkedAt") val bookmarkedAt: Long = System.currentTimeMillis()
)

