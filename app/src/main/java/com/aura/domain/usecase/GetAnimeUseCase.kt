package com.aura.domain.usecase

import com.aura.data.model.Anime
import com.aura.data.repository.AnimeRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

class GetAnimeUseCase(private val repository: AnimeRepository) {
    operator fun invoke(includeAdult: Boolean = false): Flow<NetworkResult<List<Anime>>> = repository.getLatestAnime(includeAdult)
}

