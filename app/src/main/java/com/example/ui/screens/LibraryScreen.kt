package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.data.local.PlaylistWithTracks
import com.example.model.LibraryFilter
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.SortOption
import com.example.ui.components.FormatUtils
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onPlayMedia: (DownloadEntity) -> Unit
) {
    val items by viewModel.filteredLibraryItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.librarySearchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val sort by viewModel.librarySort.collectAsStateWithLifecycle()
    val folderFilter by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isLibraryGridView.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var playlistPickerTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    fun shareFile(entity: DownloadEntity) {
        val path = entity.localFilePath ?: return
        val file = File(path)
        if (!file.exists()) {
            viewModel.showMessage("Local file unavailable to share")
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when (MediaType.fromString(entity.mediaType)) {
            MediaType.AUDIO -> "audio/*"
            MediaType.IMAGE -> "image/*"
            MediaType.VIDEO -> "video/*"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${entity.title}"))
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SaveBox Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${items.size} offline saved files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("library_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort Library"
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (option == sort) "✓ ${option.label}" else option.label
                                        )
                                    },
                                    onClick = {
                                        viewModel.setLibrarySort(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleLibraryViewMode() },
                        modifier = Modifier.testTag("library_view_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle Grid or List"
                        )
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setLibrarySearchQuery(it) },
                placeholder = { Text("Search SaveBox files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Type / Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryFilter.entries.forEach { lf ->
                    FilterChip(
                        selected = filter == lf,
                        onClick = { viewModel.setLibraryFilter(lf) },
                        label = { Text(lf.label) }
                    )
                }
            }

            // SaveBox Folder Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = folderFilter == null,
                    onClick = { viewModel.setSelectedFolderFilter(null) },
                    label = { Text("All Folders") },
                    leadingIcon = {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                )
                viewModel.getStorageBreakdownFolders().forEach { folder ->
                    FilterChip(
                        selected = folderFilter == folder,
                        onClick = {
                            viewModel.setSelectedFolderFilter(if (folderFilter == folder) null else folder)
                        },
                        label = { Text(folder) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filter == LibraryFilter.PLAYLISTS) {
                PlaylistsSection(
                    playlists = playlists,
                    onPlayTrack = { track, list ->
                        if (track.mediaType == MediaType.AUDIO.name) {
                            viewModel.playAudioTrack(track, list)
                        } else {
                            onPlayMedia(track)
                        }
                    },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                    onCreatePlaylist = { showCreatePlaylistDialog = true }
                )
            } else if (items.isEmpty()) {
                LiquidGlassCard(cornerRadius = 24.dp, contentPadding = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No SaveBox media matches this filter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Downloaded videos, music, and saved WhatsApp statuses appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryGridCard(
                            item = item,
                            onPlay = { onPlayMedia(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onRename = { renameTarget = item },
                            onAddToPlaylist = { playlistPickerTarget = item },
                            onAddToQueue = { viewModel.addToAudioQueue(item) },
                            onShare = { shareFile(item) },
                            onDelete = { viewModel.deleteDownload(item) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryListCard(
                            item = item,
                            onPlay = { onPlayMedia(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onRename = { renameTarget = item },
                            onAddToPlaylist = { playlistPickerTarget = item },
                            onAddToQueue = { viewModel.addToAudioQueue(item) },
                            onShare = { shareFile(item) },
                            onDelete = { viewModel.deleteDownload(item) }
                        )
                    }
                }
            }
        }

        if (filter == LibraryFilter.PLAYLISTS) {
            FloatingActionButton(
                onClick = { showCreatePlaylistDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 110.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Playlist")
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameDownload(target, newName)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    playlistPickerTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistPickerTarget = null },
            title = { Text("Add to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        Text("No playlists yet. Create one first!")
                    } else {
                        playlists.forEach { pl ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addTrackToPlaylist(pl.playlist.id, target.id)
                                        playlistPickerTarget = null
                                    }
                            ) {
                                Text(
                                    text = "${pl.playlist.name} (${pl.tracks.size} items)",
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistPickerTarget = null
                    showCreatePlaylistDialog = true
                }) { Text("New Playlist") }
            },
            dismissButton = {
                TextButton(onClick = { playlistPickerTarget = null }) { Text("Close") }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createPlaylist(name, desc)
                    showCreatePlaylistDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistWithTracks>,
    onPlayTrack: (DownloadEntity, List<DownloadEntity>) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    if (playlists.isEmpty()) {
        LiquidGlassCard(cornerRadius = 22.dp, contentPadding = 22.dp, onClick = onCreatePlaylist) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap + to create your first offline SaveBox playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.playlist.id }) { item ->
                LiquidGlassCard(cornerRadius = 20.dp, contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.tracks.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.tracks.isNotEmpty()) {
                            IconButton(onClick = { onPlayTrack(item.tracks.first(), item.tracks) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play Playlist")
                            }
                        }
                        IconButton(onClick = { onDeletePlaylist(item.playlist.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (item.tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        item.tracks.take(4).forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayTrack(track, item.tracks) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGridCard(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    LiquidGlassCard(cornerRadius = 20.dp, contentPadding = 10.dp, onClick = onPlay) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl ?: item.localFilePath ?: R.drawable.img_album_placeholder,
                contentDescription = item.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = when (MediaType.fromString(item.mediaType)) {
                        MediaType.AUDIO -> Icons.Filled.AudioFile
                        MediaType.IMAGE -> Icons.Filled.Image
                        MediaType.VIDEO -> Icons.Filled.VideoLibrary
                    },
                    contentDescription = item.mediaType,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(14.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.folderCategory} • ${FormatUtils.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                }
                MediaItemMenu(
                    expanded = menuOpen,
                    isFavorite = item.isFavorite,
                    onDismiss = { menuOpen = false },
                    onToggleFavorite = onToggleFavorite,
                    onRename = onRename,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddToQueue = onAddToQueue,
                    onShare = onShare,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun LibraryListCard(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    LiquidGlassCard(cornerRadius = 18.dp, contentPadding = 12.dp, onClick = onPlay) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl ?: item.localFilePath ?: R.drawable.img_album_placeholder,
                contentDescription = item.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
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
                Text(
                    text = "${MediaPlatform.fromString(item.platform).displayName} • ${item.qualityLabel} • ${FormatUtils.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                MediaItemMenu(
                    expanded = menuOpen,
                    isFavorite = item.isFavorite,
                    onDismiss = { menuOpen = false },
                    onToggleFavorite = onToggleFavorite,
                    onRename = onRename,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddToQueue = onAddToQueue,
                    onShare = onShare,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun MediaItemMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove Favorite" else "Add to Favorites") },
            leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            onClick = { onDismiss(); onToggleFavorite() }
        )
        DropdownMenuItem(
            text = { Text("Add to Audio Queue") },
            leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
            onClick = { onDismiss(); onAddToQueue() }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
            onClick = { onDismiss(); onAddToPlaylist() }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}
