package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PlatformBadgeChip
import com.example.ui.viewmodel.MainViewModel

private data class QuickBookmark(
    val title: String,
    val url: String
)

private val BrowserBookmarks = listOf(
    QuickBookmark("Google", "https://www.google.com"),
    QuickBookmark("YouTube", "https://m.youtube.com"),
    QuickBookmark("TikTok", "https://www.tiktok.com"),
    QuickBookmark("Instagram", "https://www.instagram.com"),
    QuickBookmark("SoundCloud", "https://m.soundcloud.com"),
    QuickBookmark("Spotify", "https://open.spotify.com"),
    QuickBookmark("Pinterest", "https://www.pinterest.com"),
    QuickBookmark("Traxxion Tech", "https://traxxiontech.com")
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: MainViewModel,
    onAnalyzePageUrl: (String) -> Unit
) {
    val currentUrl by viewModel.browserCurrentUrl.collectAsStateWithLifecycle()
    val pendingUrl by viewModel.browserPendingUrl.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var omniboxInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("Google") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(currentUrl) {
        if (!omniboxInput.equals(currentUrl, ignoreCase = true)) {
            omniboxInput = currentUrl
        }
    }

    LaunchedEffect(pendingUrl) {
        val target = pendingUrl ?: return@LaunchedEffect
        webViewRef?.loadUrl(target)
        viewModel.updateBrowserCurrentUrl(target)
        omniboxInput = target
        viewModel.consumeBrowserPendingUrl()
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    fun navigateToInput(raw: String) {
        val target = viewModel.resolveBrowserTarget(raw)
        focusManager.clearFocus()
        omniboxInput = target
        viewModel.updateBrowserCurrentUrl(target)
        webViewRef?.loadUrl(target)
    }

    val detectedPlatform = remember(currentUrl) {
        viewModel.detectPlatformForUrl(currentUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Glass Omnibox & Toolbar
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            cornerRadius = 22.dp,
            contentPadding = 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = omniboxInput,
                    onValueChange = { omniboxInput = it },
                    placeholder = { Text("Search Google or enter URL...") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (omniboxInput.startsWith("https://")) Icons.Filled.Lock else Icons.Filled.Search,
                            contentDescription = "URL or Google Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (omniboxInput.isNotEmpty()) {
                            IconButton(onClick = { omniboxInput = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear URL", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigateToInput(omniboxInput) }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browser_omnibox_input")
                )

                Button(
                    onClick = { navigateToInput(omniboxInput) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("browser_go_button")
                ) {
                    Text("Go", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Controls + Quick Bookmarks
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Browser Back")
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Browser Forward")
                    }
                    IconButton(
                        onClick = {
                            if (isLoading) webViewRef?.stopLoading() else webViewRef?.reload()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (isLoading) "Stop Loading" else "Reload"
                        )
                    }
                    IconButton(
                        onClick = { navigateToInput("https://www.google.com") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "Google Home")
                    }
                }

                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrowserBookmarks.forEach { bookmark ->
                    FilterChip(
                        selected = currentUrl.contains(bookmark.url.removePrefix("https://"), ignoreCase = true),
                        onClick = { navigateToInput(bookmark.url) },
                        label = { Text(bookmark.title) }
                    )
                }
            }

            if (isLoading && loadProgress in 1..99) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // WebView Canvas + Floating Save Media Action
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                return !(target.startsWith("https://") || target.startsWith("http://"))
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    viewModel.updateBrowserCurrentUrl(it)
                                    omniboxInput = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let {
                                    viewModel.updateBrowserCurrentUrl(it)
                                    omniboxInput = it
                                }
                                pageTitle = view?.title ?: "SaveBox Browser"
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        setDownloadListener(
                            DownloadListener { url, _, _, _, _ ->
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    onAnalyzePageUrl(url)
                                }
                            }
                        )

                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Glass Action Bar to Capture & Download Current Page URL
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { onAnalyzePageUrl(currentUrl) }
                    .testTag("browser_download_page_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlatformBadgeChip(platform = detectedPlatform)
                    Text(
                        text = "Download Media from Page",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download from current page",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
