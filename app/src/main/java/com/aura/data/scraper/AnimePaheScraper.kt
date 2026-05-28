package com.aura.data.scraper

import com.aura.data.model.Anime
import com.aura.data.model.AnimeDetails
import com.aura.data.model.AnimeEpisode
import com.aura.data.model.StreamLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Scraper for AnimePahe.
 * Uses ScreenScape's JSON proxy API instead of raw HTML scraping.
 */
class AnimePaheScraper : BaseScraper() {

    private val apiBase = "https://screenscapeapi.dev/api/animepahe"

    suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val raw = getRawPageSource("$apiBase/search.php?s=$encoded")
            if (raw.isEmpty()) return@withContext emptyList()

            val json = JSONObject(raw)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()

            val results = mutableListOf<Anime>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                results.add(
                    Anime(
                        title = item.optString("title"),
                        url = item.optString("session"), // AnimePahe uses session IDs
                        poster = item.optString("poster"),
                        year = item.optString("year"),
                        type = item.optString("type"),
                        status = item.optString("status")
                    )
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDetails(sessionId: String): AnimeDetails = withContext(Dispatchers.IO) {
        try {
            val raw = getRawPageSource("$apiBase/details?id=$sessionId")
            val json = JSONObject(raw)

            val epsArray = json.optJSONArray("episodes") ?: JSONArray()
            val episodes = mutableListOf<AnimeEpisode>()
            
            for (i in 0 until epsArray.length()) {
                val ep = epsArray.optJSONObject(i) ?: continue
                val epNum = ep.optInt("episode")
                episodes.add(
                    AnimeEpisode(
                        title = "Episode $epNum",
                        episode = epNum.toString(),
                        url = ep.optString("session"), // Episode session ID
                        number = epNum
                    )
                )
            }

            AnimeDetails(
                title = json.optString("title"),
                poster = json.optString("poster"),
                synopsis = json.optString("synopsis"),
                status = json.optString("status"),
                episodes = episodes
            )
        } catch (_: Exception) {
            AnimeDetails()
        }
    }

    suspend fun getStream(episodeSession: String): List<StreamLink> = withContext(Dispatchers.IO) {
        try {
            val raw = getRawPageSource("$apiBase/stream_load_failed?id=$episodeSession")
            val json = JSONObject(raw)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()

            val streams = mutableListOf<StreamLink>()
            for (i in 0 until data.length()) {
                val stream = data.optJSONObject(i) ?: continue
                val kwikLink = stream.optString("kwik")
                val quality = stream.optString("resolution", "720p")
                
                if (kwikLink.isNotEmpty()) {
                    streams.add(
                        StreamLink(
                            server = "Kwik",
                            url = kwikLink, // Kwik player requires WebView extraction or kwik bypass
                            quality = quality,
                            type = "embed"
                        )
                    )
                }
            }
            streams
        } catch (_: Exception) {
            emptyList()
        }
    }
}

