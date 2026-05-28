package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * HubCloud works like this:
 *  1. The content site links to hubcloud.tld/drive/ or similar.
 *  2. That page has a download button or "Watch Online" button.
 *  4. The final response contains a direct CDN URL (mp4 or m3u8).
 */
object HubCloudExtractor {

    private const val BYPASS_URL = "https://gamerxyt.com/hubcloud.php?url="

    suspend fun extract(url: String, scraper: BaseScraper): StreamLink? =
        withContext(Dispatchers.IO) {
            try {
                // Try direct page first
                val html = scraper.getRawPageSource(url, referer = url)
                val doc = Jsoup.parse(html)

                // Look for direct download / watch online anchors
                val directSelectors = listOf(
                    "#zipBtn", ".btn.zip", "a.btn-success",
                    "a[href*=workers.dev]", "a[href*=.mp4]", "a[href*=.m3u8]",
                    "#direct-link", ".download-btn", "a[class*=download]"
                )

                var directUrl: String? = null
                for (selector in directSelectors) {
                    val el = doc.select(selector).firstOrNull() ?: continue
                    val href = el.attr("href").takeIf { it.startsWith("http") } ?: continue
                    directUrl = href
                    break
                }

                // If nothing found, try the bypass helper
                if (directUrl == null) {
                    val bypassHtml = scraper.getRawPageSource(
                        "$BYPASS_URL${java.net.URLEncoder.encode(url, "UTF-8")}",
                        referer = url
                    )
                    val bypassDoc = Jsoup.parse(bypassHtml)
                    for (selector in directSelectors) {
                        val el = bypassDoc.select(selector).firstOrNull() ?: continue
                        val href = el.attr("href").takeIf { it.startsWith("http") } ?: continue
                        directUrl = href
                        break
                    }
                }

                // Also try extracting m3u8 directly from page source
                if (directUrl == null) {
                    directUrl = scraper.extractM3U8(html) // uses protected helper via inner object
                        ?: Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(html)?.value
                }

                directUrl?.let { finalUrl ->
                    // Follow any remaining redirect
                    val resolved = runCatching { scraper.resolveRedirect(finalUrl) }.getOrDefault(finalUrl)
                    val type = when {
                        resolved.contains(".m3u8") -> "m3u8"
                        else -> "mp4"
                    }
                    StreamLink(url = resolved, server = "HubCloud", quality = "HD", type = type)
                }
            } catch (_: Exception) {
                null
            }
        }
}

