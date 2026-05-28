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

class HomeViewModel(
    private val streamRepository: StreamRepository,
    private val dataStore: AppDataStore
) : ViewModel() {

    private val _trendingState = MutableStateFlow<NetworkResult<List<NetMirrorPost>>>(NetworkResult.Loading)
    val trendingState: StateFlow<NetworkResult<List<NetMirrorPost>>> = _trendingState

    private val _latestMoviesState = MutableStateFlow<NetworkResult<List<NetMirrorPost>>>(NetworkResult.Loading)
    val latestMoviesState: StateFlow<NetworkResult<List<NetMirrorPost>>> = _latestMoviesState

    private val _latestSeriesState = MutableStateFlow<NetworkResult<List<NetMirrorPost>>>(NetworkResult.Loading)
    val latestSeriesState: StateFlow<NetworkResult<List<NetMirrorPost>>> = _latestSeriesState

    private val _latestAnimeState = MutableStateFlow<NetworkResult<List<NetMirrorPost>>>(NetworkResult.Loading)
    val latestAnimeState: StateFlow<NetworkResult<List<NetMirrorPost>>> = _latestAnimeState

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            val includeAdult = try { dataStore.adultContentEnabled.first() } catch (_: Exception) { false }
            loadTrending(includeAdult)
            loadLatestMovies(includeAdult)
            loadLatestSeries(includeAdult)
            loadLatestAnime(includeAdult)
        }
    }

    private fun loadTrending(includeAdult: Boolean = false) {
        viewModelScope.launch {
            streamRepository.getNetMirrorHome(includeAdult).collect { result ->
                _trendingState.value = when (result) {
                    is NetworkResult.Success -> NetworkResult.Success(result.data)
                    is NetworkResult.Error   -> NetworkResult.Error(result.message)
                    NetworkResult.Loading    -> NetworkResult.Loading
                }
            }
        }
    }

    private fun loadLatestMovies(includeAdult: Boolean = false) {
        viewModelScope.launch {
            streamRepository.getLatestMovies(includeAdult).collect { result ->
                _latestMoviesState.value = when (result) {
                    is NetworkResult.Success -> NetworkResult.Success(result.data)
                    is NetworkResult.Error   -> NetworkResult.Error(result.message)
                    NetworkResult.Loading    -> NetworkResult.Loading
                }
            }
        }
    }

    private fun loadLatestSeries(includeAdult: Boolean = false) {
        viewModelScope.launch {
            streamRepository.getLatestSeries(includeAdult).collect { result ->
                _latestSeriesState.value = when (result) {
                    is NetworkResult.Success -> NetworkResult.Success(result.data)
                    is NetworkResult.Error   -> NetworkResult.Error(result.message)
                    NetworkResult.Loading    -> NetworkResult.Loading
                }
            }
        }
    }

    private fun loadLatestAnime(includeAdult: Boolean = false) {
        viewModelScope.launch {
            streamRepository.getLatestAnimeTmdb(includeAdult).collect { result ->
                _latestAnimeState.value = when (result) {
                    is NetworkResult.Success -> NetworkResult.Success(result.data)
                    is NetworkResult.Error   -> NetworkResult.Error(result.message)
                    NetworkResult.Loading    -> NetworkResult.Loading
                }
            }
        }
    }
}

