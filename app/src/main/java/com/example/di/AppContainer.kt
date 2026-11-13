package com.example.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.download.DownloadRepository
import com.example.data.download.StorageManager
import com.example.data.local.SaveBoxDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.MediaApiService
import com.example.data.remote.SaveBoxApiManager
import com.example.data.status.StatusSaverRepository
import com.example.player.MediaControllerManager
import com.example.ui.viewmodel.MainViewModel
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface AppContainer {
    val okHttpClient: OkHttpClient
    val retrofit: Retrofit
    val mediaApiService: MediaApiService
    val apiManager: SaveBoxApiManager
    val database: SaveBoxDatabase
    val settingsDataStore: SettingsDataStore
    val storageManager: StorageManager
    val downloadRepository: DownloadRepository
    val statusSaverRepository: StatusSaverRepository
    val mediaControllerManager: MediaControllerManager
    val viewModelFactory: ViewModelProvider.Factory
}

class DefaultAppContainer(private val application: Application) : AppContainer {

    companion object {
        const val BASE_URL = "https://nayan-video-downloader.vercel.app/"
    }

    private val appContext: Context = application.applicationContext

    override val okHttpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(logger)
            .build()
    }

    override val retrofit: Retrofit by lazy {
        val gson = GsonBuilder().create()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    override val mediaApiService: MediaApiService by lazy {
        retrofit.create(MediaApiService::class.java)
    }

    override val apiManager: SaveBoxApiManager by lazy {
        SaveBoxApiManager(
            okHttpClient = okHttpClient,
            apiService = mediaApiService
        )
    }

    override val database: SaveBoxDatabase by lazy {
        SaveBoxDatabase.getInstance(appContext)
    }

    override val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(appContext)
    }

    override val storageManager: StorageManager by lazy {
        StorageManager(appContext).apply { ensureAllFolders() }
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            context = appContext,
            downloadDao = database.downloadDao(),
            mediaMetadataDao = database.mediaMetadataDao(),
            searchHistoryDao = database.searchHistoryDao(),
            playlistDao = database.playlistDao(),
            storageManager = storageManager
        )
    }

    override val statusSaverRepository: StatusSaverRepository by lazy {
        StatusSaverRepository(
            context = appContext,
            storageManager = storageManager,
            downloadDao = database.downloadDao()
        )
    }

    override val mediaControllerManager: MediaControllerManager by lazy {
        MediaControllerManager(
            context = appContext,
            onPositionSaved = { id, pos, inc ->
                downloadRepository.updatePlaybackPosition(id, pos, inc)
            }
        )
    }

    override val viewModelFactory: ViewModelProvider.Factory by lazy {
        SaveBoxViewModelFactory(application, this)
    }
}

class SaveBoxViewModelFactory(
    private val application: Application,
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                application = application,
                apiManager = container.apiManager,
                mediaApiService = container.mediaApiService,
                downloadRepo = container.downloadRepository,
                storageManager = container.storageManager,
                statusRepo = container.statusSaverRepository,
                settingsStore = container.settingsDataStore,
                mediaControllerManager = container.mediaControllerManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
