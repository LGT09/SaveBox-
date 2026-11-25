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
import com.traxxiontech.savebox.model.RepeatPlaybackMode
import com.traxxiontech.savebox.player.SleepTimerOption
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
    val repeatMode: RepeatPlaybackMode = RepeatPlaybackMode.OFF,
    val autoPlayNext: Boolean = true,
    val seekStepSec: Int = 10,
    val selectedSleepOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepTimerRemainingSec: Long = 0L,
    val sleepAtEndOfVideo: Boolean = false,
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
    private var sleepTimerJob: Job? = null
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
                val state = _uiState.value
                if (state.sleepAtEndOfVideo) {
                    player.pause()
                    _uiState.update {
                        it.copy(
                            sleepAtEndOfVideo = false,
                            selectedSleepOption = SleepTimerOption.OFF,
                            sleepTimerRemainingSec = 0L
                        )
                    }
                    return
                }
                when (state.repeatMode) {
                    RepeatPlaybackMode.REPEAT_ONE -> {
                        // ExoPlayer handles REPEAT_MODE_ONE natively, fallback safe seek
                        player.seekTo(0L)
                        player.play()
                    }
                    RepeatPlaybackMode.REPEAT_ALL -> {
                        if (state.currentIndex < state.playlist.lastIndex) {
                            skipNext()
                        } else if (state.playlist.isNotEmpty()) {
                            playAtIndex(0)
                        }
                    }
                    RepeatPlaybackMode.OFF -> {
                        if (state.autoPlayNext && state.currentIndex < state.playlist.lastIndex) {
                            skipNext()
                        } else {
                            persistPosition()
                        }
                    }
                }
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
        defaultRepeatMode: RepeatPlaybackMode = RepeatPlaybackMode.OFF,
        defaultAutoPlayNext: Boolean = true,
        defaultSeekStepSec: Int = 10,
        subtitleUri: Uri? = null
    ) {
        val list = playlist.ifEmpty { listOf(target) }
        val index = list.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        if (loadedMediaId == target.id && loadedSubtitleUri == subtitleUri) return

        val isInitialLoad = loadedMediaId == -1L
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

        val activeRepeat = if (isInitialLoad) defaultRepeatMode else _uiState.value.repeatMode
        applyExoRepeatMode(activeRepeat)

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
                repeatMode = activeRepeat,
                autoPlayNext = if (isInitialLoad) defaultAutoPlayNext else it.autoPlayNext,
                seekStepSec = if (isInitialLoad) defaultSeekStepSec else it.seekStepSec,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            downloadRepository.updatePlaybackPosition(target.id, target.playbackPositionMs, true)
        }
    }

    private fun playAtIndex(index: Int) {
        val state = _uiState.value
        val target = state.playlist.getOrNull(index) ?: return
        persistPosition()
        loadedMediaId = -1L
        loadMedia(
            target = target,
            playlist = state.playlist,
            autoResume = false,
            defaultSpeed = state.playbackSpeed,
            defaultRepeatMode = state.repeatMode,
            defaultAutoPlayNext = state.autoPlayNext,
            defaultSeekStepSec = state.seekStepSec
        )
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
            playAtIndex(state.currentIndex + 1)
        } else if (state.repeatMode == RepeatPlaybackMode.REPEAT_ALL && state.playlist.isNotEmpty()) {
            playAtIndex(0)
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            playAtIndex(state.currentIndex - 1)
        } else if (state.repeatMode == RepeatPlaybackMode.REPEAT_ALL && state.playlist.isNotEmpty()) {
            playAtIndex(state.playlist.lastIndex)
        }
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val cur = _uiState.value.playbackSpeed
        val next = speeds[(speeds.indexOfFirst { it == cur } + 1) % speeds.size]
        setSpeed(next)
    }

    fun setRepeatMode(mode: RepeatPlaybackMode) {
        applyExoRepeatMode(mode)
        _uiState.update { it.copy(repeatMode = mode) }
    }

    fun cycleRepeatMode(): RepeatPlaybackMode {
        val next = when (_uiState.value.repeatMode) {
            RepeatPlaybackMode.OFF -> RepeatPlaybackMode.REPEAT_ONE
            RepeatPlaybackMode.REPEAT_ONE -> RepeatPlaybackMode.REPEAT_ALL
            RepeatPlaybackMode.REPEAT_ALL -> RepeatPlaybackMode.OFF
        }
        setRepeatMode(next)
        return next
    }

    private fun applyExoRepeatMode(mode: RepeatPlaybackMode) {
        player.repeatMode = when (mode) {
            RepeatPlaybackMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatPlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            RepeatPlaybackMode.REPEAT_ALL -> Player.REPEAT_MODE_OFF // Handled across playlist index in STATE_ENDED
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _uiState.update { it.copy(autoPlayNext = enabled) }
    }

    fun setSeekStepSec(seconds: Int) {
        _uiState.update { it.copy(seekStepSec = seconds.coerceIn(5, 60)) }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            _uiState.update {
                it.copy(
                    selectedSleepOption = SleepTimerOption.OFF,
                    sleepTimerRemainingSec = 0L,
                    sleepAtEndOfVideo = false
                )
            }
            return
        }
        var remaining = option.minutes * 60L
        _uiState.update {
            it.copy(
                selectedSleepOption = option,
                sleepTimerRemainingSec = remaining,
                sleepAtEndOfVideo = false
            )
        }
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            if (remaining <= 0L) {
                player.pause()
                persistPosition()
                _uiState.update {
                    it.copy(
                        selectedSleepOption = SleepTimerOption.OFF,
                        sleepTimerRemainingSec = 0L,
                        sleepAtEndOfVideo = false
                    )
                }
            }
        }
    }

    fun setSleepAtEndOfVideo() {
        sleepTimerJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSleepOption = SleepTimerOption.OFF,
                sleepTimerRemainingSec = 0L,
                sleepAtEndOfVideo = true
            )
        }
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
        sleepTimerJob?.cancel()
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }
}
