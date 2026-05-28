package com.aura.data.repository

import com.aura.data.model.*
import com.aura.data.scraper.KMMoviesScraper
import com.aura.data.scraper.ProviderManager
import com.aura.data.scraper.toScraperError
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MovieRepository(
    private val scraper: KMMoviesScraper,
    private val providerManager: ProviderManager
) {

    fun getLatestMovies(): Flow<NetworkResult<List<Movie>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val movies = providerManager.getLatestMoviesWithFallback(scraper)
            emit(NetworkResult.Success(movies))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load movies"))
        }
    }

    fun searchMovies(query: String): Flow<NetworkResult<List<Movie>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val movies = providerManager.scrapeMoviesWithFallback(query, scraper)
            emit(NetworkResult.Success(movies))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Search failed"))
        }
    }

    fun getMovieDetails(url: String): Flow<NetworkResult<MovieDetails>> = flow {
        emit(NetworkResult.Loading)
        try {
            val details = scraper.getMovieDetails(url)
            emit(NetworkResult.Success(details))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load details"))
        }
    }

    fun getMagicLinks(url: String): Flow<NetworkResult<List<DirectLink>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val streamLinks = scraper.getDownloadLinks(url)
            // Convert StreamLink â†’ DirectLink for API shape compatibility
            val directLinks = streamLinks.map { sl ->
                DirectLink(quality = sl.quality, url = sl.getUrl(), server = sl.server)
            }
            emit(NetworkResult.Success(directLinks))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load stream links"))
        }
    }
}

