package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.model.VisualizerStyle
import com.example.player.SleepTimerOption
import com.example.ui.components.AnimatedAudioVisualizer
import com.example.ui.components.FormatUtils
import com.example.ui.components.GlassCard
import com.example.ui.theme.EmeraldMint
import com.example.ui.viewmodel.MainViewModel
import java.io.File

private fun resolveAudioArtworkModel(track: DownloadEntity): Any {
    val thumb = track.thumbnailUrl
    if (!thumb.isNullOrBlank()) {
        if (thumb.startsWith("/")) {
            val file = File(thumb)
            if (file.exists()) return file
        } else {
            return thumb
        }
    }
    return R.drawable.img_album_placeholder
}

@Composable
fun MiniAudioPlayerBar(
    viewModel: MainViewModel,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    val coverModel = remember(track.id, track.thumbnailUrl) { resolveAudioArtworkModel(track) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 20.dp,
        contentPadding = 10.dp,
        onClick = onOpenFullPlayer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.embeddedArtwork != null) {
                Image(
                    bitmap = state.embeddedArtwork!!.asImageBitmap(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                AsyncImage(
                    model = coverModel,
                    contentDescription = track.title,
                    placeholder = painterResource(id = R.drawable.img_album_placeholder),
                    error = painterResource(id = R.drawable.img_album_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.author ?: track.sourceHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }

            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    )
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteDownloads.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showEqSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val track = state.currentTrack
    val isFav = track != null && favorites.any { it.id == track.id }
    val progressFraction = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "art_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize Player")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.sleepTimerRemainingSec > 0L) {
                        val m = state.sleepTimerRemainingSec / 60
                        val s = state.sleepTimerRemainingSec % 60
                        Text(
                            text = "Sleep in ${m}m ${s}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Row {
                    IconButton(onClick = { viewModel.cycleVisualizerStyle() }) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Cycle Visualizer Style",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (track == null) {
                Spacer(modifier = Modifier.height(48.dp))
                GlassCard(cornerRadius = 26.dp, contentPadding = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_album_placeholder),
                            contentDescription = "No track loaded",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("SaveBox Audio Studio", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Play any song from Library or YouTube Search to unlock Cover Art, Equalizer, Sleep Timer, and Visualizers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Artwork or Lyrics View
            if (showLyrics) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp),
                    cornerRadius = 26.dp,
                    contentPadding = 18.dp
                ) {
                    Text("Lyrics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.embeddedLyrics
                            ?: "No embedded lyrics metadata found in \"${track.title}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val artworkModel = remember(track.id, track.thumbnailUrl) { resolveAudioArtworkModel(track) }
                Box(
                    modifier = Modifier
                        .size(224.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (state.isPlaying) glowAlpha else 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                                )
                            )
                        )
                        .padding(6.dp)
                ) {
                    if (state.embeddedArtwork != null) {
                        Image(
                            bitmap = state.embeddedArtwork!!.asImageBitmap(),
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                        )
                    } else {
                        AsyncImage(
                            model = artworkModel,
                            contentDescription = track.title,
                            placeholder = painterResource(id = R.drawable.img_album_placeholder),
                            error = painterResource(id = R.drawable.img_album_placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${track.author ?: track.sourceHost} • ${track.qualityLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selectable Visualizer Mode Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisualizerStyle.entries.forEach { vStyle ->
                    FilterChip(
                        selected = settings.visualizerStyle == vStyle,
                        onClick = { viewModel.setVisualizerStyle(vStyle) },
                        label = { Text(vStyle.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Live Visualizer Canvas
            AnimatedAudioVisualizer(
                isPlaying = state.isPlaying,
                progress = progressFraction,
                style = settings.visualizerStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (settings.visualizerStyle == VisualizerStyle.ORBITAL_RING) 76.dp else 46.dp)
            )

            Slider(
                value = progressFraction,
                onValueChange = { frac ->
                    val target = (frac * state.durationMs).toLong()
                    viewModel.seekTo(target)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(FormatUtils.formatDuration(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(FormatUtils.formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat Mode",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Utility Bar: Equalizer, Speed, Sleep Timer, Queue
            GlassCard(cornerRadius = 20.dp, contentPadding = 10.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = { showEqSheet = true }) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.primary)
                    }
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    IconButton(onClick = {
                        val nextIdx = (speeds.indexOfFirst { it == state.playbackSpeed } + 1) % speeds.size
                        viewModel.setPlaybackSpeed(speeds[nextIdx])
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = "Speed", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${state.playbackSpeed}x", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { showSleepSheet = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (state.sleepTimerRemainingSec > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playback Queue")
                    }
                }
            }
        }
    }

    if (showEqSheet) {
        ModalBottomSheet(onDismissRequest = { showEqSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5-Band Equalizer", style = MaterialTheme.typography.titleLarge)
                    Switch(
                        checked = eqState.enabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                    )
                }

                if (!eqState.isSupported) {
                    Text(
                        "Hardware audio session initializes once playback starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        eqState.presets.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = eqState.selectedPresetIndex == idx,
                                onClick = { viewModel.selectEqualizerPreset(idx) },
                                label = { Text(name) }
                            )
                        }
                    }
                    eqState.bands.forEach { band ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${band.centerFreqHz} Hz",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(68.dp)
                            )
                            Slider(
                                value = band.levelMillibels.toFloat(),
                                onValueChange = { viewModel.setEqualizerBand(band.index, it.toInt().toShort()) },
                                valueRange = eqState.minLevelMb.toFloat()..eqState.maxLevelMb.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
                SleepTimerOption.entries.forEach { opt ->
                    FilterChip(
                        selected = state.selectedSleepOption == opt,
                        onClick = {
                            viewModel.setSleepTimer(opt)
                            showSleepSheet = false
                        },
                        label = { Text(opt.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Playback Queue (${state.queue.size})", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.queue) { index, qItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playAudioTrack(qItem, state.queue)
                                    showQueueSheet = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (index == state.currentIndex) "▶" else "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(26.dp)
                            )
                            Text(
                                text = qItem.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeFromAudioQueue(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from queue", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
