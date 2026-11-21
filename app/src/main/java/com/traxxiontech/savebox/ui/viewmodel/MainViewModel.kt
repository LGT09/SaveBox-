package com.traxxiontech.savebox.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.traxxiontech.savebox.data.download.DownloadRepository
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.data.local.DownloadQueue
import com.traxxiontech.savebox.data.local.MediaItem
import com.traxxiontech.savebox.data.local.MediaMetadataEntity
import com.traxxiontech.savebox.data.local.PlaylistWithTracks
import com.traxxiontech.savebox.data.local.SearchHistoryEntity
import com.traxxiontech.savebox.data.local.SettingsDataStore
import com.traxxiontech.savebox.data.local.UserSettings
import com.traxxiontech.savebox.data.remote.ApiResult
import com.traxxiontech.savebox.data.remote.BrowserPageSnapshot
import com.traxxiontech.savebox.data.remote.InAppBrowserEngine
import com.traxxiontech.savebox.data.remote.UrlMediaBot
import com.traxxiontech.savebox.data.remote.YouTubeSearchEngine
import com.traxxiontech.savebox.data.status.LocalMediaScanner
import com.traxxiontech.savebox.data.status.StatusSaverRepository
import com.traxxiontech.savebox.model.AnalyzedMedia
import com.traxxiontech.savebox.model.AppThemeMode
import com.traxxiontech.savebox.model.AppThemePalette
import com.traxxiontech.savebox.model.LibraryFilter
import com.traxxiontech.savebox.model.LocalFolderMediaItem
import com.traxxiontech.savebox.model.MediaFormatOption
import com.traxxiontech.savebox.model.MediaType
import com.traxxiontech.savebox.model.SearchResultItem
import com.traxxiontech.savebox.model.SearchScope
import com.traxxiontech.savebox.model.SortOption
import com.traxxiontech.savebox.model.StorageFolderBreakdown
import com.traxxiontech.savebox.model.VisualizerStyle
import com.traxxiontech.savebox.player.AudioEffectsManager
import com.traxxiontech.savebox.player.AudioPlayerState
import com.traxxiontech.savebox.player.EqualizerState
import com.traxxiontech.savebox.player.MediaControllerManager
import com.traxxiontech.savebox.player.SleepTimerOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AnalyzeUiState {
    data object Idle : AnalyzeUiState()
    data class Loading(val targetUrl: String) : AnalyzeUiState()
    data class Success(val media: AnalyzedMedia) : AnalyzeUiState()
    data class Error(val message: String) : AnalyzeUiState()
}

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Results(val items: List<SearchResultItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class BrowserReaderUiState {
    data object Idle : BrowserReaderUiState()
    data class Loading(val targetUrl: String) : BrowserReaderUiState()
    data class Loaded(val snapshot: BrowserPageSnapshot) : BrowserReaderUiState()
    data class Error(val targetUrl: String, val message: String) : BrowserReaderUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val urlMediaBot: UrlMediaBot,
    private val youTubeSearchEngine: YouTubeSearchEngine,
    private val inAppBrowserEngine: InAppBrowserEngine,
    private val downloadRepo: DownloadRepository,
    private val storageManager: StorageManager,
    private val statusRepo: StatusSaverRepository,
    private val localMediaScanner: LocalMediaScanner,
    private val settingsStore: SettingsDataStore,
    val mediaControllerManager: MediaControllerManager
) : AndroidViewModel(application) {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMediaItems: StateFlow<List<MediaItem>> = downloadRepo.allMediaItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloadQueue: StateFlow<List<DownloadQueue>> = downloadRepo.activeDownloadQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaMetadataList: StateFlow<List<MediaMetadataEntity>> = downloadRepo.allMediaMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.queueDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<DownloadEntity>> = downloadRepo.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = downloadRepo.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithTracks>> = downloadRepo.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioPlayerState: StateFlow<AudioPlayerState> = mediaControllerManager.state
    val equalizerState: StateFlow<EqualizerState> = AudioEffectsManager.state

    // Home URL input & Bot fetch state
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    private val _analyzeState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val analyzeState: StateFlow<AnalyzeUiState> = _analyzeState.asStateFlow()

    // In-App Browser state
    private val _browserCurrentUrl = MutableStateFlow("https://www.google.com")
    val browserCurrentUrl: StateFlow<String> = _browserCurrentUrl.asStateFlow()

    private val _browserPendingUrl = MutableStateFlow<String?>(null)
    val browserPendingUrl: StateFlow<String?> = _browserPendingUrl.asStateFlow()

    private val _browserReaderState = MutableStateFlow<BrowserReaderUiState>(BrowserReaderUiState.Idle)
    val browserReaderState: StateFlow<BrowserReaderUiState> = _browserReaderState.asStateFlow()

    private val browserBackStack = ArrayDeque<String>()
    private val browserForwardStack = ArrayDeque<String>()

    private val _canBrowserReaderBack = MutableStateFlow(false)
    val canBrowserReaderBack: StateFlow<Boolean> = _canBrowserReaderBack.asStateFlow()

    private val _canBrowserReaderForward = MutableStateFlow(false)
    val canBrowserReaderForward: StateFlow<Boolean> = _canBrowserReaderForward.asStateFlow()

    // Library filter/sort/view state
    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    private val _librarySort = MutableStateFlow(SortOption.DATE_DESC)
    val librarySort: StateFlow<SortOption> = _librarySort.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    private val _isLibraryGridView = MutableStateFlow(true)
    val isLibraryGridView: StateFlow<Boolean> = _isLibraryGridView.asStateFlow()

    private val _isScanningDeviceMedia = MutableStateFlow(false)
    val isScanningDeviceMedia: StateFlow<Boolean> = _isScanningDeviceMedia.asStateFlow()

    val filteredLibraryItems: StateFlow<List<DownloadEntity>> = combine(
        completedDownloads,
        _librarySearchQuery,
        _libraryFilter,
        _librarySort,
        _selectedFolderFilter
    ) { items, query, filter, sort, folder ->
        items
            .filter { item ->
                val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    (item.author?.contains(query, ignoreCase = true) == true) ||
                    item.sourceHost.contains(query, ignoreCase = true)
                val matchesFilter = when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.VIDEOS -> item.mediaType == MediaType.VIDEO.name
                    LibraryFilter.MUSIC -> item.mediaType == MediaType.AUDIO.name
                    LibraryFilter.IMAGES -> item.mediaType == MediaType.IMAGE.name
                    LibraryFilter.FAVORITES -> item.isFavorite
                    LibraryFilter.RECENT -> System.currentTimeMillis() - item.createdAt <= 7L * 24 * 3600 * 1000
                    LibraryFilter.PLAYLISTS -> true
                }
                val matchesFolder = folder == null || item.folderCategory.equals(folder, ignoreCase = true)
                matchesQuery && matchesFilter && matchesFolder
            }
            .let { list ->
                when (sort) {
                    SortOption.DATE_DESC -> list.sortedByDescending { it.createdAt }
                    SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
                    SortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
                    SortOption.TYPE -> list.sortedBy { it.mediaType }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // YouTube & Vault Search state
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _searchScope = MutableStateFlow(SearchScope.YOUTUBE)
    val searchScope: StateFlow<SearchScope> = _searchScope.asStateFlow()

    private val _onlineSearchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val onlineSearchState: StateFlow<SearchUiState> = _onlineSearchState.asStateFlow()

    private val _youtubeResolvingId = MutableStateFlow<String?>(null)
    val youtubeResolvingId: StateFlow<String?> = _youtubeResolvingId.asStateFlow()

    private val _youtubeSheetMedia = MutableStateFlow<AnalyzedMedia?>(null)
    val youtubeSheetMedia: StateFlow<AnalyzedMedia?> = _youtubeSheetMedia.asStateFlow()

    private val _transientPlayTarget = MutableStateFlow<DownloadEntity?>(null)
    val transientPlayTarget: StateFlow<DownloadEntity?> = _transientPlayTarget.asStateFlow()

    fun setTransientPlayTarget(entity: DownloadEntity?) {
        _transientPlayTarget.value = entity
    }

    // Local Folder Media Importer state
    private val _folderMediaItems = MutableStateFlow<List<LocalFolderMediaItem>>(emptyList())
    val folderMediaItems: StateFlow<List<LocalFolderMediaItem>> = _folderMediaItems.asStateFlow()

    private val _folderSearchQuery = MutableStateFlow("")
    val folderSearchQuery: StateFlow<String> = _folderSearchQuery.asStateFlow()

    private val _isScanningFolders = MutableStateFlow(false)
    val isScanningFolders: StateFlow<Boolean> = _isScanningFolders.asStateFlow()

    // Storage & Cache metrics
    private val _storageBreakdown = MutableStateFlow<List<StorageFolderBreakdown>>(emptyList())
    val storageBreakdown: StateFlow<List<StorageFolderBreakdown>> = _storageBreakdown.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        mediaControllerManager.connect()
        refreshStorageMetrics()
        scanDeviceMedia(silent = true)
        viewModelScope.launch {
            settings
                .distinctUntilChangedBy { it.customFolderTreeUri to it.secondaryFolderTreeUri }
                .collect { userSettings ->
                    if (userSettings.customFolderTreeUri != null || userSettings.secondaryFolderTreeUri != null) {
                        refreshFolderMedia()
                        scanDeviceMedia(silent = true)
                    }
                }
        }
    }

    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            settingsStore.acceptPrivacyPolicy()
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun updateBrowserCurrentUrl(url: String) {
        _browserCurrentUrl.value = url
    }

    fun requestBrowserNavigate(urlOrQuery: String) {
        _browserPendingUrl.value = resolveBrowserTarget(urlOrQuery)
    }

    fun consumeBrowserPendingUrl() {
        _browserPendingUrl.value = null
    }

    fun resolveBrowserTarget(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return trimmed
        }
        val looksLikeDomain = !trimmed.contains(" ") && trimmed.contains(".") && trimmed.length >= 4
        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
    }

    fun navigateBrowserReader(rawInput: String, recordHistory: Boolean = true) {
        val target = resolveBrowserTarget(rawInput)
        val previous = _browserCurrentUrl.value
        if (recordHistory && previous.isNotBlank() && !previous.equals(target, ignoreCase = true)) {
            browserBackStack.addLast(previous)
            if (browserBackStack.size > 30) browserBackStack.removeFirst()
            browserForwardStack.clear()
        }
        _canBrowserReaderBack.value = browserBackStack.isNotEmpty()
        _canBrowserReaderForward.value = browserForwardStack.isNotEmpty()
        _browserCurrentUrl.value = target
        _browserReaderState.value = BrowserReaderUiState.Loading(target)

        viewModelScope.launch {
            when (val res = inAppBrowserEngine.navigate(target)) {
                is ApiResult.Success -> {
                    _browserCurrentUrl.value = res.data.url
                    _browserReaderState.value = BrowserReaderUiState.Loaded(res.data)
                }
                is ApiResult.Error -> {
                    _browserReaderState.value = BrowserReaderUiState.Error(target, res.message)
                }
            }
        }
    }

    fun browserReaderBack() {
        val prev = browserBackStack.removeLastOrNull() ?: return
        browserForwardStack.addLast(_browserCurrentUrl.value)
        _canBrowserReaderBack.value = browserBackStack.isNotEmpty()
        _canBrowserReaderForward.value = browserForwardStack.isNotEmpty()
        navigateBrowserReader(prev, recordHistory = false)
    }

    fun browserReaderForward() {
        val next = browserForwardStack.removeLastOrNull() ?: return
        browserBackStack.addLast(_browserCurrentUrl.value)
        _canBrowserReaderBack.value = browserBackStack.isNotEmpty()
        _canBrowserReaderForward.value = browserForwardStack.isNotEmpty()
        navigateBrowserReader(next, recordHistory = false)
    }

    fun reloadBrowserReader() {
        navigateBrowserReader(_browserCurrentUrl.value, recordHistory = false)
    }

    fun updateUrlInput(value: String) {
        _urlInput.value = value
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _analyzeState.value = AnalyzeUiState.Idle
    }

    fun setClipboardSuggestion(url: String?) {
        _clipboardSuggestion.value = url
    }

    fun pasteAndFetch(url: String) {
        updateUrlInput(url)
        fetchMediaFromCurrentUrl()
    }

    fun fetchMediaFromCurrentUrl() {
        val raw = _urlInput.value.trim()
        if (raw.isEmpty()) {
            _analyzeState.value = AnalyzeUiState.Error("Paste or enter a link first.")
            return
        }
        _analyzeState.value = AnalyzeUiState.Loading(raw)

        viewModelScope.launch {
            downloadRepo.recordSearch(raw, SearchScope.SAVED_VAULT, isUrl = true)
            when (val result = urlMediaBot.fetchMediaFromUrl(raw)) {
                is ApiResult.Success -> {
                    downloadRepo.saveAnalyzedMetadata(result.data)
                    _analyzeState.value = AnalyzeUiState.Success(result.data)
                }
                is ApiResult.Error -> {
                    _analyzeState.value = AnalyzeUiState.Error(result.message)
                }
            }
        }
    }

    fun dismissFetchedMedia() {
        _analyzeState.value = AnalyzeUiState.Idle
    }

    fun startDownload(
        analyzed: AnalyzedMedia,
        format: MediaFormatOption,
        customTitle: String,
        customFolder: String?,
        alsoSaveCoverArt: Boolean = (format.mediaType == MediaType.AUDIO && !analyzed.thumbnailUrl.isNullOrBlank())
    ) {
        viewModelScope.launch {
            downloadRepo.enqueueDownload(
                analyzed = analyzed,
                format = format,
                customTitle = customTitle,
                customFolder = customFolder,
                alsoSaveCoverArt = alsoSaveCoverArt
            )
            _analyzeState.value = AnalyzeUiState.Idle
            _youtubeSheetMedia.value = null
            _snackbarMessage.value = "Queued \"${customTitle.ifBlank { analyzed.title }}\" (${format.label})"
            refreshStorageMetrics()
        }
    }

    // YouTube Search inline Play, Download Song + Cover Art, & Quality Sheet
    fun playYouTubeSearchResult(
        item: SearchResultItem,
        onStreamAudioReady: (DownloadEntity) -> Unit,
        onStreamVideoReady: (DownloadEntity) -> Unit
    ) {
        viewModelScope.launch {
            _youtubeResolvingId.value = item.id
            when (val res = urlMediaBot.fetchMediaFromUrl(item.url)) {
                is ApiResult.Success -> {
                    val analyzed = res.data
                    val videoFmt = analyzed.formats.firstOrNull { it.mediaType == MediaType.VIDEO }
                    val audioFmt = analyzed.formats.firstOrNull { it.mediaType == MediaType.AUDIO }
                    val chosen = videoFmt ?: audioFmt
                    if (chosen == null) {
                        _snackbarMessage.value = "No playable stream found for \"${item.title}\""
                    } else {
                        val transient = DownloadEntity(
                            id = -item.id.hashCode().toLong().let { if (it == 0L) 1L else kotlin.math.abs(it) },
                            title = analyzed.title.ifBlank { item.title },
                            fileName = "${UrlMediaBot.sanitizeFileName(item.title)}.${chosen.extension}",
                            sourceUrl = item.url,
                            downloadUrl = chosen.downloadUrl,
                            sourceHost = "YouTube Stream",
                            mediaType = chosen.mediaType.name,
                            qualityLabel = chosen.label,
                            author = analyzed.author ?: item.channel,
                            thumbnailUrl = analyzed.thumbnailUrl ?: item.thumbnailUrl,
                            durationMs = analyzed.durationMs,
                            localUri = chosen.downloadUrl,
                            status = "COMPLETED"
                        )
                        if (chosen.mediaType == MediaType.AUDIO) {
                            onStreamAudioReady(transient)
                        } else {
                            onStreamVideoReady(transient)
                        }
                    }
                }
                is ApiResult.Error -> {
                    _snackbarMessage.value = res.message
                }
            }
            _youtubeResolvingId.value = null
        }
    }

    fun downloadYouTubeSearchResult(item: SearchResultItem, preferAudioWithCover: Boolean) {
        viewModelScope.launch {
            _youtubeResolvingId.value = item.id
            when (val res = urlMediaBot.fetchMediaFromUrl(item.url)) {
                is ApiResult.Success -> {
                    val analyzed = res.data
                    val chosen = if (preferAudioWithCover) {
                        analyzed.formats.firstOrNull { it.mediaType == MediaType.AUDIO }
                            ?: analyzed.formats.firstOrNull { it.mediaType == MediaType.VIDEO }
                    } else {
                        analyzed.formats.firstOrNull { it.mediaType == MediaType.VIDEO }
                            ?: analyzed.formats.firstOrNull { it.mediaType == MediaType.AUDIO }
                    }
                    if (chosen == null) {
                        _snackbarMessage.value = "Could not extract audio/video stream from YouTube"
                    } else {
                        downloadRepo.enqueueDownload(
                            analyzed = analyzed,
                            format = chosen,
                            customTitle = item.title,
                            customFolder = null,
                            alsoSaveCoverArt = true
                        )
                        _snackbarMessage.value = "Downloading \"${item.title}\" + Cover Art (${chosen.label})"
                        refreshStorageMetrics()
                    }
                }
                is ApiResult.Error -> {
                    _snackbarMessage.value = res.message
                }
            }
            _youtubeResolvingId.value = null
        }
    }

    fun openYouTubeQualityPicker(item: SearchResultItem) {
        viewModelScope.launch {
            _youtubeResolvingId.value = item.id
            when (val res = urlMediaBot.fetchMediaFromUrl(item.url)) {
                is ApiResult.Success -> {
                    _youtubeSheetMedia.value = res.data
                }
                is ApiResult.Error -> {
                    _snackbarMessage.value = res.message
                }
            }
            _youtubeResolvingId.value = null
        }
    }

    fun dismissYouTubeSheetMedia() {
        _youtubeSheetMedia.value = null
    }

    fun pauseDownload(item: DownloadEntity) {
        viewModelScope.launch { downloadRepo.pauseDownload(item) }
    }

    fun resumeOrRetryDownload(item: DownloadEntity) {
        viewModelScope.launch { downloadRepo.resumeOrRetryDownload(item) }
    }

    fun deleteDownload(item: DownloadEntity) {
        viewModelScope.launch {
            downloadRepo.cancelAndDeleteDownload(item)
            refreshStorageMetrics()
            _snackbarMessage.value = "Removed \"${item.title}\""
        }
    }

    fun toggleFavorite(item: DownloadEntity) {
        viewModelScope.launch { downloadRepo.toggleFavorite(item) }
    }

    fun renameDownload(item: DownloadEntity, newTitle: String) {
        viewModelScope.launch {
            downloadRepo.renameDownload(item, newTitle)
            _snackbarMessage.value = "Renamed to \"$newTitle\""
        }
    }

    // Device Media Scanning & Pickers (Play Policy Compliant)
    fun scanDeviceMedia(silent: Boolean = false) {
        viewModelScope.launch {
            _isScanningDeviceMedia.value = true
            val currentSettings = settings.value
            val found = localMediaScanner.scanAllAccessibleMedia(
                primaryTreeUri = currentSettings.customFolderTreeUri,
                secondaryTreeUri = currentSettings.secondaryFolderTreeUri
            )
            refreshStorageMetrics()
            _isScanningDeviceMedia.value = false
            if (!silent) {
                _snackbarMessage.value = if (found > 0) {
                    "Scanned & indexed $found media files with cover art"
                } else {
                    "Scan complete — library is up to date"
                }
            }
        }
    }

    fun importPickedMedia(uris: List<Uri>, mediaTypeHint: MediaType? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val saved = localMediaScanner.importPickedMediaUris(uris, mediaTypeHint)
            refreshStorageMetrics()
            _snackbarMessage.value = if (saved.isNotEmpty()) {
                "Added ${saved.size} item(s) to SaveBox Library"
            } else {
                "Unable to import selected files"
            }
        }
    }

    // Library controls
    fun setLibrarySearchQuery(query: String) { _librarySearchQuery.value = query }
    fun setLibraryFilter(filter: LibraryFilter) { _libraryFilter.value = filter }
    fun setLibrarySort(sort: SortOption) { _librarySort.value = sort }
    fun setSelectedFolderFilter(folder: String?) { _selectedFolderFilter.value = folder }
    fun toggleLibraryViewMode() { _isLibraryGridView.value = !_isLibraryGridView.value }

    // Playlists
    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            downloadRepo.createPlaylist(name, description)
            _snackbarMessage.value = "Playlist \"$name\" created"
        }
    }

    fun addTrackToPlaylist(playlistId: Long, downloadId: Long) {
        viewModelScope.launch {
            downloadRepo.addToPlaylist(playlistId, downloadId)
            _snackbarMessage.value = "Added to playlist"
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, downloadId: Long) {
        viewModelScope.launch { downloadRepo.removeFromPlaylist(playlistId, downloadId) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { downloadRepo.deletePlaylist(playlistId) }
    }

    // YouTube & Vault Search
    fun setGlobalSearchQuery(query: String) { _globalSearchQuery.value = query }

    fun setSearchScope(scope: SearchScope) {
        _searchScope.value = scope
        if (_globalSearchQuery.value.isNotBlank() && scope == SearchScope.YOUTUBE) {
            performOnlineSearch()
        }
    }

    fun performOnlineSearch() {
        val query = _globalSearchQuery.value.trim()
        val scope = _searchScope.value
        if (query.isEmpty()) return

        viewModelScope.launch {
            downloadRepo.recordSearch(query, scope, isUrl = false)
            if (scope == SearchScope.SAVED_VAULT) {
                _onlineSearchState.value = SearchUiState.Idle
                return@launch
            }
            _onlineSearchState.value = SearchUiState.Loading
            when (val result = youTubeSearchEngine.search(query)) {
                is ApiResult.Success -> _onlineSearchState.value = SearchUiState.Results(result.data)
                is ApiResult.Error -> _onlineSearchState.value = SearchUiState.Error(result.message)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            downloadRepo.clearSearchHistory()
            _snackbarMessage.value = "Search history cleared"
        }
    }

    // Audio Playback controls
    fun playAudioTrack(item: DownloadEntity, playlistContext: List<DownloadEntity>? = null) {
        val queue = (playlistContext ?: completedDownloads.value.filter { it.mediaType == MediaType.AUDIO.name })
            .ifEmpty { listOf(item) }
        val idx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        mediaControllerManager.playQueue(queue, idx, settings.value.autoResumePlayback)
    }

    fun addToAudioQueue(item: DownloadEntity) {
        mediaControllerManager.addToQueue(item)
        _snackbarMessage.value = "Added \"${item.title}\" to playback queue"
    }

    fun togglePlayPause() = mediaControllerManager.togglePlayPause()
    fun seekTo(posMs: Long) = mediaControllerManager.seekTo(posMs)
    fun skipNext() = mediaControllerManager.skipNext()
    fun skipPrevious() = mediaControllerManager.skipPrevious()
    fun toggleShuffle() = mediaControllerManager.toggleShuffle()
    fun cycleRepeatMode() = mediaControllerManager.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = mediaControllerManager.setPlaybackSpeed(speed)
    fun setSleepTimer(option: SleepTimerOption) = mediaControllerManager.setSleepTimer(option)
    fun removeFromAudioQueue(index: Int) = mediaControllerManager.removeFromQueue(index)

    fun setEqualizerEnabled(enabled: Boolean) = AudioEffectsManager.setEnabled(enabled)
    fun setEqualizerBand(index: Short, levelMb: Short) = AudioEffectsManager.setBandLevel(index, levelMb)
    fun selectEqualizerPreset(index: Int) {
        AudioEffectsManager.selectPreset(index)
        viewModelScope.launch { settingsStore.setEqualizerPreset(index) }
    }

    fun saveVideoPosition(downloadId: Long, positionMs: Long, incrementPlay: Boolean) {
        viewModelScope.launch {
            downloadRepo.updatePlaybackPosition(downloadId, positionMs, incrementPlay)
        }
    }

    // Local Folder Media Importer
    fun onFolderGranted(uri: Uri, isSecondary: Boolean) {
        viewModelScope.launch {
            if (isSecondary) {
                settingsStore.setSecondaryFolderTreeUri(uri.toString())
            } else {
                settingsStore.setCustomFolderTreeUri(uri.toString())
            }
            refreshFolderMedia()
            scanDeviceMedia(silent = false)
        }
    }

    fun refreshFolderMedia() {
        viewModelScope.launch {
            _isScanningFolders.value = true
            val currentSettings = settings.value
            val items = statusRepo.scanFolderMedia(
                currentSettings.customFolderTreeUri,
                currentSettings.secondaryFolderTreeUri
            )
            _folderMediaItems.value = items
            _isScanningFolders.value = false
        }
    }

    fun setFolderSearchQuery(q: String) { _folderSearchQuery.value = q }

    fun importFolderMedia(item: LocalFolderMediaItem) {
        viewModelScope.launch {
            val saved = statusRepo.importMediaToVault(item)
            if (saved != null) {
                _snackbarMessage.value = "Imported to SaveBox Library"
                refreshFolderMedia()
                refreshStorageMetrics()
            } else {
                _snackbarMessage.value = "Unable to copy media file"
            }
        }
    }

    fun deleteImportedFolderCopy(item: LocalFolderMediaItem) {
        viewModelScope.launch {
            if (statusRepo.deleteImportedCopy(item)) {
                _snackbarMessage.value = "Deleted imported copy"
                refreshFolderMedia()
                refreshStorageMetrics()
            }
        }
    }

    // Settings, Themes, Visualizers, Storage & Backup
    fun setThemeMode(mode: AppThemeMode) { viewModelScope.launch { settingsStore.setThemeMode(mode) } }
    fun setThemePalette(palette: AppThemePalette) { viewModelScope.launch { settingsStore.setThemePalette(palette) } }
    fun setVisualizerStyle(style: VisualizerStyle) { viewModelScope.launch { settingsStore.setVisualizerStyle(style) } }
    fun cycleVisualizerStyle() {
        val all = VisualizerStyle.entries
        val next = all[(all.indexOf(settings.value.visualizerStyle) + 1) % all.size]
        setVisualizerStyle(next)
    }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { settingsStore.setDynamicColor(enabled) } }
    fun setDefaultQuality(q: String) { viewModelScope.launch { settingsStore.setDefaultQuality(q) } }
    fun setSimultaneousDownloads(n: Int) { viewModelScope.launch { settingsStore.setSimultaneousDownloads(n) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) } }
    fun setAutoClipboardDetect(enabled: Boolean) { viewModelScope.launch { settingsStore.setAutoClipboardDetect(enabled) } }
    fun setAutoResumePlayback(enabled: Boolean) { viewModelScope.launch { settingsStore.setAutoResumePlayback(enabled) } }
    fun setDefaultPlaybackSpeed(speed: Float) { viewModelScope.launch { settingsStore.setDefaultPlaybackSpeed(speed) } }
    fun setLanguage(lang: String) { viewModelScope.launch { settingsStore.setLanguage(lang) } }
    fun setCustomSubfolder(folder: String) { viewModelScope.launch { settingsStore.setCustomSubfolder(folder) } }

    fun refreshStorageMetrics() {
        _storageBreakdown.value = storageManager.getStorageBreakdown()
        _cacheSizeBytes.value = storageManager.getCacheSizeBytes()
    }

    fun clearCache() {
        storageManager.clearAppCache()
        refreshStorageMetrics()
        _snackbarMessage.value = "App cache cleared"
    }

    fun clearDownloadQueueHistory() {
        viewModelScope.launch {
            downloadRepo.clearCompletedHistory()
            _snackbarMessage.value = "Queue history cleared"
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = downloadRepo.exportBackupToUri(uri)
            _snackbarMessage.value = if (ok) "Backup exported successfully" else "Backup export failed"
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            val count = downloadRepo.restoreBackupFromUri(uri)
            _snackbarMessage.value = if (count > 0) "Restored $count media records" else "No records found in backup"
        }
    }

    fun getStorageBreakdownFolders(): List<String> = StorageManager.ALL_SAVEBOX_FOLDERS
}
