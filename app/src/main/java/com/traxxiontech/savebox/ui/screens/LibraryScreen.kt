package com.traxxiontech.savebox.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.data.local.PlaylistWithTracks
import com.traxxiontech.savebox.model.LibraryFilter
import com.traxxiontech.savebox.model.MediaType
import com.traxxiontech.savebox.model.SortOption
import com.traxxiontech.savebox.ui.components.AdBannerView
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.GlassCard
import com.traxxiontech.savebox.ui.components.ThumbnailDownloadProgressOverlay
import com.traxxiontech.savebox.ui.theme.EmeraldMint
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onPlayMedia: (DownloadEntity) -> Unit,
    onOpenFileManager: () -> Unit = {}
) {
    val items by viewModel.filteredLibraryItems.collectAsStateWithLifecycle()
    val queueDownloads by viewModel.queueDownloads.collectAsStateWithLifecycle()
    val allCompleted by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val favoriteEntries by viewModel.favoriteEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.librarySearchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val sort by viewModel.librarySort.collectAsStateWithLifecycle()
    val folderFilter by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isLibraryGridView.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningDeviceMedia.collectAsStateWithLifecycle()

    val displayItems = remember(items, queueDownloads, filter, searchQuery) {
        val activeItems = if (filter == LibraryFilter.ALL && searchQuery.isBlank()) {
            queueDownloads.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" || it.status == "PAUSED" }
        } else {
            emptyList()
        }
        activeItems + items
    }

    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var playlistPickerTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val photoVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedMedia(uris, mediaTypeHint = null)
        }
    }

    val songPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedMedia(uris, mediaTypeHint = MediaType.AUDIO)
        }
    }

    val folderScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onFolderGranted(uri, isSecondary = false)
        }
    }

    val photoCount = remember(allCompleted) { allCompleted.count { it.mediaType == MediaType.IMAGE.name } }
    val videoCount = remember(allCompleted) { allCompleted.count { it.mediaType == MediaType.VIDEO.name } }
    val songCount = remember(allCompleted) { allCompleted.count { it.mediaType == MediaType.AUDIO.name } }

    fun shareFile(entity: DownloadEntity) {
        val path = entity.localFilePath
        val uri = if (!path.isNullOrBlank() && File(path).exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        } else {
            entity.localUri?.let { Uri.parse(it) }
        } ?: run {
            viewModel.showMessage("Local file unavailable to share")
            return
        }
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
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Share ${entity.title}"))
        }
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
                        text = "Media Hub & Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$photoCount Photos • $videoCount Videos • $songCount Songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenFileManager,
                        modifier = Modifier.testTag("library_file_manager_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Open File Manager")
                    }

                    IconButton(
                        onClick = { viewModel.scanDeviceMedia(silent = false) },
                        enabled = !isScanning,
                        modifier = Modifier.testTag("library_scan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Scan Media & Cover Art")
                        }
                    }

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

            // Glass Device Media Scanner & Quick Picker Bar
            GlassCard(cornerRadius = 20.dp, contentPadding = 10.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoVideoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos & Videos", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { songPicker.launch(arrayOf("audio/*")) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Songs", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { folderScannerLauncher.launch(null) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Folder", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setLibrarySearchQuery(it) },
                placeholder = { Text("Search photos, videos, songs, artists...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Sections: All, Photos, Videos, Songs, Favorites, Recent, Playlists
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryFilter.entries.forEach { lf ->
                    val suffix = when (lf) {
                        LibraryFilter.IMAGES -> " ($photoCount)"
                        LibraryFilter.VIDEOS -> " ($videoCount)"
                        LibraryFilter.MUSIC -> " ($songCount)"
                        LibraryFilter.FAVORITES -> if (favoriteEntries.isNotEmpty()) " (${favoriteEntries.size})" else ""
                        else -> ""
                    }
                    FilterChip(
                        selected = filter == lf,
                        onClick = { viewModel.setLibraryFilter(lf) },
                        label = { Text("${lf.label}$suffix") }
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

            Spacer(modifier = Modifier.height(4.dp))
            AdBannerView()
            Spacer(modifier = Modifier.height(4.dp))

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
            } else if (displayItems.isEmpty()) {
                GlassCard(cornerRadius = 24.dp, contentPadding = 24.dp) {
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
                        Text("No SaveBox media in this section yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Tap Photos & Videos, Add Songs, or Scan Folder above to load your media with cover art.",
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
                    items(displayItems, key = { it.id }) { item ->
                        LibraryGridCard(
                            item = item,
                            onPlay = {
                                if (item.status == "COMPLETED") onPlayMedia(item)
                            },
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
                    items(displayItems, key = { it.id }) { item ->
                        LibraryListCard(
                            item = item,
                            onPlay = {
                                if (item.status == "COMPLETED") onPlayMedia(item)
                            },
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

private fun resolveCoverModel(item: DownloadEntity): Any {
    val thumb = item.thumbnailUrl
    if (!thumb.isNullOrBlank()) {
        if (thumb.startsWith("/")) {
            val f = File(thumb)
            if (f.exists()) return f
        } else {
            return thumb
        }
    }
    if (item.mediaType != MediaType.AUDIO.name) {
        val local = item.localFilePath?.let { File(it) }?.takeIf { it.exists() }
        if (local != null) return local
        if (!item.localUri.isNullOrBlank()) return item.localUri
    }
    return R.drawable.img_album_placeholder
}

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistWithTracks>,
    onPlayTrack: (DownloadEntity, List<DownloadEntity>) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    if (playlists.isEmpty()) {
        GlassCard(cornerRadius = 22.dp, contentPadding = 22.dp, onClick = onCreatePlaylist) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(40.dp))
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
                GlassCard(cornerRadius = 20.dp, contentPadding = 14.dp) {
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
    val mType = MediaType.fromString(item.mediaType)
    val coverModel = remember(item.id, item.thumbnailUrl, item.localFilePath, item.localUri) {
        resolveCoverModel(item)
    }

    GlassCard(cornerRadius = 20.dp, contentPadding = 10.dp, onClick = onPlay) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = coverModel,
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
                ringSize = 48.dp
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = when (mType) {
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

            if (item.durationMs > 0L) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mType == MediaType.IMAGE) Icons.Filled.Visibility else Icons.Filled.PlayArrow,
                    contentDescription = if (mType == MediaType.IMAGE) "View Photo" else "Play",
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
                    text = "${item.author ?: item.folderCategory} • ${FormatUtils.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val coverModel = remember(item.id, item.thumbnailUrl, item.localFilePath, item.localUri) {
        resolveCoverModel(item)
    }

    GlassCard(cornerRadius = 18.dp, contentPadding = 12.dp, onClick = onPlay) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = coverModel,
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
                    ringSize = 38.dp
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
                Text(
                    text = listOfNotNull(
                        item.author ?: item.sourceHost,
                        item.durationMs.takeIf { it > 0L }?.let { FormatUtils.formatDuration(it) },
                        FormatUtils.formatBytes(item.fileSizeBytes)
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
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
