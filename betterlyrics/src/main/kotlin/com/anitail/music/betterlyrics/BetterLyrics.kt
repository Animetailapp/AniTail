package com.anitail.music.betterlyrics

import com.anitail.music.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
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

    private suspend fun fetchTTML(
        artist: String,
        title: String,
        duration: Int = -1,
        album: String? = null,
    ): String? = runCatching {
        val params = buildString {
            append("s=$title")
            append(", a=$artist")
            if (duration > 0) append(", d=$duration")
            if (!album.isNullOrBlank()) append(", al=$album")
        }
        println("[BetterLyrics] Fetching: $params")
        
        val response = client.get("/getLyrics") {
            parameter("s", title)
            parameter("a", artist)
            if (duration > 0) {
                parameter("d", duration)
            }
            if (!album.isNullOrBlank()) {
                parameter("al", album)
            }
        }

        println("[BetterLyrics] Response status: ${response.status}")
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<TTMLResponse>()
            println("[BetterLyrics] TTML received: ${body.ttml?.take(100)}...")
            body.ttml
        } else {
            println("[BetterLyrics] HTTP ${response.status}")
            null
        }
    }.getOrElse { e ->
        if (e is CancellationException) {
            throw e
        }
        println("[BetterLyrics] Exception in fetchTTML: ${e.message}")
        e.printStackTrace()
        null
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        // Try with all parameters first
        var ttml = fetchTTML(artist, title, duration, album)
        
        // Fallback: try without album if first attempt fails
        if (ttml == null && !album.isNullOrBlank()) {
            ttml = fetchTTML(artist, title, duration, null)
        }
        
        // Fallback: try without duration if still failing
        if (ttml == null && duration > 0) {
            ttml = fetchTTML(artist, title, -1, null)
        }
        
        if (ttml == null) {
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
