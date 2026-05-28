package com.aura.data.scraper

import com.aura.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/**
 * Scraper for animesalt.to (and mirror URLs).
 * Parses anime listings, episode lists, and stream links from HTML.
 */
class AnimeSaltScraper : BaseScraper() {

    companion object {
        const val DEFAULT_BASE = "https://animesalt.to"
    }

    // â”€â”€â”€ Latest Anime â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getLatestAnime(baseUrl: String = DEFAULT_BASE): List<Anime> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(baseUrl)
            parseAnimeCards(doc, baseUrl)
        }

    // â”€â”€â”€ Search â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun searchAnime(query: String, baseUrl: String = DEFAULT_BASE): List<Anime> =
        withContext(Dispatchers.IO) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = getDocument("$baseUrl/?s=$encodedQuery")
            parseAnimeCards(doc, baseUrl)
        }

    // â”€â”€â”€ Details â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getAnimeDetails(url: String): AnimeDetails = withContext(Dispatchers.IO) {
        val doc = getDocument(url)

        val title = doc.select("h1.entry-title, h1.series-title, h1").firstOrNull()?.text()
            ?: doc.select("meta[property=og:title]").attr("content")

        val poster = doc.select(".series-image img, .thumb img, .poster img, img.attachment-post-thumbnail")
            .firstOrNull()?.let { img ->
                img.attr("src").ifEmpty { img.attr("data-src") }
            } ?: doc.select("meta[property=og:image]").attr("content")

        val synopsis = doc.select(".series-synopsis p, .entry-content p, .synopsis, div[class*=desc]")
            .firstOrNull()?.text()
            ?: doc.select("meta[name=description]").attr("content")

        val status = doc.select("*:containsOwn(Status)").firstOrNull()
            ?.nextElementSibling()?.text() ?: ""
        val type = doc.select("*:containsOwn(Type)").firstOrNull()
            ?.nextElementSibling()?.text() ?: ""
        val year = doc.select("*:containsOwn(Year), *:containsOwn(Season)").firstOrNull()
            ?.nextElementSibling()?.text()?.filter { it.isDigit() }?.take(4) ?: ""
        val rating = doc.select(".rating, .score, .imdb, *:containsOwn(Score)").firstOrNull()
            ?.let { it.text().filter { c -> c.isDigit() || c == '.' }.take(4) } ?: ""
        val genre = doc.select(".genres a, .genre a, *:containsOwn(Genre)")
            .firstOrNull()?.parent()?.select("a")?.joinToString(", ") { it.text() } ?: ""

        // Parse episodes list
        val episodes = parseEpisodeList(doc, url)

        AnimeDetails(
            title = title,
            poster = poster,
            synopsis = synopsis,
            status = status,
            type = type,
            year = year,
            rating = rating,
            genre = genre,
            episodes = episodes
        )
    }

    // â”€â”€â”€ Episode Stream Links â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun getEpisodeLinks(episodeUrl: String): List<StreamLink> =
        withContext(Dispatchers.IO) {
            val links = mutableListOf<StreamLink>()

            // Fetch the episode page
            val doc = getDocument(episodeUrl)
            val pageSource = doc.html()

            // 1. Direct m3u8 in page source or scripts
            extractM3U8(pageSource)?.let { m3u8 ->
                links.add(StreamLink(url = m3u8, quality = "Auto", server = "HLS", type = "m3u8"))
            }

            // 2. Embedded script blocks with various formats
            doc.select("script").forEach { script ->
                val src = script.data()
                extractM3U8(src)?.let { m3u8 ->
                    if (links.none { it.url == m3u8 }) {
                        links.add(StreamLink(url = m3u8, quality = "Auto", server = "HLS", type = "m3u8"))
                    }
                }
                // Pattern: file: "url"
                Regex("""file\s*:\s*["']([^"']+)["']""").findAll(src).forEach { match ->
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && links.none { it.url == url }) {
                        val type = when {
                            url.contains(".m3u8") -> "m3u8"
                            url.contains(".mp4") -> "mp4"
                            else -> "stream"
                        }
                        links.add(StreamLink(url = url, quality = "Auto", server = "Player", type = type))
                    }
                }
                // Pattern: source: "url"
                Regex("""source\s*:\s*["']([^"']+)["']""").findAll(src).forEach { match ->
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && links.none { it.url == url }) {
                        links.add(StreamLink(url = url, quality = "Auto", server = "Player", type = "stream"))
                    }
                }
            }

            // 3. Iframe sources â€” follow to extract
            extractIframeSrc(doc)?.let { iframeSrc ->
                val fullSrc = when {
                    iframeSrc.startsWith("http") -> iframeSrc
                    iframeSrc.startsWith("//") -> "https:$iframeSrc"
                    else -> iframeSrc
                }
                try {
                    val iframeSource = getRawPageSource(fullSrc)
                    extractM3U8(iframeSource)?.let { m3u8 ->
                        if (links.none { it.url == m3u8 }) {
                            links.add(StreamLink(url = m3u8, quality = "Auto", server = "Embed", type = "m3u8"))
                        }
                    }
                } catch (e: Exception) { /* ignore iframe fetch failures */ }
            }

            // 4. Server buttons / link tags
            doc.select("a[href][class*=server], a[href][class*=source], div[data-src], div[data-url]")
                .forEach { el ->
                    val url = el.attr("href").ifEmpty { el.attr("data-src") }
                        .ifEmpty { el.attr("data-url") }
                    if (url.startsWith("http") && links.none { it.url == url }) {
                        links.add(StreamLink(url = url, quality = "Auto", server = el.text().take(20), type = "stream"))
                    }
                }

            links.ifEmpty {
                throw ScraperError.ParseFailed
            }
        }

    // â”€â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun parseAnimeCards(doc: Document, baseUrl: String): List<Anime> {
        val animeList = mutableListOf<Anime>()

        val cards = doc.select(
            "article.post, div.item, div.anime-item, div.flw-item, " +
            "li.anime-card, div.entry, ul.anime-list li, div.post-thumbnail"
        )

        for (card in cards) {
            try {
                val title = card.select("h2, h3, .title, .entry-title, .film-name, a[title]")
                    .firstOrNull()?.let { it.text().ifEmpty { it.attr("title") } }
                    ?: continue
                if (title.isBlank()) continue

                val linkEl = card.select("a[href]").firstOrNull() ?: continue
                val rawUrl = linkEl.attr("href")
                val url = when {
                    rawUrl.startsWith("http") -> rawUrl
                    rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                    else -> "$baseUrl/$rawUrl"
                }

                val poster = card.select("img").firstOrNull()?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }
                        .ifEmpty { img.attr("data-lazy-src") }
                } ?: ""

                val status = card.select(".status, .badge[class*=status]").text()
                val type = card.select(".type, .badge[class*=type]").text()
                val year = card.select(".year, .date, time").text().filter { it.isDigit() }.take(4)
                val rating = card.select(".rating, .score").text()
                val genre = card.select(".genre a, .genres a").joinToString(", ") { it.text() }

                animeList.add(Anime(
                    title = title.trim(),
                    url = url,
                    poster = poster,
                    status = status,
                    type = type,
                    year = year,
                    rating = rating,
                    genre = genre
                ))
            } catch (e: Exception) { /* skip bad cards */ }
        }
        return animeList
    }

    private fun parseEpisodeList(doc: Document, baseUrl: String): List<AnimeEpisode> {
        val episodes = mutableListOf<AnimeEpisode>()

        val epElements = doc.select(
            ".episode-list a, .episodes a, ul.episode-list li a, " +
            "div.ep-list a, div.episodes-container a, a[href*=episode]"
        )

        epElements.forEachIndexed { index, el ->
            val title = el.text().ifEmpty { "Episode ${index + 1}" }
            val rawUrl = el.attr("href")
            val url = when {
                rawUrl.startsWith("http") -> rawUrl
                rawUrl.startsWith("/") -> {
                    val base = baseUrl.substringBefore("/", baseUrl)
                    "$base$rawUrl"
                }
                else -> rawUrl
            }
            if (url.isNotBlank()) {
                episodes.add(AnimeEpisode(
                    title = title,
                    episode = (index + 1).toString(),
                    url = url,
                    number = index + 1
                ))
            }
        }
        return episodes
    }
}

