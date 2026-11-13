package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.WhatsAppStatusItem
import com.example.ui.components.FormatUtils
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.EmeraldMint
import com.example.ui.viewmodel.MainViewModel

@Composable
fun StatusSaverScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statusItems by viewModel.statusItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.statusSearchQuery.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningStatuses.collectAsStateWithLifecycle()

    var previewItem by remember { mutableStateOf<WhatsAppStatusItem?>(null) }

    val whatsAppFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.onWhatsAppFolderGranted(uri, isBusiness = false)
        }
    }

    val businessFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.onWhatsAppFolderGranted(uri, isBusiness = true)
        }
    }

    val filteredStatuses = remember(statusItems, searchQuery) {
        if (searchQuery.isBlank()) statusItems
        else statusItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.sourceLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    fun shareStatus(item: WhatsAppStatusItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
        }.onFailure {
            viewModel.showMessage("Unable to share this status")
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status Saver",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "WhatsApp & WhatsApp Business (.Statuses)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshWhatsAppStatuses() },
                    modifier = Modifier.testTag("status_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Statuses")
                }
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Explicit permission / folder selection card
            LiquidGlassCard(cornerRadius = 22.dp, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy-First Scoped Folder Access",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SaveBox never accesses device folders without your permission. Grant access to Android/media/com.whatsapp/WhatsApp/Media/.Statuses (or WhatsApp Business).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { whatsAppFolderPicker.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_whatsapp_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (settings.whatsAppTreeUri != null) "WA Linked" else "WhatsApp")
                    }
                    OutlinedButton(
                        onClick = { businessFolderPicker.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_wa_business_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (settings.whatsAppBusinessTreeUri != null) "WA Biz Linked" else "WA Business")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setStatusSearchQuery(it) },
                placeholder = { Text("Search statuses by filename or source...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredStatuses.isEmpty()) {
                LiquidGlassCard(cornerRadius = 22.dp, contentPadding = 22.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (settings.whatsAppTreeUri == null && settings.whatsAppBusinessTreeUri == null) {
                                "Select your WhatsApp .Statuses folder above to view statuses"
                            } else {
                                "No statuses found in the selected folder"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "View a status in WhatsApp first, then tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredStatuses, key = { it.uriString }) { item ->
                        LiquidGlassCard(
                            cornerRadius = 18.dp,
                            contentPadding = 8.dp,
                            onClick = { previewItem = item }
                        ) {
                            Box {
                                AsyncImage(
                                    model = Uri.parse(item.uriString),
                                    contentDescription = item.name,
                                    placeholder = painterResource(id = R.drawable.img_album_placeholder),
                                    error = painterResource(id = R.drawable.img_album_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                if (item.isVideo) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircle,
                                        contentDescription = "Video Status",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(38.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = item.sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = FormatUtils.formatBytes(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row {
                                    IconButton(
                                        onClick = { shareStatus(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share Status", modifier = Modifier.size(18.dp))
                                    }
                                    if (item.isAlreadySaved) {
                                        IconButton(
                                            onClick = { viewModel.deleteSavedStatusCopy(item) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Saved (Tap to delete saved copy)",
                                                tint = EmeraldMint,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { viewModel.saveWhatsAppStatus(item) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = "Save Status",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text(item.sourceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = Uri.parse(item.uriString),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${item.name} • ${FormatUtils.formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                if (item.isAlreadySaved) {
                    TextButton(onClick = {
                        viewModel.deleteSavedStatusCopy(item)
                        previewItem = null
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Saved Copy")
                    }
                } else {
                    TextButton(onClick = {
                        viewModel.saveWhatsAppStatus(item)
                        previewItem = null
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Gallery")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { shareStatus(item) }) {
                    Text("Share")
                }
            }
        )
    }
}
