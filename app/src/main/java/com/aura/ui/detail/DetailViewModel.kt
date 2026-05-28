package com.aura.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.local.WatchHistoryDao
import com.aura.data.local.WatchlistDao
import com.aura.data.local.WatchlistEntity
import com.aura.data.model.*
import com.aura.data.repository.AnimeRepository
import com.aura.data.repository.MovieRepository
import com.aura.data.repository.StreamRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DetailViewModel(
    private val movieRepository: MovieRepository,
    private val animeRepository: AnimeRepository,
    private val streamRepository: StreamRepository,
    private val watchlistDao: WatchlistDao,
    private val watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _detailState = MutableStateFlow<NetworkResult<ContentDetail>>(NetworkResult.Loading)
    val detailState: StateFlow<NetworkResult<ContentDetail>> = _detailState

    private val _streamState = MutableStateFlow<NetworkResult<List<StreamLink>>>(NetworkResult.Error("idle"))
    val streamState: StateFlow<NetworkResult<List<StreamLink>>> = _streamState

    private val _isInWatchlist = MutableStateFlow(false)
    val isInWatchlist: StateFlow<Boolean> = _isInWatchlist

    var currentContentType: ContentType = ContentType.MOVIE
    var currentDetailUrl: String = ""
    var currentNetMirrorId: String = ""

    fun loadDetails(url: String, contentType: ContentType, netMirrorId: String = "") {
        currentDetailUrl = url
        currentContentType = contentType
        currentNetMirrorId = netMirrorId

        viewModelScope.launch {
            when (contentType) {
                ContentType.MOVIE -> {
                    movieRepository.getMovieDetails(url).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> _detailState.value = NetworkResult.Success(
                                ContentDetail.MovieDetail(result.data)
                            )
                            is NetworkResult.Error -> _detailState.value = NetworkResult.Error(result.message)
                            NetworkResult.Loading -> _detailState.value = NetworkResult.Loading
                        }
                    }
                }
                ContentType.ANIME -> {
                    animeRepository.getAnimeDetails(url).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> _detailState.value = NetworkResult.Success(
                                ContentDetail.AnimeDetail(result.data)
                            )
                            is NetworkResult.Error -> _detailState.value = NetworkResult.Error(result.message)
                            NetworkResult.Loading -> _detailState.value = NetworkResult.Loading
                        }
                    }
                }
                ContentType.NET_MIRROR -> {
                    streamRepository.getNetMirrorPost(netMirrorId).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> _detailState.value = NetworkResult.Success(
                                ContentDetail.NetMirrorDetail(result.data)
                            )
                            is NetworkResult.Error -> _detailState.value = NetworkResult.Error(result.message)
                            NetworkResult.Loading -> _detailState.value = NetworkResult.Loading
                        }
                    }
                }
            }
        }

        checkWatchlistStatus(url)
    }

    fun getStreamLinks(episodeUrl: String = currentDetailUrl) {
        viewModelScope.launch {
            when (currentContentType) {
                ContentType.MOVIE, ContentType.NET_MIRROR -> {
                    // Both movie and series now route through our OMDb+vidlink pipeline
                    // episodeUrl is the episode ID (e.g. "movie:tt0848228" or "tv:tt0903747:1:1")
                    // currentNetMirrorId is the bare IMDb ID
                    val streamId = when {
                        episodeUrl.startsWith("movie:") || episodeUrl.startsWith("tv:") -> episodeUrl
                        currentNetMirrorId.startsWith("tt") -> "movie:$currentNetMirrorId"
                        else -> episodeUrl
                    }
                    streamRepository.getNetMirrorStream(streamId).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> _streamState.value = NetworkResult.Success(result.data)
                            is NetworkResult.Error -> _streamState.value = NetworkResult.Error(result.message)
                            NetworkResult.Loading -> _streamState.value = NetworkResult.Loading
                        }
                    }
                }
                ContentType.ANIME -> {
                    animeRepository.getAnimeStream(episodeUrl).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> _streamState.value = NetworkResult.Success(result.data)
                            is NetworkResult.Error -> _streamState.value = NetworkResult.Error(result.message)
                            NetworkResult.Loading -> _streamState.value = NetworkResult.Loading
                        }
                    }
                }
            }
        }
    }

    fun toggleWatchlist(entity: WatchlistEntity) {
        viewModelScope.launch {
            if (_isInWatchlist.value) {
                watchlistDao.deleteById(entity.id)
                _isInWatchlist.value = false
            } else {
                watchlistDao.insert(entity)
                _isInWatchlist.value = true
            }
        }
    }

    private fun checkWatchlistStatus(id: String) {
        viewModelScope.launch {
            _isInWatchlist.value = watchlistDao.isInWatchlist(id)
        }
    }
}

sealed class ContentDetail {
    data class MovieDetail(val data: MovieDetails) : ContentDetail()
    data class AnimeDetail(val data: AnimeDetails) : ContentDetail()
    data class NetMirrorDetail(val data: NetMirrorPostDetails) : ContentDetail()
}

