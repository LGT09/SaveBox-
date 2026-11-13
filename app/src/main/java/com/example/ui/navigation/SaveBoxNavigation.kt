package com.example.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.model.MediaType
import com.example.model.SearchPlatformScope
import com.example.ui.components.AnimatedAuroraBackdrop
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MiniAudioPlayerBar
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatusSaverScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
sealed interface SaveBoxRoute {
    @Serializable data object Home : SaveBoxRoute
    @Serializable data object Library : SaveBoxRoute
    @Serializable data object Search : SaveBoxRoute
    @Serializable data object StatusSaver : SaveBoxRoute
    @Serializable data object Settings : SaveBoxRoute
    @Serializable data object Queue : SaveBoxRoute
    @Serializable data object AudioPlayer : SaveBoxRoute
    @Serializable data class VideoPlayer(val downloadId: Long) : SaveBoxRoute
}

private data class NavTab(
    val route: SaveBoxRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    NavTab(SaveBoxRoute.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(SaveBoxRoute.Library, "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    NavTab(SaveBoxRoute.Search, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    NavTab(SaveBoxRoute.StatusSaver, "Statuses", Icons.Filled.Smartphone, Icons.Outlined.Smartphone),
    NavTab(SaveBoxRoute.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun SaveBoxRootShell(
    viewModel: MainViewModel,
    sharedUrlFromIntent: String?,
    onConsumedSharedUrl: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(sharedUrlFromIntent) {
        if (!sharedUrlFromIntent.isNullOrBlank()) {
            showSplash = false
            viewModel.pasteAndAnalyze(sharedUrlFromIntent)
            onConsumedSharedUrl()
        }
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "splash_transition"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen(onSplashFinished = { showSplash = false })
        } else {
            SaveBoxMainApp(viewModel = viewModel)
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioState by viewModel.audioPlayerState.collectAsStateWithLifecycle()

    var imagePreviewTarget by remember { mutableStateOf<DownloadEntity?>(null) }

    // Request POST_NOTIFICATIONS on Android 13+ when entering main app
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
    val isAudioFullScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.AudioPlayer>() == true
    val isQueueScreen = backStackEntry?.destination?.hasRoute<SaveBoxRoute.Queue>() == true
    val showNavigationChrome = !isVideoScreen && !isAudioFullScreen

    fun playMediaEntity(item: DownloadEntity) {
        when (MediaType.fromString(item.mediaType)) {
            MediaType.AUDIO -> {
                viewModel.playAudioTrack(item)
                navController.navigate(SaveBoxRoute.AudioPlayer)
            }
            MediaType.VIDEO -> {
                navController.navigate(SaveBoxRoute.VideoPlayer(item.id))
            }
            MediaType.IMAGE -> {
                imagePreviewTarget = item
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
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = if (isVideoScreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
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
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                        windowInsets = WindowInsets.navigationBars
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
                                    onOpenStatusSaver = { navigateToTab(SaveBoxRoute.StatusSaver) },
                                    onOpenAudioPlayer = { navController.navigate(SaveBoxRoute.AudioPlayer) },
                                    onPlayMedia = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.Library> {
                                LibraryScreen(
                                    viewModel = viewModel,
                                    onPlayMedia = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.Search> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onAnalyzeResultUrl = { url ->
                                        navigateToTab(SaveBoxRoute.Home)
                                        viewModel.pasteAndAnalyze(url)
                                    },
                                    onPlayLocal = { playMediaEntity(it) }
                                )
                            }

                            composable<SaveBoxRoute.StatusSaver> {
                                StatusSaverScreen(viewModel = viewModel)
                            }

                            composable<SaveBoxRoute.Settings> {
                                SettingsScreen(viewModel = viewModel)
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

                                if (target != null) {
                                    VideoPlayerScreen(
                                        initialVideo = target,
                                        playlist = videoList.ifEmpty { listOf(target) },
                                        autoResumePosition = settings.autoResumePlayback,
                                        defaultSpeed = settings.defaultPlaybackSpeed,
                                        onSavePosition = { id, pos, inc ->
                                            viewModel.saveVideoPosition(id, pos, inc)
                                        },
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
    }

    imagePreviewTarget?.let { img ->
        AlertDialog(
            onDismissRequest = { imagePreviewTarget = null },
            title = { Text(img.title) },
            text = {
                AsyncImage(
                    model = img.localFilePath?.let { File(it) } ?: img.localUri ?: img.thumbnailUrl,
                    contentDescription = img.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { imagePreviewTarget = null }) { Text("Close") }
            }
        )
    }
}
