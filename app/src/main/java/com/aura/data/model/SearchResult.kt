package com.aura.data.model

/**
 * Unified search result that can represent a Movie, Anime, or NetMirror post.
 * Used in the combined search screen.
 */
data class SearchResult(
    val id: String = "",
    val title: String,
    val posterUrl: String,
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val detailUrl: String = "",
    val contentType: ContentType,
    val netMirrorId: String = ""
)

enum class ContentType {
    MOVIE, ANIME, NET_MIRROR
}

