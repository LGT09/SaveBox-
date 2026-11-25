package com.traxxiontech.savebox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.LiquidGlassCard
import com.traxxiontech.savebox.ui.components.MediaSourceBadgeChip
import com.traxxiontech.savebox.ui.components.ThumbnailDownloadProgressOverlay
import com.traxxiontech.savebox.ui.theme.EmeraldMint
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel

@Composable
fun QueueScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayMedia: (DownloadEntity) -> Unit
) {
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Download Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (allDownloads.any { it.status != "COMPLETED" }) {
                    TextButton(onClick = { viewModel.clearDownloadQueueHistory() }) {
                        Text("Clear Pending")
                    }
                }
            }

            if (allDownloads.isEmpty()) {
                LiquidGlassCard(cornerRadius = 24.dp, contentPadding = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No downloads in queue", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Analyze any link on Home to queue simultaneous background downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allDownloads, key = { it.id }) { item ->
                        QueueDownloadCard(
                            item = item,
                            onPause = { viewModel.pauseDownload(item) },
                            onResumeOrRetry = { viewModel.resumeOrRetryDownload(item) },
                            onDelete = { viewModel.deleteDownload(item) },
                            onPlay = { onPlayMedia(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDownloadCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    LiquidGlassCard(
        cornerRadius = 20.dp,
        contentPadding = 14.dp,
        onClick = if (item.status == "COMPLETED") onPlay else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AsyncImage(
                    model = item.thumbnailUrl ?: R.drawable.img_album_placeholder,
                    contentDescription = item.title,
                    placeholder = painterResource(id = R.drawable.img_album_placeholder),
                    error = painterResource(id = R.drawable.img_album_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ThumbnailDownloadProgressOverlay(
                    progress = item.progressFraction,
                    status = item.status,
                    isIndeterminate = item.status == "DOWNLOADING" && item.fileSizeBytes <= 0L,
                    modifier = Modifier.fillMaxSize(),
                    ringSize = 42.dp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaSourceBadgeChip(label = item.sourceHost)
                    Text(
                        text = item.qualityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (item.status) {
                "DOWNLOADING", "QUEUED" -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                }
                "PAUSED" -> {
                    IconButton(onClick = onResumeOrRetry) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    }
                }
                "FAILED" -> {
                    IconButton(onClick = onResumeOrRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                "COMPLETED" -> {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = EmeraldMint)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }

        if (item.status != "COMPLETED") {
            Spacer(modifier = Modifier.height(10.dp))
            if (item.status == "DOWNLOADING" && item.fileSizeBytes <= 0L) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            } else {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pct = (item.progressFraction * 100).toInt()
                Text(
                    text = "${item.status} • $pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${FormatUtils.formatBytes(item.downloadedBytes)} / ${FormatUtils.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
