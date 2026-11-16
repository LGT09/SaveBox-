package com.example.di

import com.example.data.remote.InAppBrowserEngine
import com.example.data.remote.UrlMediaBot
import com.example.data.remote.YouTubeSearchEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    fun provideUrlMediaBot(okHttpClient: OkHttpClient): UrlMediaBot {
        return UrlMediaBot(okHttpClient = okHttpClient)
    }

    @Provides
    @Singleton
    fun provideYouTubeSearchEngine(okHttpClient: OkHttpClient): YouTubeSearchEngine {
        return YouTubeSearchEngine(okHttpClient = okHttpClient)
    }

    @Provides
    @Singleton
    fun provideInAppBrowserEngine(okHttpClient: OkHttpClient): InAppBrowserEngine {
        return InAppBrowserEngine(okHttpClient = okHttpClient)
    }
}
