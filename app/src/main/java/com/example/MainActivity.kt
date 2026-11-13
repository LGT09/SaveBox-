package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.SaveBoxRootShell
import com.example.ui.theme.SaveBoxTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var incomingSharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedUrl(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            SaveBoxTheme(
                themeMode = settings.themeMode,
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
