package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun observeCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'FAILED') ORDER BY createdAt DESC")
    fun observeQueueDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE isFavorite = 1 AND status = 'COMPLETED' ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 15")
    fun observeRecentlyPlayed(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE localFilePath = :path OR localUri = :path LIMIT 1")
    suspend fun getByPathOrUri(path: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAllOnce(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, fileSizeBytes = :total, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, status: String)

    @Query("UPDATE downloads SET status = :status, localFilePath = :path, localUri = :uri, fileSizeBytes = :size, downloadedBytes = :size, thumbnailUrl = COALESCE(:coverPath, thumbnailUrl), errorMessage = NULL WHERE id = :id")
    suspend fun markCompleted(id: Long, status: String, path: String, uri: String?, size: Long, coverPath: String? = null)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE downloads SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE downloads SET playbackPositionMs = :positionMs, lastPlayedAt = :playedAt, playCount = playCount + :incrementPlay WHERE id = :id")
    suspend fun updatePlaybackStats(id: Long, positionMs: Long, playedAt: Long, incrementPlay: Int)

    @Query("UPDATE downloads SET title = :newTitle, fileName = :newFileName WHERE id = :id")
    suspend fun renameItem(id: Long, newTitle: String, newFileName: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status != 'COMPLETED'")
    suspend fun clearNonCompleted()

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY timestamp DESC")
    fun observeAllMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE mediaType = :mediaType ORDER BY timestamp DESC")
    fun observeByMediaType(mediaType: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItem>)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_items WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM media_items")
    suspend fun clearAll()
}

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    fun observeQueue(): Flow<List<DownloadQueue>>

    @Query("SELECT * FROM download_queue WHERE downloadId = :downloadId LIMIT 1")
    suspend fun getByDownloadId(downloadId: Long): DownloadQueue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DownloadQueue): Long

    @Query("UPDATE download_queue SET downloadedBytes = :downloaded, totalBytes = :total, progressPercent = :percent, status = :status, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateProgress(
        downloadId: Long,
        downloaded: Long,
        total: Long,
        percent: Int,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE download_queue SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateStatus(
        downloadId: Long,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM download_queue WHERE downloadId = :downloadId")
    suspend fun deleteByDownloadId(downloadId: Long)

    @Query("DELETE FROM download_queue WHERE status != 'DOWNLOADING' AND status != 'QUEUED'")
    suspend fun clearInactive()
}

@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata ORDER BY updatedAt DESC")
    fun observeAllMetadata(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: MediaMetadataEntity): Long

    @Query("UPDATE media_metadata SET filePath = :filePath, downloadStatus = :status, updatedAt = :updatedAt WHERE url = :url")
    suspend fun updateFileAndStatus(url: String, filePath: String?, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM media_metadata")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 25")
    fun observeRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND downloadId = :downloadId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, downloadId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
