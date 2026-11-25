package com.traxxiontech.savebox.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.model.RepeatPlaybackMode
import com.traxxiontech.savebox.model.VisualizerStyle
import com.traxxiontech.savebox.player.SleepTimerOption
import com.traxxiontech.savebox.ui.components.AnimatedAudioVisualizer
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.PlayerSponsoredAdSection
import com.traxxiontech.savebox.ui.theme.AuroraViolet
import com.traxxiontech.savebox.ui.theme.CoralAlert
import com.traxxiontech.savebox.ui.theme.DimTextPrimary
import com.traxxiontech.savebox.ui.theme.ElectricCyan
import com.traxxiontech.savebox.ui.theme.GlassBorderLight
import com.traxxiontech.savebox.ui.viewmodel.MediaPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    initialVideo: DownloadEntity,
    playlist: List<DownloadEntity>,
    autoResumePosition: Boolean,
    defaultSpeed: Float,
    defaultRepeatMode: RepeatPlaybackMode = RepeatPlaybackMode.OFF,
    defaultAutoPlayNext: Boolean = true,
    defaultSeekStepSec: Int = 10,
    visualizerStyle: VisualizerStyle = VisualizerStyle.SPECTRUM_BARS,
    onBack: () -> Unit,
    playerViewModel: MediaPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showVisualizer by remember { mutableStateOf(false) }
    var showSponsoredStrip by remember { mutableStateOf(true) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showPlaybackSettingsSheet by remember { mutableStateOf(false) }
    var activeVisualizerStyle by remember(visualizerStyle) { mutableStateOf(visualizerStyle) }
    var isFullscreen by remember { mutableStateOf(false) }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }

    // Gesture & mode feedback states
    var seekOverlayText by remember { mutableStateOf<String?>(null) }
    var hudLabel by remember { mutableStateOf<String?>(null) }
    var hudIsBrightness by remember { mutableStateOf(true) }

    // Scrubbing frame preview
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTargetMs by remember { mutableLongStateOf(0L) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val currentVideo = uiState.currentItem ?: initialVideo
    val displayedPositionMs = if (isScrubbing) scrubTargetMs else uiState.positionMs
    val durationMs = uiState.durationMs.coerceAtLeast(currentVideo.durationMs)
    val resizeLabel = playerViewModel.resizeModes
        .getOrElse(uiState.resizeModeIndex) { playerViewModel.resizeModes.first() }
        .second
    val activeAccent = MaterialTheme.colorScheme.primary

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            subtitleUri = uri
        }
    }

    LaunchedEffect(initialVideo.id, subtitleUri) {
        playerViewModel.loadMedia(
            target = initialVideo,
            playlist = playlist,
            autoResume = autoResumePosition,
            defaultSpeed = defaultSpeed,
            defaultRepeatMode = defaultRepeatMode,
            defaultAutoPlayNext = defaultAutoPlayNext,
            defaultSeekStepSec = defaultSeekStepSec,
            subtitleUri = subtitleUri
        )
    }

    // Auto-hide Liquid Glass controls during active playback
    LaunchedEffect(showControls, uiState.isPlaying, isScrubbing, showSleepSheet, showPlaybackSettingsSheet) {
        if (showControls && uiState.isPlaying && !isScrubbing && !showSleepSheet && !showPlaybackSettingsSheet) {
            delay(4500L)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.persistPosition()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isScrubbing, scrubTargetMs, currentVideo.localFilePath) {
        val localPath = currentVideo.localFilePath
        if (isScrubbing && !localPath.isNullOrBlank() && File(localPath).exists()) {
            val frame = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(localPath)
                    retriever.getFrameAtTime(scrubTargetMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
            previewBitmap = frame
        } else if (!isScrubbing) {
            previewBitmap = null
        }
    }

    BackHandler {
        playerViewModel.persistPosition()
        onBack()
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.window?.let { win ->
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            if (isFullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun toggleRotation() {
        val currentOrient = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (currentOrient == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { activity.enterPictureInPictureMode(params) }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val glassPanelBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xB8141B26),
                Color(0x9E080C14)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_savebox_player, null, false) as PlayerView).apply {
                    useController = false
                    player = playerViewModel.player
                }
            },
            update = { pv ->
                pv.player = playerViewModel.player
                pv.resizeMode = playerViewModel.resizeModes
                    .getOrElse(uiState.resizeModeIndex) { playerViewModel.resizeModes.first() }
                    .first
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = pinchScale,
                    scaleY = pinchScale
                )
        )

        // Gesture capture layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.seekStepSec) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val isRight = offset.x > size.width / 2f
                            val stepSec = uiState.seekStepSec
                            val delta = if (isRight) stepSec * 1000L else -stepSec * 1000L
                            playerViewModel.seekBy(delta)
                            seekOverlayText = if (isRight) "+${stepSec}s" else "-${stepSec}s"
                            scope.launch {
                                delay(750L)
                                seekOverlayText = null
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                delay(700L)
                                hudLabel = null
                            }
                        }
                    ) { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2f
                        val deltaRatio = -dragAmount / size.height.toFloat()
                        if (isLeft && activity != null) {
                            hudIsBrightness = true
                            val lp = activity.window.attributes
                            val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                            val next = (current + deltaRatio * 1.4f).coerceIn(0.05f, 1.0f)
                            lp.screenBrightness = next
                            activity.window.attributes = lp
                            hudLabel = "Brightness ${(next * 100).toInt()}%"
                        } else {
                            hudIsBrightness = false
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val step = if (deltaRatio > 0.015f) 1 else if (deltaRatio < -0.015f) -1 else 0
                            if (step != 0) {
                                val nextVol = (curVol + step).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                hudLabel = "Volume ${((nextVol.toFloat() / maxVol) * 100).toInt()}%"
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        pinchScale = (pinchScale * zoom).coerceIn(1f, 2.5f)
                    }
                }
        )

        // Error pill if playback fails
        uiState.errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CoralAlert.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, GlassBorderLight),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 74.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = err,
                    color = DimTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Glass double-tap seek / mode indicator
        seekOverlayText?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(glassPanelBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (label.startsWith("+") || label.startsWith("-")) {
                        Icon(
                            imageVector = if (label.startsWith("+")) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                            contentDescription = null,
                            tint = activeAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(label, color = DimTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Glass Brightness / Volume HUD indicator
        hudLabel?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(glassPanelBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hudIsBrightness) Icons.Filled.BrightnessHigh else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = DimTextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Ambient Live Media Visualizer Overlay
        AnimatedVisibility(
            visible = showVisualizer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = if (showControls) 174.dp else 26.dp
                )
        ) {
            val vizProgress = if (durationMs > 0L) {
                (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassPanelBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                    .clickable {
                        val entries = VisualizerStyle.entries
                        activeVisualizerStyle = entries[(entries.indexOf(activeVisualizerStyle) + 1) % entries.size]
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedAudioVisualizer(
                        isPlaying = uiState.isPlaying,
                        progress = vizProgress,
                        style = activeVisualizerStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        barCount = 38
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeVisualizerStyle.label} • Tap to switch",
                        color = activeAccent,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Liquid Glass Playback Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x9E030509),
                                Color(0x20030509),
                                Color(0xBD030509)
                            )
                        )
                    )
                    .systemBarsPadding()
                    .padding(14.dp)
            ) {
                // Top Liquid Glass Toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(26.dp), ambientColor = activeAccent.copy(alpha = 0.16f))
                            .clip(RoundedCornerShape(26.dp))
                            .background(glassPanelBrush)
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    playerViewModel.persistPosition()
                                    onBack()
                                },
                                modifier = Modifier.testTag("video_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DimTextPrimary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentVideo.title,
                                    color = DimTextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${currentVideo.qualityLabel} • $resizeLabel • ${uiState.playbackSpeed}x",
                                    color = activeAccent,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { showSleepSheet = true },
                                modifier = Modifier.testTag("video_sleep_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (uiState.sleepTimerRemainingSec > 0L || uiState.sleepAtEndOfVideo) activeAccent else DimTextPrimary
                                )
                            }
                            IconButton(
                                onClick = { showPlaybackSettingsSheet = true },
                                modifier = Modifier.testTag("video_playback_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Playback Settings",
                                    tint = DimTextPrimary
                                )
                            }
                            IconButton(onClick = { enterPiP() }) {
                                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = DimTextPrimary)
                            }
                            IconButton(onClick = { toggleRotation() }) {
                                Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate Screen", tint = DimTextPrimary)
                            }
                        }
                    }

                    // Active Repeat / Sleep Status Pills on screen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (uiState.repeatMode != RepeatPlaybackMode.OFF) {
                                activeAccent.copy(alpha = 0.28f)
                            } else {
                                Color(0x88111722)
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .clickable {
                                    val next = playerViewModel.cycleRepeatMode()
                                    seekOverlayText = next.label
                                    scope.launch {
                                        delay(900L)
                                        seekOverlayText = null
                                    }
                                }
                                .testTag("video_repeat_mode_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.repeatMode == RepeatPlaybackMode.REPEAT_ONE) {
                                        Icons.Filled.RepeatOne
                                    } else {
                                        Icons.Filled.Repeat
                                    },
                                    contentDescription = "Repeat Mode",
                                    tint = if (uiState.repeatMode != RepeatPlaybackMode.OFF) activeAccent else DimTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.repeatMode.label,
                                    color = DimTextPrimary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        if (uiState.sleepTimerRemainingSec > 0L || uiState.sleepAtEndOfVideo) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = activeAccent.copy(alpha = 0.26f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier.clickable { showSleepSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bedtime,
                                        contentDescription = "Sleep Timer Active",
                                        tint = activeAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val label = if (uiState.sleepAtEndOfVideo) {
                                        "Sleep: End of Video"
                                    } else {
                                        val m = uiState.sleepTimerRemainingSec / 60
                                        val s = uiState.sleepTimerRemainingSec % 60
                                        "Sleep ${m}:${s.toString().padStart(2, '0')}"
                                    }
                                    Text(
                                        text = label,
                                        color = DimTextPrimary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (showVisualizer) activeAccent.copy(alpha = 0.26f) else Color(0x88111722),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .clickable { showVisualizer = !showVisualizer }
                                .testTag("video_visualizer_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = "Visualizer",
                                    tint = if (showVisualizer) activeAccent else DimTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Visualizer", color = DimTextPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0x88111722),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.clickable { subtitlePicker.launch(arrayOf("*/*")) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = DimTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Subtitles", color = DimTextPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Center Liquid Glass Transport Pod
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .shadow(16.dp, RoundedCornerShape(40.dp), ambientColor = AuroraViolet.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(40.dp))
                        .background(glassPanelBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(40.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { playerViewModel.skipPrevious() },
                            enabled = uiState.currentIndex > 0 || uiState.repeatMode == RepeatPlaybackMode.REPEAT_ALL,
                            modifier = Modifier.testTag("video_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = if (uiState.currentIndex > 0 || uiState.repeatMode == RepeatPlaybackMode.REPEAT_ALL) {
                                    DimTextPrimary
                                } else {
                                    DimTextPrimary.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.seekBy(-uiState.seekStepSec * 1000L) },
                            modifier = Modifier.testTag("video_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastRewind,
                                contentDescription = "Rewind ${uiState.seekStepSec} seconds",
                                tint = DimTextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(66.dp)
                                .shadow(12.dp, CircleShape, ambientColor = activeAccent.copy(alpha = 0.45f))
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(activeAccent, AuroraViolet)))
                                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                .testTag("video_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = DimTextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.seekBy(uiState.seekStepSec * 1000L) },
                            modifier = Modifier.testTag("video_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = "Forward ${uiState.seekStepSec} seconds",
                                tint = DimTextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.skipNext() },
                            enabled = uiState.currentIndex < uiState.playlist.lastIndex || uiState.repeatMode == RepeatPlaybackMode.REPEAT_ALL,
                            modifier = Modifier.testTag("video_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (uiState.currentIndex < uiState.playlist.lastIndex || uiState.repeatMode == RepeatPlaybackMode.REPEAT_ALL) {
                                    DimTextPrimary
                                } else {
                                    DimTextPrimary.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bottom Liquid Glass Seekbar & Utility Dock
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    if (isScrubbing && previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(glassPanelBrush)
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Seek Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatDuration(scrubTargetMs),
                                    color = activeAccent,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = activeAccent.copy(alpha = 0.16f))
                            .clip(RoundedCornerShape(26.dp))
                            .background(glassPanelBrush)
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val progress = if (durationMs > 0L) {
                                (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = progress,
                                onValueChange = { frac ->
                                    isScrubbing = true
                                    scrubTargetMs = (frac * durationMs).toLong()
                                },
                                onValueChangeFinished = {
                                    playerViewModel.seekTo(scrubTargetMs)
                                    isScrubbing = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = activeAccent,
                                    activeTrackColor = activeAccent,
                                    inactiveTrackColor = DimTextPrimary.copy(alpha = 0.24f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${FormatUtils.formatDuration(displayedPositionMs)} / ${FormatUtils.formatDuration(durationMs)}",
                                    color = DimTextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { playerViewModel.toggleMute() }) {
                                        Icon(
                                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                                            tint = if (uiState.isMuted) CoralAlert else DimTextPrimary
                                        )
                                    }
                                    IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                                        Icon(
                                            imageVector = if (uiState.repeatMode == RepeatPlaybackMode.REPEAT_ONE) {
                                                Icons.Filled.RepeatOne
                                            } else {
                                                Icons.Filled.Repeat
                                            },
                                            contentDescription = "Cycle Repeat Mode",
                                            tint = if (uiState.repeatMode != RepeatPlaybackMode.OFF) activeAccent else DimTextPrimary
                                        )
                                    }
                                    IconButton(onClick = { playerViewModel.cycleSpeed() }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Speed, contentDescription = "Playback Speed", tint = activeAccent, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "${uiState.playbackSpeed}x",
                                                color = DimTextPrimary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    IconButton(onClick = { playerViewModel.cycleResizeMode() }) {
                                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = DimTextPrimary)
                                    }
                                    IconButton(onClick = { toggleFullscreen() }) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                            contentDescription = "Fullscreen",
                                            tint = DimTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showSponsoredStrip && !isFullscreen) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PlayerSponsoredAdSection(
                            onDismiss = { showSponsoredStrip = false }
                        )
                    }
                }
            }
        }
    }

    if (showSleepSheet) {
        ModalBottomSheet(onDismissRequest = { showSleepSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Video Sleep Timer", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Automatically pause video playback when the timer ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SleepTimerOption.entries.forEach { opt ->
                    FilterChip(
                        selected = !uiState.sleepAtEndOfVideo && uiState.selectedSleepOption == opt,
                        onClick = {
                            playerViewModel.setSleepTimer(opt)
                            showSleepSheet = false
                        },
                        label = { Text(opt.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FilterChip(
                    selected = uiState.sleepAtEndOfVideo,
                    onClick = {
                        playerViewModel.setSleepAtEndOfVideo()
                        showSleepSheet = false
                    },
                    label = { Text("Pause at End of Current Video") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showPlaybackSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showPlaybackSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Playback Settings", style = MaterialTheme.typography.titleLarge)

                Text("Repeat Mode", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepeatPlaybackMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.repeatMode == mode,
                            onClick = { playerViewModel.setRepeatMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Text("Playback Speed", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        FilterChip(
                            selected = uiState.playbackSpeed == spd,
                            onClick = { playerViewModel.setSpeed(spd) },
                            label = { Text("${spd}x") }
                        )
                    }
                }

                Text("Skip / Seek Step", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { step ->
                        FilterChip(
                            selected = uiState.seekStepSec == step,
                            onClick = { playerViewModel.setSeekStepSec(step) },
                            label = { Text("${step}s") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Play Next Video", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Automatically play next item in queue when finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.autoPlayNext,
                        onCheckedChange = { playerViewModel.setAutoPlayNext(it) }
                    )
                }

                Text("Visualizer Overlay Style", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VisualizerStyle.entries.forEach { vStyle ->
                        FilterChip(
                            selected = activeVisualizerStyle == vStyle,
                            onClick = { activeVisualizerStyle = vStyle },
                            label = { Text(vStyle.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
