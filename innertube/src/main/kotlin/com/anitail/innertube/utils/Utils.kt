package com.anitail.innertube.utils

import com.anitail.innertube.YouTube
import com.anitail.innertube.pages.LibraryPage
import com.anitail.innertube.pages.PlaylistPage
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50 // Prevent excessive API calls

    while (continuation != null && requestCount < maxRequests) {
        // Prevent infinite loops by tracking seen continuations
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrThrow()
        songs += continuationPage.songs
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50 // Prevent excessive API calls

    while (continuation != null && requestCount < maxRequests) {
        // Prevent infinite loops by tracking seen continuations
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrThrow()
        items += continuationPage.items
        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        continuation = page.continuation
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

/**
 * Sanitizes a YouTube/InnerTube cookie string by stripping any token prefixes
 * (such as ***INNERTUBE COOKIE*** = or *INNERTUBE COOKIE* =), DevTools header labels,
 * wrapping quotes, and leading delimiters.
 */
fun sanitizeCookieString(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var cleaned = raw.trim()

    // If multiline token or block was passed, find the line containing the actual cookie or SAPISID
    if (cleaned.contains('\n')) {
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val cookieLine = lines.firstOrNull { line ->
            line.contains("SAPISID=", ignoreCase = true) ||
                line.contains("__Secure-", ignoreCase = true) ||
                line.contains("INNERTUBE COOKIE", ignoreCase = true)
        } ?: lines.firstOrNull() ?: ""
        cleaned = cookieLine
    }

    // Strip any prefix like ***INNERTUBE COOKIE***, *INNERTUBE COOKIE*, INNERTUBE COOKIE
    if (cleaned.contains("INNERTUBE COOKIE", ignoreCase = true)) {
        cleaned = cleaned.substringAfter("INNERTUBE COOKIE")
            .trimStart('*', ' ', '\t')
    }

    // Strip leading "Cookie:" or "cookie:" if copied directly from DevTools headers
    if (cleaned.startsWith("Cookie:", ignoreCase = true)) {
        cleaned = cleaned.substringAfter(":").trim()
    }

    // Strip leading '=' if remaining
    if (cleaned.startsWith("=")) {
        cleaned = cleaned.removePrefix("=").trim()
    }

    // Strip wrapping quotes
    if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length > 1) {
        cleaned = cleaned.substring(1, cleaned.length - 1).trim()
    }

    return cleaned.ifBlank { null }
}

fun parseCookieString(cookie: String): Map<String, String> {
    val clean = sanitizeCookieString(cookie) ?: return emptyMap()
    return clean.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else {
                val key = part.substring(0, splitIndex).trim()
                val value = part.substring(splitIndex + 1).trim()
                if (key.isNotEmpty()) key to value else null
            }
        }
        .toMap()
}

fun String.parseTime(): Int? {
    try {
        val parts = split(":").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
