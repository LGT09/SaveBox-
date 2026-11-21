package com.traxxiontech.savebox.di

import android.content.Context
import com.traxxiontech.savebox.data.download.DownloadRepository
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.DownloadDao
import com.traxxiontech.savebox.data.local.DownloadQueueDao
import com.traxxiontech.savebox.data.local.MediaItemDao
import com.traxxiontech.savebox.data.local.MediaMetadataDao
import com.traxxiontech.savebox.data.local.PlaylistDao
import com.traxxiontech.savebox.data.local.SaveBoxDatabase
import com.traxxiontech.savebox.data.local.SearchHistoryDao
import com.traxxiontech.savebox.data.local.SettingsDataStore
import com.traxxiontech.savebox.data.status.LocalMediaScanner
import com.traxxiontech.savebox.data.status.StatusSaverRepository
import com.traxxiontech.savebox.player.MediaControllerManager
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
    fun provideMediaItemDao(database: SaveBoxDatabase): MediaItemDao = database.mediaItemDao()

    @Provides
    @Singleton
    fun provideDownloadQueueDao(database: SaveBoxDatabase): DownloadQueueDao = database.downloadQueueDao()

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
        mediaItemDao: MediaItemDao,
        downloadQueueDao: DownloadQueueDao,
        mediaMetadataDao: MediaMetadataDao,
        searchHistoryDao: SearchHistoryDao,
        playlistDao: PlaylistDao,
        storageManager: StorageManager
    ): DownloadRepository {
        return DownloadRepository(
            context = context,
            downloadDao = downloadDao,
            mediaItemDao = mediaItemDao,
            downloadQueueDao = downloadQueueDao,
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
    fun provideLocalMediaScanner(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
        downloadDao: DownloadDao,
        mediaItemDao: MediaItemDao
    ): LocalMediaScanner {
        return LocalMediaScanner(
            context = context,
            storageManager = storageManager,
            downloadDao = downloadDao,
            mediaItemDao = mediaItemDao
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
