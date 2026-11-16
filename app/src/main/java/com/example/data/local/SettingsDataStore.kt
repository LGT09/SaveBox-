package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.AppThemePalette
import com.example.model.VisualizerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.saveBoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "savebox_prefs")

data class UserSettings(
    val isLoaded: Boolean = false,
    val hasAcceptedPrivacyPolicy: Boolean = false,
    val privacyPolicyAcceptedAt: Long = 0L,
    val privacyPolicyVersion: String = "1.0",
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val themePalette: AppThemePalette = AppThemePalette.OBSIDIAN_CYAN,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.SPECTRUM_BARS,
    val dynamicColor: Boolean = false,
    val defaultQuality: String = "Best Available",
    val simultaneousDownloads: Int = 3,
    val notificationsEnabled: Boolean = true,
    val autoClipboardDetect: Boolean = true,
    val autoResumePlayback: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val language: String = "English",
    val customSubfolder: String = "Auto by Type",
    val customFolderTreeUri: String? = null,
    val secondaryFolderTreeUri: String? = null,
    val equalizerPresetIndex: Int = 0
)

class SettingsDataStore(private val context: Context) {

    companion object {
        const val CURRENT_POLICY_VERSION = "1.0"
    }

    private object Keys {
        val HAS_ACCEPTED_PRIVACY = booleanPreferencesKey("has_accepted_privacy_policy")
        val PRIVACY_ACCEPTED_AT = longPreferencesKey("privacy_policy_accepted_at")
        val PRIVACY_VERSION = stringPreferencesKey("privacy_policy_version")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val VISUALIZER_STYLE = stringPreferencesKey("visualizer_style")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneous_downloads")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_CLIPBOARD_DETECT = booleanPreferencesKey("auto_clipboard_detect")
        val AUTO_RESUME_PLAYBACK = booleanPreferencesKey("auto_resume_playback")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val LANGUAGE = stringPreferencesKey("language")
        val CUSTOM_SUBFOLDER = stringPreferencesKey("custom_subfolder")
        val CUSTOM_FOLDER_TREE_URI = stringPreferencesKey("custom_folder_tree_uri")
        val SECONDARY_FOLDER_TREE_URI = stringPreferencesKey("secondary_folder_tree_uri")
        val EQ_PRESET_INDEX = intPreferencesKey("eq_preset_index")
    }

    val settingsFlow: Flow<UserSettings> = context.saveBoxDataStore.data.map { prefs ->
        UserSettings(
            isLoaded = true,
            hasAcceptedPrivacyPolicy = prefs[Keys.HAS_ACCEPTED_PRIVACY] ?: false,
            privacyPolicyAcceptedAt = prefs[Keys.PRIVACY_ACCEPTED_AT] ?: 0L,
            privacyPolicyVersion = prefs[Keys.PRIVACY_VERSION] ?: CURRENT_POLICY_VERSION,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                AppThemeMode.entries.firstOrNull { it.name == name }
            } ?: AppThemeMode.DARK,
            themePalette = prefs[Keys.THEME_PALETTE]?.let { name ->
                AppThemePalette.entries.firstOrNull { it.name == name }
            } ?: AppThemePalette.OBSIDIAN_CYAN,
            visualizerStyle = prefs[Keys.VISUALIZER_STYLE]?.let { name ->
                VisualizerStyle.entries.firstOrNull { it.name == name }
            } ?: VisualizerStyle.SPECTRUM_BARS,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Best Available",
            simultaneousDownloads = prefs[Keys.SIMULTANEOUS_DOWNLOADS] ?: 3,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            autoClipboardDetect = prefs[Keys.AUTO_CLIPBOARD_DETECT] ?: true,
            autoResumePlayback = prefs[Keys.AUTO_RESUME_PLAYBACK] ?: true,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
            language = prefs[Keys.LANGUAGE] ?: "English",
            customSubfolder = prefs[Keys.CUSTOM_SUBFOLDER] ?: "Auto by Type",
            customFolderTreeUri = prefs[Keys.CUSTOM_FOLDER_TREE_URI],
            secondaryFolderTreeUri = prefs[Keys.SECONDARY_FOLDER_TREE_URI],
            equalizerPresetIndex = prefs[Keys.EQ_PRESET_INDEX] ?: 0
        )
    }

    suspend fun acceptPrivacyPolicy() {
        context.saveBoxDataStore.edit {
            it[Keys.HAS_ACCEPTED_PRIVACY] = true
            it[Keys.PRIVACY_ACCEPTED_AT] = System.currentTimeMillis()
            it[Keys.PRIVACY_VERSION] = CURRENT_POLICY_VERSION
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.saveBoxDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: AppThemePalette) {
        context.saveBoxDataStore.edit { it[Keys.THEME_PALETTE] = palette.name }
    }

    suspend fun setVisualizerStyle(style: VisualizerStyle) {
        context.saveBoxDataStore.edit { it[Keys.VISUALIZER_STYLE] = style.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.saveBoxDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.saveBoxDataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    suspend fun setSimultaneousDownloads(count: Int) {
        context.saveBoxDataStore.edit { it[Keys.SIMULTANEOUS_DOWNLOADS] = count.coerceIn(1, 5) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.saveBoxDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoClipboardDetect(enabled: Boolean) {
        context.saveBoxDataStore.edit { it[Keys.AUTO_CLIPBOARD_DETECT] = enabled }
    }

    suspend fun setAutoResumePlayback(enabled: Boolean) {
        context.saveBoxDataStore.edit { it[Keys.AUTO_RESUME_PLAYBACK] = enabled }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.saveBoxDataStore.edit { it[Keys.DEFAULT_PLAYBACK_SPEED] = speed }
    }

    suspend fun setLanguage(language: String) {
        context.saveBoxDataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setCustomSubfolder(folder: String) {
        context.saveBoxDataStore.edit { it[Keys.CUSTOM_SUBFOLDER] = folder }
    }

    suspend fun setCustomFolderTreeUri(uri: String?) {
        context.saveBoxDataStore.edit {
            if (uri == null) it.remove(Keys.CUSTOM_FOLDER_TREE_URI) else it[Keys.CUSTOM_FOLDER_TREE_URI] = uri
        }
    }

    suspend fun setSecondaryFolderTreeUri(uri: String?) {
        context.saveBoxDataStore.edit {
            if (uri == null) it.remove(Keys.SECONDARY_FOLDER_TREE_URI) else it[Keys.SECONDARY_FOLDER_TREE_URI] = uri
        }
    }

    suspend fun setEqualizerPreset(index: Int) {
        context.saveBoxDataStore.edit { it[Keys.EQ_PRESET_INDEX] = index }
    }
}
