package com.traxxiontech.savebox.ui.components

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

object SaveBoxAdUnits {
    const val APP_ID = "ca-app-pub-4601877857713565~1719613958"
    const val NATIVE_ADVANCED_ID = "ca-app-pub-4601877857713565/6695453519"
    const val BANNER_ID = "ca-app-pub-4601877857713565/6252600380"
    const val APP_OPEN_ID = "ca-app-pub-4601877857713565/8831817213"
    const val APP_ADS_TXT_LINE = "google.com, pub-4601877857713565, DIRECT, f08c47fec0942fa0"
}

object AppOpenAdManager {
    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var lastLoadedAtMs = 0L
    private var lastShownAtMs = 0L

    fun loadAd(context: Context) {
        if (isLoading || isAdAvailable()) return
        isLoading = true
        runCatching {
            val request = AdRequest.Builder().build()
            AppOpenAd.load(
                context.applicationContext,
                SaveBoxAdUnits.APP_OPEN_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoading = false
                        lastLoadedAtMs = System.currentTimeMillis()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoading = false
                    }
                }
            )
        }.onFailure {
            isLoading = false
        }
    }

    private fun isAdAvailable(): Boolean {
        appOpenAd ?: return false
        val ageMs = System.currentTimeMillis() - lastLoadedAtMs
        return ageMs < 4 * 60 * 60_000L
    }

    fun showAdIfAvailable(activity: Activity) {
        if (isShowing) return
        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < 3 * 60_000L) return

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            loadAd(activity)
            return
        }

        isShowing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowing = false
                lastShownAtMs = System.currentTimeMillis()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                appOpenAd = null
                isShowing = false
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                lastShownAtMs = System.currentTimeMillis()
            }
        }
        runCatching { ad.show(activity) }.onFailure {
            isShowing = false
            appOpenAd = null
        }
    }
}

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    adUnitId: String = SaveBoxAdUnits.BANNER_ID,
    compact: Boolean = false
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 4.dp else 12.dp,
                vertical = if (compact) 2.dp else 6.dp
            ),
        cornerRadius = if (compact) 14.dp else 18.dp,
        contentPadding = if (compact) 4.dp else 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        runCatching { loadAd(AdRequest.Builder().build()) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }
    }
}

/**
 * Customized Player Ad Dock shown cleanly during video or audio playback.
 */
@Composable
fun PlayerSponsoredAdSection(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_sponsored_ad_section"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sponsored Playback Partner",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hide ad strip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            AndroidView(
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = SaveBoxAdUnits.BANNER_ID
                        runCatching { loadAd(AdRequest.Builder().build()) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }
    }
}

/**
 * Loads an AdMob Native Advanced Ad (ca-app-pub-4601877857713565/6695453519)
 * styled to blend into SaveBox's dim glass aesthetic.
 */
@Composable
fun NativeAdvancedAdSlot(
    modifier: Modifier = Modifier,
    adUnitId: String = SaveBoxAdUnits.NATIVE_ADVANCED_ID
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    DisposableEffect(adUnitId) {
        var activeAd: NativeAd? = null
        runCatching {
            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    activeAd?.destroy()
                    activeAd = ad
                    nativeAd = ad
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadFailed = true
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()
            loader.loadAd(AdRequest.Builder().build())
        }.onFailure {
            loadFailed = true
        }
        onDispose {
            activeAd?.destroy()
            nativeAd = null
        }
    }

    val loaded = nativeAd
    if (loaded != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            AndroidView(
                factory = { ctx ->
                    NativeAdView(ctx).apply {
                        val container = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(24, 20, 24, 20)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        val badge = TextView(ctx).apply {
                            text = "AD • SPONSORED"
                            textSize = 10f
                            setTextColor(textSecondaryColor)
                        }
                        val headline = TextView(ctx).apply {
                            textSize = 15f
                            setTextColor(textPrimaryColor)
                            setPadding(0, 6, 0, 4)
                        }
                        val body = TextView(ctx).apply {
                            textSize = 12f
                            setTextColor(textSecondaryColor)
                            setPadding(0, 0, 0, 12)
                        }
                        val cta = Button(ctx).apply {
                            gravity = Gravity.CENTER
                            setBackgroundColor(primaryColor)
                            setTextColor(onPrimaryColor)
                        }
                        container.addView(badge)
                        container.addView(headline)
                        container.addView(body)
                        container.addView(cta)
                        addView(container)
                        this.headlineView = headline
                        this.bodyView = body
                        this.callToActionView = cta
                    }
                },
                update = { adView ->
                    (adView.headlineView as? TextView)?.text = loaded.headline ?: "Sponsored Partner"
                    (adView.bodyView as? TextView)?.text = loaded.body ?: "Supports free SaveBox media downloads."
                    val ctaBtn = adView.callToActionView as? Button
                    if (loaded.callToAction != null) {
                        ctaBtn?.text = loaded.callToAction
                        ctaBtn?.visibility = android.view.View.VISIBLE
                    } else {
                        ctaBtn?.visibility = android.view.View.GONE
                    }
                    adView.setNativeAd(loaded)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else if (loadFailed) {
        // Fallback banner when native unit isn't filled in test/offline mode
        AdBannerView(modifier = modifier, adUnitId = SaveBoxAdUnits.BANNER_ID, compact = true)
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading sponsored pass preview...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RewardedDownloadPassDialog(
    onDismiss: () -> Unit,
    onRewardGranted: () -> Unit
) {
    var adFinished by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4500, easing = LinearEasing)
        )
        adFinished = true
    }

    Dialog(onDismissRequest = { if (adFinished) onDismiss() }) {
        GlassCard(
            cornerRadius = 26.dp,
            contentPadding = 22.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rewarded_download_pass_dialog")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sponsored Download Pass",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "View this brief sponsored slot to unlock 60 minutes of unlimited media downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

                NativeAdvancedAdSlot(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (adFinished) {
                    Button(
                        onClick = {
                            onRewardGranted()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("claim_download_pass_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Unlock 60-Min Pass & Download", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val secsLeft = ((1f - progress.value) * 4.5f).toInt() + 1
                        Text(
                            text = "Granting download time in ${secsLeft}s...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
