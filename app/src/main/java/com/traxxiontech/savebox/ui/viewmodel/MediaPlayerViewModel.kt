package com.traxxiontech.savebox.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.traxxiontech.savebox.data.download.DownloadRepository
import com.traxxiontech.savebox.data.local.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MediaPlayerUiState(
    val currentItem: DownloadEntity? = null,
    val playlist: List<DownloadEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val resizeModeIndex: Int = 0,
    val isMuted: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
    )

    private val _uiState = MutableStateFlow(MediaPlayerUiState())
    val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var loadedMediaId: Long = -1L
    private var loadedSubtitleUri: Uri? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val dur = player.duration.coerceAtLeast(0L)
            if (dur > 0L) {
                _uiState.update { it.copy(durationMs = dur) }
            }
            if (playbackState == Player.STATE_ENDED) {
                skipNext()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(errorMessage = error.localizedMessage ?: "Unable to play this media file.")
            }
        }
    }

    init {
        player.addListener(playerListener)
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                if (player.playbackState != Player.STATE_IDLE) {
                    _uiState.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                        )
                    }
                }
                delay(300L)
            }
        }
    }

    fun loadMedia(
        target: DownloadEntity,
        playlist: List<DownloadEntity>,
        autoResume: Boolean,
        defaultSpeed: Float,
        subtitleUri: Uri? = null
    ) {
        val list = playlist.ifEmpty { listOf(target) }
        val index = list.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        if (loadedMediaId == target.id && loadedSubtitleUri == subtitleUri) return

        loadedMediaId = target.id
        loadedSubtitleUri = subtitleUri

        val mediaUri = when {
            !target.localFilePath.isNullOrBlank() && File(target.localFilePath).exists() ->
                Uri.fromFile(File(target.localFilePath))
            !target.localUri.isNullOrBlank() -> Uri.parse(target.localUri)
            else -> Uri.parse(target.downloadUrl)
        }

        val builder = MediaItem.Builder().setUri(mediaUri)
        subtitleUri?.let { sub ->
            val subConfig = MediaItem.SubtitleConfiguration.Builder(sub)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        player.setMediaItem(builder.build())
        player.setPlaybackSpeed(defaultSpeed)
        if (autoResume && target.playbackPositionMs > 0L) {
            player.seekTo(target.playbackPositionMs)
        }
        player.prepare()
        player.playWhenReady = true

        _uiState.update {
            it.copy(
                currentItem = target,
                playlist = list,
                currentIndex = index,
                positionMs = if (autoResume) target.playbackPositionMs else 0L,
                durationMs = target.durationMs,
                playbackSpeed = defaultSpeed,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            downloadRepository.updatePlaybackPosition(target.id, target.playbackPositionMs, true)
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            persistPosition()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, player.duration.coerceAtLeast(1L))
        player.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.currentIndex < state.playlist.lastIndex) {
            persistPosition()
            val next = state.playlist[state.currentIndex + 1]
            loadedMediaId = -1L
            loadMedia(next, state.playlist, autoResume = false, defaultSpeed = state.playbackSpeed)
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            persistPosition()
            val prev = state.playlist[state.currentIndex - 1]
            loadedMediaId = -1L
            loadMedia(prev, state.playlist, autoResume = false, defaultSpeed = state.playbackSpeed)
        }
    }

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        val cur = _uiState.value.playbackSpeed
        val next = speeds[(speeds.indexOfFirst { it == cur } + 1) % speeds.size]
        player.setPlaybackSpeed(next)
        _uiState.update { it.copy(playbackSpeed = next) }
    }

    fun cycleResizeMode() {
        val next = (_uiState.value.resizeModeIndex + 1) % resizeModes.size
        _uiState.update { it.copy(resizeModeIndex = next) }
    }

    fun toggleMute() {
        val mute = !_uiState.value.isMuted
        player.volume = if (mute) 0f else 1f
        _uiState.update { it.copy(isMuted = mute) }
    }

    fun persistPosition() {
        val item = _uiState.value.currentItem ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        viewModelScope.launch {
            downloadRepository.updatePlaybackPosition(item.id, pos, false)
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        val item = _uiState.value.currentItem
        val pos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        if (item != null && pos > 0L) {
            // Fire-and-forget safe save before clearing scope isn't guaranteed; persistPosition is also called onDispose/onBack
        }
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }
}
