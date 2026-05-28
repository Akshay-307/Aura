package com.aura.data.local

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlist(): LiveData<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun getAllWatchlistSync(): List<WatchlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id)")
    suspend fun isInWatchlist(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id)")
    fun isInWatchlistLive(id: String): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchlistEntity)

    @Delete
    suspend fun delete(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllHistory(): LiveData<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 20): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchHistoryEntity)

    @Query("UPDATE watch_history SET lastPosition = :position, watchedAt = :watchedAt WHERE id = :id")
    suspend fun updatePosition(id: String, position: Long, watchedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun getSearchHistory(): LiveData<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    suspend fun getSearchHistorySync(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface IptvFavouritesDao {

    @Query("SELECT * FROM iptv_favourites ORDER BY addedAt DESC")
    fun getAllFavourites(): LiveData<List<IptvFavouriteEntity>>

    @Query("SELECT * FROM iptv_favourites ORDER BY addedAt DESC")
    suspend fun getAllFavouritesSync(): List<IptvFavouriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM iptv_favourites WHERE id = :id)")
    suspend fun isFavourite(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM iptv_favourites WHERE id = :id)")
    fun isFavouriteLive(id: String): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IptvFavouriteEntity)

    @Query("DELETE FROM iptv_favourites WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM iptv_favourites")
    suspend fun clearAll()
}

