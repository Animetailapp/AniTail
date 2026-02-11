package com.anitail.desktop.lyrics

import com.anitail.desktop.storage.DesktopPreferences
import kotlin.coroutines.cancellation.CancellationException

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
    ): Result<LyricsResult> = try {
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
                return Result.success(
                    buildLyricsResult(
                        title = title,
                        artist = artist,
                        album = album,
                        durationSec = durationSec,
                        rawLyrics = lyrics,
                        provider = provider.name,
                    ),
                )
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                if (firstError == null) {
                    firstError = error
                }
            }
        }

        throw firstError ?: IllegalStateException("No lyrics providers returned a result")
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
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
            try {
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Ignore provider-specific failures and continue collecting alternatives.
            }
        }
        return results
    }

    /**
     * Parse synchronized LRC lyrics into timestamped lines.
     */
    fun parseTimedLyrics(syncedLyrics: String?): List<LyricLine> {
        if (syncedLyrics.isNullOrBlank()) return emptyList()

        val lineRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        val wordRegex = Regex("<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]*)")

        data class ParsedWordStart(
            val text: String,
            val startMs: Long,
        )

        data class ParsedLine(
            val timestampMs: Long,
            val text: String,
            val wordStarts: List<ParsedWordStart>,
        )

        val parsedLines = syncedLyrics
            .trim()
            .lines()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.length < 10 || !line.startsWith("[")) return@mapNotNull null
                val match = lineRegex.matchEntire(line) ?: return@mapNotNull null

                val lineTimestampMs = parseTimestampToMs(
                    minutes = match.groupValues[1],
                    seconds = match.groupValues[2],
                    fraction = match.groupValues[3],
                ) ?: return@mapNotNull null

                val content = match.groupValues[4]
                val wordStarts = wordRegex.findAll(content).mapNotNull { wordMatch ->
                    val wordStartMs = parseTimestampToMs(
                        minutes = wordMatch.groupValues[1],
                        seconds = wordMatch.groupValues[2],
                        fraction = wordMatch.groupValues[3],
                    ) ?: return@mapNotNull null
                    val wordText = wordMatch.groupValues[4].trim()
                    if (wordText.isBlank()) return@mapNotNull null
                    ParsedWordStart(text = wordText, startMs = wordStartMs)
                }.toList()

                val normalizedText = if (wordStarts.isNotEmpty()) {
                    wordStarts.joinToString(" ") { it.text }
                } else {
                    content.replace(Regex("<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*"), "").trim()
                }

                ParsedLine(
                    timestampMs = lineTimestampMs,
                    text = normalizedText,
                    wordStarts = wordStarts,
                )
            }

        return parsedLines
            .mapIndexed { index, line ->
                val nextLineTimestamp = parsedLines.getOrNull(index + 1)?.timestampMs
                    ?: (line.timestampMs + DefaultLineDurationMs)
                val wordTimestamps = if (line.wordStarts.isNotEmpty()) {
                    line.wordStarts.mapIndexed { wordIndex, word ->
                        val nextWordStart = line.wordStarts.getOrNull(wordIndex + 1)?.startMs ?: nextLineTimestamp
                        val endMs = nextWordStart.coerceAtLeast(word.startMs + MinWordDurationMs)
                        WordTimestamp(
                            text = word.text,
                            startMs = word.startMs,
                            endMs = endMs,
                        )
                    }
                } else {
                    null
                }
                LyricLine(
                    timestampMs = line.timestampMs,
                    text = line.text,
                    wordTimestamps = wordTimestamps,
                )
            }
            .sortedBy { it.timestampMs }
    }

    private fun parseTimestampToMs(minutes: String, seconds: String, fraction: String): Long? {
        val minutesPart = minutes.toLongOrNull() ?: return null
        val secondsPart = seconds.toLongOrNull() ?: return null
        val fractionPartRaw = fraction.toLongOrNull() ?: return null
        val millis = if (fraction.length == 3) fractionPartRaw else fractionPartRaw * 10
        return (minutesPart * 60_000L) + (secondsPart * 1_000L) + millis
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
    
    private const val DefaultLineDurationMs = 2_500L
    private const val MinWordDurationMs = 80L
}

/**
 * Word timestamp for rich synced lyrics.
 */
data class WordTimestamp(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

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
    val wordTimestamps: List<WordTimestamp>? = null,
) {
    val formattedTime: String
        get() {
            val minutes = timestampMs / 60000
            val seconds = (timestampMs % 60000) / 1000
            return "%02d:%02d".format(minutes, seconds)
        }
}
