package com.aura.data.scraper

sealed class ScraperError : Exception() {
    object SiteDown : ScraperError() {
        override val message = "This provider is temporarily unavailable. Try another provider."
    }
    object NoResults : ScraperError() {
        override val message = "No results found."
    }
    object ParseFailed : ScraperError() {
        override val message = "Content unavailable. Try again later."
    }
    object NetworkTimeout : ScraperError() {
        override val message = "Connection timed out. Check your internet connection."
    }
    object BlockedBySite : ScraperError() {
        override val message = "Switching to backup provider..."
    }
    data class Unknown(override val message: String) : ScraperError()
}

fun Exception.toScraperError(): ScraperError = when {
    message?.contains("timeout", ignoreCase = true) == true ||
    message?.contains("timed out", ignoreCase = true) == true ->
        ScraperError.NetworkTimeout
    message?.contains("403", ignoreCase = true) == true ||
    message?.contains("blocked", ignoreCase = true) == true ||
    message?.contains("forbidden", ignoreCase = true) == true ->
        ScraperError.BlockedBySite
    message?.contains("500", ignoreCase = true) == true ||
    message?.contains("502", ignoreCase = true) == true ||
    message?.contains("503", ignoreCase = true) == true ->
        ScraperError.SiteDown
    message?.contains("parse", ignoreCase = true) == true ||
    message?.contains("null", ignoreCase = true) == true ->
        ScraperError.ParseFailed
    else -> ScraperError.Unknown(message ?: "Unknown scraping error")
}

