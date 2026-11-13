package com.example.data.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.SaveBoxApplication
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.FileOutputStream

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return@withContext Result.failure()

        val app = applicationContext as SaveBoxApplication
        val container = app.container
        val db = container.database
        val dao = db.downloadDao()
        val metadataDao = db.mediaMetadataDao()
        val storage = container.storageManager

        val entity = dao.getById(downloadId) ?: return@withContext Result.failure()
        if (entity.status == "PAUSED") return@withContext Result.success()

        val notificationId = 2000 + downloadId.toInt()
        setForeground(createForegroundInfo(notificationId, entity.title, 0, true))

        val targetFile = storage.getTargetFile(entity.folderCategory, entity.fileName)
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        try {
            dao.markStatus(downloadId, "DOWNLOADING", null)
            metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "DOWNLOADING")

            val requestBuilder = Request.Builder()
                .url(entity.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) SaveBox/1.0")

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            container.okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416 && existingBytes > 0L) {
                        targetFile.delete()
                        dao.markStatus(downloadId, "FAILED", "Server rejected range; tap Retry to restart.")
                        metadataDao.updateFileAndStatus(entity.sourceUrl, null, "FAILED")
                        return@withContext Result.failure()
                    }
                    dao.markStatus(downloadId, "FAILED", "Download error HTTP ${response.code}")
                    metadataDao.updateFileAndStatus(entity.sourceUrl, null, "FAILED")
                    return@withContext Result.failure()
                }

                val body = response.body ?: run {
                    dao.markStatus(downloadId, "FAILED", "Empty media stream.")
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
                                dao.updateProgress(downloadId, downloaded, totalBytes, "PAUSED")
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
                                dao.updateProgress(downloadId, downloaded, totalBytes, "DOWNLOADING")
                                val pct = if (totalBytes > 0L) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                setForeground(createForegroundInfo(notificationId, entity.title, pct, totalBytes <= 0L))
                            }
                        }
                    }
                }

                val finalSize = targetFile.length()
                val mediaType = MediaType.fromString(entity.mediaType)
                val mimeType = when (mediaType) {
                    MediaType.AUDIO -> "audio/mpeg"
                    MediaType.IMAGE -> "image/jpeg"
                    MediaType.VIDEO -> "video/mp4"
                }
                val mediaStoreUri = storage.exportToMediaStore(targetFile, mediaType, mimeType)

                dao.markCompleted(
                    id = downloadId,
                    status = "COMPLETED",
                    path = targetFile.absolutePath,
                    uri = mediaStoreUri?.toString(),
                    size = finalSize
                )
                metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "COMPLETED")
                showCompletedNotification(notificationId, entity.title)
                Result.success()
            }
        } catch (t: Throwable) {
            val latest = dao.getById(downloadId)
            if (latest?.status != "PAUSED") {
                dao.markStatus(downloadId, "FAILED", t.localizedMessage ?: "Download interrupted")
                metadataDao.updateFileAndStatus(entity.sourceUrl, targetFile.absolutePath, "FAILED")
            }
            Result.failure()
        }
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
