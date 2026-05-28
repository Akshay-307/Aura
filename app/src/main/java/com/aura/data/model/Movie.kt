package com.aura.data.model

// â”€â”€â”€ Movie models â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class Movie(
    val title: String = "",
    val url: String = "",
    val poster: String = "",
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val quality: String = "",
    val language: String = "",
    val size: String = ""
)

data class MovieDetails(
    val title: String = "",
    val poster: String = "",
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val description: String = "",
    val synopsis: String = "",
    val director: String = "",
    val cast: String = "",
    val language: String = "",
    val quality: String = "",
    val qualityLinks: List<QualityLink> = emptyList(),
    val links: List<QualityLink> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val similar: List<Movie> = emptyList()
) {
    @JvmName("fetchDescription")
    fun getDescription(): String = synopsis.ifEmpty { description }
    @JvmName("fetchLinks")
    fun getLinks(): List<QualityLink> = if (qualityLinks.isNotEmpty()) qualityLinks else links
}

data class QualityLink(
    val quality: String = "",
    val url: String = "",
    val link: String = "",
    val size: String = ""
) {
    @JvmName("fetchUrl")
    fun getUrl(): String = url.ifEmpty { link }
}

data class MagicLinksResponse(
    val status: Boolean = false,
    val links: List<DirectLink> = emptyList(),
    val data: List<DirectLink> = emptyList()
) {
    @JvmName("fetchLinks")
    fun getLinks(): List<DirectLink> = if (links.isNotEmpty()) links else data
}

data class DirectLink(
    val quality: String = "",
    val url: String = "",
    val link: String = "",
    val server: String = ""
) {
    @JvmName("fetchUrl")
    fun getUrl(): String = url.ifEmpty { link }
}

data class Episode(
    val title: String = "",
    val episode: String = "",
    val url: String = "",
    val number: Int = 0
)

