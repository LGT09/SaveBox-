package com.example.data.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadQueue
import com.example.data.local.DownloadQueueDao
import com.example.data.local.MediaItem
import com.example.data.local.MediaItemDao
import com.example.data.local.MediaMetadataDao
import com.example.data.local.MediaMetadataEntity
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SearchHistoryDao
import com.example.data.local.SearchHistoryEntity
import com.example.data.remote.UrlMediaBot
import com.example.model.AnalyzedMedia
import com.example.model.BackupDownloadRecord
import com.example.model.BackupPayload
import com.example.model.BackupPlaylistRecord
import com.example.model.MediaFormatOption
import com.example.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val mediaItemDao: MediaItemDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val mediaMetadataDao: MediaMetadataDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val playlistDao: PlaylistDao,
    private val storageManager: StorageManager
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.observeAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.observeCompletedDownloads()
    val queueDownloads: Flow<List<DownloadEntity>> = downloadDao.observeQueueDownloads()
    val activeDownloadQueue: Flow<List<DownloadQueue>> = downloadQueueDao.observeQueue()
    val allMediaItems: Flow<List<MediaItem>> = mediaItemDao.observeAllMediaItems()
    val favorites: Flow<List<DownloadEntity>> = downloadDao.observeFavorites()
    val recentlyPlayed: Flow<List<DownloadEntity>> = downloadDao.observeRecentlyPlayed()
    val recentSearches: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeRecentSearches()
    val playlists: Flow<List<PlaylistWithTracks>> = playlistDao.observePlaylistsWithTracks()
    val allMediaMetadata: Flow<List<MediaMetadataEntity>> = mediaMetadataDao.observeAllMetadata()

    suspend fun saveAnalyzedMetadata(analyzed: AnalyzedMedia) {
        val primaryFormat = analyzed.formats.firstOrNull()
        mediaMetadataDao.upsert(
            MediaMetadataEntity(
                url = analyzed.sourceUrl,
                title = analyzed.title,
                filePath = null,
                downloadStatus = "ANALYZED",
                sourceHost = analyzed.sourceHost,
                mediaType = primaryFormat?.mediaType?.name ?: analyzed.mediaType.name,
                author = analyzed.author,
                thumbnailUrl = analyzed.thumbnailUrl,
                durationMs = analyzed.durationMs,
                durationText = analyzed.durationText,
                qualityLabel = primaryFormat?.label,
                fileSizeBytes = primaryFormat?.estimatedSizeBytes ?: 0L
            )
        )
    }

    suspend fun enqueueDownload(
        analyzed: AnalyzedMedia,
        format: MediaFormatOption,
        customTitle: String,
        customFolder: String?,
        alsoSaveCoverArt: Boolean = false
    ): Long {
        val cleanTitle = customTitle.trim().ifBlank { analyzed.title }
        val safeBaseName = UrlMediaBot.sanitizeFileName(cleanTitle)
            .ifBlank { "savebox_${System.currentTimeMillis()}" }
        val fileName = "${safeBaseName}_${System.currentTimeMillis() % 10000}.${format.extension}"
        val folderCategory = storageManager.resolveTargetFolder(format.mediaType, customFolder)
        val targetPath = storageManager.getTargetFile(folderCategory, fileName).absolutePath

        val workTag = "savebox_dl_${UUID.randomUUID()}"
        val entity = DownloadEntity(
            title = cleanTitle,
            fileName = fileName,
            sourceUrl = analyzed.sourceUrl,
            downloadUrl = format.downloadUrl,
            sourceHost = analyzed.sourceHost,
            mediaType = format.mediaType.name,
            qualityLabel = format.label,
            author = analyzed.author,
            thumbnailUrl = analyzed.thumbnailUrl,
            durationMs = analyzed.durationMs,
            fileSizeBytes = format.estimatedSizeBytes ?: 0L,
            folderCategory = folderCategory,
            status = "QUEUED",
            workId = workTag
        )
        val insertedId = downloadDao.insert(entity)

        downloadQueueDao.upsert(
            DownloadQueue(
                downloadId = insertedId,
                title = cleanTitle,
                url = analyzed.sourceUrl,
                downloadUrl = format.downloadUrl,
                filePath = targetPath,
                status = "QUEUED",
                downloadedBytes = 0L,
                totalBytes = format.estimatedSizeBytes ?: 0L,
                progressPercent = 0,
                workId = workTag
            )
        )

        mediaMetadataDao.upsert(
            MediaMetadataEntity(
                url = analyzed.sourceUrl,
                title = cleanTitle,
                filePath = targetPath,
                downloadStatus = "QUEUED",
                sourceHost = analyzed.sourceHost,
                mediaType = format.mediaType.name,
                author = analyzed.author,
                thumbnailUrl = analyzed.thumbnailUrl,
                durationMs = analyzed.durationMs,
                durationText = analyzed.durationText,
                qualityLabel = format.label,
                fileSizeBytes = format.estimatedSizeBytes ?: 0L
            )
        )

        scheduleWorker(insertedId, workTag, alsoSaveCoverArt)
        return insertedId
    }

    private fun scheduleWorker(downloadId: Long, uniqueName: String, alsoSaveCoverArt: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putLong(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
            .putBoolean(DownloadWorker.KEY_SAVE_COVER_ART, alsoSaveCoverArt)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .addTag("savebox_download")
            .build()

        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun pauseDownload(item: DownloadEntity) {
        downloadDao.markStatus(item.id, "PAUSED", null)
        downloadQueueDao.updateStatus(item.id, "PAUSED", null)
        mediaMetadataDao.updateFileAndStatus(item.sourceUrl, item.localFilePath, "PAUSED")
        item.workId?.let { workManager.cancelUniqueWork(it) }
    }

    suspend fun resumeOrRetryDownload(item: DownloadEntity) {
        val uniqueName = item.workId ?: "savebox_dl_${UUID.randomUUID()}"
        downloadDao.update(item.copy(status = "QUEUED", errorMessage = null, workId = uniqueName))
        downloadQueueDao.updateStatus(item.id, "QUEUED", null)
        mediaMetadataDao.updateFileAndStatus(item.sourceUrl, item.localFilePath, "QUEUED")
        scheduleWorker(item.id, uniqueName)
    }

    suspend fun cancelAndDeleteDownload(item: DownloadEntity) = withContext(Dispatchers.IO) {
        item.workId?.let { workManager.cancelUniqueWork(it) }
        item.localFilePath?.let { path ->
            runCatching { File(path).delete() }
            mediaItemDao.deleteByFilePath(path)
        }
        downloadQueueDao.deleteByDownloadId(item.id)
        downloadDao.deleteById(item.id)
        mediaMetadataDao.updateFileAndStatus(item.sourceUrl, null, "DELETED")
    }

    suspend fun toggleFavorite(item: DownloadEntity) {
        downloadDao.setFavorite(item.id, !item.isFavorite)
    }

    suspend fun renameDownload(item: DownloadEntity, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext
        val oldPath = item.localFilePath
        var updatedPath = oldPath
        var updatedFileName = item.fileName
        if (oldPath != null) {
            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                val ext = oldFile.extension.ifBlank { "mp4" }
                val clean = UrlMediaBot.sanitizeFileName(trimmed)
                val target = File(oldFile.parentFile, "$clean.$ext")
                if (oldFile.renameTo(target)) {
                    updatedPath = target.absolutePath
                    updatedFileName = target.name
                }
            }
        }
        downloadDao.update(item.copy(title = trimmed, fileName = updatedFileName, localFilePath = updatedPath))
        mediaMetadataDao.updateFileAndStatus(item.sourceUrl, updatedPath, item.status)
        if (updatedPath != null) {
            mediaItemDao.getByFilePath(oldPath ?: updatedPath)?.let { mi ->
                mediaItemDao.upsert(mi.copy(title = trimmed, filePath = updatedPath))
            }
        }
    }

    suspend fun updatePlaybackPosition(downloadId: Long, positionMs: Long, incrementPlayCount: Boolean) {
        downloadDao.updatePlaybackStats(
            id = downloadId,
            positionMs = positionMs,
            playedAt = System.currentTimeMillis(),
            incrementPlay = if (incrementPlayCount) 1 else 0
        )
    }

    suspend fun recordSearch(query: String, scope: SearchScope, isUrl: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.deleteByQuery(trimmed)
        searchHistoryDao.insert(
            SearchHistoryEntity(
                query = trimmed,
                searchScope = scope.name,
                isUrl = isUrl
            )
        )
    }

    suspend fun clearSearchHistory() = searchHistoryDao.clearAll()

    suspend fun clearCompletedHistory() {
        downloadDao.clearNonCompleted()
        downloadQueueDao.clearInactive()
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name.trim(), description = description.trim()))
    }

    suspend fun addToPlaylist(playlistId: Long, downloadId: Long) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, downloadId = downloadId))
    }

    suspend fun removeFromPlaylist(playlistId: Long, downloadId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, downloadId)
    }

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    suspend fun exportBackupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val downloads = downloadDao.getAllOnce().map {
                BackupDownloadRecord(
                    title = it.title,
                    sourceUrl = it.sourceUrl,
                    downloadUrl = it.downloadUrl,
                    sourceHost = it.sourceHost,
                    mediaType = it.mediaType,
                    qualityLabel = it.qualityLabel,
                    author = it.author,
                    thumbnailUrl = it.thumbnailUrl,
                    durationMs = it.durationMs,
                    fileSizeBytes = it.fileSizeBytes,
                    folderCategory = it.folderCategory,
                    isFavorite = it.isFavorite,
                    createdAt = it.createdAt
                )
            }
            val playlists = playlistDao.getAllPlaylistsOnce().map {
                BackupPlaylistRecord(name = it.name, description = it.description, createdAt = it.createdAt)
            }
            val payload = BackupPayload(downloads = downloads, playlists = playlists)
            val serialized = json.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(serialized.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun restoreBackupFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@withContext 0
            val payload = json.decodeFromString<BackupPayload>(content)
            var restored = 0
            payload.playlists.forEach { pl ->
                playlistDao.insertPlaylist(PlaylistEntity(name = pl.name, description = pl.description, createdAt = pl.createdAt))
            }
            payload.downloads.forEach { rec ->
                downloadDao.insert(
                    DownloadEntity(
                        title = rec.title,
                        fileName = UrlMediaBot.sanitizeFileName(rec.title),
                        sourceUrl = rec.sourceUrl,
                        downloadUrl = rec.downloadUrl,
                        sourceHost = rec.sourceHost,
                        mediaType = rec.mediaType,
                        qualityLabel = rec.qualityLabel,
                        author = rec.author,
                        thumbnailUrl = rec.thumbnailUrl,
                        durationMs = rec.durationMs,
                        fileSizeBytes = rec.fileSizeBytes,
                        folderCategory = rec.folderCategory,
                        status = "PAUSED",
                        isFavorite = rec.isFavorite,
                        createdAt = rec.createdAt
                    )
                )
                restored++
            }
            restored
        } catch (_: Throwable) {
            0
        }
    }
}
