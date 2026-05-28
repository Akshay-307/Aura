package com.aura.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Fetches live provider domain config from ScreenScape's GitHub Pages at startup.
 * Falls back silently to hardcoded defaults if offline or 404.
 */
object RemoteConfigManager {

    private const val PROVIDERS_URL =
        "https://anshu78780.github.io/json/providers.json"

    // Key â†’ base URL (no trailing slash)
    private val providers = mutableMapOf<String, String>()
    private var initialised = false

    suspend fun init() = withContext(Dispatchers.IO) {
        if (initialised) return@withContext
        try {
            val raw = URL(PROVIDERS_URL).openStream()
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(raw)
            obj.keys().forEach { key ->
                val urlVal = obj.optJSONObject(key)?.optString("url") ?: ""
                if (urlVal.startsWith("http")) {
                    providers[key] = urlVal.trimEnd('/')
                }
            }
            initialised = true
        } catch (_: Exception) {
            // silently use hardcoded defaults
        }
    }

    /** Returns the live URL for [key], or [default] if not fetched yet. */
    fun url(key: String, default: String): String =
        providers[key]?.takeIf { it.isNotBlank() } ?: default

    // â”€â”€â”€ Convenience accessors with hardcoded fallbacks â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun movies4uUrl()  = url("movies4u",   "https://movies4u.promo")
    fun zeeflizUrl()   = url("zeefliz",    "https://zeefliz.beer")
    fun filmycabUrl()  = url("filmyclub",  "https://filmycab.co")
    fun hianimeUrl()   = url("hianime",    "https://hianime.mx")
    fun nfMirrorUrl()  = url("nfMirror",   "https://net22.cc")
    fun consumetUrl()  = url("consumet",   "https://consumet.zendax.tech")
    fun kmMoviesUrl()  = url("KMMovies",   "https://kmmovies.mom")
    fun animeSaltUrl() = url("animesalt",  "https://animesalt.ac")
}

