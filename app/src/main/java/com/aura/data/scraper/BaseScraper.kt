package com.aura.data.scraper

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import kotlin.random.Random

abstract class BaseScraper {

    companion object {
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; Redmi Note 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Mobile Safari/537.36"
        )
    }

    protected val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun randomUserAgent(): String = USER_AGENTS[Random.nextInt(USER_AGENTS.size)]

    // â”€â”€â”€ Document fetch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    protected suspend fun getDocument(
        url: String,
        referer: String = "https://www.google.com"
    ): Document {
        kotlinx.coroutines.delay(Random.nextLong(300, 800))
        return Jsoup.connect(url)
            .userAgent(randomUserAgent())
            .referrer(referer)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("DNT", "1")
            .ignoreHttpErrors(true)
            .timeout(15000)
            .maxBodySize(0)
            .get()
    }

    // â”€â”€â”€ Redirect resolution â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun resolveRedirect(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", randomUserAgent())
            .build()
        client.newCall(request).execute().use { response ->
            return response.request.url.toString()
        }
    }

    // â”€â”€â”€ Raw page source â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getRawPageSource(
        url: String,
        referer: String = "https://www.google.com",
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", randomUserAgent())
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute().use { it.body?.string() ?: "" }
    }

    // â”€â”€â”€ POST form â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    protected fun postForm(
        url: String,
        params: Map<String, String>,
        referer: String = "https://www.google.com"
    ): String {
        val bodyStr = params.entries
            .joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        val body = bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", randomUserAgent())
            .header("Referer", referer)
            .post(body)
            .build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    // â”€â”€â”€ Redirect proxy â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    protected fun proxyRedirect(rawUrl: String): String {
        val encoded = java.net.URLEncoder.encode(rawUrl, "UTF-8")
        return getRawPageSource("https://ssbackend-2r7z.onrender.com/api/redirect?url=$encoded")
    }

    // â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun extractM3U8(pageSource: String): String? =
        Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""").find(pageSource)?.value

    fun extractIframeSrc(doc: Document): String? =
        doc.select("iframe[src]").attr("src").takeIf { it.isNotEmpty() }
}

