package com.aura.ui.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.datastore.AppDataStore
import com.aura.data.model.Anime
import com.aura.domain.usecase.GetAnimeUseCase
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnimeViewModel(
    private val getAnimeUseCase: GetAnimeUseCase,
    private val dataStore: AppDataStore
) : ViewModel() {

    private val _animeState = MutableStateFlow<NetworkResult<List<Anime>>>(NetworkResult.Loading)
    val animeState: StateFlow<NetworkResult<List<Anime>>> = _animeState

    init { loadAnime() }

    fun loadAnime() {
        viewModelScope.launch {
            val includeAdult = try { dataStore.adultContentEnabled.first() } catch (_: Exception) { false }
            getAnimeUseCase(includeAdult).collect { _animeState.value = it }
        }
    }
}

