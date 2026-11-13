package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room entity storing download history, file paths, and live download status.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,
    @ColumnInfo(name = "sourceUrl") val sourceUrl: String,
    @ColumnInfo(name = "downloadUrl") val downloadUrl: String,
    val platform: String,
    val mediaType: String,
    val qualityLabel: String,
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
    val createdAt: Long = System.currentTimeMillis(),
    val workId: String? = null,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (fileSizeBytes > 0L) {
            (downloadedBytes.toFloat() / fileSizeBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == "COMPLETED") 1f else 0f
}

/**
 * Room entity storing analyzed media metadata alongside URL, title, file path, and download status.
 */
@Entity(
    tableName = "media_metadata",
    indices = [Index(value = ["url"], unique = true)]
)
data class MediaMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val filePath: String? = null,
    val downloadStatus: String = "ANALYZED",
    val platform: String,
    val mediaType: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val durationText: String? = null,
    val qualityLabel: String? = null,
    val fileSizeBytes: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val platformScope: String,
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
