package com.aura.data.scraper

import com.aura.data.model.Anime
import com.aura.data.model.Movie
import com.aura.data.model.NetMirrorPost
import com.aura.data.model.StreamLink
import com.aura.data.remote.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Routes requests to the appropriate scraper and manages fallback mirrors.
 * Driven entirely by RemoteConfigManager.
 */
class ProviderManager {

    suspend fun getLatestMoviesWithFallback(scraper: KMMoviesScraper): List<Movie> =
        withContext(Dispatchers.IO) {
            val url = RemoteConfigManager.kmMoviesUrl()
            try {
                val movies = scraper.getLatestMovies(url)
                if (movies.isNotEmpty()) return@withContext movies
            } catch (_: Exception) {}
            throw ScraperError.SiteDown
        }

    suspend fun scrapeMoviesWithFallback(query: String, scraper: KMMoviesScraper): List<Movie> =
        withContext(Dispatchers.IO) {
            val url = RemoteConfigManager.kmMoviesUrl()
            try {
                val movies = scraper.searchMovies(query, url)
                if (movies.isNotEmpty()) return@withContext movies
            } catch (_: Exception) {}
            throw ScraperError.NoResults
        }

    suspend fun getLatestAnimeWithFallback(scraper: AnimeSaltScraper): List<Anime> =
        withContext(Dispatchers.IO) {
            val url = RemoteConfigManager.animeSaltUrl()
            try {
                val anime = scraper.getLatestAnime(url)
                if (anime.isNotEmpty()) return@withContext anime
            } catch (_: Exception) {}
            throw ScraperError.SiteDown
        }

    suspend fun scrapeAnimeWithFallback(query: String, scraper: AnimeSaltScraper): List<Anime> =
        withContext(Dispatchers.IO) {
            val url = RemoteConfigManager.animeSaltUrl()
            try {
                val anime = scraper.searchAnime(query, url)
                if (anime.isNotEmpty()) return@withContext anime
            } catch (_: Exception) {}
            throw ScraperError.NoResults
        }

    /**
     * Resolves stream URLs using the full multi-scraper fallback chain.
     */
    suspend fun resolveStream(detailUrl: String): List<StreamLink> =
        withContext(Dispatchers.IO) {
            // Priority 1: New high-speed Scrapers
            val scrapers: List<suspend () -> List<StreamLink>> = listOf(
                { Movies4uScraper().getEpisodeLinks(detailUrl) },
                { ZeeFlizScraper().getEpisodeLinks(detailUrl) },
                { FilmyCabScraper().getStreamLinks(detailUrl) }
            )

            for (scraper in scrapers) {
                val result = runCatching { scraper() }.getOrNull()
                if (!result.isNullOrEmpty()) return@withContext result
            }

            // Priority 2: Fallback to existing KMMovies logic if it's a km URL
            if (detailUrl.contains("kmmovies")) {
                val kmScraper = KMMoviesScraper()
                val result = runCatching { kmScraper.getDownloadLinks(detailUrl) }.getOrNull()
                if (!result.isNullOrEmpty()) {
                    return@withContext result.map { dl ->
                        StreamLink(server = dl.server, url = dl.getUrl(), quality = dl.quality)
                    }
                }
            }
            
            emptyList()
        }
}

