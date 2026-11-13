package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.saveBoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "savebox_prefs")

data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val defaultQuality: String = "Best Available",
    val simultaneousDownloads: Int = 3,
    val notificationsEnabled: Boolean = true,
    val autoClipboardDetect: Boolean = true,
    val autoResumePlayback: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val language: String = "English",
    val customSubfolder: String = "Auto by Platform",
    val whatsAppTreeUri: String? = null,
    val whatsAppBusinessTreeUri: String? = null,
    val equalizerPresetIndex: Int = 0
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneous_downloads")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_CLIPBOARD_DETECT = booleanPreferencesKey("auto_clipboard_detect")
        val AUTO_RESUME_PLAYBACK = booleanPreferencesKey("auto_resume_playback")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val LANGUAGE = stringPreferencesKey("language")
        val CUSTOM_SUBFOLDER = stringPreferencesKey("custom_subfolder")
        val WHATSAPP_TREE_URI = stringPreferencesKey("whatsapp_tree_uri")
        val WHATSAPP_BUSINESS_TREE_URI = stringPreferencesKey("whatsapp_business_tree_uri")
        val EQ_PRESET_INDEX = intPreferencesKey("eq_preset_index")
    }

    val settingsFlow: Flow<UserSettings> = context.saveBoxDataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                AppThemeMode.entries.firstOrNull { it.name == name }
            } ?: AppThemeMode.DARK,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Best Available",
            simultaneousDownloads = prefs[Keys.SIMULTANEOUS_DOWNLOADS] ?: 3,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            autoClipboardDetect = prefs[Keys.AUTO_CLIPBOARD_DETECT] ?: true,
            autoResumePlayback = prefs[Keys.AUTO_RESUME_PLAYBACK] ?: true,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
            language = prefs[Keys.LANGUAGE] ?: "English",
            customSubfolder = prefs[Keys.CUSTOM_SUBFOLDER] ?: "Auto by Platform",
            whatsAppTreeUri = prefs[Keys.WHATSAPP_TREE_URI],
            whatsAppBusinessTreeUri = prefs[Keys.WHATSAPP_BUSINESS_TREE_URI],
            equalizerPresetIndex = prefs[Keys.EQ_PRESET_INDEX] ?: 0
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.saveBoxDataStore.edit { it[Keys.THEME_MODE] = mode.name }
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

    suspend fun setWhatsAppTreeUri(uri: String?) {
        context.saveBoxDataStore.edit {
            if (uri == null) it.remove(Keys.WHATSAPP_TREE_URI) else it[Keys.WHATSAPP_TREE_URI] = uri
        }
    }

    suspend fun setWhatsAppBusinessTreeUri(uri: String?) {
        context.saveBoxDataStore.edit {
            if (uri == null) it.remove(Keys.WHATSAPP_BUSINESS_TREE_URI) else it[Keys.WHATSAPP_BUSINESS_TREE_URI] = uri
        }
    }

    suspend fun setEqualizerPreset(index: Int) {
        context.saveBoxDataStore.edit { it[Keys.EQ_PRESET_INDEX] = index }
    }
}
