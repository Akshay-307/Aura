package com.aura.data.model

// â”€â”€â”€ Stream Link models â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class StreamLink(
    val server: String = "",
    val url: String = "",
    val link: String = "",
    val quality: String = "",
    val type: String = "" // "m3u8", "mp4", "hls"
) {
    @JvmName("fetchUrl")
    fun getUrl(): String = url.ifEmpty { link }
    fun isHls(): Boolean = type.contains("m3u8", true) || type.contains("hls", true)
            || getUrl().contains(".m3u8")
}

data class StreamLinksResponse(
    val status: Boolean = false,
    val links: List<StreamLink> = emptyList(),
    val streams: List<StreamLink> = emptyList(),
    val data: List<StreamLink> = emptyList(),
    val url: String = ""
) {
    @JvmName("fetchLinks")
    fun getLinks(): List<StreamLink> {
        val all = links + streams + data
        return if (all.isNotEmpty()) all
        else if (url.isNotEmpty()) listOf(StreamLink(url = url, server = "Default", type = guessType(url)))
        else emptyList()
    }

    private fun guessType(u: String): String = when {
        u.contains(".m3u8") -> "m3u8"
        u.contains(".mp4") -> "mp4"
        else -> "unknown"
    }
}

// â”€â”€â”€ NetMirror models â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class NetMirrorPost(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val image: String = "",
    val year: String = "",
    val genre: String = "",
    val rating: String = "",
    val type: String = "",
    val url: String = ""
) {
    @JvmName("fetchPoster")
    fun getPoster(): String = poster.ifEmpty { image }
}

data class NetMirrorHomeResponse(
    val status: Boolean = false,
    val results: List<NetMirrorPost> = emptyList(),
    val data: List<NetMirrorPost> = emptyList(),
    val featured: List<NetMirrorPost> = emptyList()
) {
    fun posts(): List<NetMirrorPost> = if (results.isNotEmpty()) results else data
}

data class NetMirrorSearchResponse(
    val status: Boolean = false,
    val results: List<NetMirrorPost> = emptyList(),
    val data: List<NetMirrorPost> = emptyList()
) {
    fun posts(): List<NetMirrorPost> = if (results.isNotEmpty()) results else data
}

data class NetMirrorPostDetails(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val image: String = "",
    val year: String = "",
    val genre: String = "",
    val rating: String = "",
    val description: String = "",
    val synopsis: String = "",
    val episodes: List<StreamEpisode> = emptyList()
) {
    @JvmName("fetchPoster")
    fun getPoster(): String = poster.ifEmpty { image }
    @JvmName("fetchDescription")
    fun getDescription(): String = synopsis.ifEmpty { description }
}

data class NetMirrorPostResponse(
    val status: Boolean = false,
    val data: NetMirrorPostDetails? = null,
    val result: NetMirrorPostDetails? = null
) {
    fun details(): NetMirrorPostDetails? = data ?: result
}

data class StreamEpisode(
    val id: String = "",
    val title: String = "",
    val episode: String = "",
    val number: Int = 0
)

