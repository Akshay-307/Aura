package com.aura.domain.usecase

import com.aura.data.model.Movie
import com.aura.data.repository.MovieRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

class GetMoviesUseCase(private val repository: MovieRepository) {
    operator fun invoke(): Flow<NetworkResult<List<Movie>>> = repository.getLatestMovies()
}

