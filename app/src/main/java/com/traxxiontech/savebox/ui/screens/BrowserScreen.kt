package com.traxxiontech.savebox.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traxxiontech.savebox.data.remote.BrowserPageSnapshot
import com.traxxiontech.savebox.data.remote.BrowserWebLink
import com.traxxiontech.savebox.ui.components.GlassCard
import com.traxxiontech.savebox.ui.components.LiquidGlassCard
import com.traxxiontech.savebox.ui.components.MediaSourceBadgeChip
import com.traxxiontech.savebox.ui.viewmodel.BrowserReaderUiState
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val TAG = "SaveBoxBrowser"

private data class QuickBookmark(
    val title: String,
    val url: String
)

private val BrowserBookmarks = listOf(
    QuickBookmark("Google", "https://www.google.com"),
    QuickBookmark("YouTube", "https://m.youtube.com"),
    QuickBookmark("Wikimedia", "https://commons.wikimedia.org"),
    QuickBookmark("Traxxion Tech", "https://traxxiontech.com")
)

private fun isContainerOrEmulatorEnvironment(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    val product = Build.PRODUCT.lowercase()
    val device = Build.DEVICE.lowercase()
    val model = Build.MODEL.lowercase()
    val board = Build.BOARD.lowercase()
    return hardware.contains("ranchu") ||
        hardware.contains("goldfish") ||
        hardware.contains("cutf") ||
        hardware.contains("vsoc") ||
        product.contains("sdk_gphone") ||
        product.contains("emulator") ||
        product.contains("cf_") ||
        product.contains("cuttlefish") ||
        device.contains("vsoc") ||
        device.contains("emulator") ||
        device.contains("generic") ||
        board.contains("cutf") ||
        model.contains("cuttlefish") ||
        model.contains("emulator") ||
        model.contains("android sdk") ||
        fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        fingerprint.contains("cuttlefish") ||
        fingerprint.contains("vsoc") ||
        fingerprint.contains("test-keys")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: MainViewModel,
    onAnalyzePageUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val currentUrl by viewModel.browserCurrentUrl.collectAsStateWithLifecycle()
    val pendingUrl by viewModel.browserPendingUrl.collectAsStateWithLifecycle()
    val readerState by viewModel.browserReaderState.collectAsStateWithLifecycle()
    val canReaderBack by viewModel.canBrowserReaderBack.collectAsStateWithLifecycle()
    val canReaderForward by viewModel.canBrowserReaderForward.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRestrictedEmulator = remember { isContainerOrEmulatorEnvironment() }
    var useNativeReaderMode by remember { mutableStateOf(isRestrictedEmulator) }
    var omniboxInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("Google") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isWebViewLoading by remember { mutableStateOf(false) }
    var canWebViewBack by remember { mutableStateOf(false) }
    var canWebViewForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewKey by remember { mutableIntStateOf(0) }
    var useSoftwareLayer by remember { mutableStateOf(isRestrictedEmulator) }

    LaunchedEffect(useNativeReaderMode, readerState) {
        if (useNativeReaderMode && readerState is BrowserReaderUiState.Idle) {
            viewModel.navigateBrowserReader(currentUrl, recordHistory = false)
        }
        if (useNativeReaderMode && readerState is BrowserReaderUiState.Loaded) {
            pageTitle = (readerState as BrowserReaderUiState.Loaded).snapshot.title
        }
    }

    LaunchedEffect(currentUrl) {
        if (!omniboxInput.equals(currentUrl, ignoreCase = true)) {
            omniboxInput = currentUrl
        }
    }

    LaunchedEffect(pendingUrl) {
        val target = pendingUrl ?: return@LaunchedEffect
        viewModel.updateBrowserCurrentUrl(target)
        omniboxInput = target
        viewModel.consumeBrowserPendingUrl()
        if (useNativeReaderMode) {
            viewModel.navigateBrowserReader(target)
        } else {
            webViewRef?.loadUrl(target) ?: run { webViewKey++ }
        }
    }

    val effectiveCanGoBack = if (useNativeReaderMode) canReaderBack else canWebViewBack
    val effectiveCanGoForward = if (useNativeReaderMode) canReaderForward else canWebViewForward
    val effectiveIsLoading = if (useNativeReaderMode) readerState is BrowserReaderUiState.Loading else isWebViewLoading

    BackHandler(enabled = effectiveCanGoBack) {
        if (useNativeReaderMode) {
            viewModel.browserReaderBack()
        } else {
            webViewRef?.goBack()
        }
    }

    DisposableEffect(lifecycleOwner, useNativeReaderMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (!useNativeReaderMode) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                    Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun navigateToInput(raw: String) {
        val target = viewModel.resolveBrowserTarget(raw)
        focusManager.clearFocus()
        omniboxInput = target
        viewModel.updateBrowserCurrentUrl(target)
        if (useNativeReaderMode) {
            viewModel.navigateBrowserReader(target)
        } else {
            webViewRef?.loadUrl(target) ?: run { webViewKey++ }
        }
    }

    fun openExternalBrowser(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    val sourceHost = remember(currentUrl) {
        currentUrl.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: "Web Page"
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

            // Navigation Controls + Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (useNativeReaderMode) viewModel.browserReaderBack() else webViewRef?.goBack()
                        },
                        enabled = effectiveCanGoBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Browser Back")
                    }
                    IconButton(
                        onClick = {
                            if (useNativeReaderMode) viewModel.browserReaderForward() else webViewRef?.goForward()
                        },
                        enabled = effectiveCanGoForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Browser Forward")
                    }
                    IconButton(
                        onClick = {
                            if (useNativeReaderMode) {
                                viewModel.reloadBrowserReader()
                            } else if (isWebViewLoading) {
                                webViewRef?.stopLoading()
                            } else {
                                webViewRef?.reload()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (effectiveIsLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (effectiveIsLoading) "Stop Loading" else "Reload"
                        )
                    }
                    IconButton(
                        onClick = { navigateToInput("https://www.google.com") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "Google Home")
                    }
                    IconButton(
                        onClick = { openExternalBrowser(currentUrl) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in External Browser")
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
                        .padding(horizontal = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !useNativeReaderMode,
                    onClick = {
                        useNativeReaderMode = !useNativeReaderMode
                        if (useNativeReaderMode) {
                            viewModel.navigateBrowserReader(currentUrl, recordHistory = false)
                        } else {
                            webViewKey++
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (useNativeReaderMode) Icons.Filled.Language else Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (useNativeReaderMode) "Safe Browser" else "Full WebView") }
                )
                BrowserBookmarks.forEach { bookmark ->
                    FilterChip(
                        selected = currentUrl.contains(bookmark.url.removePrefix("https://"), ignoreCase = true),
                        onClick = { navigateToInput(bookmark.url) },
                        label = { Text(bookmark.title) }
                    )
                }
            }

            if (!useNativeReaderMode && isWebViewLoading && loadProgress in 1..99) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (useNativeReaderMode && readerState is BrowserReaderUiState.Loading) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Browser Canvas + Floating Save Media Action
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (useNativeReaderMode) {
                NativeBrowserCanvas(
                    state = readerState,
                    onNavigate = { target -> navigateToInput(target) },
                    onRetry = { viewModel.reloadBrowserReader() },
                    onOpenExternal = { openExternalBrowser(currentUrl) },
                    onAnalyzePage = { target -> onAnalyzePageUrl(target) }
                )
            } else {
                key(webViewKey) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                if (useSoftwareLayer) {
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.setSupportMultipleWindows(false)
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString() ?: return false
                                        return !(target.startsWith("https://") || target.startsWith("http://"))
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isWebViewLoading = true
                                        url?.let {
                                            viewModel.updateBrowserCurrentUrl(it)
                                            omniboxInput = it
                                        }
                                        canWebViewBack = view?.canGoBack() == true
                                        canWebViewForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isWebViewLoading = false
                                        url?.let {
                                            viewModel.updateBrowserCurrentUrl(it)
                                            omniboxInput = it
                                        }
                                        pageTitle = view?.title ?: "SaveBox Browser"
                                        canWebViewBack = view?.canGoBack() == true
                                        canWebViewForward = view?.canGoForward() == true
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?
                                    ): Boolean {
                                        Log.w(TAG, "WebView renderer gone; switching cleanly to Safe Browser mode")
                                        isWebViewLoading = false
                                        useSoftwareLayer = true
                                        if (view != null) {
                                            (view.parent as? ViewGroup)?.removeView(view)
                                            runCatching { view.destroy() }
                                        }
                                        if (webViewRef == view) {
                                            webViewRef = null
                                        }
                                        useNativeReaderMode = true
                                        viewModel.navigateBrowserReader(currentUrl, recordHistory = false)
                                        return true
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
                        onRelease = { wv ->
                            if (webViewRef == wv) {
                                webViewRef = null
                            }
                            runCatching {
                                wv.stopLoading()
                                wv.webChromeClient = null
                                wv.removeAllViews()
                                (wv.parent as? ViewGroup)?.removeView(wv)
                                wv.destroy()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

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
                    MediaSourceBadgeChip(label = sourceHost)
                    Text(
                        text = "Fetch Media from Page",
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

@Composable
private fun NativeBrowserCanvas(
    state: BrowserReaderUiState,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    onAnalyzePage: (String) -> Unit
) {
    when (state) {
        is BrowserReaderUiState.Idle,
        is BrowserReaderUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(cornerRadius = 24.dp, contentPadding = 22.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Column {
                            Text(
                                text = "Loading Web Page...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = (state as? BrowserReaderUiState.Loading)?.targetUrl ?: "Preparing SaveBox Safe Browser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        is BrowserReaderUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(cornerRadius = 26.dp, contentPadding = 22.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Unable to Open Page",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(onClick = onOpenExternal, modifier = Modifier.weight(1f)) {
                                Text("System Browser", maxLines = 1)
                            }
                            Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                                Text("Retry", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = { onAnalyzePage(state.targetUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Media Directly")
                        }
                    }
                }
            }
        }

        is BrowserReaderUiState.Loaded -> {
            val page: BrowserPageSnapshot = state.snapshot
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "page_header") {
                    GlassCard(cornerRadius = 22.dp, contentPadding = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MediaSourceBadgeChip(label = page.host)
                            if (page.isSearchPage) {
                                Text(
                                    text = "Google & Web Results",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (page.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = page.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (page.paragraphs.isNotEmpty()) {
                    item(key = "page_summary") {
                        GlassCard(cornerRadius = 20.dp, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                page.paragraphs.take(6).forEach { para ->
                                    Text(
                                        text = para,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (page.links.isNotEmpty()) {
                    item(key = "links_heading") {
                        Text(
                            text = if (page.isSearchPage) "Search Results" else "Links & Resources on Page",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    items(page.links, key = { it.url }) { link: BrowserWebLink ->
                        LiquidGlassCard(
                            cornerRadius = 18.dp,
                            contentPadding = 14.dp,
                            onClick = { onNavigate(link.url) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = link.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (link.snippet.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = link.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = link.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onAnalyzePage(link.url) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Fetch media from ${link.title}",
                                        tint = MaterialTheme.colorScheme.primary
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
