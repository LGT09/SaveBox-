package com.example.data.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.SaveBoxApplication
import com.example.data.local.DownloadEntity
import com.example.data.local.MediaItem
import com.example.data.local.SaveBoxDatabase
import com.example.data.remote.UrlMediaBot
import com.example.model.MediaType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun database(): SaveBoxDatabase
    fun storageManager(): StorageManager
}

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SAVE_COVER_ART = "save_cover_art"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return@withContext Result.failure()
        val alsoSaveCoverToImages = inputData.getBoolean(KEY_SAVE_COVER_ART, false)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val db = entryPoint.database()
        val dao = db.downloadDao()
        val mediaItemDao = db.mediaItemDao()
        val queueDao = db.downloadQueueDao()
        val metadataDao = db.mediaMetadataDao()
        val storage = entryPoint.storageManager()
        val okHttpClient = entryPoint.okHttpClient()

        val entity = dao.getById(downloadId) ?: return@withContext Result.failure()
        if (entity.status == "PAUSED") return@withContext Result.success()

        val notificationId = 2000 + downloadId.toInt()
        setForeground(createForegroundInfo(notificationId, entity.title, 0, true))

        val targetFile = storage.getTargetFile(entity.folderCategory, entity.fileName)
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        try {
            dao.markStatus(downloadId, "DOWNLOADING", null)
            queueDao.updateStatus(downloadId, "DOWNLOADING", null)
            metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "DOWNLOADING")

            val requestBuilder = Request.Builder()
                .url(entity.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")

            if (entity.sourceUrl.startsWith("http", ignoreCase = true)) {
                requestBuilder.header("Referer", entity.sourceUrl)
            }
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    val errMsg = if (response.code == 416 && existingBytes > 0L) {
                        targetFile.delete()
                        "Server rejected range; tap Retry to restart."
                    } else {
                        "Download error HTTP ${response.code}"
                    }
                    dao.markStatus(downloadId, "FAILED", errMsg)
                    queueDao.updateStatus(downloadId, "FAILED", errMsg)
                    metadataDao.updateFileAndStatus(entity.sourceUrl, null, "FAILED")
                    return@withContext Result.failure()
                }

                val body = response.body ?: run {
                    dao.markStatus(downloadId, "FAILED", "Empty media stream.")
                    queueDao.updateStatus(downloadId, "FAILED", "Empty media stream.")
                    metadataDao.updateFileAndStatus(entity.sourceUrl, null, "FAILED")
                    return@withContext Result.failure()
                }

                val isResuming = response.code == 206 && existingBytes > 0L
                val startOffset = if (isResuming) existingBytes else 0L
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0L) startOffset + contentLength else entity.fileSizeBytes

                var downloaded = startOffset
                var lastUiUpdate = 0L

                FileOutputStream(targetFile, isResuming).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (isStopped) {
                                val pct = if (totalBytes > 0L) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                dao.updateProgress(downloadId, downloaded, totalBytes, "PAUSED")
                                queueDao.updateProgress(downloadId, downloaded, totalBytes, pct, "PAUSED")
                                metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "PAUSED")
                                return@withContext Result.success()
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate >= 350L) {
                                lastUiUpdate = now
                                val pct = if (totalBytes > 0L) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                dao.updateProgress(downloadId, downloaded, totalBytes, "DOWNLOADING")
                                queueDao.updateProgress(downloadId, downloaded, totalBytes, pct, "DOWNLOADING")
                                setForeground(createForegroundInfo(notificationId, entity.title, pct, totalBytes <= 0L))
                            }
                        }
                    }
                }

                val finalSize = targetFile.length()
                val mediaType = MediaType.fromString(entity.mediaType)
                val mimeType = when (mediaType) {
                    MediaType.AUDIO -> "audio/mp4"
                    MediaType.IMAGE -> "image/jpeg"
                    MediaType.VIDEO -> "video/mp4"
                }
                val mediaStoreUri = storage.exportToMediaStore(targetFile, mediaType, mimeType)

                // Cache remote cover art locally so offline songs & videos always render rich artwork
                val localCoverPath = cacheRemoteCoverArt(
                    okHttpClient = okHttpClient,
                    storage = storage,
                    thumbUrl = entity.thumbnailUrl,
                    baseTitle = entity.title,
                    downloadId = downloadId
                )

                // If user requested saving Cover Art alongside the song/video (e.g. YouTube Music download)
                if (alsoSaveCoverToImages && localCoverPath != null && mediaType != MediaType.IMAGE) {
                    val coverSource = File(localCoverPath)
                    if (coverSource.exists()) {
                        val coverName = "${UrlMediaBot.sanitizeFileName(entity.title)}_cover_${downloadId}.jpg"
                        val savedCoverFile = storage.getTargetFile("Images", coverName)
                        runCatching { coverSource.copyTo(savedCoverFile, overwrite = true) }
                        if (savedCoverFile.exists()) {
                            val coverUri = storage.exportToMediaStore(savedCoverFile, MediaType.IMAGE, "image/jpeg")
                            dao.insert(
                                DownloadEntity(
                                    title = "${entity.title} (Cover Art)",
                                    fileName = coverName,
                                    sourceUrl = entity.thumbnailUrl ?: entity.sourceUrl,
                                    downloadUrl = entity.thumbnailUrl ?: entity.downloadUrl,
                                    sourceHost = entity.sourceHost,
                                    mediaType = MediaType.IMAGE.name,
                                    qualityLabel = "Cover Art JPG",
                                    author = entity.author,
                                    thumbnailUrl = savedCoverFile.absolutePath,
                                    fileSizeBytes = savedCoverFile.length(),
                                    downloadedBytes = savedCoverFile.length(),
                                    localFilePath = savedCoverFile.absolutePath,
                                    localUri = coverUri?.toString() ?: Uri.fromFile(savedCoverFile).toString(),
                                    folderCategory = "Images",
                                    status = "COMPLETED"
                                )
                            )
                            mediaItemDao.upsert(
                                MediaItem(
                                    title = "${entity.title} (Cover Art)",
                                    url = entity.thumbnailUrl ?: entity.sourceUrl,
                                    filePath = savedCoverFile.absolutePath,
                                    coverArtPath = savedCoverFile.absolutePath,
                                    mediaType = MediaType.IMAGE.name,
                                    mimeType = "image/jpeg",
                                    author = entity.author,
                                    qualityLabel = "Cover Art JPG",
                                    fileSizeBytes = savedCoverFile.length()
                                )
                            )
                        }
                    }
                }

                val effectiveThumb = localCoverPath ?: entity.thumbnailUrl
                dao.markCompleted(
                    id = downloadId,
                    status = "COMPLETED",
                    path = targetFile.absolutePath,
                    uri = mediaStoreUri?.toString(),
                    size = finalSize,
                    coverPath = effectiveThumb
                )
                mediaItemDao.upsert(
                    MediaItem(
                        title = entity.title,
                        url = entity.sourceUrl,
                        filePath = targetFile.absolutePath,
                        coverArtPath = effectiveThumb,
                        mediaType = mediaType.name,
                        mimeType = mimeType,
                        author = entity.author,
                        qualityLabel = entity.qualityLabel,
                        durationMs = entity.durationMs,
                        fileSizeBytes = finalSize
                    )
                )
                queueDao.deleteByDownloadId(downloadId)
                metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "COMPLETED")
                showCompletedNotification(notificationId, entity.title)
                Result.success()
            }
        } catch (t: Throwable) {
            val latest = dao.getById(downloadId)
            if (latest?.status != "PAUSED") {
                val msg = t.localizedMessage ?: "Download interrupted"
                dao.markStatus(downloadId, "FAILED", msg)
                queueDao.updateStatus(downloadId, "FAILED", msg)
                metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "FAILED")
            }
            Result.failure()
        }
    }

    private fun cacheRemoteCoverArt(
        okHttpClient: OkHttpClient,
        storage: StorageManager,
        thumbUrl: String?,
        baseTitle: String,
        downloadId: Long
    ): String? {
        if (thumbUrl.isNullOrBlank()) return null
        if (!thumbUrl.startsWith("http", ignoreCase = true)) {
            return thumbUrl.takeIf { File(it).exists() }
        }
        return runCatching {
            val safeName = UrlMediaBot.sanitizeFileName(baseTitle).ifBlank { "cover" }
            val coverFile = File(storage.getCoversDir(), "${safeName}_$downloadId.jpg")
            if (coverFile.exists() && coverFile.length() > 0L) return coverFile.absolutePath
            val req = Request.Builder()
                .url(thumbUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) SaveBox/1.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.size < 512) return@use null
                FileOutputStream(coverFile).use { it.write(bytes) }
                coverFile.absolutePath
            }
        }.getOrNull()
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, SaveBoxApplication.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading $title")
            .setContentText(if (indeterminate) "Saving to SaveBox..." else "$progress% completed")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showCompletedNotification(notificationId: Int, title: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, SaveBoxApplication.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Saved to SaveBox")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId + 5000, notification)
    }
}
