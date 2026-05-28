package com.aura.utils

object Constants {
    const val BASE_URL = "https://screenscapeapi.dev/"
    const val API_URL_SITE = "https://screenscapeapi.dev"

    // Intent keys
    const val EXTRA_DETAIL_URL = "extra_detail_url"
    const val EXTRA_CONTENT_TYPE = "extra_content_type"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_POSTER_URL = "extra_poster_url"
    const val EXTRA_NET_MIRROR_ID = "extra_net_mirror_id"
    const val EXTRA_STREAM_LINKS = "extra_stream_links"
    const val EXTRA_STREAM_URL = "extra_stream_url"
    const val EXTRA_STREAM_IS_HLS = "extra_stream_is_hls"

    // Player
    const val SEEK_INCREMENT_MS = 10_000L
    const val POSITION_UPDATE_INTERVAL_MS = 5_000L

    // Quality options
    val QUALITY_OPTIONS = listOf("Auto", "1080p", "720p", "480p", "360p")

    // Speed options
    val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    // Start.io Ads
    const val STARTIO_APP_ID = "204183705"
    const val STARTIO_TEST_MODE = false
    const val MAX_AD_RETRY_COUNT = 3
    const val AD_RETRY_DELAY_MS = 1500L


}


