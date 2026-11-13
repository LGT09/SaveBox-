package com.example.data.status

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.download.StorageManager
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.WhatsAppStatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StatusSaverRepository(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadDao: DownloadDao
) {

    suspend fun scanStatuses(
        whatsAppTreeUri: String?,
        whatsAppBusinessTreeUri: String?
    ): List<WhatsAppStatusItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WhatsAppStatusItem>()
        val savedDir = File(storageManager.getSaveBoxRootDir(), "Favorites")
        val savedNames = savedDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        whatsAppTreeUri?.let { uriStr ->
            scanDocumentTree(uriStr, "WhatsApp", savedNames, results)
        }
        whatsAppBusinessTreeUri?.let { uriStr ->
            scanDocumentTree(uriStr, "WhatsApp Business", savedNames, results)
        }

        results.sortedByDescending { it.lastModified }
    }

    private fun scanDocumentTree(
        uriString: String,
        sourceLabel: String,
        savedNames: Set<String>,
        out: MutableList<WhatsAppStatusItem>
    ) {
        try {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
            if (!rootDoc.canRead()) return

            // If user picked parent WhatsApp folder, try resolving Media/.Statuses automatically
            val statusDir = resolveStatusesSubdir(rootDoc) ?: rootDoc

            statusDir.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                if (name.startsWith(".") || !file.isFile) return@forEach
                val lower = name.lowercase()
                val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".3gp")
                val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                if (!isVideo && !isImage) return@forEach

                val mime = file.type ?: if (isVideo) "video/mp4" else "image/jpeg"
                out.add(
                    WhatsAppStatusItem(
                        uriString = file.uri.toString(),
                        name = name,
                        mimeType = mime,
                        isVideo = isVideo,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        sourceLabel = sourceLabel,
                        isAlreadySaved = savedNames.contains("WA_$name")
                    )
                )
            }
        } catch (_: Throwable) {
            // Permission revoked or folder unavailable
        }
    }

    private fun resolveStatusesSubdir(root: DocumentFile): DocumentFile? {
        if (root.name.equals(".Statuses", ignoreCase = true)) return root
        root.findFile(".Statuses")?.let { if (it.isDirectory) return it }
        root.findFile("Media")?.findFile(".Statuses")?.let { if (it.isDirectory) return it }
        root.findFile("WhatsApp")?.findFile("Media")?.findFile(".Statuses")?.let { if (it.isDirectory) return it }
        root.findFile("WhatsApp Business")?.findFile("Media")?.findFile(".Statuses")?.let { if (it.isDirectory) return it }
        return null
    }

    suspend fun saveStatusToGallery(item: WhatsAppStatusItem): DownloadEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            val sourceUri = Uri.parse(item.uriString)
            val destFileName = "WA_${item.name}"
            val targetFile = storageManager.getTargetFile("Favorites", destFileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val mediaType = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE
            val mediaStoreUri = storageManager.exportToMediaStore(targetFile, mediaType, item.mimeType)

            val entity = DownloadEntity(
                title = "${item.sourceLabel} Status",
                fileName = destFileName,
                sourceUrl = item.uriString,
                downloadUrl = item.uriString,
                platform = MediaPlatform.WHATSAPP_STATUS.name,
                mediaType = mediaType.name,
                qualityLabel = if (item.isVideo) "Status Video" else "Status Photo",
                author = item.sourceLabel,
                thumbnailUrl = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                downloadedBytes = targetFile.length(),
                localFilePath = targetFile.absolutePath,
                localUri = mediaStoreUri?.toString() ?: Uri.fromFile(targetFile).toString(),
                folderCategory = "Favorites",
                status = "COMPLETED",
                isFavorite = true
            )
            val id = downloadDao.insert(entity)
            entity.copy(id = id)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun deleteSavedCopy(item: WhatsAppStatusItem): Boolean = withContext(Dispatchers.IO) {
        val savedFile = storageManager.getTargetFile("Favorites", "WA_${item.name}")
        return@withContext if (savedFile.exists()) savedFile.delete() else false
    }
}
