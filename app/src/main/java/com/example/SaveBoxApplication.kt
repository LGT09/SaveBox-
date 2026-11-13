package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.download.DownloadRepository
import com.example.data.download.StorageManager
import com.example.data.local.SaveBoxDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.SaveBoxApiManager
import com.example.data.status.StatusSaverRepository
import com.example.player.MediaControllerManager

class SaveBoxApplication : Application() {

    companion object {
        const val CHANNEL_DOWNLOADS = "savebox_downloads"
        const val CHANNEL_PLAYBACK = "savebox_playback"
    }

    lateinit var database: SaveBoxDatabase
        private set
    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var apiManager: SaveBoxApiManager
        private set
    lateinit var storageManager: StorageManager
        private set
    lateinit var downloadRepository: DownloadRepository
        private set
    lateinit var statusSaverRepository: StatusSaverRepository
        private set
    lateinit var mediaControllerManager: MediaControllerManager
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        database = SaveBoxDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        apiManager = SaveBoxApiManager()
        storageManager = StorageManager(this).apply { ensureAllFolders() }

        downloadRepository = DownloadRepository(
            context = this,
            downloadDao = database.downloadDao(),
            searchHistoryDao = database.searchHistoryDao(),
            playlistDao = database.playlistDao(),
            storageManager = storageManager
        )

        statusSaverRepository = StatusSaverRepository(
            context = this,
            storageManager = storageManager,
            downloadDao = database.downloadDao()
        )

        mediaControllerManager = MediaControllerManager(
            context = this,
            onPositionSaved = { id, pos, inc ->
                downloadRepository.updatePlaybackPosition(id, pos, inc)
            }
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_desc)
            }
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_desc)
            }
            manager.createNotificationChannels(listOf(downloadChannel, playbackChannel))
        }
    }
}
