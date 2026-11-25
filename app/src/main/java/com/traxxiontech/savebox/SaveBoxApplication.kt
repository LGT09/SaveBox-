package com.traxxiontech.savebox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SaveBoxApplication : Application() {

    companion object {
        const val CHANNEL_DOWNLOADS = "savebox_downloads"
        const val CHANNEL_PLAYBACK = "savebox_playback"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        runCatching {
            com.google.android.gms.ads.MobileAds.initialize(this) {
                com.traxxiontech.savebox.ui.components.AppOpenAdManager.loadAd(this)
            }
        }
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
