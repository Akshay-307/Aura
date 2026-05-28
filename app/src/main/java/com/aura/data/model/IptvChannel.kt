package com.aura.data.model

/**
 * Represents a parsed IPTV live TV channel.
 */
data class IptvChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String
)

