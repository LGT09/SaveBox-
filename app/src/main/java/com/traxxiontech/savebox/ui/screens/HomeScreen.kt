package com.traxxiontech.savebox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.model.AnalyzedMedia
import com.traxxiontech.savebox.model.MediaFormatOption
import com.traxxiontech.savebox.model.MediaType
import com.traxxiontech.savebox.model.SearchScope
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.LiquidGlassCard
import com.traxxiontech.savebox.ui.components.MediaSourceBadgeChip
import com.traxxiontech.savebox.ui.components.ShimmerSkeletonCard
import com.traxxiontech.savebox.ui.theme.AuroraViolet
import com.traxxiontech.savebox.ui.theme.ElectricCyan
import com.traxxiontech.savebox.ui.theme.EmeraldMint
import com.traxxiontech.savebox.ui.viewmodel.AnalyzeUiState
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenQueue: () -> Unit,
    onOpenSearch: (SearchScope) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenAudioPlayer: () -> Unit,
    onPlayMedia: (DownloadEntity) -> Unit
) {
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val clipboardSuggestion by viewModel.clipboardSuggestion.collectAsStateWithLifecycle()
    val analyzeState by viewModel.analyzeState.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val queueDownloads by viewModel.queueDownloads.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteDownloads.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(settings.autoClipboardDetect) {
        if (settings.autoClipboardDetect) {
            val clip = clipboardManager.getText()?.text?.trim()
            if (!clip.isNullOrBlank() && (clip.startsWith("http://", true) || clip.startsWith("https://", true))) {
                viewModel.setClipboardSuggestion(clip)
            }
        }
    }

    val totalSavedBytes = storageBreakdown.sumOf { it.sizeBytes }
    val activeQueueCount = queueDownloads.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SaveBox",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Smart URL Media Bot & Offline Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("home_queue_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeQueueCount > 0) {
                                    Badge { Text("$activeQueueCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Download Queue",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Clipboard detection chip
            item {
                AnimatedVisibility(visible = !clipboardSuggestion.isNullOrBlank() && urlInput.isBlank()) {
                    LiquidGlassCard(
                        cornerRadius = 18.dp,
                        contentPadding = 12.dp,
                        onClick = {
                            clipboardSuggestion?.let {
                                viewModel.pasteAndFetch(it)
                                viewModel.setClipboardSuggestion(null)
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Clipboard link detected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clipboard URL Detected",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = clipboardSuggestion.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                clipboardSuggestion?.let {
                                    viewModel.pasteAndFetch(it)
                                    viewModel.setClipboardSuggestion(null)
                                }
                            }) {
                                Text("Fetch Media")
                            }
                        }
                    }
                }
            }

            // URL Input & Smart Bot Card
            item {
                LiquidGlassCard(cornerRadius = 26.dp, contentPadding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paste Link to Fetch Video or Image",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        MediaSourceBadgeChip(label = "Link Bot")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.updateUrlInput(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_url_input"),
                        placeholder = {
                            Text(
                                "https://example.com/video.mp4 or any media page URL",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = "Media URL",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearUrlInput() },
                                    modifier = Modifier.testTag("home_clear_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear URL"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { viewModel.fetchMediaFromCurrentUrl() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) {
                                    viewModel.pasteAndFetch(clip)
                                } else {
                                    viewModel.showMessage("Clipboard is empty")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("home_paste_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste")
                        }

                        Button(
                            onClick = { viewModel.fetchMediaFromCurrentUrl() },
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .testTag("home_analyze_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch Media", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Loading / Error feedback
                    when (val state = analyzeState) {
                        is AnalyzeUiState.Loading -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Scanning URL for videos, images & quality sizes...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ShimmerSkeletonCard(height = 110.dp)
                        }
                        is AnalyzeUiState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Inline Fetched Media Preview & Quality/Size Selector right on screen
            val fetchedState = analyzeState as? AnalyzeUiState.Success
            if (fetchedState != null) {
                item(key = "fetched_media_preview_${fetchedState.media.sourceUrl}") {
                    FetchedMediaPreviewCard(
                        analyzed = fetchedState.media,
                        availableFolders = listOf("Auto by Type") + viewModel.getStorageBreakdownFolders(),
                        onDismiss = { viewModel.dismissFetchedMedia() },
                        onStartDownload = { format, customTitle, folder ->
                            viewModel.startDownload(fetchedState.media, format, customTitle, folder)
                        }
                    )
                }
            }

            // Legal Notice Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Legal Notice",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Download only videos or images you own or have permission to save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Actions
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "YouTube",
                        subtitle = "Search Media",
                        icon = Icons.Filled.Search,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenSearch(SearchScope.YOUTUBE) }
                    )
                    QuickActionTile(
                        title = "Browser",
                        subtitle = "Google & Web",
                        icon = Icons.Filled.Public,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenBrowser
                    )
                    QuickActionTile(
                        title = "Queue",
                        subtitle = "$activeQueueCount active",
                        icon = Icons.Filled.Download,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenQueue
                    )
                    QuickActionTile(
                        title = "Privacy",
                        subtitle = "Policy Info",
                        icon = Icons.Filled.PrivacyTip,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPrivacyPolicy
                    )
                }
            }

            // Download Statistics & Storage Usage
            item {
                LiquidGlassCard(cornerRadius = 22.dp, contentPadding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "Storage",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SaveBox Vault Statistics",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = FormatUtils.formatBytes(totalSavedBytes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatMetricItem("Saved", "${completedDownloads.size}", Modifier.weight(1f))
                        StatMetricItem("Favorites", "${favorites.size}", Modifier.weight(1f))
                        StatMetricItem("Queue", "${queueDownloads.size}", Modifier.weight(1f))
                        StatMetricItem("Played", "${recentlyPlayed.size}", Modifier.weight(1f))
                    }
                }
            }

            // Recently Played Media
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "Recently Played",
                        actionLabel = "Library",
                        onAction = onOpenLibrary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentlyPlayed, key = { "rp_${it.id}" }) { item ->
                            CompactMediaCard(
                                item = item,
                                onClick = { onPlayMedia(item) }
                            )
                        }
                    }
                }
            }

            // Recent Downloads
            item {
                SectionHeaderRow(
                    title = "Recent Downloads",
                    actionLabel = "View All",
                    onAction = onOpenLibrary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (completedDownloads.isEmpty()) {
                    LiquidGlassCard(cornerRadius = 20.dp, contentPadding = 18.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your SaveBox Vault is ready",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Paste any video or image URL above, or search YouTube to save media offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        completedDownloads.take(5).forEach { item ->
                            CompactDownloadRow(
                                item = item,
                                onPlay = { onPlayMedia(item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
            }

            // Favorite Downloads
            if (favorites.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "Favorites",
                        actionLabel = "See All",
                        onAction = onOpenLibrary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites.take(8), key = { "fav_${it.id}" }) { item ->
                            CompactMediaCard(item = item, onClick = { onPlayMedia(item) })
                        }
                    }
                }
            }

            // Search History
            if (recentSearches.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "Recent Links & Searches",
                        actionLabel = "Clear",
                        onAction = { viewModel.clearSearchHistory() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.take(10).forEach { entry ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (entry.isUrl) {
                                        viewModel.pasteAndFetch(entry.query)
                                    } else {
                                        viewModel.setGlobalSearchQuery(entry.query)
                                        onOpenSearch(SearchScope.YOUTUBE)
                                    }
                                },
                                label = {
                                    Text(
                                        text = entry.query,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 180.dp)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FetchedMediaPreviewCard(
    analyzed: AnalyzedMedia,
    availableFolders: List<String>,
    onDismiss: () -> Unit,
    onStartDownload: (MediaFormatOption, String, String?) -> Unit
) {
    var selectedFormat by remember(analyzed) { mutableStateOf(analyzed.formats.first()) }
    var customTitle by remember(analyzed) { mutableStateOf(analyzed.title) }
    var selectedFolder by remember(analyzed) { mutableStateOf("Auto by Type") }

    val previewModel: Any = when {
        selectedFormat.mediaType == MediaType.IMAGE -> selectedFormat.downloadUrl
        !analyzed.thumbnailUrl.isNullOrBlank() -> analyzed.thumbnailUrl
        else -> R.drawable.img_album_placeholder
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fetched_media_preview_card"),
        cornerRadius = 26.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaSourceBadgeChip(label = selectedFormat.mediaType.label)
                Text(
                    text = analyzed.sourceHost,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close Preview")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Media Preview directly on screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            AsyncImage(
                model = previewModel,
                contentDescription = analyzed.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            analyzed.durationText?.let { dur ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = dur,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = analyzed.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        analyzed.author?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = customTitle,
            onValueChange = { customTitle = it },
            label = { Text("File title") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Select Quality & File Size (${analyzed.formats.size} found)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Quality + Size Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            analyzed.formats.forEach { option ->
                val isSelected = option.id == selectedFormat.id
                val sizeText = option.sizeDisplay
                    ?: option.estimatedSizeBytes?.let { FormatUtils.formatBytes(it) }
                    ?: "Size varies"
                val icon = when (option.mediaType) {
                    MediaType.VIDEO -> Icons.Filled.VideoFile
                    MediaType.AUDIO -> Icons.Filled.AudioFile
                    MediaType.IMAGE -> Icons.Filled.Image
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedFormat = option }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val detailParts = listOfNotNull(
                                    option.mediaType.label,
                                    option.extension.uppercase(),
                                    option.resolution
                                ).joinToString(" • ")
                                Text(
                                    text = detailParts,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableFolders.forEach { folder ->
                FilterChip(
                    selected = selectedFolder == folder,
                    onClick = { selectedFolder = folder },
                    label = { Text(folder) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onStartDownload(selectedFormat, customTitle, selectedFolder) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("sheet_download_now_button"),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(imageVector = Icons.Filled.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            val btnSize = selectedFormat.sizeDisplay?.let { " • $it" } ?: ""
            Text(
                text = "Download ${selectedFormat.quality}$btnSize",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = 12.dp,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun StatMetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun resolveHomeThumbModel(thumbnailUrl: String?): Any {
    if (thumbnailUrl.isNullOrBlank()) return R.drawable.img_album_placeholder
    return if (thumbnailUrl.startsWith("/")) {
        java.io.File(thumbnailUrl)
    } else {
        thumbnailUrl
    }
}

@Composable
private fun CompactMediaCard(
    item: DownloadEntity,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.width(156.dp),
        cornerRadius = 20.dp,
        contentPadding = 10.dp,
        onClick = onClick
    ) {
        Box {
            AsyncImage(
                model = resolveHomeThumbModel(item.thumbnailUrl),
                contentDescription = item.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricCyan, AuroraViolet))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.qualityLabel} • ${FormatUtils.formatBytes(item.fileSizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactDownloadRow(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    LiquidGlassCard(
        cornerRadius = 18.dp,
        contentPadding = 12.dp,
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolveHomeThumbModel(item.thumbnailUrl),
                contentDescription = item.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.folderCategory} • ${FormatUtils.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}
