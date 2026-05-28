package com.aura.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.datastore.AppDataStore
import com.aura.data.local.WatchHistoryDao
import com.aura.data.local.WatchlistDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: AppDataStore,
    private val watchHistoryDao: WatchHistoryDao,
    private val watchlistDao: WatchlistDao
) : ViewModel() {

    val defaultQuality = dataStore.defaultQuality.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "720p"
    )

    val adultContentEnabled = dataStore.adultContentEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val customIptvUrl = dataStore.customIptvUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val adProvider = dataStore.adProvider.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "startio"
    )


    fun clearWatchHistory() {
        viewModelScope.launch { watchHistoryDao.clearAll() }
    }

    fun clearWatchlist() {
        viewModelScope.launch { watchlistDao.clearAll() }
    }

    fun saveDefaultQuality(quality: String) {
        viewModelScope.launch { dataStore.saveDefaultQuality(quality) }
    }

    fun toggleAdultContent(enabled: Boolean) {
        viewModelScope.launch { dataStore.saveAdultContent(enabled) }
    }

    fun saveCustomIptvUrl(url: String) {
        viewModelScope.launch { dataStore.saveCustomIptvUrl(url) }
    }

    fun saveAdProvider(provider: String) {
        viewModelScope.launch { dataStore.saveAdProvider(provider) }
    }

}

