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
 * Scraper for HiAnime.mx (formerly ZoroTV).
 * Uses their AJAX/v2 JSON endpoints.
 */
class HiAnimeScraper : BaseScraper() {

    private val baseUrl = "https://hianime.mx"

    suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val raw = getRawPageSource("$baseUrl/ajax/v2/search?keyword=$encoded")
            val json = JSONObject(raw)
            val html = json.optString("html") // Returns pre-rendered HTML

            val doc = org.jsoup.Jsoup.parse(html)
            val results = mutableListOf<Anime>()

            for (item in doc.select(".flw-item")) {
                val anchor = item.select("a.film-poster-ahref")
                val url = anchor.attr("href")
                // Extract show ID from /watch/title-ID
                val showId = url.substringAfterLast("-").substringBefore("?")
                
                results.add(
                    Anime(
                        title = anchor.attr("title"),
                        url = showId,
                        poster = item.select("img.film-poster-img").attr("data-src"),
                        type = item.select(".fdi-item").firstOrNull()?.text() ?: ""
                    )
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisodes(showId: String): List<AnimeEpisode> = withContext(Dispatchers.IO) {
        try {
            val raw = getRawPageSource("$baseUrl/ajax/v2/episode/list/$showId")
            val json = JSONObject(raw)
            val html = json.optString("html")
            val doc = org.jsoup.Jsoup.parse(html)

            val episodes = mutableListOf<AnimeEpisode>()
            for (item in doc.select("a.ep-item")) {
                val epNum = item.attr("data-number").toIntOrNull() ?: continue
                episodes.add(
                    AnimeEpisode(
                        title = item.attr("title").ifEmpty { "Episode $epNum" },
                        episode = epNum.toString(),
                        url = item.attr("data-id"),
                        number = epNum
                    )
                )
            }
            episodes
        } catch (_: Exception) {
            emptyList()
        }
    }

    // HiAnime stream extraction requires complex Megacloud/Rabbitstream decryption
    // For now, we return the embed URLs which PlayerActivity will load in a WebView
    suspend fun getStream(episodeId: String): List<StreamLink> = withContext(Dispatchers.IO) {
        try {
            val raw = getRawPageSource("$baseUrl/ajax/v2/episode/servers?episodeId=$episodeId")
            val json = JSONObject(raw)
            val html = json.optString("html")
            val doc = org.jsoup.Jsoup.parse(html)

            val streams = mutableListOf<StreamLink>()
            
            // Get sub/dub servers
            for (server in doc.select(".server-item")) {
                val serverId = server.attr("data-id")
                val type = server.attr("data-type") // sub or dub
                val name = server.text()
                
                val srcRaw = getRawPageSource("$baseUrl/ajax/v2/episode/sources?id=$serverId")
                val srcJson = JSONObject(srcRaw)
                val link = srcJson.optString("link")
                
                if (link.isNotEmpty()) {
                    streams.add(
                        StreamLink(
                            server = "$name ($type)",
                            url = link,
                            quality = "Auto",
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

