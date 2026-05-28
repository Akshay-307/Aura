package com.aura.domain.usecase

import com.aura.data.model.StreamLink
import com.aura.data.repository.MovieRepository
import com.aura.data.repository.AnimeRepository
import com.aura.data.repository.StreamRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

class GetStreamLinksUseCase(
    private val movieRepository: MovieRepository,
    private val animeRepository: AnimeRepository,
    private val streamRepository: StreamRepository
) {
    fun getMovieLinks(url: String): Flow<NetworkResult<List<StreamLink>>> = movieRepository.getMagicLinks(url)
        .let { flow ->
            // Convert DirectLink list to StreamLink list
            kotlinx.coroutines.flow.flow {
                emit(NetworkResult.Loading)
                try {
                    movieRepository.getMagicLinks(url).collect { result ->
                        when (result) {
                            is NetworkResult.Success -> {
                                val links = result.data.map { dl ->
                                    StreamLink(
                                        server = dl.server.ifEmpty { "Server ${result.data.indexOf(dl) + 1}" },
                                        url = dl.getUrl(),
                                        quality = dl.quality
                                    )
                                }
                                emit(NetworkResult.Success(links))
                            }
                            is NetworkResult.Error -> emit(NetworkResult.Error(result.message))
                            NetworkResult.Loading -> emit(NetworkResult.Loading)
                        }
                    }
                } catch (e: Exception) {
                    emit(NetworkResult.Error(e.message ?: "Failed to get stream links"))
                }
            }
        }

    fun getAnimeStream(url: String): Flow<NetworkResult<List<StreamLink>>> =
        animeRepository.getAnimeStream(url)

    fun getNetStream(id: String): Flow<NetworkResult<List<StreamLink>>> =
        streamRepository.getNetMirrorStream(id)
}

