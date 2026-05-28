package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Extracts direct stream URLs from GDFlix pages.
 *
 * GDFlix download pages have a mirror-buttons section with multiple quality options.
 * CSS selectors from ScreenScape bundle:
 *   #mirror-buttons .mirror-buttons a.btn.gdflix
 *   #mirror-buttons .mirror-buttons a.btn.hubcloud
 */
object GDFlixExtractor {

    suspend fun extract(url: String, scraper: BaseScraper): StreamLink? =
        withContext(Dispatchers.IO) {
            try {
                val html = scraper.getRawPageSource(url, referer = url)
                val doc = Jsoup.parse(html)

                // Primary selector from ScreenScape bundle strings
                val primarySelectors = listOf(
                    "#mirror-buttons .mirror-buttons a.btn.gdflix",
                    "#mirror-buttons a.btn",
                    "a[href*=gdflix]",
                    "a.btn-primary[href*=download]",
                    "a[href*=workers.dev]",
                    "a[href*=.mp4]",
                    "a[href*=.m3u8]"
                )

                var targetUrl: String? = null
                var quality = "HD"

                for (selector in primarySelectors) {
                    val el = doc.select(selector).firstOrNull() ?: continue
                    val href = el.attr("href").takeIf { it.startsWith("http") } ?: continue
                    // Try to infer quality from link text
                    val text = el.text()
                    quality = when {
                        text.contains("1080", ignoreCase = true) -> "1080p"
                        text.contains("720", ignoreCase = true)  -> "720p"
                        text.contains("480", ignoreCase = true)  -> "480p"
                        text.contains("4K", ignoreCase = true)   -> "4K"
                        else -> "HD"
                    }
                    targetUrl = href
                    break
                }

                // Try m3u8 extraction from raw source
                if (targetUrl == null) {
                    targetUrl = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(html)?.value
                        ?: Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(html)?.value
                }

                targetUrl?.let { href ->
                    val resolved = runCatching { scraper.resolveRedirect(href) }.getOrDefault(href)
                    // If redirect leads to another host extractor, recurse
                    when {
                        resolved.contains("hubcloud") || resolved.contains("hubdrive") ->
                            HubCloudExtractor.extract(resolved, scraper)?.copy(server = "GDFlixâ†’HubCloud")
                        else -> {
                            val type = if (resolved.contains(".m3u8")) "m3u8" else "mp4"
                            StreamLink(url = resolved, server = "GDFlix", quality = quality, type = type)
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
}

