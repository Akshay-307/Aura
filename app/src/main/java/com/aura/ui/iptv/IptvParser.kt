package com.aura.ui.iptv

import com.aura.data.model.IptvChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader

/**
 * High-performance parser that reads M3U playlists line-by-line
 * and maps them into [IptvChannel] models.
 */
object IptvParser {

    private val client = OkHttpClient()

    fun fetchAndParse(url: String): List<IptvChannel> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parse(body)
        }
    }

    fun parse(m3uContent: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = BufferedReader(StringReader(m3uContent))
        var line = reader.readLine()
        
        var currentLogoUrl = ""
        var currentCategory = "General"
        var currentName = ""
        var hasExtinf = false

        while (line != null) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#EXTINF:")) {
                hasExtinf = true
                currentLogoUrl = extractAttribute(trimmedLine, "tvg-logo").ifBlank {
                    extractAttribute(trimmedLine, "logo")
                }
                currentCategory = extractAttribute(trimmedLine, "group-title").ifBlank {
                    extractAttribute(trimmedLine, "category")
                }.ifBlank { "General" }

                // Channel name is everything after the last comma
                val commaIndex = trimmedLine.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmedLine.length - 1) {
                    trimmedLine.substring(commaIndex + 1).trim()
                } else {
                    "Unknown Channel"
                }
            } else if (hasExtinf && trimmedLine.startsWith("http")) {
                channels.add(
                    IptvChannel(
                        id = md5(trimmedLine),
                        name = currentName,
                        logoUrl = currentLogoUrl,
                        streamUrl = trimmedLine,
                        category = currentCategory
                    )
                )
                hasExtinf = false
                currentLogoUrl = ""
                currentCategory = "General"
                currentName = ""
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun extractAttribute(line: String, key: String): String {
        val pattern = "$key=\""
        val start = line.indexOf(pattern)
        if (start == -1) return ""
        val startPos = start + pattern.length
        val end = line.indexOf("\"", startPos)
        if (end == -1) return ""
        return line.substring(startPos, end).trim()
    }
}

