package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.StorageFolderBreakdown
import java.io.File

class StorageManager(private val context: Context) {

    companion object {
        val ALL_SAVEBOX_FOLDERS = listOf(
            "Videos",
            "Music",
            "Instagram",
            "TikTok",
            "Facebook",
            "Twitter",
            "Spotify",
            "Threads",
            "Pinterest",
            "Downloads",
            "Favorites"
        )
    }

    fun getSaveBoxRootDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "SaveBox").apply { if (!exists()) mkdirs() }
    }

    fun ensureAllFolders() {
        val root = getSaveBoxRootDir()
        ALL_SAVEBOX_FOLDERS.forEach { folder ->
            File(root, folder).apply { if (!exists()) mkdirs() }
        }
    }

    fun resolveTargetFolder(platform: MediaPlatform, mediaType: MediaType, customFolder: String?): String {
        if (!customFolder.isNullOrBlank() && customFolder != "Auto by Platform") {
            return customFolder
        }
        return when (platform) {
            MediaPlatform.INSTAGRAM -> "Instagram"
            MediaPlatform.TIKTOK -> "TikTok"
            MediaPlatform.FACEBOOK -> "Facebook"
            MediaPlatform.TWITTER -> "Twitter"
            MediaPlatform.SPOTIFY -> "Spotify"
            MediaPlatform.THREADS -> "Threads"
            MediaPlatform.PINTEREST -> "Pinterest"
            MediaPlatform.SOUNDCLOUD -> "Music"
            MediaPlatform.YOUTUBE, MediaPlatform.YOUTUBE_PLAYLIST, MediaPlatform.LIKEE ->
                if (mediaType == MediaType.AUDIO) "Music" else "Videos"
            MediaPlatform.WHATSAPP_STATUS -> "Favorites"
            else -> when (mediaType) {
                MediaType.AUDIO -> "Music"
                MediaType.VIDEO -> "Videos"
                MediaType.IMAGE -> "Downloads"
            }
        }
    }

    fun getTargetFile(folderCategory: String, fileName: String): File {
        val dir = File(getSaveBoxRootDir(), folderCategory).apply { if (!exists()) mkdirs() }
        return File(dir, fileName)
    }

    fun getStorageBreakdown(): List<StorageFolderBreakdown> {
        ensureAllFolders()
        val root = getSaveBoxRootDir()
        return ALL_SAVEBOX_FOLDERS.map { name ->
            val dir = File(root, name)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            StorageFolderBreakdown(
                folderName = name,
                fileCount = files.size,
                sizeBytes = files.sumOf { it.length() }
            )
        }
    }

    fun getTotalSaveBoxUsageBytes(): Long {
        return getStorageBreakdown().sumOf { it.sizeBytes }
    }

    fun getCacheSizeBytes(): Long {
        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
    }

    fun clearAppCache() {
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()
        context.externalCacheDir?.deleteRecursively()
        context.externalCacheDir?.mkdirs()
    }

    fun exportToMediaStore(file: File, mediaType: MediaType, mimeType: String): Uri? {
        if (!file.exists()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Uri.fromFile(file)
        }
        val collection: Uri
        val relativePath: String
        when (mediaType) {
            MediaType.VIDEO -> {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_MOVIES}/SaveBox"
            }
            MediaType.AUDIO -> {
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_MUSIC}/SaveBox"
            }
            MediaType.IMAGE -> {
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                relativePath = "${Environment.DIRECTORY_PICTURES}/SaveBox"
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return try {
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Throwable) {
            null
        }
    }
}
