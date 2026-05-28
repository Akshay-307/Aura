package com.aura.data.scraper

import com.aura.data.model.StreamLink
import com.aura.data.scraper.extractors.LinkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scraper for ZeeFliz.beer.
 * WP site. Features a /postlink/ internal redirect system before reaching file hosts.
 */
class ZeeFlizScraper : BaseScraper() {

    suspend fun getEpisodeLinks(detailUrl: String): List<StreamLink> = withContext(Dispatchers.IO) {
        try {
            val doc = getDocument(detailUrl)
            
            // ZeeFliz uses intermediate postlinks
            val postLinks = doc.select("a[href*='/postlink/']")
                .mapNotNull { it.attr("href").takeIf { h -> h.startsWith("http") } }
                .distinct()
                .take(3)

            val streams = mutableListOf<StreamLink>()
            for (plink in postLinks) {
                // 1. Resolve the internal 302 redirect
                val realUrl = runCatching { resolveRedirect(plink) }.getOrDefault(plink)
                
                // 2. Pass the real HubCloud/GDFlix URL to the extractor
                val stream = LinkExtractor.resolve(realUrl)
                if (stream != null) streams.add(stream)
            }
            streams
        } catch (_: Exception) {
            emptyList()
        }
    }
}

