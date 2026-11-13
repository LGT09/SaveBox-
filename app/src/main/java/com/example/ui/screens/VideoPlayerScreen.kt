package com.example.ui.screens

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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.DownloadEntity
import com.example.ui.components.FormatUtils
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
    onSavePosition: (Long, Long, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val videos = remember(playlist, initialVideo) {
        playlist.ifEmpty { listOf(initialVideo) }
    }
    var currentIndex by remember(initialVideo) {
        mutableIntStateOf(videos.indexOfFirst { it.id == initialVideo.id }.coerceAtLeast(0))
    }
    val currentVideo = videos.getOrElse(currentIndex) { initialVideo }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(if (autoResumePosition) currentVideo.playbackPositionMs else 0L) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs) }
    var playbackSpeed by remember { mutableFloatStateOf(defaultSpeed) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
    )
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

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            subtitleUri = uri
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setPlaybackSpeed(defaultSpeed)
            playWhenReady = true
        }
    }

    LaunchedEffect(currentVideo, subtitleUri) {
        val mediaUri = when {
            !currentVideo.localFilePath.isNullOrBlank() && File(currentVideo.localFilePath).exists() ->
                Uri.fromFile(File(currentVideo.localFilePath))
            !currentVideo.localUri.isNullOrBlank() -> Uri.parse(currentVideo.localUri)
            else -> Uri.parse(currentVideo.downloadUrl)
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
        exoPlayer.setMediaItem(builder.build())
        if (autoResumePosition && currentVideo.playbackPositionMs > 0L) {
            exoPlayer.seekTo(currentVideo.playbackPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.play()
        onSavePosition(currentVideo.id, currentVideo.playbackPositionMs, true)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSavePosition(currentVideo.id, exoPlayer.currentPosition, false)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Periodic progress ticker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            }
            delay(350L)
        }
    }

    // Extract thumbnail frame while scrubbing local file
    LaunchedEffect(isScrubbing, scrubTargetMs) {
        if (isScrubbing && !currentVideo.localFilePath.isNullOrBlank() && File(currentVideo.localFilePath).exists()) {
            val frame = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(currentVideo.localFilePath)
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
        onSavePosition(currentVideo.id, exoPlayer.currentPosition, false)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { pv ->
                pv.resizeMode = resizeModes[resizeModeIndex].first
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
                            val target = (exoPlayer.currentPosition + delta).coerceIn(0L, durationMs.coerceAtLeast(1L))
                            exoPlayer.seekTo(target)
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

        // Double-tap seek indicator
        seekOverlayText?.let { label ->
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (label.startsWith("+")) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Brightness / Volume HUD indicator
        hudLabel?.let { label ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hudIsBrightness) Icons.Filled.BrightnessHigh else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .systemBarsPadding()
                    .padding(14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onSavePosition(currentVideo.id, exoPlayer.currentPosition, false)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${resizeModes[resizeModeIndex].second} • ${playbackSpeed}x",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Subtitles, contentDescription = "Load Subtitles", tint = Color.White)
                    }
                    IconButton(onClick = { enterPiP() }) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
                    }
                    IconButton(onClick = { toggleRotation() }) {
                        Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White)
                    }
                }

                // Center Play/Pause + Prev/Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) currentIndex--
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Video", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (currentIndex < videos.lastIndex) currentIndex++
                        },
                        enabled = currentIndex < videos.lastIndex
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next Video", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom Bar + Scrub Thumbnail Preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    if (isScrubbing && previewBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
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
                                        .width(136.dp)
                                        .height(76.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatDuration(scrubTargetMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            isScrubbing = true
                            val target = (frac * durationMs).toLong()
                            scrubTargetMs = target
                            positionMs = target
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(scrubTargetMs)
                            isScrubbing = false
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${FormatUtils.formatDuration(positionMs)} / ${FormatUtils.formatDuration(durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row {
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            IconButton(onClick = {
                                val next = speeds[(speeds.indexOfFirst { it == playbackSpeed } + 1) % speeds.size]
                                playbackSpeed = next
                                exoPlayer.setPlaybackSpeed(next)
                            }) {
                                Icon(Icons.Filled.Speed, contentDescription = "Playback Speed", tint = Color.White)
                            }
                            IconButton(onClick = {
                                resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                            }) {
                                Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                            }
                            IconButton(onClick = { toggleFullscreen() }) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
