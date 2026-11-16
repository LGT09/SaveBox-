package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.PrivacyPolicyGateScreen
import com.example.ui.theme.SaveBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readStringFromContext_returnsSaveBox() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SaveBox", appName)
    }

    @Test
    fun privacyPolicyGate_requiresAgreementBeforeAccepting() {
        var accepted = false
        composeRule.setContent {
            SaveBoxTheme {
                PrivacyPolicyGateScreen(
                    onAccept = { accepted = true },
                    onDecline = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("privacy_accept_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("privacy_accept_checkbox").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("privacy_accept_button").assertIsEnabled()
        composeRule.onNodeWithTag("privacy_accept_button").performClick()
        composeRule.waitForIdle()

        assertTrue(accepted)
    }
}
