package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.DownloadRepository
import com.example.data.download.StorageManager
import com.example.data.local.DownloadEntity
import com.example.data.local.MediaMetadataEntity
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.data.remote.ApiResult
import com.example.data.remote.MediaApiService
import com.example.data.remote.SaveBoxApiManager
import com.example.data.status.StatusSaverRepository
import com.example.model.AnalyzedMedia
import com.example.model.AppThemeMode
import com.example.model.LibraryFilter
import com.example.model.MediaFormatOption
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.SearchPlatformScope
import com.example.model.SearchResultItem
import com.example.model.SortOption
import com.example.model.StorageFolderBreakdown
import com.example.model.WhatsAppStatusItem
import com.example.player.AudioEffectsManager
import com.example.player.AudioPlayerState
import com.example.player.EqualizerState
import com.example.player.MediaControllerManager
import com.example.player.SleepTimerOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AnalyzeUiState {
    data object Idle : AnalyzeUiState()
    data class Loading(val platform: MediaPlatform) : AnalyzeUiState()
    data class Success(val media: AnalyzedMedia) : AnalyzeUiState()
    data class Error(val message: String) : AnalyzeUiState()
}

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Results(val items: List<SearchResultItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val apiManager: SaveBoxApiManager,
    @Suppress("unused") val mediaApiService: MediaApiService,
    private val downloadRepo: DownloadRepository,
    private val storageManager: StorageManager,
    private val statusRepo: StatusSaverRepository,
    private val settingsStore: SettingsDataStore,
    val mediaControllerManager: MediaControllerManager
) : AndroidViewModel(application) {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.allDownloads
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

    // Home URL input & analysis
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _detectedPlatform = MutableStateFlow<MediaPlatform?>(null)
    val detectedPlatform: StateFlow<MediaPlatform?> = _detectedPlatform.asStateFlow()

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    private val _analyzeState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val analyzeState: StateFlow<AnalyzeUiState> = _analyzeState.asStateFlow()

    // In-App Browser state
    private val _browserCurrentUrl = MutableStateFlow("https://www.google.com")
    val browserCurrentUrl: StateFlow<String> = _browserCurrentUrl.asStateFlow()

    private val _browserPendingUrl = MutableStateFlow<String?>(null)
    val browserPendingUrl: StateFlow<String?> = _browserPendingUrl.asStateFlow()

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
                    item.platform.contains(query, ignoreCase = true)
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

    // Global Search state
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _searchScope = MutableStateFlow(SearchPlatformScope.YOUTUBE)
    val searchScope: StateFlow<SearchPlatformScope> = _searchScope.asStateFlow()

    private val _onlineSearchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val onlineSearchState: StateFlow<SearchUiState> = _onlineSearchState.asStateFlow()

    // WhatsApp Status Saver state
    private val _statusItems = MutableStateFlow<List<WhatsAppStatusItem>>(emptyList())
    val statusItems: StateFlow<List<WhatsAppStatusItem>> = _statusItems.asStateFlow()

    private val _statusSearchQuery = MutableStateFlow("")
    val statusSearchQuery: StateFlow<String> = _statusSearchQuery.asStateFlow()

    private val _isScanningStatuses = MutableStateFlow(false)
    val isScanningStatuses: StateFlow<Boolean> = _isScanningStatuses.asStateFlow()

    // Storage & Cache metrics
    private val _storageBreakdown = MutableStateFlow<List<StorageFolderBreakdown>>(emptyList())
    val storageBreakdown: StateFlow<List<StorageFolderBreakdown>> = _storageBreakdown.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    // Transient UI toast/snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        mediaControllerManager.connect()
        refreshStorageMetrics()
        viewModelScope.launch {
            settings.collect { userSettings ->
                if (userSettings.whatsAppTreeUri != null || userSettings.whatsAppBusinessTreeUri != null) {
                    refreshWhatsAppStatuses()
                }
            }
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun detectPlatformForUrl(url: String): MediaPlatform = apiManager.detectPlatform(url)

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

    fun updateUrlInput(value: String) {
        _urlInput.value = value
        _detectedPlatform.value = if (value.contains(".") || value.startsWith("http", true)) {
            apiManager.detectPlatform(value)
        } else null
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _detectedPlatform.value = null
        _analyzeState.value = AnalyzeUiState.Idle
    }

    fun setClipboardSuggestion(url: String?) {
        _clipboardSuggestion.value = url
    }

    fun pasteAndAnalyze(url: String) {
        updateUrlInput(url)
        analyzeCurrentUrl()
    }

    fun analyzeCurrentUrl() {
        val raw = _urlInput.value.trim()
        if (raw.isEmpty()) {
            _analyzeState.value = AnalyzeUiState.Error("Paste or type a media link first.")
            return
        }
        val platform = apiManager.detectPlatform(raw)
        _detectedPlatform.value = platform
        _analyzeState.value = AnalyzeUiState.Loading(platform)

        viewModelScope.launch {
            downloadRepo.recordSearch(raw, SearchPlatformScope.ALL_LOCAL, isUrl = true)
            when (val result = apiManager.analyzeUrl(raw)) {
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

    fun dismissAnalysisSheet() {
        _analyzeState.value = AnalyzeUiState.Idle
    }

    fun startDownload(
        analyzed: AnalyzedMedia,
        format: MediaFormatOption,
        customTitle: String,
        customFolder: String?
    ) {
        viewModelScope.launch {
            downloadRepo.enqueueDownload(analyzed, format, customTitle, customFolder)
            _analyzeState.value = AnalyzeUiState.Idle
            _snackbarMessage.value = "Added \"${customTitle.ifBlank { analyzed.title }}\" to Download Queue"
            refreshStorageMetrics()
        }
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

    // Global Search
    fun setGlobalSearchQuery(query: String) { _globalSearchQuery.value = query }
    fun setSearchScope(scope: SearchPlatformScope) {
        _searchScope.value = scope
        if (_globalSearchQuery.value.isNotBlank() && scope != SearchPlatformScope.ALL_LOCAL) {
            performOnlineSearch()
        }
    }

    fun performOnlineSearch() {
        val query = _globalSearchQuery.value.trim()
        val scope = _searchScope.value
        if (query.isEmpty()) return

        viewModelScope.launch {
            downloadRepo.recordSearch(query, scope, isUrl = false)
            if (scope == SearchPlatformScope.ALL_LOCAL) {
                _onlineSearchState.value = SearchUiState.Idle
                return@launch
            }
            _onlineSearchState.value = SearchUiState.Loading
            when (val result = apiManager.searchMedia(query, scope)) {
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

    // WhatsApp Status Saver
    fun onWhatsAppFolderGranted(uri: Uri, isBusiness: Boolean) {
        viewModelScope.launch {
            if (isBusiness) {
                settingsStore.setWhatsAppBusinessTreeUri(uri.toString())
            } else {
                settingsStore.setWhatsAppTreeUri(uri.toString())
            }
            refreshWhatsAppStatuses()
        }
    }

    fun refreshWhatsAppStatuses() {
        viewModelScope.launch {
            _isScanningStatuses.value = true
            val currentSettings = settings.value
            val items = statusRepo.scanStatuses(
                currentSettings.whatsAppTreeUri,
                currentSettings.whatsAppBusinessTreeUri
            )
            _statusItems.value = items
            _isScanningStatuses.value = false
        }
    }

    fun setStatusSearchQuery(q: String) { _statusSearchQuery.value = q }

    fun saveWhatsAppStatus(item: WhatsAppStatusItem) {
        viewModelScope.launch {
            val saved = statusRepo.saveStatusToGallery(item)
            if (saved != null) {
                _snackbarMessage.value = "Status saved to SaveBox Gallery"
                refreshWhatsAppStatuses()
                refreshStorageMetrics()
            } else {
                _snackbarMessage.value = "Unable to copy status file"
            }
        }
    }

    fun deleteSavedStatusCopy(item: WhatsAppStatusItem) {
        viewModelScope.launch {
            if (statusRepo.deleteSavedCopy(item)) {
                _snackbarMessage.value = "Deleted saved status copy"
                refreshWhatsAppStatuses()
                refreshStorageMetrics()
            }
        }
    }

    // Settings, Storage & Backup
    fun setThemeMode(mode: AppThemeMode) { viewModelScope.launch { settingsStore.setThemeMode(mode) } }
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
