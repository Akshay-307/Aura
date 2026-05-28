package com.aura.data.scraper

import com.aura.data.model.Movie
import com.aura.data.model.StreamLink
import com.aura.data.scraper.extractors.LinkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scraper for Movies4u.promo.
 * WordPress-based site. Uses LinkExtractor to resolve GDrive/HubCloud anchors.
 */
class Movies4uScraper : BaseScraper() {

    suspend fun getHomePosts(baseUrl: String): List<Movie> = withContext(Dispatchers.IO) {
        try {
            val doc = getDocument(baseUrl)
            parseMovieCards(doc, baseUrl)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchPosts(query: String, baseUrl: String): List<Movie> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = getDocument("$baseUrl/?s=$encoded")
            parseMovieCards(doc, baseUrl)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisodeLinks(detailUrl: String): List<StreamLink> = withContext(Dispatchers.IO) {
        try {
            val doc = getDocument(detailUrl)
            
            // Extract raw download anchors (HubCloud, GDFlix, etc.)
            val rawLinks = doc.select("a[href*=hubcloud], a[href*=gdflix], a[href*=gofile], a.maxbutton-filepress, a[href*=filepress], a.btn-success")
                .mapNotNull { it.attr("href").takeIf { h -> h.startsWith("http") } }
                .distinct()
                .take(4) // Don't overwhelm the extractors

            val streams = mutableListOf<StreamLink>()
            for (rawUrl in rawLinks) {
                // Pass directly to the universal LinkExtractor
                val stream = LinkExtractor.resolve(rawUrl)
                if (stream != null) streams.add(stream)
            }
            streams
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMovieCards(doc: org.jsoup.nodes.Document, baseUrl: String): List<Movie> {
        val movies = mutableListOf<Movie>()
        val articles = doc.select("article, .post-item, .item, .movie-card")
        
        for (article in articles) {
            val anchor = article.select("a[href]").firstOrNull() ?: continue
            val url = anchor.attr("href")
            val title = article.select("h2, h3, .title, .post-title").text().ifEmpty { anchor.attr("title") }.ifEmpty { anchor.text() }
            
            var imgUrl = article.select("img").attr("src")
            if (imgUrl.isEmpty() || imgUrl.contains("data:image")) {
                imgUrl = article.select("img").attr("data-src")
            }
            if (imgUrl.startsWith("/")) imgUrl = baseUrl + imgUrl

            if (title.isNotBlank() && url.isNotBlank()) {
                movies.add(
                    Movie(
                        title = title,
                        url = url,
                        poster = imgUrl,
                        year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value ?: ""
                    )
                )
            }
        }
        return movies
    }
}

