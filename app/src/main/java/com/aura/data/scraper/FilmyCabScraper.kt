package com.aura.data.scraper

import com.aura.data.model.StreamLink
import com.aura.data.scraper.extractors.LinkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scraper for FilmyCab.co.
 * Specifically handles the "Fast Server (G-Drive)" buttons that point to HubCloud.
 */
class FilmyCabScraper : BaseScraper() {

    suspend fun getStreamLinks(detailUrl: String): List<StreamLink> = withContext(Dispatchers.IO) {
        try {
            // FilmyCab blocks requests without a referer
            val html = getRawPageSource(detailUrl, referer = "https://filmycab.co/")
            val doc = org.jsoup.Jsoup.parse(html)
            
            val anchors = doc.select("#info .movie-button-container a.movie-simple-button, a[href*=hubcloud], a[href*=gdflix]")
                .mapNotNull { it.attr("href").takeIf { h -> h.startsWith("http") } }
                .distinct()
                .take(3)

            val streams = mutableListOf<StreamLink>()
            for (rawUrl in anchors) {
                val stream = LinkExtractor.resolve(rawUrl)
                if (stream != null) streams.add(stream)
            }
            streams
        } catch (_: Exception) {
            emptyList()
        }
    }
}

