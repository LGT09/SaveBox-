package com.traxxiontech.savebox.ui.navigation

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.traxxiontech.savebox.data.local.DownloadEntity
import com.traxxiontech.savebox.model.MediaType
import com.traxxiontech.savebox.ui.components.AnimatedAuroraBackdrop
import com.traxxiontech.savebox.ui.components.RewardedDownloadPassDialog
import com.traxxiontech.savebox.ui.screens.AudioPlayerScreen
import com.traxxiontech.savebox.ui.screens.BrowserScreen
import com.traxxiontech.savebox.ui.screens.FileManagerScreen
import com.traxxiontech.savebox.ui.screens.HomeScreen
import com.traxxiontech.savebox.ui.screens.LibraryScreen
import com.traxxiontech.savebox.ui.screens.MiniAudioPlayerBar
import com.traxxiontech.savebox.ui.screens.PhotoViewerScreen
import com.traxxiontech.savebox.ui.screens.PrivacyPolicyGateScreen
import com.traxxiontech.savebox.ui.screens.PrivacyPolicyScreen
import com.traxxiontech.savebox.ui.screens.QueueScreen
import com.traxxiontech.savebox.ui.screens.SearchScreen
import com.traxxiontech.savebox.ui.screens.SettingsScreen
import com.traxxiontech.savebox.ui.screens.SplashScreen
import com.traxxiontech.savebox.ui.screens.StatusSaverScreen
import com.traxxiontech.savebox.ui.screens.VideoPlayerScreen
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface SaveBoxRoute {
    @Serializable data object Home : SaveBoxRoute
    @Serializable data object Browser : SaveBoxRoute
    @Serializable data object Library : SaveBoxRoute
    @Serializable data object Search : SaveBoxRoute
    @Serializable data object StatusSaver : SaveBoxRoute
    @Serializable data object FileManager : SaveBoxRoute
    @Serializable data object Settings : SaveBoxRoute
    @Serializable data object PrivacyPolicy : SaveBoxRoute
    @Serializable data object Queue : SaveBoxRoute
    @Serializable data object AudioPlayer : SaveBoxRoute
    @Serializable data class VideoPlayer(val downloadId: Long) : SaveBoxRoute
    @Serializable data class PhotoViewer(val downloadId: Long) : SaveBoxRoute
}

private data class NavTab(
    val route: SaveBoxRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    NavTab(SaveBoxRoute.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(SaveBoxRoute.Browser, "Browser", Icons.Filled.Public, Icons.Outlined.Public),
    NavTab(SaveBoxRoute.Library, "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    NavTab(SaveBoxRoute.Search, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    NavTab(SaveBoxRoute.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun SaveBoxRootShell(
    viewModel: MainViewModel,
    sharedUrlFromIntent: String?,
    onConsumedSharedUrl: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(sharedUrlFromIntent, settings.isLoaded, settings.hasAcceptedPrivacyPolicy) {
        if (!sharedUrlFromIntent.isNullOrBlank() && settings.isLoaded) {
            showSplash = false
            if (settings.hasAcceptedPrivacyPolicy) {
                viewModel.pasteAndFetch(sharedUrlFromIntent)
                onConsumedSharedUrl()
            } else {
                viewModel.updateUrlInput(sharedUrlFromIntent)
                onConsumedSharedUrl()
            }
        }
    }

    val gateState = when {
        showSplash -> 0
        !settings.isLoaded -> 1
        !settings.hasAcceptedPrivacyPolicy -> 2
        else -> 3
    }

    AnimatedContent(
        targetState = gateState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "root_gate_transition"
    ) { state ->
        when (state) {
            0 -> SplashScreen(onSplashFinished = { showSplash = false })
            1 -> AnimatedAuroraBackdrop { Box(modifier = Modifier.fillMaxSize()) }
            2 -> PrivacyPolicyGateScreen(
                onAccept = { viewModel.acceptPrivacyPolicy() },
                onDecline = { (context as? Activity)?.finish() }
            )
            else -> SaveBoxMainApp(viewModel = viewModel)
        }
    }
}

@Composable
private fun SaveBoxMainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val transientPlayTarget by viewModel.transientPlayTarget.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioState by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val showRewardedAdDialog by viewModel.showRewardedAdDialog.collectAsStateWithLifecycle()

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val isVideoScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.VideoPlayer>() == true
    val isPhotoScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.PhotoViewer>() == true
    val isAudioFullScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.AudioPlayer>() == true
    val isQueueScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.Queue>() == true
    val isPrivacyScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.PrivacyPolicy>() == true
    val showNavigationChrome = !isVideoScreen && !isPhotoScreen && !isAudioFullScreen && !isPrivacyScreen

    fun playMediaEntity(item: DownloadEntity) {
        when (MediaType.fromString(item.mediaType)) {
            MediaType.AUDIO -> {
                viewModel.playAudioTrack(item)
                navController.navigate(SaveBoxRoute.AudioPlayer)
            }
            MediaType.VIDEO -> {
                if (item.id <= 0L) {
                    viewModel.setTransientPlayTarget(item)
                }
                navController.navigate(SaveBoxRoute.VideoPlayer(item.id))
            }
            MediaType.IMAGE -> {
                navController.navigate(SaveBoxRoute.PhotoViewer(item.id))
            }
        }
    }

    fun navigateToTab(route: SaveBoxRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openInAppBrowser(url: String) {
        viewModel.requestBrowserNavigate(url)
        navigateToTab(SaveBoxRoute.Browser)
    }

    AnimatedAuroraBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp && showNavigationChrome

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = backStackEntry?.destination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                        val filesSelected = backStackEntry?.destination?.hasRoute<SaveBoxRoute.FileManager>() == true
                        NavigationRailItem(
                            selected = filesSelected,
                            onClick = { navigateToTab(SaveBoxRoute.FileManager) },
                            icon = {
                                Icon(
                                    imageVector = if (filesSelected) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                                    contentDescription = "Files"
                                )
                            },
                            label = { Text("Files") }
                        )
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = if (isVideoScreen || isPhotoScreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showNavigationChrome && !isQueueScreen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                            ) {
                                if (audioState.currentTrack != null) {
                                    MiniAudioPlayerBar(
                                        viewModel = viewModel,
                                        onOpenFullPlayer = { navController.navigate(SaveBoxRoute.AudioPlayer) }
                                    )
                                }
                                if (!useRail) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(WindowInsets.navigationBars)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(30.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                        tonalElevation = 8.dp,
                                        shadowElevation = 12.dp,
                                        border = BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.18f)
                                        )
                                    ) {
                                        NavigationBar(
                                            containerColor = Color.Transparent,
                                            windowInsets = WindowInsets(0, 0, 0, 0)
                                        ) {
                                            TopTabs.forEach { tab ->
                                                val selected = backStackEntry?.destination?.hasRoute(tab.route::class) == true
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = { navigateToTab(tab.route) },
                                                    icon = {
                                                        Icon(
                                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                            contentDescription = tab.label
                                                        )
                                                    },
                                                    label = { Text(tab.label, maxLines = 1) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = SaveBoxRoute.Home
                        ) {
                            composable<SaveBoxRoute.Home> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onOpenQueue = { navController.navigate(SaveBoxRoute.Queue) },
                                    onOpenSearch = { scope ->
                                        viewModel.setSearchScope(scope)
                                        navigateToTab(SaveBoxRoute.Search)
                                    },
                                    onOpenLibrary = { navigateToTab(SaveBoxRoute.Library) },
                                    onOpenFileManager = { navController.navigate(SaveBoxRoute.FileManager) },
                                    onOpenPrivacyPolicy = { navController.navigate(SaveBoxRoute.PrivacyPolicy) },
                                    onOpenBrowser = { navigateToTab(SaveBoxRoute.Browser) },
                                    onOpenAudioPlayer = { navController.navigate(SaveBoxRoute.AudioPlayer) },
                                    onPlayMedia = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.Browser> {
                                BrowserScreen(
                                    viewModel = viewModel,
                                    onAnalyzePageUrl = { url ->
                                        navigateToTab(SaveBoxRoute.Home)
                                        viewModel.pasteAndFetch(url)
                                    }
                                )
                            }

                            composable<SaveBoxRoute.Library> {
                                LibraryScreen(
                                    viewModel = viewModel,
                                    onPlayMedia = { playMediaEntity(it) },
                                    onOpenFileManager = { navController.navigate(SaveBoxRoute.FileManager) }
                                )
                            }

                            composable<SaveBoxRoute.Search> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onPlayStreamAudio = { stream ->
                                        viewModel.playAudioTrack(stream, listOf(stream))
                                        navController.navigate(SaveBoxRoute.AudioPlayer)
                                    },
                                    onPlayStreamVideo = { stream ->
                                        viewModel.setTransientPlayTarget(stream)
                                        navController.navigate(SaveBoxRoute.VideoPlayer(stream.id))
                                    },
                                    onPlayLocal = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.StatusSaver> {
                                BackHandler { navigateToTab(SaveBoxRoute.Home) }
                                StatusSaverScreen(viewModel = viewModel)
                            }

                            composable<SaveBoxRoute.FileManager> {
                                BackHandler { navController.popBackStack() }
                                FileManagerScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onPlayMedia = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.Settings> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onOpenInAppBrowser = { url -> openInAppBrowser(url) },
                                    onOpenPrivacyPolicy = { navController.navigate(SaveBoxRoute.PrivacyPolicy) }
                                )
                            }

                            composable<SaveBoxRoute.PrivacyPolicy> {
                                BackHandler { navController.popBackStack() }
                                PrivacyPolicyScreen(
                                    acceptedAt = settings.privacyPolicyAcceptedAt,
                                    policyVersion = settings.privacyPolicyVersion,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SaveBoxRoute.Queue> {
                                BackHandler { navController.popBackStack() }
                                QueueScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onPlayMedia = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.AudioPlayer> {
                                BackHandler { navController.popBackStack() }
                                AudioPlayerScreen(
                                    viewModel = viewModel,
                                    onClose = { navController.popBackStack() }
                                )
                            }

                            composable<SaveBoxRoute.VideoPlayer> { entry ->
                                val route = entry.toRoute<SaveBoxRoute.VideoPlayer>()
                                val videoList = completedDownloads.filter { it.mediaType == MediaType.VIDEO.name }
                                val target = videoList.firstOrNull { it.id == route.downloadId }
                                    ?: completedDownloads.firstOrNull { it.id == route.downloadId }
                                    ?: transientPlayTarget?.takeIf { it.id == route.downloadId }

                                if (target != null) {
                                    VideoPlayerScreen(
                                        initialVideo = target,
                                        playlist = videoList.ifEmpty { listOf(target) },
                                        autoResumePosition = settings.autoResumePlayback,
                                        defaultSpeed = settings.defaultPlaybackSpeed,
                                        defaultRepeatMode = settings.defaultRepeatMode,
                                        defaultAutoPlayNext = settings.autoPlayNext,
                                        defaultSeekStepSec = settings.defaultSeekStepSec,
                                        visualizerStyle = settings.visualizerStyle,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }

                            composable<SaveBoxRoute.PhotoViewer> { entry ->
                                val route = entry.toRoute<SaveBoxRoute.PhotoViewer>()
                                val photoList = completedDownloads.filter { it.mediaType == MediaType.IMAGE.name }
                                val target = photoList.firstOrNull { it.id == route.downloadId }
                                    ?: completedDownloads.firstOrNull { it.id == route.downloadId }

                                if (target != null) {
                                    PhotoViewerScreen(
                                        initialPhoto = target,
                                        photos = photoList.ifEmpty { listOf(target) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onDelete = { viewModel.deleteDownload(it) },
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showRewardedAdDialog) {
            RewardedDownloadPassDialog(
                onDismiss = { viewModel.dismissRewardedAdDialog() },
                onRewardGranted = { viewModel.grantDownloadPass() }
            )
        }
    }
}
