package com.traxxiontech.savebox.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadEntity::class,
        MediaItem::class,
        DownloadQueue::class,
        MediaMetadataEntity::class,
        SearchHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        FavoriteEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class SaveBoxDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        @Volatile
        private var INSTANCE: SaveBoxDatabase? = null

        fun getInstance(context: Context): SaveBoxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaveBoxDatabase::class.java,
                    "savebox_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
