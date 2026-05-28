package com.aura.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.datastore.AppDataStore
import com.aura.data.model.NetMirrorPost
import com.aura.data.repository.StreamRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SectionDetailViewModel(
    private val streamRepository: StreamRepository,
    private val dataStore: AppDataStore
) : ViewModel() {

    private val _itemsState = MutableStateFlow<NetworkResult<List<NetMirrorPost>>>(NetworkResult.Loading)
    val itemsState: StateFlow<NetworkResult<List<NetMirrorPost>>> = _itemsState

    fun loadSection(sectionType: String) {
        viewModelScope.launch {
            _itemsState.value = NetworkResult.Loading
            val includeAdult = try { dataStore.adultContentEnabled.first() } catch (_: Exception) { false }
            
            val flow = when (sectionType) {
                "latest_movies" -> streamRepository.getLatestMovies(includeAdult)
                "popular_series" -> streamRepository.getLatestSeries(includeAdult)
                "latest_anime" -> streamRepository.getLatestAnimeTmdb(includeAdult)
                "trending" -> streamRepository.getNetMirrorHome(includeAdult)
                else -> null
            }

            if (flow != null) {
                flow.collect { result ->
                    _itemsState.value = result
                }
            } else {
                _itemsState.value = NetworkResult.Error("Unknown section type: $sectionType")
            }
        }
    }
}

