package com.aura.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.aura.data.model.ContentType

@Database(
    entities = [WatchlistEntity::class, WatchHistoryEntity::class, SearchHistoryEntity::class, IptvFavouriteEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun iptvFavouritesDao(): IptvFavouritesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "Aura.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AppTypeConverters {
    @TypeConverter
    fun fromContentType(type: ContentType): String = type.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)
}

