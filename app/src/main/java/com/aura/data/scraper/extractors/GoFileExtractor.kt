package com.aura.data.scraper.extractors

import com.aura.data.model.StreamLink
import com.aura.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Extracts direct stream URLs from GoFile using their public API.
 * Format: gofile.io/d/{code} -> api.gofile.io/contents/{code} -> child links
 */
object GoFileExtractor {

    private const val API_BASE = "https://api.gofile.io"
    // Guest token used by ScreenScape
    private const val GUEST_WT = "4fd6sg89d7s6"

    suspend fun extract(url: String, scraper: BaseScraper): StreamLink? =
        withContext(Dispatchers.IO) {
            try {
                // 1. Extract file code from URL
                val code = Regex("""gofile\.io/d/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1)
                    ?: return@withContext null

                // 2. Fetch folder contents from GoFile API
                val apiUrl = "$API_BASE/contents/$code?wt=$GUEST_WT"
                val jsonStr = scraper.getRawPageSource(apiUrl, referer = url)
                if (jsonStr.isEmpty()) return@withContext null

                val json = JSONObject(jsonStr)
                if (json.optString("status") != "ok") return@withContext null

                val data = json.optJSONObject("data") ?: return@withContext null
                val children = data.optJSONObject("children") ?: return@withContext null

                // 3. Find the first playable child (.mp4 or .m3u8)
                val keys = children.keys()
                while (keys.hasNext()) {
                    val childId = keys.next()
                    val child = children.optJSONObject(childId) ?: continue
                    val childUrl = child.optString("link")
                    if (childUrl.isNotEmpty() && (childUrl.endsWith(".mp4") || childUrl.endsWith(".m3u8"))) {
                        val type = if (childUrl.endsWith(".m3u8")) "m3u8" else "mp4"
                        return@withContext StreamLink(
                            url = childUrl,
                            server = "GoFile",
                            quality = "Auto",
                            type = type
                        )
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
}

