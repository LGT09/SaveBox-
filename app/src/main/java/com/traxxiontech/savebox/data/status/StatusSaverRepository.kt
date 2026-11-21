package com.traxxiontech.savebox.data.status

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.DownloadDao
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.model.LocalFolderMediaItem
import com.traxxiontech.savebox.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StatusSaverRepository(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadDao: DownloadDao
) {

    suspend fun scanFolderMedia(
        primaryTreeUri: String?,
        secondaryTreeUri: String?
    ): List<LocalFolderMediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalFolderMediaItem>()
        val savedDir = File(storageManager.getSaveBoxRootDir(), "Favorites")
        val savedNames = savedDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        primaryTreeUri?.let { uriStr ->
            scanDocumentTree(uriStr, "Primary Folder", savedNames, results)
        }
        secondaryTreeUri?.let { uriStr ->
            scanDocumentTree(uriStr, "Secondary Folder", savedNames, results)
        }

        results.sortedByDescending { it.lastModified }
    }

    private fun scanDocumentTree(
        uriString: String,
        sourceLabel: String,
        savedNames: Set<String>,
        out: MutableList<LocalFolderMediaItem>
    ) {
        try {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
            if (!rootDoc.canRead()) return

            val targetDir = rootDoc.findFile(".Statuses")?.takeIf { it.isDirectory } ?: rootDoc

            targetDir.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                if (name.startsWith(".") || !file.isFile) return@forEach
                val lower = name.lowercase()
                val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".3gp")
                val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                if (!isVideo && !isImage) return@forEach

                val mime = file.type ?: if (isVideo) "video/mp4" else "image/jpeg"
                out.add(
                    LocalFolderMediaItem(
                        uriString = file.uri.toString(),
                        name = name,
                        mimeType = mime,
                        isVideo = isVideo,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        sourceLabel = sourceLabel,
                        isAlreadySaved = savedNames.contains("IMP_$name")
                    )
                )
            }
        } catch (_: Throwable) {
            // Permission revoked or folder unavailable
        }
    }

    suspend fun importMediaToVault(item: LocalFolderMediaItem): DownloadEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            val sourceUri = Uri.parse(item.uriString)
            val destFileName = "IMP_${item.name}"
            val targetFile = storageManager.getTargetFile("Favorites", destFileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val mediaType = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE
            val mediaStoreUri = storageManager.exportToMediaStore(targetFile, mediaType, item.mimeType)

            val entity = DownloadEntity(
                title = item.name.substringBeforeLast('.'),
                fileName = destFileName,
                sourceUrl = item.uriString,
                downloadUrl = item.uriString,
                sourceHost = "local_folder",
                mediaType = mediaType.name,
                qualityLabel = if (item.isVideo) "Imported Video" else "Imported Photo",
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

    suspend fun deleteImportedCopy(item: LocalFolderMediaItem): Boolean = withContext(Dispatchers.IO) {
        val savedFile = storageManager.getTargetFile("Favorites", "IMP_${item.name}")
        return@withContext if (savedFile.exists()) savedFile.delete() else false
    }
}
