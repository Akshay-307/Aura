package com.aura.data.scraper

import com.aura.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/**
 * Scraper for kmmovies.wtf (and mirror URLs).
 * Parses movie listings, detail pages, and download links directly from HTML.
 */
class KMMoviesScraper : BaseScraper() {

    companion object {
        const val DEFAULT_BASE = "https://kmmovies.wtf"
    }

    // â”€â”€â”€ Latest Movies â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getLatestMovies(baseUrl: String = DEFAULT_BASE): List<Movie> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(baseUrl)
            parseMovieCards(doc, baseUrl)
        }

    // â”€â”€â”€ Search â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun searchMovies(query: String, baseUrl: String = DEFAULT_BASE): List<Movie> =
        withContext(Dispatchers.IO) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = getDocument("$baseUrl/?s=$encodedQuery")
            parseMovieCards(doc, baseUrl)
        }

    // â”€â”€â”€ Details â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getMovieDetails(url: String): MovieDetails = withContext(Dispatchers.IO) {
        val doc = getDocument(url)

        val title = doc.select("h1.entry-title, h1.post-title, h1").firstOrNull()?.text()
            ?: doc.select("meta[property=og:title]").attr("content")

        val poster = doc.select("div.post-thumbnail img, .entry-content img, img.wp-post-image")
            .firstOrNull()?.let { img ->
                img.attr("src").ifEmpty { img.attr("data-src") }
            } ?: doc.select("meta[property=og:image]").attr("content")

        val desc = doc.select("div.entry-content p, div.movie-desc, div.synopsis").firstOrNull()?.text()
            ?: doc.select("meta[name=description]").attr("content")

        // Extract metadata from info tables or spans
        val year = extractMetaValue(doc, listOf("year", "release", "date"))
        val rating = extractMetaValue(doc, listOf("rating", "imdb", "score"))
        val genre = extractMetaValue(doc, listOf("genre", "category"))
        val language = extractMetaValue(doc, listOf("language", "lang", "audio"))
        val quality = extractMetaValue(doc, listOf("quality", "resolution", "print"))

        // Extract download links from page
        val links = extractDownloadLinks(doc)

        MovieDetails(
            title = title,
            poster = poster,
            year = year,
            rating = rating,
            genre = genre,
            language = language,
            quality = quality,
            synopsis = desc,
            qualityLinks = links
        )
    }

    // â”€â”€â”€ Download / Stream Links â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getDownloadLinks(url: String): List<StreamLink> = withContext(Dispatchers.IO) {
        val doc = getDocument(url)
        val links = mutableListOf<StreamLink>()

        // Try to get the direct links from the page
        val directLinks = extractDownloadLinks(doc)
        directLinks.forEach { ql ->
            // Resolve any redirect URLs to get final URLs
            try {
                val finalUrl = resolveRedirect(ql.getUrl())
                links.add(StreamLink(
                    url = finalUrl,
                    quality = ql.quality,
                    server = "Direct",
                    type = if (finalUrl.contains(".m3u8")) "m3u8" else "mp4"
                ))
            } catch (e: Exception) {
                links.add(StreamLink(url = ql.getUrl(), quality = ql.quality, server = "Direct"))
            }
        }

        // Also try to extract any m3u8 from embedded scripts
        val pageSource = doc.html()
        extractM3U8(pageSource)?.let { m3u8 ->
            if (links.none { it.url == m3u8 }) {
                links.add(0, StreamLink(url = m3u8, quality = "Auto", server = "HLS", type = "m3u8"))
            }
        }

        links
    }

    // â”€â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun parseMovieCards(doc: Document, baseUrl: String): List<Movie> {
        val movies = mutableListOf<Movie>()

        // Try multiple common WordPress/movie-site selectors
        val cards = doc.select(
            "article.post, div.item, div.movie-item, div.entry, " +
            "ul.MovieList li, div.flw-item, div.film-poster, " +
            "div.post-thumbnail, div.blog-post"
        )

        for (card in cards) {
            try {
                val title = card.select("h2, h3, .title, .entry-title, .film-name, a[title]")
                    .firstOrNull()?.let {
                        it.text().ifEmpty { it.attr("title") }
                    } ?: continue

                if (title.isBlank()) continue

                val linkEl = card.select("a[href]").firstOrNull() ?: continue
                val rawUrl = linkEl.attr("href")
                val url = when {
                    rawUrl.startsWith("http") -> rawUrl
                    rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                    else -> "$baseUrl/$rawUrl"
                }

                val poster = card.select("img").firstOrNull()?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }
                        .ifEmpty { img.attr("data-lazy-src") }
                } ?: ""

                val year = card.select(".year, .date, time, .quality span").text()
                    .filter { it.isDigit() }.take(4)
                val quality = card.select(".quality, .badge, .hd").text()
                val genre = card.select(".genre, .cat").text()
                val rating = card.select(".rating, .imdb, .score").text()

                movies.add(Movie(
                    title = title.trim(),
                    url = url,
                    poster = poster,
                    year = year,
                    quality = quality,
                    genre = genre,
                    rating = rating
                ))
            } catch (e: Exception) {
                // Skip malformed cards
            }
        }
        return movies
    }

    private fun extractDownloadLinks(doc: Document): List<QualityLink> {
        val links = mutableListOf<QualityLink>()

        // Common patterns: anchor tags with quality labels
        val anchors = doc.select("a[href]")
        for (anchor in anchors) {
            val href = anchor.attr("href")
            val text = anchor.text().trim()

            // Filter for likely download/stream links
            if (text.isBlank()) continue
            val isLikelyLink = href.contains("drive.google", true) ||
                href.contains("mega.nz", true) ||
                href.contains("mediafire", true) ||
                href.contains(".mp4", true) ||
                href.contains(".m3u8", true) ||
                href.contains("download", true) ||
                href.contains("stream", true) ||
                href.contains("mirror", true)

            val hasQualityLabel = text.contains("480", true) || text.contains("720", true) ||
                text.contains("1080", true) || text.contains("4K", true) ||
                text.contains("HD", true) || text.contains("BluRay", true) ||
                text.contains("HQ", true) || text.contains("FHD", true)

            if ((isLikelyLink || hasQualityLabel) && href.startsWith("http")) {
                val quality = when {
                    text.contains("4K", true) || text.contains("2160", true) -> "4K"
                    text.contains("1080", true) -> "1080p"
                    text.contains("720", true) -> "720p"
                    text.contains("480", true) -> "480p"
                    text.contains("360", true) -> "360p"
                    else -> text.take(20)
                }
                links.add(QualityLink(quality = quality, url = href))
            }
        }

        // Deduplicate by URL
        return links.distinctBy { it.url }
    }

    private fun extractMetaValue(doc: Document, keys: List<String>): String {
        // Try info rows / spans first
        for (key in keys) {
            val value = doc.select("*:containsOwn($key)").firstOrNull()
                ?.nextElementSibling()?.text()?.trim()
            if (!value.isNullOrBlank()) return value

            // Try spans that directly contain the value after a colon
            val inlineValue = doc.select("strong:containsOwn($key), b:containsOwn($key), span:containsOwn($key)")
                .firstOrNull()?.parent()?.ownText()?.removePrefix(":")?.trim()
            if (!inlineValue.isNullOrBlank()) return inlineValue
        }
        return ""
    }
}

