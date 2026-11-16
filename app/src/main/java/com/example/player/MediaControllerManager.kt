package com.example.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.DownloadEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SleepTimerOption(val label: String, val minutes: Int) {
    OFF("Off", 0),
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    MIN_45("45 min", 45),
    MIN_60("60 min", 60)
}

data class AudioPlayerState(
    val currentTrack: DownloadEntity? = null,
    val queue: List<DownloadEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val embeddedArtwork: Bitmap? = null,
    val embeddedLyrics: String? = null,
    val sleepTimerRemainingSec: Long = 0L,
    val selectedSleepOption: SleepTimerOption = SleepTimerOption.OFF
)

class MediaControllerManager(
    private val context: Context,
    private val onPositionSaved: suspend (Long, Long, Boolean) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachPlayerListener(controller)
                    startProgressTicker()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = controller.currentMediaItemIndex
                val queue = _state.value.queue
                val track = queue.getOrNull(idx)
                _state.value = _state.value.copy(
                    currentTrack = track,
                    currentIndex = idx,
                    durationMs = controller.duration.coerceAtLeast(track?.durationMs ?: 0L)
                )
                track?.let { extractLocalMetadata(it) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = controller.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    _state.value = _state.value.copy(durationMs = dur)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.value = _state.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.value = _state.value.copy(repeatMode = repeatMode)
            }
        })
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.mediaItemCount > 0) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(_state.value.durationMs)
                    _state.value = _state.value.copy(
                        positionMs = pos,
                        durationMs = dur,
                        isPlaying = controller.isPlaying
                    )
                    val track = _state.value.currentTrack
                    if (track != null && controller.isPlaying && pos % 3000L < 500L) {
                        scope.launch(Dispatchers.IO) {
                            onPositionSaved(track.id, pos, false)
                        }
                    }
                }
                delay(350L)
            }
        }
    }

    fun playQueue(tracks: List<DownloadEntity>, startIndex: Int = 0, autoResumePosition: Boolean = true) {
        if (tracks.isEmpty()) return
        connect()
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val firstTrack = tracks[safeIdx]

        _state.value = _state.value.copy(
            queue = tracks,
            currentIndex = safeIdx,
            currentTrack = firstTrack,
            positionMs = if (autoResumePosition) firstTrack.playbackPositionMs else 0L
        )
        extractLocalMetadata(firstTrack)
        scope.launch(Dispatchers.IO) {
            onPositionSaved(firstTrack.id, firstTrack.playbackPositionMs, true)
        }

        val controller = mediaController
        if (controller != null) {
            val mediaItems = tracks.mapNotNull { toMediaItem(it) }
            if (mediaItems.isEmpty()) return
            val clampedIdx = safeIdx.coerceIn(0, mediaItems.lastIndex)
            val startPos = if (autoResumePosition) firstTrack.playbackPositionMs else 0L
            controller.setMediaItems(mediaItems, clampedIdx, startPos)
            controller.prepare()
            controller.play()
        } else {
            scope.launch {
                delay(400L)
                mediaController?.let { ready ->
                    val mediaItems = tracks.mapNotNull { toMediaItem(it) }
                    if (mediaItems.isNotEmpty()) {
                        ready.setMediaItems(mediaItems, safeIdx.coerceIn(0, mediaItems.lastIndex), 0L)
                        ready.prepare()
                        ready.play()
                    }
                }
            }
        }
    }

    fun addToQueue(track: DownloadEntity) {
        val currentQueue = _state.value.queue
        if (currentQueue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val updated = currentQueue + track
        _state.value = _state.value.copy(queue = updated)
        toMediaItem(track)?.let { mediaController?.addMediaItem(it) }
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _state.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        _state.value = _state.value.copy(queue = currentQueue)
        mediaController?.removeMediaItem(index)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            _state.value = _state.value.copy(selectedSleepOption = option, sleepTimerRemainingSec = 0L)
            return
        }
        var remaining = option.minutes * 60L
        _state.value = _state.value.copy(selectedSleepOption = option, sleepTimerRemainingSec = remaining)
        sleepTimerJob = scope.launch {
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining--
                _state.value = _state.value.copy(sleepTimerRemainingSec = remaining)
            }
            if (remaining <= 0L) {
                mediaController?.pause()
                _state.value = _state.value.copy(
                    selectedSleepOption = SleepTimerOption.OFF,
                    sleepTimerRemainingSec = 0L
                )
            }
        }
    }

    private fun toMediaItem(entity: DownloadEntity): MediaItem? {
        val uri = when {
            !entity.localFilePath.isNullOrBlank() && File(entity.localFilePath).exists() ->
                Uri.fromFile(File(entity.localFilePath))
            !entity.localUri.isNullOrBlank() -> Uri.parse(entity.localUri)
            else -> return null
        }
        val artworkUri = entity.thumbnailUrl?.let { thumb ->
            if (thumb.startsWith("/")) Uri.fromFile(File(thumb)) else Uri.parse(thumb)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(entity.title)
            .setArtist(entity.author ?: entity.sourceHost)
            .setArtworkUri(artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(entity.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun extractLocalMetadata(entity: DownloadEntity) {
        scope.launch(Dispatchers.IO) {
            val path = entity.localFilePath
            if (path.isNullOrBlank() || !File(path).exists()) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(embeddedArtwork = null, embeddedLyrics = null)
                }
                return@launch
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val artBytes = retriever.embeddedPicture
                val bitmap = artBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                // Standard metadata key 24 or custom lyrics tag if present
                val lyrics = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        embeddedArtwork = bitmap,
                        embeddedLyrics = lyrics?.takeIf { it.length > 20 }
                    )
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(embeddedArtwork = null, embeddedLyrics = null)
                }
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
