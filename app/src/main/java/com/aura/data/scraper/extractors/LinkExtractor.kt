package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Dispatcher: detects the file host from a URL and calls the right extractor.
 */
object LinkExtractor {

    private val scraper = object : BaseScraper() {}

    suspend fun resolve(url: String): StreamLink? = withContext(Dispatchers.IO) {
        when {
            // Direct playable URLs â€” return immediately, no extra fetch needed
            url.contains(".m3u8") ->
                StreamLink(url = url, server = "Direct HLS", quality = "Auto", type = "m3u8")
            url.contains(".mp4") && !url.contains("hubcloud") && !url.contains("gdflix") ->
                StreamLink(url = url, server = "Direct MP4", quality = "Auto", type = "mp4")

            // File hosts â€” extract real stream URL
            url.contains("hubcloud") || url.contains("hubdrive") || url.contains("hubcloud.ink") ||
            url.contains("hubcloud.foo") || url.contains("hubcloud.one") ->
                HubCloudExtractor.extract(url, scraper)

            url.contains("gdflix") || url.contains("gdflix.world") ->
                GDFlixExtractor.extract(url, scraper)

            url.contains("gofile.io") ->
                GoFileExtractor.extract(url, scraper)

            url.contains("streamtape") || url.contains("strcloud") ->
                StreamtapeExtractor.extract(url, scraper)

            url.contains("filepress") || url.contains("bigwarp") || url.contains("new1.filepress") ->
                FilepressExtractor.extract(url, scraper)

            // Unknown â€” try returning raw and let ExoPlayer figure it out
            url.startsWith("http") ->
                StreamLink(url = url, server = "Auto", quality = "Auto", type = "stream")

            else -> null
        }
    }
}

