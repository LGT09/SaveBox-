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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import com.traxxiontech.savebox.model.VisualizerStyle
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
@Composable
fun VideoPlayerScreen(
    initialVideo: DownloadEntity,
    playlist: List<DownloadEntity>,
    autoResumePosition: Boolean,
    defaultSpeed: Float,
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
    var activeVisualizerStyle by remember(visualizerStyle) { mutableStateOf(visualizerStyle) }
    var isFullscreen by remember { mutableStateOf(false) }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }

    // Gesture feedback states
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
            subtitleUri = subtitleUri
        )
    }

    // Auto-hide glass controls during active playback
    LaunchedEffect(showControls, uiState.isPlaying, isScrubbing) {
        if (showControls && uiState.isPlaying && !isScrubbing) {
            delay(4200L)
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

    // Extract thumbnail frame while scrubbing local file
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
        Brush.linearGradient(
            colors = listOf(
                Color(0xC2080D16),
                Color(0xA605080E)
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

        // Gesture capture layer (tap, double-tap seek, vertical volume/brightness swipe, pinch-to-zoom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val isRight = offset.x > size.width / 2f
                            val delta = if (isRight) 10_000L else -10_000L
                            playerViewModel.seekBy(delta)
                            seekOverlayText = if (isRight) "+10s" else "-10s"
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

        // Glass double-tap seek indicator
        seekOverlayText?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(glassPanelBrush)
                    .border(1.dp, GlassBorderLight, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (label.startsWith("+")) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                    .border(1.dp, GlassBorderLight, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hudIsBrightness) Icons.Filled.BrightnessHigh else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = DimTextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Ambient Live Media Visualizer Overlay (persists even when controls auto-hide)
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
                    bottom = if (showControls) 164.dp else 26.dp
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
                    .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp))
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
                        color = ElectricCyan.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Glassmorphism Playback Controls Overlay
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
                                Color(0xA6030509),
                                Color(0x24030509),
                                Color(0xC4030509)
                            )
                        )
                    )
                    .systemBarsPadding()
                    .padding(14.dp)
            ) {
                // Top Glass Toolbar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = ElectricCyan.copy(alpha = 0.16f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(glassPanelBrush)
                        .border(1.dp, GlassBorderLight, RoundedCornerShape(22.dp))
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
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${currentVideo.qualityLabel} • $resizeLabel • ${uiState.playbackSpeed}x",
                                color = ElectricCyan.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showVisualizer = !showVisualizer },
                            modifier = Modifier.testTag("video_visualizer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Toggle Video Visualizer",
                                tint = if (showVisualizer) ElectricCyan else DimTextPrimary
                            )
                        }
                        IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "Load Subtitles", tint = DimTextPrimary)
                        }
                        IconButton(onClick = { enterPiP() }) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = DimTextPrimary)
                        }
                        IconButton(onClick = { toggleRotation() }) {
                            Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate Screen", tint = DimTextPrimary)
                        }
                    }
                }

                // Center Glass Transport Pod
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .shadow(16.dp, RoundedCornerShape(38.dp), ambientColor = AuroraViolet.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(38.dp))
                        .background(glassPanelBrush)
                        .border(1.dp, GlassBorderLight, RoundedCornerShape(38.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { playerViewModel.skipPrevious() },
                            enabled = uiState.currentIndex > 0,
                            modifier = Modifier.testTag("video_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = if (uiState.currentIndex > 0) DimTextPrimary else DimTextPrimary.copy(alpha = 0.35f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.seekBy(-10_000L) },
                            modifier = Modifier.testTag("video_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastRewind,
                                contentDescription = "Rewind 10 seconds",
                                tint = DimTextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(66.dp)
                                .shadow(12.dp, CircleShape, ambientColor = ElectricCyan.copy(alpha = 0.45f))
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ElectricCyan, AuroraViolet)))
                                .border(1.5.dp, DimTextPrimary.copy(alpha = 0.55f), CircleShape)
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
                            onClick = { playerViewModel.seekBy(10_000L) },
                            modifier = Modifier.testTag("video_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = "Forward 10 seconds",
                                tint = DimTextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.skipNext() },
                            enabled = uiState.currentIndex < uiState.playlist.lastIndex,
                            modifier = Modifier.testTag("video_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (uiState.currentIndex < uiState.playlist.lastIndex) DimTextPrimary else DimTextPrimary.copy(alpha = 0.35f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bottom Glass Seekbar & Utility Dock
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(glassPanelBrush)
                                .border(1.dp, GlassBorderLight, RoundedCornerShape(14.dp))
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
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatDuration(scrubTargetMs),
                                    color = ElectricCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = ElectricCyan.copy(alpha = 0.16f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(glassPanelBrush)
                            .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp))
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
                                    thumbColor = ElectricCyan,
                                    activeTrackColor = ElectricCyan,
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
                                    fontWeight = FontWeight.Medium
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { playerViewModel.toggleMute() }) {
                                        Icon(
                                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                                            tint = if (uiState.isMuted) CoralAlert else DimTextPrimary
                                        )
                                    }
                                    IconButton(onClick = { playerViewModel.cycleSpeed() }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Speed, contentDescription = "Playback Speed", tint = ElectricCyan, modifier = Modifier.size(18.dp))
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
}
