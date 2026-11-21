package com.traxxiontech.savebox.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.ui.components.FormatUtils
import com.traxxiontech.savebox.ui.components.GlassCard
import com.traxxiontech.savebox.ui.theme.EmeraldMint
import java.io.File

@Composable
fun PhotoViewerScreen(
    initialPhoto: DownloadEntity,
    photos: List<DownloadEntity>,
    onToggleFavorite: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gallery = remember(photos, initialPhoto) {
        photos.ifEmpty { listOf(initialPhoto) }
    }
    var currentIndex by remember(initialPhoto.id) {
        mutableIntStateOf(gallery.indexOfFirst { it.id == initialPhoto.id }.coerceAtLeast(0))
    }
    val photo = gallery.getOrElse(currentIndex) { initialPhoto }

    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var showChrome by remember { mutableStateOf(true) }

    BackHandler { onBack() }

    val imageModel: Any = remember(photo.id, photo.localFilePath, photo.localUri, photo.thumbnailUrl) {
        val local = photo.localFilePath?.let { File(it) }?.takeIf { it.exists() }
        local ?: photo.localUri?.let { Uri.parse(it) } ?: photo.thumbnailUrl ?: photo.downloadUrl
    }

    fun sharePhoto() {
        val path = photo.localFilePath
        val uri = if (!path.isNullOrBlank() && File(path).exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        } else {
            photo.localUri?.let { Uri.parse(it) }
        } ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share ${photo.title}"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = photo.title,
            placeholder = painterResource(id = R.drawable.img_album_placeholder),
            error = painterResource(id = R.drawable.img_album_placeholder),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onTap = { showChrome = !showChrome },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        }
                    )
                }
                .pointerInput(photo.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = nextScale
                        offset = if (nextScale > 1.02f) {
                            Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            Offset.Zero
                        }
                    }
                }
        )

        if (showChrome) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(14.dp)
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    cornerRadius = 22.dp,
                    contentPadding = 10.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = photo.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentIndex + 1} of ${gallery.size} • ${FormatUtils.formatBytes(photo.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(photo) }) {
                            Icon(
                                imageVector = if (photo.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (photo.isFavorite) EmeraldMint else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { sharePhoto() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Photo")
                        }
                        IconButton(onClick = {
                            onDelete(photo)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Photo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (gallery.size > 1) {
                    GlassCard(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        cornerRadius = 24.dp,
                        contentPadding = 8.dp
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentIndex > 0) currentIndex-- },
                                enabled = currentIndex > 0,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Photo")
                            }
                            Text(
                                text = "${currentIndex + 1} / ${gallery.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (currentIndex < gallery.lastIndex) currentIndex++ },
                                enabled = currentIndex < gallery.lastIndex,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Photo")
                            }
                        }
                    }
                }
            }
        }
    }
}
