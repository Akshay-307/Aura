package com.aura.data.repository

import com.aura.data.model.NetMirrorPost
import com.aura.data.model.NetMirrorPostDetails
import com.aura.data.model.StreamLink
import com.aura.data.scraper.NetMirrorScraper
import com.aura.data.scraper.ProviderManager
import com.aura.data.scraper.toScraperError
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StreamRepository(
    private val netMirrorScraper: NetMirrorScraper,
    private val providerManager: ProviderManager
) {

    /** Trending (all media) â€” for "Trending Now" */
    fun getNetMirrorHome(includeAdult: Boolean = false): Flow<NetworkResult<List<NetMirrorPost>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val posts = netMirrorScraper.getHomepageContent(includeAdult = includeAdult)
            emit(NetworkResult.Success(posts))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load trending"))
        }
    }

    /** Latest movies (now_playing) â€” for "Latest Movies" */
    fun getLatestMovies(includeAdult: Boolean = false): Flow<NetworkResult<List<NetMirrorPost>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val posts = netMirrorScraper.getLatestMovies(includeAdult)
            emit(NetworkResult.Success(posts))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load movies"))
        }
    }

    /** Latest series (on_the_air) â€” for "Latest Series" */
    fun getLatestSeries(includeAdult: Boolean = false): Flow<NetworkResult<List<NetMirrorPost>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val posts = netMirrorScraper.getLatestSeries(includeAdult)
            emit(NetworkResult.Success(posts))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load series"))
        }
    }

    /** Popular anime (animation genre via TMDB) â€” for "Latest Anime" */
    fun getLatestAnimeTmdb(includeAdult: Boolean = false): Flow<NetworkResult<List<NetMirrorPost>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val posts = netMirrorScraper.getLatestAnimeTmdb(includeAdult)
            emit(NetworkResult.Success(posts))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load anime"))
        }
    }

    fun searchNetMirror(query: String, includeAdult: Boolean = false): Flow<NetworkResult<List<NetMirrorPost>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val results = netMirrorScraper.searchContent(query, includeAdult = includeAdult)
            emit(NetworkResult.Success(results))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to search"))
        }
    }

    fun getNetMirrorPost(id: String): Flow<NetworkResult<NetMirrorPostDetails>> = flow {
        emit(NetworkResult.Loading)
        try {
            val details = netMirrorScraper.getPostDetails(id)
            emit(NetworkResult.Success(details))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to load details"))
        }
    }

    fun getNetMirrorStream(episodeId: String): Flow<NetworkResult<List<StreamLink>>> = flow {
        emit(NetworkResult.Loading)
        try {
            // First try ProviderManager extractors (fast, direct .m3u8)
            val magicLinks = providerManager.resolveStream(episodeId)
            if (magicLinks.isNotEmpty()) {
                emit(NetworkResult.Success(magicLinks))
                return@flow
            }
            // Fallback to embed provider links
            val links = netMirrorScraper.getStreamUrl(episodeId)
            emit(NetworkResult.Success(links))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.toScraperError().message ?: "Failed to get streams"))
        }
    }
}

