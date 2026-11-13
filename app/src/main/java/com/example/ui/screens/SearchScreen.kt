package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.model.SearchPlatformScope
import com.example.model.SearchResultItem
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PlatformBadgeChip
import com.example.ui.components.ShimmerSkeletonCard
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onAnalyzeResultUrl: (String) -> Unit,
    onPlayLocal: (DownloadEntity) -> Unit
) {
    val query by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val scope by viewModel.searchScope.collectAsStateWithLifecycle()
    val onlineState by viewModel.onlineSearchState.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.setGlobalSearchQuery(spoken)
                viewModel.performOnlineSearch()
            }
        }
    }

    val localMatches = remember(query, completedDownloads) {
        if (query.isBlank()) completedDownloads.take(6)
        else completedDownloads.filter {
            it.title.contains(query, ignoreCase = true) ||
                (it.author?.contains(query, ignoreCase = true) == true) ||
                it.platform.contains(query, ignoreCase = true)
        }
    }

    val suggestions = remember(query, recentSearches) {
        val base = listOf("Afrobeats 2026", "Lo-Fi Chill Mix", "4K Nature Drone", "Acoustic Sessions")
        val historyTitles = recentSearches.map { it.query }
        (historyTitles + base).distinct()
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .take(6)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Global Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setGlobalSearchQuery(it) },
                    placeholder = { Text("Search YouTube, Spotify, SoundCloud or Vault...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search media")
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure { viewModel.showMessage("Voice search is unavailable on this device") }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.performOnlineSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("global_search_input")
                )

                Button(
                    onClick = { viewModel.performOnlineSearch() },
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("global_search_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Go")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchPlatformScope.entries.forEach { item ->
                    FilterChip(
                        selected = scope == item,
                        onClick = { viewModel.setSearchScope(item) },
                        label = { Text(item.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Instant suggestions & recent searches
                if (suggestions.isNotEmpty() && onlineState is SearchUiState.Idle) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Suggestions & History", style = MaterialTheme.typography.titleSmall)
                            if (recentSearches.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearSearchHistory() }) { Text("Clear") }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { text ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.setGlobalSearchQuery(text)
                                        viewModel.performOnlineSearch()
                                    },
                                    label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                // Online results
                if (scope != SearchPlatformScope.ALL_LOCAL) {
                    when (val state = onlineState) {
                        is SearchUiState.Loading -> {
                            items(4) { ShimmerSkeletonCard(height = 90.dp) }
                        }
                        is SearchUiState.Error -> {
                            item {
                                LiquidGlassCard(cornerRadius = 18.dp, contentPadding = 14.dp) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is SearchUiState.Results -> {
                            item {
                                Text(
                                    text = "${scope.label} Results (${state.items.size})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (state.items.isEmpty()) {
                                item {
                                    LiquidGlassCard(cornerRadius = 18.dp, contentPadding = 16.dp) {
                                        Text("No results found for \"$query\".")
                                    }
                                }
                            } else {
                                items(state.items, key = { it.id }) { result ->
                                    OnlineSearchResultCard(
                                        item = result,
                                        onAnalyze = { onAnalyzeResultUrl(result.url) }
                                    )
                                }
                            }
                        }
                        SearchUiState.Idle -> Unit
                    }
                }

                // Local SaveBox Vault matches
                if (localMatches.isNotEmpty()) {
                    item {
                        Text(
                            text = "In Your SaveBox Vault (${localMatches.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    items(localMatches, key = { "local_${it.id}" }) { local ->
                        LiquidGlassCard(
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp,
                            onClick = { onPlayLocal(local) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = local.thumbnailUrl ?: R.drawable.img_album_placeholder,
                                    contentDescription = local.title,
                                    placeholder = painterResource(id = R.drawable.img_album_placeholder),
                                    error = painterResource(id = R.drawable.img_album_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(local.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${local.folderCategory} • ${local.qualityLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSearchResultCard(
    item: SearchResultItem,
    onAnalyze: () -> Unit
) {
    LiquidGlassCard(cornerRadius = 18.dp, contentPadding = 12.dp, onClick = onAnalyze) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl ?: R.drawable.img_album_placeholder,
                contentDescription = item.title,
                placeholder = painterResource(id = R.drawable.img_album_placeholder),
                error = painterResource(id = R.drawable.img_album_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformBadgeChip(platform = item.platform)
                    val meta = listOfNotNull(item.subtitle, item.durationText).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(onClick = onAnalyze) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Analyze & Download",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
