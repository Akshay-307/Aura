package com.aura.data.model

// â”€â”€â”€ Anime models â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class Anime(
    val title: String = "",
    val url: String = "",
    val poster: String = "",
    val image: String = "",
    val year: String = "",
    val status: String = "",
    val type: String = "",
    val genre: String = "",
    val rating: String = "",
    val episodes: String = ""
) {
    @JvmName("fetchPoster")
    fun getPoster(): String = poster.ifEmpty { image }
}

data class AnimeDetails(
    val title: String = "",
    val poster: String = "",
    val image: String = "",
    val year: String = "",
    val status: String = "",
    val type: String = "",
    val genre: String = "",
    val rating: String = "",
    val synopsis: String = "",
    val description: String = "",
    val episodes: List<AnimeEpisode> = emptyList(),
    val similar: List<Anime> = emptyList()
) {
    @JvmName("fetchPoster")
    fun getPoster(): String = poster.ifEmpty { image }
    @JvmName("fetchDescription")
    fun getDescription(): String = synopsis.ifEmpty { description }
}

data class AnimeEpisode(
    val title: String = "",
    val episode: String = "",
    val url: String = "",
    val number: Int = 0
)

