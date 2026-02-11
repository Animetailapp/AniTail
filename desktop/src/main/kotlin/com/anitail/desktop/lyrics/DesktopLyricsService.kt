package com.anitail.desktop.lyrics

import com.anitail.desktop.storage.DesktopPreferences

/**
 * Lyrics service for Desktop that follows Android provider priority behavior.
 */
object DesktopLyricsService {

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Int = -1,
        videoId: String? = null,
        album: String? = null,
        preferences: DesktopPreferences = DesktopPreferences.getInstance(),
    ): Result<LyricsResult> = runCatching {
        val query = LyricsQuery(
            videoId = resolveVideoId(videoId),
            title = title,
            artist = artist,
            durationSec = durationSec,
            album = album,
        )

        val providers = DesktopLyricsProviders.ordered(preferences.preferredLyricsProvider.value)
        var firstError: Throwable? = null

        for (provider in providers) {
            if (!provider.isEnabled(preferences)) {
                continue
            }
            val result = runCatching { provider.getLyrics(query).getOrThrow() }
            result.onSuccess { lyrics ->
                return@runCatching buildLyricsResult(
                    title = title,
                    artist = artist,
                    album = album,
                    durationSec = durationSec,
                    rawLyrics = lyrics,
                    provider = provider.name,
                )
            }.onFailure { error ->
                if (firstError == null) {
                    firstError = error
                }
            }
        }

        throw firstError ?: IllegalStateException("No lyrics providers returned a result")
    }

    suspend fun getAllLyricsOptions(
        title: String,
        artist: String,
        durationSec: Int = -1,
        videoId: String? = null,
        album: String? = null,
        preferences: DesktopPreferences = DesktopPreferences.getInstance(),
    ): List<LyricsResult> {
        val query = LyricsQuery(
            videoId = resolveVideoId(videoId),
            title = title,
            artist = artist,
            durationSec = durationSec,
            album = album,
        )
        val providers = DesktopLyricsProviders.ordered(preferences.preferredLyricsProvider.value)
        val results = mutableListOf<LyricsResult>()
        for (provider in providers) {
            if (!provider.isEnabled(preferences)) {
                continue
            }
            runCatching {
                provider.getAllLyrics(query) { lyrics ->
                    results += buildLyricsResult(
                        title = title,
                        artist = artist,
                        album = album,
                        durationSec = durationSec,
                        rawLyrics = lyrics,
                        provider = provider.name,
                    )
                }
            }
        }
        return results
    }

    /**
     * Parse synchronized LRC lyrics into timestamped lines.
     */
    fun parseTimedLyrics(syncedLyrics: String?): List<LyricLine> {
        if (syncedLyrics.isNullOrBlank()) return emptyList()

        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        return syncedLyrics
            .trim()
            .lines()
            .filter { line -> line.length >= 10 && line.startsWith("[") }
            .mapNotNull { line ->
                regex.matchEntire(line)?.let { match ->
                    val minutes = match.groupValues[1].toIntOrNull() ?: 0
                    val seconds = match.groupValues[2].toIntOrNull() ?: 0
                    val millis = match.groupValues[3].let { value ->
                        when (value.length) {
                            2 -> (value.toIntOrNull() ?: 0) * 10
                            3 -> value.toIntOrNull() ?: 0
                            else -> 0
                        }
                    }
                    val text = match.groupValues[4]
                        .replace(Regex("<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*"), "")
                        .trim()
                    val timestampMs = (minutes * 60 * 1000L) + (seconds * 1000L) + millis
                    LyricLine(timestampMs = timestampMs, text = text)
                }
            }
            .sortedBy { it.timestampMs }
    }

    private fun buildLyricsResult(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
        rawLyrics: String,
        provider: String,
    ): LyricsResult {
        val trimmedLyrics = rawLyrics.trim()
        val parsedLines = if (trimmedLyrics.startsWith("[")) parseTimedLyrics(trimmedLyrics) else emptyList()
        val syncedLyrics = trimmedLyrics.takeIf { parsedLines.isNotEmpty() }
        val plainLyrics = if (parsedLines.isEmpty()) trimmedLyrics else null

        return LyricsResult(
            trackName = title,
            artistName = artist,
            albumName = album,
            duration = durationSec.toDouble().coerceAtLeast(0.0),
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            parsedLines = parsedLines,
            provider = provider,
        )
    }

    private fun resolveVideoId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        val patterns = listOf(
            Regex("watch\\?v=([^&]+)"),
            Regex("youtu\\.be/([^?]+)"),
            Regex("/watch/([^?]+)"),
            Regex("v=([^&]+)"),
        )
        for (pattern in patterns) {
            val candidate = pattern.find(trimmed)?.groupValues?.getOrNull(1)
            if (!candidate.isNullOrBlank()) {
                return candidate
            }
        }
        return trimmed.takeIf { it.matches(Regex("^[a-zA-Z0-9_-]{11}$")) }
    }
}

/**
 * Lyrics search result.
 */
data class LyricsResult(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val parsedLines: List<LyricLine>,
    val provider: String,
) {
    val hasSyncedLyrics: Boolean get() = !syncedLyrics.isNullOrBlank() && parsedLines.isNotEmpty()
    val hasPlainLyrics: Boolean get() = !plainLyrics.isNullOrBlank()

    fun getCurrentLine(positionMs: Long): LyricLine? {
        if (parsedLines.isEmpty()) return null
        return parsedLines.lastOrNull { it.timestampMs <= positionMs }
    }

    fun getCurrentLineIndex(positionMs: Long): Int {
        if (parsedLines.isEmpty()) return -1
        return parsedLines.indexOfLast { it.timestampMs <= positionMs }
    }
}

/**
 * Lyric line with timestamp.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
) {
    val formattedTime: String
        get() {
            val minutes = timestampMs / 60000
            val seconds = (timestampMs % 60000) / 1000
            return "%02d:%02d".format(minutes, seconds)
        }
}
