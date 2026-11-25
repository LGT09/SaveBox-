package com.traxxiontech.savebox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traxxiontech.savebox.ui.navigation.SaveBoxRootShell
import com.traxxiontech.savebox.ui.theme.SaveBoxTheme
import com.traxxiontech.savebox.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var incomingSharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedUrl(intent)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            SaveBoxTheme(
                themeMode = settings.themeMode,
                themePalette = settings.themePalette,
                dynamicColor = settings.dynamicColor
            ) {
                SaveBoxRootShell(
                    viewModel = viewModel,
                    sharedUrlFromIntent = incomingSharedUrl,
                    onConsumedSharedUrl = { incomingSharedUrl = null }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.traxxiontech.savebox.ui.components.AppOpenAdManager.showAdIfAvailable(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrl(intent)
    }

    private fun extractSharedUrl(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                val urlMatch = Regex("""https?://\S+""").find(sharedText)?.value ?: sharedText
                incomingSharedUrl = urlMatch
            }
        }
    }
}
