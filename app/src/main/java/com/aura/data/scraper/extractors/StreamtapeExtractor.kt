package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts direct mp4 URL from Streamtape by reconstructing the obfuscated URL
 * from the <div id="div_download"> element or the robot-trap script.
 */
object StreamtapeExtractor {

    suspend fun extract(url: String, scraper: BaseScraper): StreamLink? =
        withContext(Dispatchers.IO) {
            try {
                // Ensure we use the embed/e domain which often has the direct link exposed
                val targetUrl = url.replace("/v/", "/e/")
                val html = scraper.getRawPageSource(targetUrl, referer = "https://www.google.com")

                // Streamtape obfuscates the link in a script like:
                // document.getElementById('videolink').innerHTML = "/get_video?id=X..." + "...";
                
                val part1Match = Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*'//([^']+)';""").find(html)
                val part2Match = Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*'//[^']+'\+\s*'([^']+)';""").find(html)
                
                var finalUrl: String? = null

                if (part1Match != null && part2Match != null) {
                    val p1 = part1Match.groupValues[1]
                    val p2 = part2Match.groupValues[1]
                    finalUrl = "https://$p1$p2"
                }

                // Fallback regex if the DOM structure changed
                if (finalUrl == null) {
                    val rawLinkMatch = Regex("""['"](/get_video\?[^'"]+)['"]""").find(html)
                    if (rawLinkMatch != null) {
                        finalUrl = "https://streamtape.com${rawLinkMatch.groupValues[1]}"
                    }
                }

                if (finalUrl != null) {
                    StreamLink(
                        url = finalUrl,
                        server = "Streamtape",
                        quality = "Auto",
                        type = "mp4"
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
}

