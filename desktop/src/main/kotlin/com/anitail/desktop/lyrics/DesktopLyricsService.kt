package com.anitail.desktop.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Servicio de letras para Desktop usando LrcLib API.
 * Compatible con JVM usando Ktor CIO engine.
 */
object DesktopLyricsService {
    
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
            defaultRequest {
                url("https://lrclib.net")
            }
            expectSuccess = true
        }
    }

    /**
     * Busca letras para una canción.
     * @param title Título de la canción
     * @param artist Artista de la canción
     * @param durationSec Duración en segundos (opcional, -1 para ignorar)
     * @param album Nombre del álbum (opcional)
     * @return Resultado con las letras sincronizadas o error
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Int = -1,
        album: String? = null,
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = searchLyrics(artist, title, album)
            
            if (tracks.isEmpty()) {
                throw IllegalStateException("No se encontraron letras para '$title' de '$artist'")
            }
            
            // Encontrar la mejor coincidencia
            val bestMatch = if (durationSec <= 0) {
                // Sin duración, ordenar por similitud de título/artista
                tracks.maxByOrNull { track ->
                    calculateSimilarity(title, track.trackName) + 
                    calculateSimilarity(artist, track.artistName)
                }
            } else {
                // Con duración, encontrar la más cercana dentro de tolerancia
                tracks.filter { abs(it.duration.toInt() - durationSec) <= 5 }
                    .minByOrNull { abs(it.duration.toInt() - durationSec) }
                    ?: tracks.minByOrNull { abs(it.duration.toInt() - durationSec) }
            }
            
            bestMatch?.let { track ->
                val lyrics = track.syncedLyrics ?: track.plainLyrics
                    ?: throw IllegalStateException("La pista no tiene letras")
                
                LyricsResult(
                    trackName = track.trackName,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    duration = track.duration,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    parsedLines = parseTimedLyrics(track.syncedLyrics),
                )
            } ?: throw IllegalStateException("No se encontró coincidencia para la canción")
        }
    }

    /**
     * Obtiene todas las opciones de letras disponibles.
     */
    suspend fun getAllLyricsOptions(
        title: String,
        artist: String,
        durationSec: Int = -1,
        album: String? = null,
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = searchLyrics(artist, title, album)
            
            tracks.filter { track ->
                track.syncedLyrics != null || track.plainLyrics != null
            }.filter { track ->
                durationSec <= 0 || abs(track.duration.toInt() - durationSec) <= 10
            }.sortedByDescending { track ->
                var score = 0.0
                if (track.syncedLyrics != null) score += 2.0
                if (track.plainLyrics != null) score += 1.0
                score += calculateSimilarity(title, track.trackName)
                score += calculateSimilarity(artist, track.artistName)
                if (durationSec > 0) {
                    score -= abs(track.duration.toInt() - durationSec) * 0.1
                }
                score
            }.take(5).map { track ->
                LyricsResult(
                    trackName = track.trackName,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    duration = track.duration,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    parsedLines = parseTimedLyrics(track.syncedLyrics),
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun searchLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ): List<LrcLibTrack> {
        return client.get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (album != null) parameter("album_name", album)
        }.body<List<LrcLibTrack>>()
    }

    /**
     * Parsea letras sincronizadas en formato LRC a una lista de líneas con timestamps.
     */
    fun parseTimedLyrics(syncedLyrics: String?): List<LyricLine> {
        if (syncedLyrics.isNullOrBlank()) return emptyList()
        
        return syncedLyrics.trim().lines()
            .filter { line -> line.length >= 10 && line.startsWith("[") }
            .mapNotNull { line ->
                // Format: [mm:ss.xx] text
                val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
                regex.matchEntire(line)?.let { match ->
                    val minutes = match.groupValues[1].toIntOrNull() ?: 0
                    val seconds = match.groupValues[2].toIntOrNull() ?: 0
                    val millis = match.groupValues[3].let { ms ->
                        when (ms.length) {
                            2 -> (ms.toIntOrNull() ?: 0) * 10
                            3 -> ms.toIntOrNull() ?: 0
                            else -> 0
                        }
                    }
                    val text = match.groupValues[4]
                    val timestampMs = (minutes * 60 * 1000L) + (seconds * 1000) + millis
                    LyricLine(timestampMs, text)
                }
            }
            .sortedBy { it.timestampMs }
    }

    private fun calculateSimilarity(str1: String, str2: String): Double {
        val s1 = str1.trim().lowercase()
        val s2 = str2.trim().lowercase()
        
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        return when {
            s1.contains(s2) || s2.contains(s1) -> 0.8
            else -> {
                val maxLength = maxOf(s1.length, s2.length)
                val distance = levenshteinDistance(s1, s2)
                1.0 - (distance.toDouble() / maxLength)
            }
        }
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost,
                )
            }
        }
        
        return matrix[len1][len2]
    }
}

/**
 * Resultado de búsqueda de letras.
 */
data class LyricsResult(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val parsedLines: List<LyricLine>,
) {
    val hasSyncedLyrics: Boolean get() = syncedLyrics != null
    val hasPlainLyrics: Boolean get() = plainLyrics != null
    
    /**
     * Obtiene el texto de la letra actual basado en la posición de reproducción.
     */
    fun getCurrentLine(positionMs: Long): LyricLine? {
        if (parsedLines.isEmpty()) return null
        
        return parsedLines.lastOrNull { it.timestampMs <= positionMs }
    }
    
    /**
     * Obtiene el índice de la línea actual.
     */
    fun getCurrentLineIndex(positionMs: Long): Int {
        if (parsedLines.isEmpty()) return -1
        
        return parsedLines.indexOfLast { it.timestampMs <= positionMs }
    }
}

/**
 * Línea de letra con timestamp.
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

/**
 * Modelo de respuesta de LrcLib API.
 */
@Serializable
data class LrcLibTrack(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val duration: Double,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
