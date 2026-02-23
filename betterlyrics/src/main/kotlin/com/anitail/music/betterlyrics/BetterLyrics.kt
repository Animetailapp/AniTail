package com.anitail.music.betterlyrics

import com.anitail.music.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

object BetterLyrics {
    private data class QueryVariant(
        val artist: String,
        val title: String,
        val duration: Int,
        val album: String?,
    )

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lyrics-api.boidu.dev")
            }

            expectSuccess = false
        }
    }

    private fun buildQueryVariants(
        artist: String,
        title: String,
        duration: Int,
        album: String?,
    ): List<QueryVariant> = buildList {
        add(QueryVariant(artist = artist, title = title, duration = duration, album = album))
        if (!album.isNullOrBlank()) {
            add(QueryVariant(artist = artist, title = title, duration = duration, album = null))
        }
        if (duration > 0) {
            add(QueryVariant(artist = artist, title = title, duration = -1, album = null))
        }
    }.distinct()

    private suspend fun fetchTTML(
        query: QueryVariant,
    ): String? {
        val params = buildString {
            append("s=${query.title}")
            append(", a=${query.artist}")
            if (query.duration > 0) append(", d=${query.duration}")
            if (!query.album.isNullOrBlank()) append(", al=${query.album}")
        }
        println("[BetterLyrics] Fetching: $params")

        try {
            val response = client.get("/getLyrics") {
                parameter("s", query.title)
                parameter("a", query.artist)
                if (query.duration > 0) {
                    parameter("d", query.duration)
                }
                if (!query.album.isNullOrBlank()) {
                    parameter("al", query.album)
                }
            }

            println("[BetterLyrics] Response status: ${response.status}")
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<TTMLResponse>()
                println("[BetterLyrics] TTML received: ${body.ttml?.take(100)}...")
                body.ttml?.takeIf { it.isNotBlank() }
            } else {
                println("[BetterLyrics] HTTP ${response.status}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            println("[BetterLyrics] Request timeout: ${e.message}")
            throw e
        } catch (e: Exception) {
            println("[BetterLyrics] Exception in fetchTTML: ${e.message}")
            throw e
        }
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        val normalizedAlbum = album?.trim()?.takeIf { it.isNotEmpty() }

        require(normalizedTitle.isNotEmpty()) { "Song title is blank" }
        require(normalizedArtist.isNotEmpty()) { "Artist is blank; skipping BetterLyrics lookup" }

        val attempts = buildQueryVariants(
            artist = normalizedArtist,
            title = normalizedTitle,
            duration = duration,
            album = normalizedAlbum,
        )

        var ttml: String? = null
        for ((index, query) in attempts.withIndex()) {
            ttml = try {
                fetchTTML(query)
            } catch (e: HttpRequestTimeoutException) {
                throw IllegalStateException(
                    "BetterLyrics request timed out on attempt ${index + 1}/${attempts.size}",
                    e,
                )
            }
            if (!ttml.isNullOrBlank()) {
                break
            }
        }

        if (ttml.isNullOrBlank()) {
            throw IllegalStateException("Lyrics unavailable after all fallbacks")
        }

        val parsedLines = TTMLParser.parseTTML(ttml)
        if (parsedLines.isEmpty()) {
            throw IllegalStateException("Failed to parse lyrics")
        }

        TTMLParser.toLRC(parsedLines)
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration, album)
            .onSuccess { lrcString ->
                callback(lrcString)
            }
    }
}
