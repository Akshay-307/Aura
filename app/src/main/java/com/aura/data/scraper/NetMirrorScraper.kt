package com.aura.data.scraper

import com.aura.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * NetMirrorScraper â€” TMDB API (metadata) + multi-source embed players.
 *
 * ID wire format stored in NetMirrorPost:
 *   "movie:{tmdbId}"  â†’ movie
 *   "tv:{tmdbId}"     â†’ TV series
 *
 * Episode ID wire format (passed to getStreamUrl):
 *   "movie:{imdbId}"           â†’ movie
 *   "tv:{imdbId}:{s}:{e}"     â†’ TV episode
 */
class NetMirrorScraper : BaseScraper() {

    init {
        updateDomains()
    }

    private fun updateDomains() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val url = "https://raw.githubusercontent.com/Akshay-Built/Aura-Config/main/domains.json"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        if (json.has("peachify")) {
                            peachifyDomain = json.getString("peachify").trimEnd('/')
                        }
                        if (json.has("movies111")) {
                            movies111Domain = json.getString("movies111").trimEnd('/')
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        val TMDB_KEY by lazy {
            String(android.util.Base64.decode("OTI4ZmJiNDg5OTdiNTVlYjFjZWViYTRmMmY0YWNmNWE=", android.util.Base64.DEFAULT))
        }
        const val TMDB_BASE = "https://api.themoviedb.org/3"

        var peachifyDomain = "https://peachify.top"
        var movies111Domain = "https://111movies.net"

        // â”€â”€ Embed providers (ordered by reliability) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val PROVIDERS = listOf(
            // â˜… Peachify â€” TMDB IDs, high performance cloud transcode, multi-audio
            EmbedProvider(
                name       = "Peachify",
                movieUrl   = { id -> "$peachifyDomain/?id=$id&type=movie" },
                tvUrl      = { id, s, e -> "$peachifyDomain/?id=$id&s=$s&e=$e&type=tv" },
                usesTmdbId = true
            ),
            // â˜… 111Movies â€” IMDb IDs, multi-audio/Hindi support
            EmbedProvider(
                name       = "111Movies",
                movieUrl   = { id -> "$movies111Domain/movie/$id" },
                tvUrl      = { id, s, e -> "$movies111Domain/tv/$id/$s/$e" },
                usesTmdbId = false
            ),
            // â˜… VidZee â€” TMDB IDs, Stremio-backed multi-audio player
            EmbedProvider(
                name       = "VidZee",
                movieUrl   = { id -> "https://player.vidzee.wtf/embed/movie/$id" },
                tvUrl      = { id, s, e -> "https://player.vidzee.wtf/embed/tv/$id/$s/$e" },
                usesTmdbId = true
            ),
            // â˜… VidZee V2 â€” Alternative VidZee endpoint
            EmbedProvider(
                name       = "VidZeeV2",
                movieUrl   = { id -> "https://player.vidzee.wtf/v2/embed/movie/$id" },
                tvUrl      = { id, s, e -> "https://player.vidzee.wtf/v2/embed/tv/$id/$s/$e" },
                usesTmdbId = true
            )
        )


        /** In-memory cache: imdbId -> tmdbId */
        private val tmdbIdCache   = mutableMapOf<String, String>()
        /** In-memory cache: tmdbId -> imdbId */
        private val tmdbToImdbCache = mutableMapOf<String, String>()

        fun buildTmdbUrl(path: String, queryParams: String = ""): String {
            val tmdbUrl = "$TMDB_BASE/$path?api_key=$TMDB_KEY$queryParams"
            val encodedUrl = java.net.URLEncoder.encode(tmdbUrl, "UTF-8")
            return "https://api.codetabs.com/v1/proxy/?quest=$encodedUrl"
        }
    }

    data class EmbedProvider(
        val name: String,
        val movieUrl: (String) -> String,
        val tvUrl: (String, Int, Int) -> String,
        val usesTmdbId: Boolean = false
    )

    // â”€â”€â”€ Home page sections â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Trending â€” all media (movies + TV), used for "Trending Now" */
    suspend fun getHomepageContent(baseUrl: String = "", includeAdult: Boolean = false): List<NetMirrorPost> =
        withContext(Dispatchers.IO) {
            fetchTmdbList("trending/all/day", includeAdult = includeAdult)
        }

    /** Latest/now-playing movies for "Latest Movies" section */
    suspend fun getLatestMovies(includeAdult: Boolean = false): List<NetMirrorPost> =
        withContext(Dispatchers.IO) {
            fetchTmdbList("movie/now_playing", includeAdult = includeAdult)
        }

    /** On-the-air TV shows for "Latest Series" section */
    suspend fun getLatestSeries(includeAdult: Boolean = false): List<NetMirrorPost> =
        withContext(Dispatchers.IO) {
            fetchTmdbList("tv/on_the_air", includeAdult = includeAdult)
        }

    /** Popular anime (animation genre = 16) for "Latest Anime" section */
    suspend fun getLatestAnimeTmdb(includeAdult: Boolean = false): List<NetMirrorPost> =
        withContext(Dispatchers.IO) {
            fetchTmdbList("discover/tv", "&with_genres=16&sort_by=popularity.desc", includeAdult = includeAdult)
        }

    private fun isAdultContent(title: String, overview: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerOverview = overview.lowercase()
        // Hard block explicit keywords in title or overview
        val titleKeywords = listOf(
            "porn", "xxx", "erotic film", "erotic movie", "hentai", "ecchi",
            "nsfw", "creampie", "milf", "blowjob", "handjob", "hardcore sex",
            "softcore", "sex tape", "sex film", "adult film", "nude scene only",
            "gangbang", "deepthroat", "brazzers", "bangbros", "onlyfans",
            "cam girl", "cam show", "stripper film", "escort film"
        )
        val overviewKeywords = listOf("hentai", "ecchi", "explicitly sexual", "explicit sex")
        for (kw in titleKeywords) {
            if (lowerTitle.contains(kw)) return true
        }
        for (kw in overviewKeywords) {
            if (lowerOverview.contains(kw)) return true
        }
        return false
    }

    private fun fetchTmdbList(endpoint: String, extra: String = "", includeAdult: Boolean = false): List<NetMirrorPost> {
        val posts = mutableListOf<NetMirrorPost>()
        try {
            val url = buildTmdbUrl(endpoint, extra + "&include_adult=$includeAdult")
            val raw = getJson(url)
            if (raw != null) {
                val results = raw.optJSONArray("results") ?: JSONArray()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    // media_type is only present in trending; for other endpoints infer from endpoint
                    val mediaType = item.optString("media_type").let { mt ->
                        when {
                            mt == "movie" || mt == "tv" -> mt
                            item.has("title") -> "movie"
                            else -> "tv"
                        }
                    }
                    if (mediaType != "movie" && mediaType != "tv") continue

                    val title = item.optString("title").ifEmpty { item.optString("name") }
                    val overview = item.optString("overview")
                    // Always filter adult content regardless of 18+ setting
                    if (item.optBoolean("adult", false) || isAdultContent(title, overview)) continue

                    val tmdbId = item.optInt("id", -1)
                    if (tmdbId <= 0) continue

                    val posterPath = item.optString("poster_path")
                    val poster = if (posterPath.isNotEmpty() && posterPath != "null")
                        "https://image.tmdb.org/t/p/w500$posterPath" else ""
                    val date  = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
                    val year  = date.take(4)
                    // Store a prefixed composite ID so detail screen knows the type
                    val compositeId = if (mediaType == "tv") "tv:$tmdbId" else "movie:$tmdbId"

                    posts.add(NetMirrorPost(
                        id     = compositeId,
                        title  = title,
                        poster = poster,
                        year   = year,
                        type   = if (mediaType == "tv") "series" else "movie",
                        url    = compositeId
                    ))
                }
            }
        } catch (_: Exception) {}
        return posts.distinctBy { it.id }
    }

    // â”€â”€â”€ Search â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun searchContent(query: String, baseUrl: String = "", includeAdult: Boolean = false): List<NetMirrorPost> =
        withContext(Dispatchers.IO) {
            val posts = mutableListOf<NetMirrorPost>()
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                // Always pass include_adult=false to TMDB to block explicit content at the API level
                val url = buildTmdbUrl("search/multi", "&query=$encoded&include_adult=false")
                val raw = getJson(url)
                if (raw != null) {
                    val results = raw.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val mediaType = item.optString("media_type")
                        if (mediaType != "movie" && mediaType != "tv") continue

                        val title = item.optString("title").ifEmpty { item.optString("name") }
                        val overview = item.optString("overview")
                        // Always filter adult content regardless of 18+ setting
                        if (item.optBoolean("adult", false) || isAdultContent(title, overview)) continue

                        val tmdbId = item.optInt("id", -1)
                        if (tmdbId <= 0) continue

                        val posterPath = item.optString("poster_path")
                        val poster = if (posterPath.isNotEmpty() && posterPath != "null")
                            "https://image.tmdb.org/t/p/w500$posterPath" else ""
                        val date  = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
                        val year  = date.take(4)
                        val compositeId = if (mediaType == "tv") "tv:$tmdbId" else "movie:$tmdbId"

                        posts.add(NetMirrorPost(
                            id     = compositeId,
                            title  = title,
                            poster = poster,
                            year   = year,
                            type   = if (mediaType == "tv") "series" else "movie",
                            url    = compositeId
                        ))
                    }
                }
            } catch (_: Exception) {}
            posts
        }

    // â”€â”€â”€ Post details â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getPostDetails(input: String): NetMirrorPostDetails =
        withContext(Dispatchers.IO) {
            try {
                // Parse the composite ID (e.g. "tv:1396", "movie:603", or bare IMDb "tt0903747")
                val (isMovie, tmdbId, imdbIdHint) = when {
                    input.startsWith("movie:") -> {
                        val rest = input.removePrefix("movie:")
                        if (rest.startsWith("tt")) Triple(true, "", rest)
                        else Triple(true, rest, "")
                    }
                    input.startsWith("tv:") -> {
                        val rest = input.removePrefix("tv:")
                        if (rest.startsWith("tt")) Triple(false, "", rest)
                        else Triple(false, rest, "")
                    }
                    input.startsWith("tt") -> Triple(true, "", input) // treat bare IMDb as movie unless we detect otherwise
                    else -> Triple(true, input, "") // bare TMDB ID, assume movie
                }

                var resolvedTmdbId = tmdbId
                var resolvedImdbId = imdbIdHint
                var resolvedIsMovie = isMovie

                // If we only have IMDb ID, resolve to TMDB
                if (resolvedTmdbId.isEmpty() && resolvedImdbId.isNotEmpty()) {
                    val findUrl = buildTmdbUrl("find/$resolvedImdbId", "&external_source=imdb_id")
                    val findRaw = getJson(findUrl)
                    if (findRaw != null) {
                        val movieArr = findRaw.optJSONArray("movie_results")
                        val tvArr    = findRaw.optJSONArray("tv_results")
                        when {
                            movieArr != null && movieArr.length() > 0 -> {
                                resolvedTmdbId = movieArr.getJSONObject(0).optInt("id").toString()
                                resolvedIsMovie = true
                            }
                            tvArr != null && tvArr.length() > 0 -> {
                                resolvedTmdbId = tvArr.getJSONObject(0).optInt("id").toString()
                                resolvedIsMovie = false
                            }
                        }
                    }
                }

                if (resolvedTmdbId.isEmpty()) {
                    return@withContext NetMirrorPostDetails(id = input, title = "Details Unavailable")
                }

                // Fetch details from correct endpoint based on known type
                val raw = if (resolvedIsMovie) {
                    getJson(buildTmdbUrl("movie/$resolvedTmdbId")) ?: run {
                        // Might actually be a TV show â€” try TV
                        val tvRaw = getJson(buildTmdbUrl("tv/$resolvedTmdbId"))
                        if (tvRaw != null) resolvedIsMovie = false
                        tvRaw
                    }
                } else {
                    getJson(buildTmdbUrl("tv/$resolvedTmdbId"))
                }

                if (raw == null) {
                    return@withContext NetMirrorPostDetails(id = input, title = "Details Unavailable")
                }

                val title      = raw.optString("title").ifEmpty { raw.optString("name") }
                val date       = raw.optString("release_date").ifEmpty { raw.optString("first_air_date") }
                val year       = date.take(4)
                val synopsis   = raw.optString("overview")
                val posterPath = raw.optString("poster_path")
                val poster     = if (posterPath.isNotEmpty() && posterPath != "null")
                    "https://image.tmdb.org/t/p/w500$posterPath" else ""
                val voteAvg    = raw.optDouble("vote_average", 0.0)
                val rating     = if (voteAvg > 0) "â­ ${String.format("%.1f", voteAvg)}" else ""

                val genreArr = raw.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genreArr != null) {
                    for (i in 0 until genreArr.length()) genres.add(genreArr.getJSONObject(i).optString("name"))
                }
                val genre = genres.joinToString(", ")

                // Resolve IMDb ID
                if (resolvedImdbId.isEmpty()) {
                    resolvedImdbId = raw.optString("imdb_id")
                    if (resolvedImdbId.isEmpty() || resolvedImdbId == "null") {
                        val extUrl = buildTmdbUrl(
                            if (resolvedIsMovie) "movie/$resolvedTmdbId/external_ids"
                            else "tv/$resolvedTmdbId/external_ids"
                        )
                        val extRaw = getJson(extUrl)
                        resolvedImdbId = extRaw?.optString("imdb_id") ?: ""
                    }
                }
                if (resolvedImdbId == "null") resolvedImdbId = ""

                if (resolvedImdbId.isNotEmpty()) {
                    tmdbIdCache[resolvedImdbId]   = resolvedTmdbId
                    tmdbToImdbCache[resolvedTmdbId] = resolvedImdbId
                }

                val finalId  = resolvedImdbId.ifEmpty { resolvedTmdbId }
                val episodes = mutableListOf<StreamEpisode>()

                if (resolvedIsMovie) {
                    episodes += StreamEpisode(
                        id      = "movie:$finalId",
                        title   = "â–¶  Play Movie",
                        episode = "1",
                        number  = 1
                    )
                } else {
                    val seasonsCount = raw.optInt("number_of_seasons", 1)
                    val deferredSeasons = (1..minOf(seasonsCount, 15)).map { s ->
                        async {
                            val seasonUrl = buildTmdbUrl("tv/$resolvedTmdbId/season/$s")
                            s to getJson(seasonUrl)
                        }
                    }
                    val seasonRaws = deferredSeasons.awaitAll()
                    for ((s, seasonRaw) in seasonRaws) {
                        if (seasonRaw == null) continue
                        val epArr = seasonRaw.optJSONArray("episodes") ?: continue
                        for (e in 0 until epArr.length()) {
                            val ep     = epArr.getJSONObject(e)
                            val epNum  = ep.optInt("episode_number", e + 1)
                            val epTitle = ep.optString("name").let {
                                if (it.isEmpty() || it == "null") "Episode $epNum" else it
                            }
                            episodes += StreamEpisode(
                                id      = "tv:$finalId:$s:$epNum",
                                title   = "S${s}E${ "%02d".format(epNum)} â€“ $epTitle",
                                episode = epNum.toString(),
                                number  = (s * 1000) + epNum
                            )
                        }
                    }
                    if (episodes.isEmpty()) {
                        episodes += StreamEpisode(id = "tv:$finalId:1:1", title = "S1E1", episode = "1", number = 1001)
                    }
                }

                NetMirrorPostDetails(
                    id       = finalId,
                    title    = title,
                    poster   = poster,
                    year     = year,
                    genre    = genre,
                    rating   = rating,
                    synopsis = synopsis,
                    episodes = episodes
                )
            } catch (e: Exception) {
                NetMirrorPostDetails(id = input, title = "Error Loading Details")
            }
        }

    // â”€â”€â”€ Stream URLs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private suspend fun tmdbIdFor(imdbId: String, isMovie: Boolean): String? =
        withContext(Dispatchers.IO) {
            tmdbIdCache[imdbId] ?: try {
                kotlinx.coroutines.withTimeoutOrNull(2500) {
                    val tmdbUrl     = "$TMDB_BASE/find/$imdbId?api_key=$TMDB_KEY&external_source=imdb_id"
                    val encodedUrl  = java.net.URLEncoder.encode(tmdbUrl, "UTF-8")
                    val proxyUrl    = "https://api.codetabs.com/v1/proxy/?quest=$encodedUrl"
                    val json        = getJson(proxyUrl) ?: getJson(tmdbUrl) ?: return@withTimeoutOrNull null
                    val arr: JSONArray? = if (isMovie) json.optJSONArray("movie_results")
                                         else          json.optJSONArray("tv_results")
                    val tmdbId = arr?.takeIf { it.length() > 0 }?.getJSONObject(0)
                        ?.optInt("id", -1)?.takeIf { it > 0 }?.toString()
                    if (tmdbId != null) tmdbIdCache[imdbId] = tmdbId
                    tmdbId
                }
            } catch (_: Exception) { null }
        }

    private suspend fun imdbIdFor(tmdbId: String, isMovie: Boolean): String? =
        withContext(Dispatchers.IO) {
            tmdbToImdbCache[tmdbId] ?: try {
                kotlinx.coroutines.withTimeoutOrNull(2500) {
                    val url    = if (isMovie) buildTmdbUrl("movie/$tmdbId/external_ids")
                                 else         buildTmdbUrl("tv/$tmdbId/external_ids")
                    val json   = getJson(url)
                    val imdbId = json?.optString("imdb_id")?.takeIf { it.isNotEmpty() && it != "null" }
                    if (imdbId != null) {
                        tmdbToImdbCache[tmdbId] = imdbId
                        tmdbIdCache[imdbId]     = tmdbId
                    }
                    imdbId
                }
            } catch (_: Exception) { null }
        }

    suspend fun getStreamUrl(episodeId: String): List<StreamLink> =
        withContext(Dispatchers.IO) {
            val links = mutableListOf<StreamLink>()
            when {
                episodeId.startsWith("movie:") -> {
                    val rawId  = episodeId.removePrefix("movie:")
                    val isImdb = rawId.startsWith("tt")
                    val tmdbId by lazy { if (isImdb) runBlocking { tmdbIdFor(rawId, isMovie = true) } else rawId }
                    val imdbId by lazy { if (!isImdb) runBlocking { imdbIdFor(rawId, isMovie = true) } else rawId }
                    // Embed providers (instant, no wait)
                    PROVIDERS.forEach { p ->
                        val id = if (p.usesTmdbId) tmdbId else imdbId
                        if (id.isNullOrEmpty()) return@forEach
                        links += StreamLink(server = p.name, url = p.movieUrl(id), quality = "HD", type = "embed")
                    }
                }
                episodeId.startsWith("tv:") -> {
                    val parts     = episodeId.split(":")
                    val rawId     = parts.getOrElse(1) { "" }
                    val seasonVal = parts.getOrElse(2) { "1" }.toIntOrNull() ?: 1
                    val epVal     = parts.getOrElse(3) { "1" }.toIntOrNull() ?: 1
                    val isImdb    = rawId.startsWith("tt")
                    val tmdbId by lazy { if (isImdb) runBlocking { tmdbIdFor(rawId, isMovie = false) } else rawId }
                    val imdbId by lazy { if (!isImdb) runBlocking { imdbIdFor(rawId, isMovie = false) } else rawId }
                    // Embed providers (instant)
                    PROVIDERS.forEach { p ->
                        val id = if (p.usesTmdbId) tmdbId else imdbId
                        if (id.isNullOrEmpty()) return@forEach
                        links += StreamLink(server = p.name, url = p.tvUrl(id, seasonVal, epVal), quality = "HD", type = "embed")
                    }
                }
                else -> {
                    val rawId  = extractImdbId(episodeId).ifEmpty { episodeId }
                    val isImdb = rawId.startsWith("tt")
                    val tmdbId by lazy { if (isImdb) runBlocking { tmdbIdFor(rawId, isMovie = true) } else rawId }
                    val imdbId by lazy { if (!isImdb) runBlocking { imdbIdFor(rawId, isMovie = true) } else rawId }
                    PROVIDERS.forEach { p ->
                        val id = if (p.usesTmdbId) tmdbId else imdbId
                        if (id.isNullOrEmpty()) return@forEach
                        links += StreamLink(server = p.name, url = p.movieUrl(id), quality = "HD", type = "embed")
                    }
                }
            }
            links
        }

    // â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun extractImdbId(input: String): String =
        Regex("tt\\d{7,9}").find(input)?.value ?: ""

    private fun getJsonDirect(url: String): JSONObject? = try {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
            .build()
        val body = client.newCall(request).execute().use { it.body?.string() ?: "" }
        if (body.startsWith("{")) JSONObject(body) else null
    } catch (_: Exception) { null }

    private fun getJson(url: String): JSONObject? = try {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
            .build()
        val body = client.newCall(request).execute().use { it.body?.string() ?: "" }
        if (body.startsWith("{")) {
            JSONObject(body)
        } else {
            if (url.contains("codetabs.com/v1/proxy")) {
                val questParam = url.substringAfter("quest=")
                val decodedUrl = java.net.URLDecoder.decode(questParam, "UTF-8")
                getJsonDirect(decodedUrl)
            } else null
        }
    } catch (e: Exception) {
        if (url.contains("codetabs.com/v1/proxy")) {
            try {
                val questParam = url.substringAfter("quest=")
                val decodedUrl = java.net.URLDecoder.decode(questParam, "UTF-8")
                getJsonDirect(decodedUrl)
            } catch (_: Exception) { null }
        } else null
    }
}

