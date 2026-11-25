package com.traxxiontech.savebox.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.traxxiontech.savebox.model.DocumentFileEntry
import com.traxxiontech.savebox.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentFileManager(
    private val context: Context,
    private val storageManager: StorageManager
) {
    fun listSaveBoxCategoryFiles(category: String? = null): List<DocumentFileEntry> {
        storageManager.ensureAllFolders()
        val rootDir = storageManager.getSaveBoxRootDir()
        val rootDoc = DocumentFile.fromFile(rootDir)

        val entries = mutableListOf<DocumentFileEntry>()
        val targetDirs = if (category.isNullOrBlank() || category == "All") {
            rootDoc.listFiles().filter { it.isDirectory }
        } else {
            listOfNotNull(rootDoc.findFile(category)?.takeIf { it.isDirectory })
        }

        targetDirs.forEach { dirDoc ->
            val dirName = dirDoc.name ?: "SaveBox"
            dirDoc.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                val childFile = File(File(rootDir, dirName), childName)
                val mime = child.type ?: inferMimeFromName(childName)
                entries.add(
                    DocumentFileEntry(
                        uriString = child.uri.toString(),
                        name = childName,
                        isDirectory = child.isDirectory,
                        mimeType = mime,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified(),
                        folderLabel = dirName,
                        localFilePath = childFile.absolutePath,
                        canWrite = child.canWrite(),
                        mediaType = inferMediaType(mime, childName)
                    )
                )
            }
        }
        return entries.sortedWith(compareBy<DocumentFileEntry> { !it.isDirectory }.thenByDescending { it.lastModified })
    }

    fun listSafTreeFiles(treeUriString: String): List<DocumentFileEntry> {
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return emptyList()
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val label = rootDoc.name ?: "Linked Folder"
        return rootDoc.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            val mime = doc.type ?: inferMimeFromName(name)
            DocumentFileEntry(
                uriString = doc.uri.toString(),
                name = name,
                isDirectory = doc.isDirectory,
                mimeType = mime,
                sizeBytes = doc.length(),
                lastModified = doc.lastModified(),
                folderLabel = label,
                localFilePath = null,
                canWrite = doc.canWrite(),
                mediaType = inferMediaType(mime, name)
            )
        }.sortedWith(compareBy<DocumentFileEntry> { !it.isDirectory }.thenByDescending { it.lastModified })
    }

    suspend fun createFolder(categoryOrTreeUri: String?, folderName: String, isSafTree: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val cleanName = folderName.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
            if (cleanName.isBlank()) return@withContext false
            runCatching {
                if (isSafTree && !categoryOrTreeUri.isNullOrBlank()) {
                    val doc = DocumentFile.fromTreeUri(context, Uri.parse(categoryOrTreeUri)) ?: return@runCatching false
                    doc.createDirectory(cleanName) != null
                } else {
                    val rootDoc = DocumentFile.fromFile(storageManager.getSaveBoxRootDir())
                    val parentDoc = if (!categoryOrTreeUri.isNullOrBlank() && categoryOrTreeUri != "All") {
                        rootDoc.findFile(categoryOrTreeUri) ?: rootDoc
                    } else {
                        rootDoc
                    }
                    parentDoc.createDirectory(cleanName) != null
                }
            }.getOrDefault(false)
        }

    suspend fun renameEntry(entry: DocumentFileEntry, newName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val clean = newName.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
            if (clean.isBlank()) return@withContext RenameResult(false)

            // Preserve extension for files if omitted
            val finalName = if (!entry.isDirectory && !clean.contains('.') && entry.name.contains('.')) {
                val ext = entry.name.substringAfterLast('.')
                "$clean.$ext"
            } else {
                clean
            }

            return@withContext runCatching {
                val localPath = entry.localFilePath
                if (localPath != null) {
                    val file = File(localPath)
                    val doc = DocumentFile.fromFile(file)
                    if (doc.renameTo(finalName)) {
                        val newFile = File(file.parentFile, finalName)
                        RenameResult(
                            success = true,
                            oldPath = localPath,
                            newPath = newFile.absolutePath,
                            newName = finalName
                        )
                    } else {
                        RenameResult(false)
                    }
                } else {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.uriString))
                        ?: return@runCatching RenameResult(false)
                    if (doc.renameTo(finalName)) {
                        RenameResult(
                            success = true,
                            oldPath = entry.uriString,
                            newPath = doc.uri.toString(),
                            newName = finalName
                        )
                    } else {
                        RenameResult(false)
                    }
                }
            }.getOrDefault(RenameResult(false))
        }

    suspend fun moveToSaveBoxCategory(entry: DocumentFileEntry, targetCategory: String): MoveResult =
        withContext(Dispatchers.IO) {
            if (entry.isDirectory) return@withContext MoveResult(false)
            val targetDir = File(storageManager.getSaveBoxRootDir(), targetCategory).apply {
                if (!exists()) mkdirs()
            }
            val destDocDir = DocumentFile.fromFile(targetDir)

            return@withContext runCatching {
                val localPath = entry.localFilePath
                if (localPath != null) {
                    val srcFile = File(localPath)
                    if (!srcFile.exists()) return@runCatching MoveResult(false)
                    val destFile = resolveUniqueFile(targetDir, srcFile.name)
                    if (srcFile.renameTo(destFile)) {
                        MoveResult(true, localPath, destFile.absolutePath, targetCategory)
                    } else {
                        srcFile.copyTo(destFile, overwrite = true)
                        if (destFile.exists()) {
                            srcFile.delete()
                            MoveResult(true, localPath, destFile.absolutePath, targetCategory)
                        } else {
                            MoveResult(false)
                        }
                    }
                } else {
                    // Copy from SAF DocumentFile into SaveBox category folder via streams
                    val srcUri = Uri.parse(entry.uriString)
                    val srcDoc = DocumentFile.fromSingleUri(context, srcUri)
                    val destDoc = destDocDir.createFile(entry.mimeType, entry.name)
                        ?: return@runCatching MoveResult(false)
                    context.contentResolver.openInputStream(srcUri)?.use { input ->
                        context.contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    srcDoc?.delete()
                    val destFile = File(targetDir, destDoc.name ?: entry.name)
                    MoveResult(true, entry.uriString, destFile.absolutePath, targetCategory)
                }
            }.getOrDefault(MoveResult(false))
        }

    suspend fun deleteEntry(entry: DocumentFileEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val localPath = entry.localFilePath
            if (localPath != null) {
                DocumentFile.fromFile(File(localPath)).delete()
            } else {
                DocumentFile.fromSingleUri(context, Uri.parse(entry.uriString))?.delete() ?: false
            }
        }.getOrDefault(false)
    }

    private fun resolveUniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }

    private fun inferMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "mkv", "webm", "mov", "avi" -> "video/mp4"
            "mp3", "m4a", "aac", "ogg", "wav", "flac" -> "audio/mpeg"
            "jpg", "jpeg", "png", "webp", "gif" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun inferMediaType(mime: String, name: String): MediaType? {
        val lowerMime = mime.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            lowerMime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi") -> MediaType.VIDEO
            lowerMime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "wav", "flac") -> MediaType.AUDIO
            lowerMime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif") -> MediaType.IMAGE
            else -> null
        }
    }

    data class RenameResult(
        val success: Boolean,
        val oldPath: String? = null,
        val newPath: String? = null,
        val newName: String? = null
    )

    data class MoveResult(
        val success: Boolean,
        val oldPath: String? = null,
        val newPath: String? = null,
        val newFolder: String? = null
    )
}
