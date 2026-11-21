package com.traxxiontech.savebox.data.status

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.DownloadDao
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.data.local.MediaItem
import com.traxxiontech.savebox.data.local.MediaItemDao
import com.traxxiontech.savebox.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Google-Play-compliant media scanner that discovers Photos, Videos, Songs, and their cover art across:
 * 1) SaveBox app directories (Videos, Music, Images, Downloads, Favorites),
 * 2) Accessible MediaStore entries (no broad READ_MEDIA_* permissions required),
 * 3) Persistent user-granted SAF DocumentTree folders, and
 * 4) Zero-permission Photo Picker / File Picker imports.
 */
class LocalMediaScanner(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadDao: DownloadDao,
    private val mediaItemDao: MediaItemDao
) {
    companion object {
        private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "3gp", "m4v", "avi")
        private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    }

    suspend fun scanAllAccessibleMedia(
        primaryTreeUri: String?,
        secondaryTreeUri: String?
    ): Int = withContext(Dispatchers.IO) {
        var indexedCount = 0
        val existingByPathOrUri = downloadDao.getAllOnce()
            .flatMap { listOfNotNull(it.localFilePath, it.localUri) }
            .toMutableSet()

        // 1. Scan SaveBox internal folders
        val root = storageManager.getSaveBoxRootDir()
        StorageManager.ALL_SAVEBOX_FOLDERS.forEach { folder ->
            val dir = File(root, folder)
            dir.listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { file ->
                val ext = file.extension.lowercase()
                val mediaType = classifyExtension(ext) ?: return@forEach
                if (existingByPathOrUri.contains(file.absolutePath)) return@forEach

                val meta = extractMetadataFromFile(file, mediaType)
                val coverPath = meta.coverArtFilePath ?: if (mediaType == MediaType.IMAGE) file.absolutePath else null
                val title = meta.title ?: file.nameWithoutExtension
                val mime = mimeForType(mediaType, ext)

                val entity = DownloadEntity(
                    title = title,
                    fileName = file.name,
                    sourceUrl = file.absolutePath,
                    downloadUrl = file.absolutePath,
                    sourceHost = "SaveBox $folder",
                    mediaType = mediaType.name,
                    qualityLabel = "Local ${mediaType.label}",
                    author = meta.artist ?: "SaveBox",
                    thumbnailUrl = coverPath,
                    durationMs = meta.durationMs,
                    fileSizeBytes = file.length(),
                    downloadedBytes = file.length(),
                    localFilePath = file.absolutePath,
                    localUri = Uri.fromFile(file).toString(),
                    folderCategory = folder,
                    status = "COMPLETED",
                    isFavorite = folder == "Favorites",
                    createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                )
                downloadDao.insert(entity)
                mediaItemDao.upsert(
                    MediaItem(
                        title = title,
                        url = file.absolutePath,
                        filePath = file.absolutePath,
                        coverArtPath = coverPath,
                        mediaType = mediaType.name,
                        mimeType = mime,
                        author = meta.artist,
                        qualityLabel = "Local ${mediaType.label}",
                        durationMs = meta.durationMs,
                        fileSizeBytes = file.length(),
                        timestamp = entity.createdAt
                    )
                )
                existingByPathOrUri.add(file.absolutePath)
                indexedCount++
            }
        }

        // 2. Query accessible MediaStore collections (app-owned & scoped accessible media)
        indexedCount += scanMediaStoreAudio(existingByPathOrUri)
        indexedCount += scanMediaStoreVisual(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.VIDEO,
            defaultFolder = "Videos",
            existingPaths = existingByPathOrUri
        )
        indexedCount += scanMediaStoreVisual(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.IMAGE,
            defaultFolder = "Images",
            existingPaths = existingByPathOrUri
        )

        // 3. Recursively scan any user-granted SAF folders
        listOfNotNull(primaryTreeUri, secondaryTreeUri).forEachIndexed { idx, uriStr ->
            indexedCount += scanSafTree(uriStr, if (idx == 0) "Linked Folder" else "Folder #2", existingByPathOrUri)
        }

        indexedCount
    }

    private suspend fun scanMediaStoreAudio(existingPaths: MutableSet<String>): Int {
        var added = 0
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 150) {
                    count++
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    if (existingPaths.contains(contentUri)) continue

                    val displayName = cursor.getString(nameCol) ?: "Audio_$id.mp3"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.')
                    val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val albumId = cursor.getLong(albumCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val dateSec = cursor.getLong(dateCol)

                    val coverPath = extractEmbeddedArtFromUri(Uri.parse(contentUri), "ms_audio_$id")
                        ?: if (albumId > 0L) {
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                        } else null

                    downloadDao.insert(
                        DownloadEntity(
                            title = title,
                            fileName = displayName,
                            sourceUrl = contentUri,
                            downloadUrl = contentUri,
                            sourceHost = "Device Music",
                            mediaType = MediaType.AUDIO.name,
                            qualityLabel = "Audio",
                            author = artist,
                            thumbnailUrl = coverPath,
                            durationMs = durationMs,
                            fileSizeBytes = sizeBytes,
                            downloadedBytes = sizeBytes,
                            localFilePath = null,
                            localUri = contentUri,
                            folderCategory = "Music",
                            status = "COMPLETED",
                            createdAt = if (dateSec > 0L) dateSec * 1000L else System.currentTimeMillis()
                        )
                    )
                    mediaItemDao.upsert(
                        MediaItem(
                            title = title,
                            url = contentUri,
                            filePath = contentUri,
                            coverArtPath = coverPath,
                            mediaType = MediaType.AUDIO.name,
                            mimeType = mime,
                            author = artist,
                            qualityLabel = "Audio",
                            durationMs = durationMs,
                            fileSizeBytes = sizeBytes
                        )
                    )
                    existingPaths.add(contentUri)
                    added++
                }
            }
        }
        return added
    }

    private suspend fun scanMediaStoreVisual(
        collection: Uri,
        mediaType: MediaType,
        defaultFolder: String,
        existingPaths: MutableSet<String>
    ): Int {
        var added = 0
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 150) {
                    count++
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    if (existingPaths.contains(contentUri)) continue

                    val name = cursor.getString(nameCol) ?: "${mediaType.label}_$id"
                    val title = name.substringBeforeLast('.')
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol) ?: mimeForType(mediaType, name.substringAfterLast('.', ""))
                    val dateSec = cursor.getLong(dateCol)

                    downloadDao.insert(
                        DownloadEntity(
                            title = title,
                            fileName = name,
                            sourceUrl = contentUri,
                            downloadUrl = contentUri,
                            sourceHost = "Device ${defaultFolder}",
                            mediaType = mediaType.name,
                            qualityLabel = mediaType.label,
                            author = "Device Storage",
                            thumbnailUrl = contentUri,
                            fileSizeBytes = size,
                            downloadedBytes = size,
                            localFilePath = null,
                            localUri = contentUri,
                            folderCategory = defaultFolder,
                            status = "COMPLETED",
                            createdAt = if (dateSec > 0L) dateSec * 1000L else System.currentTimeMillis()
                        )
                    )
                    mediaItemDao.upsert(
                        MediaItem(
                            title = title,
                            url = contentUri,
                            filePath = contentUri,
                            coverArtPath = contentUri,
                            mediaType = mediaType.name,
                            mimeType = mime,
                            qualityLabel = mediaType.label,
                            fileSizeBytes = size
                        )
                    )
                    existingPaths.add(contentUri)
                    added++
                }
            }
        }
        return added
    }

    private suspend fun scanSafTree(
        uriString: String,
        sourceLabel: String,
        existingPaths: MutableSet<String>
    ): Int {
        var added = 0
        runCatching {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return 0
            if (!rootDoc.canRead()) return 0

            val queue = ArrayDeque<DocumentFile>()
            queue.add(rootDoc)
            var visitedDirs = 0

            while (queue.isNotEmpty() && visitedDirs < 20 && added < 200) {
                val dir = queue.removeFirst()
                visitedDirs++
                dir.listFiles().forEach { doc ->
                    val name = doc.name ?: return@forEach
                    if (doc.isDirectory && !name.startsWith(".")) {
                        queue.add(doc)
                        return@forEach
                    }
                    if (!doc.isFile || name.startsWith(".")) return@forEach

                    val ext = name.substringAfterLast('.', "").lowercase()
                    val mime = doc.type.orEmpty()
                    val mediaType = classifyMimeOrExt(mime, ext) ?: return@forEach
                    val uriStr = doc.uri.toString()
                    if (existingPaths.contains(uriStr)) return@forEach

                    val title = name.substringBeforeLast('.')
                    val folder = storageManager.resolveTargetFolder(mediaType, null)
                    val meta = if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
                        extractMetadataFromUri(doc.uri, "saf_${name.hashCode()}", mediaType)
                    } else LocalMeta()

                    val coverPath = meta.coverArtFilePath ?: if (mediaType != MediaType.AUDIO) uriStr else null

                    downloadDao.insert(
                        DownloadEntity(
                            title = meta.title ?: title,
                            fileName = name,
                            sourceUrl = uriStr,
                            downloadUrl = uriStr,
                            sourceHost = sourceLabel,
                            mediaType = mediaType.name,
                            qualityLabel = "Device ${mediaType.label}",
                            author = meta.artist ?: sourceLabel,
                            thumbnailUrl = coverPath,
                            durationMs = meta.durationMs,
                            fileSizeBytes = doc.length(),
                            downloadedBytes = doc.length(),
                            localFilePath = null,
                            localUri = uriStr,
                            folderCategory = folder,
                            status = "COMPLETED",
                            createdAt = doc.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                    )
                    mediaItemDao.upsert(
                        MediaItem(
                            title = meta.title ?: title,
                            url = uriStr,
                            filePath = uriStr,
                            coverArtPath = coverPath,
                            mediaType = mediaType.name,
                            mimeType = mime.ifBlank { mimeForType(mediaType, ext) },
                            author = meta.artist,
                            qualityLabel = "Device ${mediaType.label}",
                            durationMs = meta.durationMs,
                            fileSizeBytes = doc.length()
                        )
                    )
                    existingPaths.add(uriStr)
                    added++
                }
            }
        }
        return added
    }

    /**
     * Imports user-picked URIs from the zero-permission Android Photo Picker or System File Picker,
     * saves them into SaveBox folders, extracts embedded album art/duration, and indexes them in Room.
     */
    suspend fun importPickedMediaUris(
        uris: List<Uri>,
        mediaTypeHint: MediaType? = null
    ): List<DownloadEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<DownloadEntity>()
        uris.forEach { uri ->
            runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri).orEmpty()
                val rawName = queryDisplayName(uri) ?: "media_${System.currentTimeMillis()}"
                val ext = rawName.substringAfterLast('.', "").lowercase().ifBlank {
                    when {
                        mime.startsWith("audio/") -> "mp3"
                        mime.startsWith("image/") -> "jpg"
                        else -> "mp4"
                    }
                }
                val mediaType = mediaTypeHint ?: classifyMimeOrExt(mime, ext) ?: MediaType.VIDEO
                val folder = storageManager.resolveTargetFolder(mediaType, null)
                val finalName = if (rawName.contains('.')) rawName else "$rawName.$ext"
                val destFile = storageManager.getTargetFile(folder, "${System.currentTimeMillis() % 10000}_$finalName")

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching

                val meta = extractMetadataFromFile(destFile, mediaType)
                val coverPath = meta.coverArtFilePath ?: if (mediaType == MediaType.IMAGE) destFile.absolutePath else null
                val title = meta.title ?: rawName.substringBeforeLast('.')

                val entity = DownloadEntity(
                    title = title,
                    fileName = destFile.name,
                    sourceUrl = uri.toString(),
                    downloadUrl = uri.toString(),
                    sourceHost = "Device Import",
                    mediaType = mediaType.name,
                    qualityLabel = "Imported ${mediaType.label}",
                    author = meta.artist ?: "Device",
                    thumbnailUrl = coverPath,
                    durationMs = meta.durationMs,
                    fileSizeBytes = destFile.length(),
                    downloadedBytes = destFile.length(),
                    localFilePath = destFile.absolutePath,
                    localUri = Uri.fromFile(destFile).toString(),
                    folderCategory = folder,
                    status = "COMPLETED"
                )
                val id = downloadDao.insert(entity)
                mediaItemDao.upsert(
                    MediaItem(
                        title = title,
                        url = uri.toString(),
                        filePath = destFile.absolutePath,
                        coverArtPath = coverPath,
                        mediaType = mediaType.name,
                        mimeType = mime.ifBlank { mimeForType(mediaType, ext) },
                        author = meta.artist,
                        qualityLabel = "Imported ${mediaType.label}",
                        durationMs = meta.durationMs,
                        fileSizeBytes = destFile.length()
                    )
                )
                imported.add(entity.copy(id = id))
            }
        }
        imported
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private data class LocalMeta(
        val title: String? = null,
        val artist: String? = null,
        val durationMs: Long = 0L,
        val coverArtFilePath: String? = null
    )

    private fun extractMetadataFromFile(file: File, mediaType: MediaType): LocalMeta {
        if (mediaType == MediaType.IMAGE) return LocalMeta(coverArtFilePath = file.absolutePath)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val art = retriever.embeddedPicture
            val coverPath = if (art != null && art.isNotEmpty()) {
                val outFile = File(storageManager.getCoversDir(), "art_${file.nameWithoutExtension.hashCode()}.jpg")
                FileOutputStream(outFile).use { it.write(art) }
                outFile.absolutePath
            } else null
            LocalMeta(title = title, artist = artist, durationMs = dur, coverArtFilePath = coverPath)
        } catch (_: Throwable) {
            LocalMeta()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractMetadataFromUri(uri: Uri, key: String, mediaType: MediaType): LocalMeta {
        if (mediaType == MediaType.IMAGE) return LocalMeta(coverArtFilePath = uri.toString())
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val art = retriever.embeddedPicture
            val coverPath = if (art != null && art.isNotEmpty()) {
                val outFile = File(storageManager.getCoversDir(), "art_$key.jpg")
                FileOutputStream(outFile).use { it.write(art) }
                outFile.absolutePath
            } else null
            LocalMeta(title = title, artist = artist, durationMs = dur, coverArtFilePath = coverPath)
        } catch (_: Throwable) {
            LocalMeta()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractEmbeddedArtFromUri(uri: Uri, key: String): String? {
        val outFile = File(storageManager.getCoversDir(), "art_$key.jpg")
        if (outFile.exists() && outFile.length() > 0L) return outFile.absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                FileOutputStream(outFile).use { it.write(art) }
                outFile.absolutePath
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun classifyExtension(ext: String): MediaType? = when {
        ext in VIDEO_EXTS -> MediaType.VIDEO
        ext in AUDIO_EXTS -> MediaType.AUDIO
        ext in IMAGE_EXTS -> MediaType.IMAGE
        else -> null
    }

    private fun classifyMimeOrExt(mime: String, ext: String): MediaType? = when {
        mime.startsWith("video/", true) -> MediaType.VIDEO
        mime.startsWith("audio/", true) -> MediaType.AUDIO
        mime.startsWith("image/", true) -> MediaType.IMAGE
        else -> classifyExtension(ext)
    }

    private fun mimeForType(type: MediaType, ext: String): String = when (type) {
        MediaType.VIDEO -> if (ext == "webm") "video/webm" else "video/mp4"
        MediaType.AUDIO -> if (ext == "m4a") "audio/mp4" else "audio/mpeg"
        MediaType.IMAGE -> if (ext == "png") "image/png" else "image/jpeg"
    }
}
