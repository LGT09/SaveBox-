package com.example.di

import android.content.Context
import com.example.data.download.DownloadRepository
import com.example.data.download.StorageManager
import com.example.data.local.DownloadDao
import com.example.data.local.MediaMetadataDao
import com.example.data.local.PlaylistDao
import com.example.data.local.SaveBoxDatabase
import com.example.data.local.SearchHistoryDao
import com.example.data.local.SettingsDataStore
import com.example.data.status.StatusSaverRepository
import com.example.player.MediaControllerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSaveBoxDatabase(@ApplicationContext context: Context): SaveBoxDatabase {
        return SaveBoxDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: SaveBoxDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideMediaMetadataDao(database: SaveBoxDatabase): MediaMetadataDao = database.mediaMetadataDao()

    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: SaveBoxDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: SaveBoxDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        return StorageManager(context).apply { ensureAllFolders() }
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        downloadDao: DownloadDao,
        mediaMetadataDao: MediaMetadataDao,
        searchHistoryDao: SearchHistoryDao,
        playlistDao: PlaylistDao,
        storageManager: StorageManager
    ): DownloadRepository {
        return DownloadRepository(
            context = context,
            downloadDao = downloadDao,
            mediaMetadataDao = mediaMetadataDao,
            searchHistoryDao = searchHistoryDao,
            playlistDao = playlistDao,
            storageManager = storageManager
        )
    }

    @Provides
    @Singleton
    fun provideStatusSaverRepository(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
        downloadDao: DownloadDao
    ): StatusSaverRepository {
        return StatusSaverRepository(
            context = context,
            storageManager = storageManager,
            downloadDao = downloadDao
        )
    }

    @Provides
    @Singleton
    fun provideMediaControllerManager(
        @ApplicationContext context: Context,
        downloadRepository: DownloadRepository
    ): MediaControllerManager {
        return MediaControllerManager(
            context = context,
            onPositionSaved = { id, pos, inc ->
                downloadRepository.updatePlaybackPosition(id, pos, inc)
            }
        )
    }
}
