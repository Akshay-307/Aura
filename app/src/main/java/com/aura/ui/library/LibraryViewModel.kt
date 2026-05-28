package com.aura.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.local.IptvFavouritesDao
import com.aura.data.local.IptvFavouriteEntity
import com.aura.data.local.WatchHistoryDao
import com.aura.data.local.WatchHistoryEntity
import com.aura.data.local.WatchlistDao
import com.aura.data.local.WatchlistEntity
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val watchlistDao: WatchlistDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val iptvFavouritesDao: IptvFavouritesDao
) : ViewModel() {

    val watchlist = watchlistDao.getAllWatchlist()
    val watchHistory = watchHistoryDao.getAllHistory()
    val iptvFavourites = iptvFavouritesDao.getAllFavourites()

    fun removeFromWatchlist(entity: WatchlistEntity) {
        viewModelScope.launch { watchlistDao.deleteById(entity.id) }
    }

    fun removeFromHistory(entity: WatchHistoryEntity) {
        viewModelScope.launch { watchHistoryDao.deleteById(entity.id) }
    }

    fun removeFromIptvFavourites(id: String) {
        viewModelScope.launch { iptvFavouritesDao.deleteById(id) }
    }
}

