package com.anitail.simpmusic

import com.anitail.simpmusic.models.LyricsData
import com.anitail.simpmusic.models.SimpMusicApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

object SimpMusicLyrics {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
    private val htmlHexEntityRegex = "&#x([0-9A-Fa-f]{1,6});".toRegex()
    private val htmlDecEntityRegex = "&#(\\d{1,7});".toRegex()

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
                header(HttpHeaders.ContentType, "application/json")
            }

            expectSuccess = false
        }
    }

    suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> = runCatching {
        println("[SimpMusic] Fetching lyrics for videoId: $videoId")
        println("[SimpMusic] URL: ${BASE_URL + videoId}")
        
        val response = client.get(BASE_URL + videoId)
        
        println("[SimpMusic] Response status: ${response.status}")
        if (response.status == HttpStatusCode.OK) {
            val apiResponse = response.body<SimpMusicApiResponse>()
            println("[SimpMusic] API success: ${apiResponse.success}, tracks: ${apiResponse.data.size}")
            if (apiResponse.success) {
                apiResponse.data.forEachIndexed { idx, track ->
                    println("[SimpMusic] Track $idx: duration=${track.duration}, hasRichSync=${track.richSyncLyrics != null}, hasSynced=${track.syncedLyrics != null}, hasPlain=${track.plainLyrics != null}")
                }
                apiResponse.data
            } else {
                println("[SimpMusic] API returned success=false")
                emptyList()
            }
        } else {
            println("[SimpMusic] HTTP ${response.status}")
            emptyList()
        }
    }.getOrElse { e ->
        if (e is CancellationException) {
            throw e
        }
        println("[SimpMusic] Exception in getLyricsByVideoId: ${e.message}")
        e.printStackTrace()
        emptyList()
    }

    suspend fun getLyrics(
        videoId: String,
        duration: Int = 0,
    ): Result<String> = runCatching {
        val tracks = getLyricsByVideoId(videoId)
        
        if (tracks.isEmpty()) {
            throw IllegalStateException("No lyrics found for videoId: $videoId")
        }

        val bestMatch = if (duration > 0 && tracks.size > 1) {
            // Relaxed duration matching: ±30 seconds instead of ±10
            val closeMatches = tracks.filter { track ->
                abs((track.duration ?: 0) - duration) <= 30
            }
            closeMatches.minByOrNull { track ->
                abs((track.duration ?: 0) - duration)
            } ?: tracks.firstOrNull()
        } else {
            tracks.firstOrNull()
        }

        // Prioritize richSyncLyrics for word-by-word sync, then syncedLyrics, then plainLyrics
        val lyrics = bestMatch?.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Track found but all lyrics empty for videoId: $videoId")

        decodeHtmlEntities(lyrics)
    }

    suspend fun getAllLyrics(
        videoId: String,
        duration: Int = 0,
        callback: (String) -> Unit,
    ) {
        val tracks = getLyricsByVideoId(videoId)
        var count = 0
        var plain = 0

        val sortedTracks = if (duration > 0) {
            tracks.sortedBy { abs((it.duration ?: 0) - duration) }
        } else {
            tracks
        }

        sortedTracks.forEach { track ->
            if (count <= 4) {
                // Check duration match - relaxed to 30 seconds or skip if duration is 0
                val durationMatch = duration <= 0 || abs((track.duration ?: 0) - duration) <= 30

                // Prioritize richSyncLyrics for word-by-word sync
                if (track.richSyncLyrics != null && track.richSyncLyrics.isNotBlank() && durationMatch) {
                    count++
                    callback(decodeHtmlEntities(track.richSyncLyrics))
                } else if (track.syncedLyrics != null && track.syncedLyrics.isNotBlank() && durationMatch) {
                    count++
                    callback(decodeHtmlEntities(track.syncedLyrics))
                }
                if (track.plainLyrics != null && track.plainLyrics.isNotBlank() && durationMatch && plain == 0) {
                    count++
                    plain++
                    callback(decodeHtmlEntities(track.plainLyrics))
                }
            }
        }
    }

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text

        var decoded = text
        repeat(2) {
            decoded = decodeHtmlEntitiesPass(decoded)
        }
        return decoded
    }

    private fun decodeHtmlEntitiesPass(text: String): String {
        var decoded = text
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

        decoded = htmlHexEntityRegex.replace(decoded) { match ->
            match.groupValues[1].toIntOrNull(16)?.toUnicodeCharString() ?: match.value
        }

        decoded = htmlDecEntityRegex.replace(decoded) { match ->
            match.groupValues[1].toIntOrNull()?.toUnicodeCharString() ?: match.value
        }

        return decoded
    }

    private fun Int.toUnicodeCharString(): String? {
        if (this !in 0..0x10FFFF) return null
        return try {
            String(Character.toChars(this))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
