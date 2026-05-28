package com.aura.domain.usecase

import com.aura.data.model.SearchResult
import com.aura.data.model.ContentType
import com.aura.data.repository.MovieRepository
import com.aura.data.repository.AnimeRepository
import com.aura.data.repository.StreamRepository
import com.aura.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SearchUseCase(
    private val movieRepository: MovieRepository,
    private val animeRepository: AnimeRepository,
    private val streamRepository: StreamRepository? = null
) {

    // â”€â”€â”€ Search All (movies + series + anime) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun searchAll(query: String, includeAdult: Boolean = false): Flow<NetworkResult<List<SearchResult>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val results = mutableListOf<SearchResult>()

            // Movies & series via OMDb (NetMirrorScraper)
            try {
                streamRepository?.searchNetMirror(query, includeAdult)?.let { flow ->
                    flow.collect { result ->
                        if (result is NetworkResult.Success) {
                            results.addAll(result.data.map { m ->
                                SearchResult(
                                    id = m.id,
                                    title = m.title,
                                    posterUrl = m.poster,
                                    year = m.year,
                                    rating = m.rating,
                                    genre = m.genre,
                                    detailUrl = m.id,
                                    netMirrorId = m.id,
                                    contentType = if (m.id.startsWith("tv:") || m.type == "series") ContentType.NET_MIRROR else ContentType.MOVIE
                                )
                            })
                        }
                    }
                }
            } catch (_: Exception) {}

            // Anime
            try {
                animeRepository.searchAnime(query, includeAdult).collect { result ->
                    if (result is NetworkResult.Success) {
                        results.addAll(result.data.map { a ->
                            SearchResult(
                                id = a.url,
                                title = a.title,
                                posterUrl = a.getPoster(),
                                year = a.year,
                                rating = a.rating,
                                genre = a.genre,
                                detailUrl = a.url,
                                contentType = ContentType.ANIME
                            )
                        })
                    }
                }
            } catch (_: Exception) {}

            val filteredResults = deduplicateResults(results)
            emit(NetworkResult.Success(filteredResults))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Search failed"))
        }
    }

    // â”€â”€â”€ Search Movies only â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun searchMovies(query: String, includeAdult: Boolean = false): Flow<NetworkResult<List<SearchResult>>> = flow {
        emit(NetworkResult.Loading)
        try {
            streamRepository?.searchNetMirror(query, includeAdult)?.let { flow ->
                flow.collect { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            val filtered = result.data.filter { m ->
                                !m.id.startsWith("tv:") && m.type != "series"
                            }.map { m ->
                                SearchResult(
                                    id = m.id,
                                    title = m.title,
                                    posterUrl = m.poster,
                                    year = m.year,
                                    rating = m.rating,
                                    genre = m.genre,
                                    detailUrl = m.id,
                                    netMirrorId = m.id,
                                    contentType = ContentType.MOVIE
                                )
                            }.let { deduplicateResults(it) }
                            emit(NetworkResult.Success(filtered))
                        }
                        is NetworkResult.Error -> emit(NetworkResult.Error(result.message))
                        is NetworkResult.Loading -> emit(NetworkResult.Loading)
                    }
                }
            } ?: emit(NetworkResult.Error("Search unavailable"))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Search failed"))
        }
    }

    // â”€â”€â”€ Search Anime only â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun searchAnime(query: String, includeAdult: Boolean = false): Flow<NetworkResult<List<SearchResult>>> = flow {
        emit(NetworkResult.Loading)
        try {
            animeRepository.searchAnime(query, includeAdult).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val filtered = result.data.map { a ->
                            SearchResult(
                                id = a.url,
                                title = a.title,
                                posterUrl = a.getPoster(),
                                year = a.year,
                                rating = a.rating,
                                genre = a.genre,
                                detailUrl = a.url,
                                contentType = ContentType.ANIME
                            )
                        }.let { deduplicateResults(it) }
                        emit(NetworkResult.Success(filtered))
                    }
                    is NetworkResult.Error -> emit(NetworkResult.Error(result.message))
                    is NetworkResult.Loading -> emit(NetworkResult.Loading)
                }
            }
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Search failed"))
        }
    }

    // â”€â”€â”€ Deduplication Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun selectBestSearchResult(group: List<SearchResult>): SearchResult {
        return group.maxByOrNull {
            when {
                it.id.startsWith("tv:") -> 4
                it.id.startsWith("movie:") -> 3
                it.contentType == ContentType.NET_MIRROR -> 2
                it.contentType == ContentType.ANIME -> 2
                else -> 1
            }
        } ?: group.first()
    }

    // â”€â”€â”€ Adult Content Filter â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // These keywords permanently block explicit/adult content from appearing
    // in search results regardless of 18+ mode. This is enforced at the app level.
    private val ADULT_KEYWORDS = setOf(
        "porn", "xxx", "sex", "nude", "naked", "erotic", "hentai", "nsfw",
        "adult", "18+", "hardcore", "softcore", "creampie", "milf", "blowjob",
        "handjob", "orgasm", "masturbat", "strip", "stripper", "escort",
        "cam girl", "onlyfans", "playboy", "penthouse", "hustler", "brazzers",
        "bangbros", "mofos", "naughty", "dirty", "filthy", "lusty", "slutt",
        "horny", "kinky", "fetish", "bdsm", "bondage", "dominat", "submissiv",
        "lesbian sex", "gay sex", "anal", "cum", "jizz", "dildo", "vibrator",
        "threesome", "orgy", "gangbang", "deepthroat", "facial", "bukak",
        "transsexual", "shemale", "ladyboy", "fisting", "squirt",
        "overflow", "dripping", "soaking", "wet dream"
    )

    private fun isAdultContent(result: SearchResult): Boolean {
        val titleLower = result.title.lowercase()
        return ADULT_KEYWORDS.any { keyword -> titleLower.contains(keyword) }
    }

    private fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        val distinctById = results.filter {
            it.title.isNotBlank() &&
            it.posterUrl.isNotBlank() &&
            !it.posterUrl.equals("null", ignoreCase = true) &&
            !isAdultContent(it)  // â† Permanently block adult content
        }.groupBy { it.id }.map { (_, group) ->
            group.firstOrNull { it.year.isNotBlank() } ?: group.first()
        }

        return distinctById.groupBy {
            normalizeTitle(it.title) + "_" + it.contentType
        }.flatMap { (_, group) ->
            val nonBlankYears = group.map { it.year }.filter { it.isNotBlank() }.distinct()
            if (nonBlankYears.size <= 1) {
                listOf(selectBestSearchResult(group))
            } else {
                nonBlankYears.map { y ->
                    val subGroup = group.filter { it.year == y }
                    selectBestSearchResult(subGroup)
                }
            }
        }
    }
}


