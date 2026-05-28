package com.aura.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aura.data.model.ContentType

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val posterUrl: String,
    val detailUrl: String,
    val contentType: ContentType,
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val netMirrorId: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val id: String,             // detailUrl as unique ID
    val title: String,
    val posterUrl: String,
    val detailUrl: String,
    val contentType: ContentType,
    val netMirrorId: String = "",
    val lastPosition: Long = 0L, // milliseconds
    val duration: Long = 0L,
    val watchedAt: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (duration > 0) ((lastPosition.toFloat() / duration) * 100).toInt() else 0
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "iptv_favourites")
data class IptvFavouriteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)

