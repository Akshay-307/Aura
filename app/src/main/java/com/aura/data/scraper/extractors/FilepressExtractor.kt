package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Extracts direct stream URLs from Filepress / BigWarp.
 * These pages often have a simple video tag, a jwplayer config, or an explicit download button.
 */
object FilepressExtractor {

    suspend fun extract(url: String, scraper: BaseScraper): StreamLink? =
        withContext(Dispatchers.IO) {
            try {
                // Must pass the URL itself as referer for Filepress
                val html = scraper.getRawPageSource(url, referer = url)
                val doc = Jsoup.parse(html)

                // 1. Look for direct video tag source
                var directUrl = doc.select("video source[src]").attr("src").takeIf { it.isNotEmpty() }

                // 2. Look for download button
                if (directUrl == null) {
                    directUrl = doc.select("a.btn-success[href*=.mp4], a.btn-primary[href*=.mkv]")
                        .firstOrNull()?.attr("href")
                }

                // 3. Fallback to regex for jwplayer config or hidden links
                if (directUrl == null) {
                    directUrl = Regex("""file:\s*["'](https?://[^"']+\.mp4)["']""").find(html)?.groupValues?.get(1)
                        ?: Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(html)?.value
                }

                if (directUrl != null && directUrl.startsWith("http")) {
                    val resolved = runCatching { scraper.resolveRedirect(directUrl) }.getOrDefault(directUrl)
                    StreamLink(
                        url = resolved,
                        server = "Filepress",
                        quality = "HD",
                        type = if (resolved.contains(".m3u8")) "m3u8" else "mp4"
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
}

