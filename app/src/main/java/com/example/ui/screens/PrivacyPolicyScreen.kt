package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AnimatedAuroraBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SaveBoxOrbitalLogo
import java.text.DateFormat
import java.util.Date

private data class PolicySection(
    val title: String,
    val body: String
)

private val SaveBoxPolicySections = listOf(
    PolicySection(
        title = "1. Local-First Data & Zero Third-Party Tracking",
        body = "SaveBox operates with a strict local-first architecture. Your download history, saved media file paths, playback timestamps, playlists, and search history are stored exclusively on your device inside an encrypted-at-rest Android Room database and Jetpack DataStore preferences. SaveBox does not run third-party analytics SDKs, advertising trackers, or sell user data."
    ),
    PolicySection(
        title = "2. User-Initiated Network Requests",
        body = "SaveBox connects to the internet only when you explicitly paste a URL to inspect media metadata, run a search query, or open websites in the built-in browser. All requests use HTTPS transport security (android:usesCleartextTraffic=\"false\") and fetch only the media streams or pages you request."
    ),
    PolicySection(
        title = "3. Minimal Permissions & Play Store Data Safety",
        body = "• INTERNET & ACCESS_NETWORK_STATE: Required to fetch URLs, search results, and download media files.\n" +
            "• FOREGROUND_SERVICE (dataSync & mediaPlayback): Ensures WorkManager downloads and Media3 audio playback continue reliably when the app moves to the background.\n" +
            "• POST_NOTIFICATIONS (Android 13+): Optional permission to display active download progress and media player controls.\n" +
            "• Zero Broad Storage Permissions: SaveBox never requests READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, or READ_MEDIA_VIDEO. Files are stored in app-scoped storage, exported via MediaStore, or accessed from user-selected folders via Android's Storage Access Framework (SAF)."
    ),
    PolicySection(
        title = "4. Responsible Use & Intellectual Property",
        body = "SaveBox is a media management, inspection, and offline playback tool. You agree to download and store only media content that you own, created, are authorized to access, or have explicit permission from the rights holder to save, and to comply with applicable copyright laws and website terms of use."
    ),
    PolicySection(
        title = "5. Data Control, Retention & Deletion",
        body = "You have full control over your local data at all times. You can delete individual downloads, clear search and queue history, clear cached files from Settings, or uninstall SaveBox to permanently erase all application data stored on your device."
    ),
    PolicySection(
        title = "6. Children's Privacy & Developer Contact",
        body = "SaveBox does not knowingly collect personal information from children under 13 (or the applicable age in your jurisdiction). For privacy inquiries, copyright notices, or support, contact:\n" +
            "Developer: Vincent Ganiza — Traxxion Tech (Zimbabwe)\n" +
            "Email: ceo@stinger.co.zw | Website: https://traxxiontech.com"
    )
)

/**
 * First-launch mandatory Privacy Policy consent gate shown before entering SaveBox.
 */
@Composable
fun PrivacyPolicyGateScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }

    AnimatedAuroraBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SaveBoxOrbitalLogo(size = 54.dp, isAnimating = true)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Policy & Terms",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Please read and accept before using SaveBox",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Policy Card
                LiquidGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    cornerRadius = 24.dp,
                    contentPadding = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Effective Date: 2026 • Policy v1.0",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        SaveBoxPolicySections.forEach { section ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = section.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Agreement Checkbox & Action Buttons
                LiquidGlassCard(cornerRadius = 20.dp, contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { agreed = !agreed }
                            .testTag("privacy_accept_checkbox_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = agreed,
                            onCheckedChange = { agreed = it },
                            modifier = Modifier.testTag("privacy_accept_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I have read and accept the SaveBox Privacy Policy and Responsible Use Terms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("privacy_decline_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Decline")
                        }

                        Button(
                            onClick = onAccept,
                            enabled = agreed,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .testTag("privacy_accept_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accept & Continue", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated in-app Privacy Policy section accessible from Settings & Navigation.
 */
@Composable
fun PrivacyPolicyScreen(
    acceptedAt: Long,
    policyVersion: String,
    onBack: () -> Unit
) {
    val acceptedDateLabel = remember(acceptedAt) {
        if (acceptedAt > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(acceptedAt))
        } else {
            "Pending"
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(
                    imageVector = Icons.Filled.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version $policyVersion • Accepted: $acceptedDateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaveBoxPolicySections.forEach { section ->
                    LiquidGlassCard(cornerRadius = 20.dp, contentPadding = 16.dp) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
