package com.traxxiontech.savebox.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.model.DocumentFileEntry
import com.traxxiontech.savebox.model.MediaType
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.GlassCard
import com.traxxiontech.savebox.ui.components.MediaSourceBadgeChip
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun FileManagerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayMedia: (DownloadEntity) -> Unit
) {
    val context = LocalContext.current
    val files by viewModel.managedDocumentFiles.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedFileCategory.collectAsStateWithLifecycle()
    val activeSafTreeUri by viewModel.activeSafTreeUri.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingFiles.collectAsStateWithLifecycle()
    val allDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()

    var filterQuery by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentFileEntry?>(null) }
    var moveTarget by remember { mutableStateOf<DocumentFileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentFileEntry?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshManagedFiles()
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.onSafTreeSelectedForFileManager(uri)
        }
    }

    val categories = remember(activeSafTreeUri) {
        val base = listOf("All") + StorageManager.ALL_SAVEBOX_FOLDERS
        if (activeSafTreeUri != null) base + "SAF Folder" else base
    }

    val visibleFiles = remember(files, filterQuery) {
        if (filterQuery.isBlank()) files
        else files.filter {
            it.name.contains(filterQuery, ignoreCase = true) ||
                it.folderLabel.contains(filterQuery, ignoreCase = true)
        }
    }

    fun openEntry(entry: DocumentFileEntry) {
        if (entry.isDirectory) {
            if (selectedCategory != "SAF Folder") {
                viewModel.selectFileCategory(entry.name)
            }
            return
        }
        val mediaType = entry.mediaType ?: return
        val matched = allDownloads.firstOrNull {
            (entry.localFilePath != null && it.localFilePath == entry.localFilePath) ||
                it.localUri == entry.uriString
        }
        val target = matched ?: DownloadEntity(
            id = -entry.uriString.hashCode().toLong().let { if (it == 0L) 1L else kotlin.math.abs(it) },
            title = entry.name.substringBeforeLast('.'),
            fileName = entry.name,
            sourceUrl = entry.uriString,
            downloadUrl = entry.uriString,
            sourceHost = entry.folderLabel,
            mediaType = mediaType.name,
            qualityLabel = "Local File",
            fileSizeBytes = entry.sizeBytes,
            localFilePath = entry.localFilePath,
            localUri = entry.uriString,
            folderCategory = entry.folderLabel,
            status = "COMPLETED"
        )
        onPlayMedia(target)
    }

    fun shareEntry(entry: DocumentFileEntry) {
        val shareUri = entry.localFilePath?.let { path ->
            val f = File(path)
            if (f.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f) else null
        } ?: Uri.parse(entry.uriString)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entry.mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
        }.onFailure {
            viewModel.showMessage("Unable to share file")
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("file_manager_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "File Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Organize, rename & manage via DocumentFile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.testTag("file_manager_new_folder_button")
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create Folder")
                    }
                    IconButton(
                        onClick = { viewModel.refreshManagedFiles() },
                        modifier = Modifier.testTag("file_manager_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Files")
                    }
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Action & SAF Toolbar
            GlassCard(cornerRadius = 20.dp, contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { safFolderLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("file_manager_pick_saf_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open SAF Folder", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Folder", maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Search files by name or folder...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_manager_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectFileCategory(cat) },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (visibleFiles.isEmpty()) {
                GlassCard(cornerRadius = 22.dp, contentPadding = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No files in $selectedCategory",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Downloaded media or linked SAF DocumentFile items will appear here for organizing, renaming, or deleting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleFiles, key = { it.uriString }) { entry ->
                        DocumentFileRowCard(
                            entry = entry,
                            onOpen = { openEntry(entry) },
                            onRename = { renameTarget = entry },
                            onMove = { moveTarget = entry },
                            onShare = { shareEntry(entry) },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createDocumentFolder(folderName)
                        showCreateFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New file name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameDocumentEntry(target, newName)
                        renameTarget = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move to Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose destination folder for \"${target.name}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StorageManager.ALL_SAVEBOX_FOLDERS.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveDocumentEntry(target, folder)
                                    moveTarget = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(folder, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete File?") },
            text = { Text("Permanently remove \"${target.name}\" from storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocumentEntry(target)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DocumentFileRowCard(
    entry: DocumentFileEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(cornerRadius = 18.dp, contentPadding = 12.dp, onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!entry.isDirectory && (entry.mediaType == MediaType.IMAGE || entry.mediaType == MediaType.VIDEO)) {
                AsyncImage(
                    model = entry.localFilePath?.let { File(it) } ?: Uri.parse(entry.uriString),
                    contentDescription = entry.name,
                    placeholder = painterResource(id = R.drawable.img_album_placeholder),
                    error = painterResource(id = R.drawable.img_album_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                val icon = when {
                    entry.isDirectory -> Icons.Filled.Folder
                    entry.mediaType == MediaType.AUDIO -> Icons.Filled.AudioFile
                    entry.mediaType == MediaType.VIDEO -> Icons.Filled.VideoFile
                    entry.mediaType == MediaType.IMAGE -> Icons.Filled.Image
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = entry.name, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaSourceBadgeChip(label = entry.folderLabel)
                    if (!entry.isDirectory) {
                        Text(
                            text = FormatUtils.formatBytes(entry.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "File Actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (entry.canWrite) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                    }
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                    }
                    if (entry.canWrite) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
