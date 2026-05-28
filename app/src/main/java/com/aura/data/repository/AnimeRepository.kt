package com.aura.data.repository

import com.aura.data.model.*
import com.aura.data.scraper.AnimeSaltScraper
import com.aura.data.scraper.ProviderManager
import com.aura.data.scraper.NetMirrorScraper
import com.aura.data.scraper.toScraperError
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AnimeRepository(
    private val scraper: AnimeSaltScraper,
    private val providerManager: ProviderManager,
    private val netMirrorScraper: NetMirrorScraper
) {

    fun getLatestAnime(includeAdult: Boolean = false): Flow<NetworkResult<List<Anime>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val posts = netMirrorScraper.getLatestAnimeTmdb(includeAdult)
            val anime = posts.map { post ->
                Anime(
                    title = post.title,
                    url = post.id, // composite ID "tv:tmdbId"
                    poster = post.poster,
                    year = post.year,
                    type = "tv",
                    status = "Ongoing"
                )
            }
            emit(NetworkResult.Success(anime))
        } catch (e: Exception) {
            emit(NetworkResult.Error("Failed to load anime"))
        }
    }

    fun searchAnime(query: String, includeAdult: Boolean = false): Flow<NetworkResult<List<Anime>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val anime = providerManager.scrapeAnimeWithFallback(query, scraper)
            emit(NetworkResult.Success(anime))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Search failed"))
        }
    }

    fun getAnimeDetails(url: String): Flow<NetworkResult<AnimeDetails>> = flow {
        emit(NetworkResult.Loading)
        try {
            val details = scraper.getAnimeDetails(url)
            emit(NetworkResult.Success(details))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load anime details"))
        }
    }

    fun getAnimeStream(url: String): Flow<NetworkResult<List<StreamLink>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val links = scraper.getEpisodeLinks(url)
            emit(NetworkResult.Success(links))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load stream links"))
        }
    }
}

