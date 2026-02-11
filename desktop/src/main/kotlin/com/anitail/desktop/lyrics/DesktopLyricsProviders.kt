package com.anitail.desktop.lyrics

import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PreferredLyricsProvider
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.music.betterlyrics.BetterLyrics
import com.anitail.simpmusic.SimpMusicLyrics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.math.abs

data class LyricsQuery(
    val videoId: String?,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val album: String? = null,
)

interface DesktopLyricsProvider {
    val name: String

    fun isEnabled(preferences: DesktopPreferences): Boolean = true

    suspend fun getLyrics(query: LyricsQuery): Result<String>

    suspend fun getAllLyrics(query: LyricsQuery, callback: (String) -> Unit) {
        getLyrics(query).onSuccess(callback)
    }
}

object DesktopLyricsProviders {
    private val betterLyricsProvider = BetterLyricsDesktopProvider
    private val simpMusicProvider = SimpMusicDesktopProvider
    private val lrcLibProvider = LrcLibDesktopProvider
    private val kuGouProvider = KuGouDesktopProvider
    private val youTubeSubtitleProvider = YouTubeSubtitleDesktopProvider
    private val youTubeMusicProvider = YouTubeMusicDesktopProvider

    fun ordered(preferred: PreferredLyricsProvider): List<DesktopLyricsProvider> = when (preferred) {
        PreferredLyricsProvider.LRCLIB -> listOf(
            betterLyricsProvider,
            lrcLibProvider,
            simpMusicProvider,
            kuGouProvider,
            youTubeSubtitleProvider,
            youTubeMusicProvider,
        )

        PreferredLyricsProvider.KUGOU -> listOf(
            betterLyricsProvider,
            kuGouProvider,
            simpMusicProvider,
            lrcLibProvider,
            youTubeSubtitleProvider,
            youTubeMusicProvider,
        )

        PreferredLyricsProvider.BETTER_LYRICS -> listOf(
            betterLyricsProvider,
            simpMusicProvider,
            lrcLibProvider,
            kuGouProvider,
            youTubeSubtitleProvider,
            youTubeMusicProvider,
        )

        PreferredLyricsProvider.SIMPMUSIC -> listOf(
            betterLyricsProvider,
            simpMusicProvider,
            lrcLibProvider,
            kuGouProvider,
            youTubeSubtitleProvider,
            youTubeMusicProvider,
        )
    }
}

private object BetterLyricsDesktopProvider : DesktopLyricsProvider {
    override val name: String = "BetterLyrics"

    override fun isEnabled(preferences: DesktopPreferences): Boolean = preferences.enableBetterLyrics.value

    override suspend fun getLyrics(query: LyricsQuery): Result<String> =
        BetterLyrics.getLyrics(query.title, query.artist, query.durationSec, query.album)

    override suspend fun getAllLyrics(query: LyricsQuery, callback: (String) -> Unit) {
        BetterLyrics.getAllLyrics(query.title, query.artist, query.durationSec, query.album, callback)
    }
}

private object SimpMusicDesktopProvider : DesktopLyricsProvider {
    override val name: String = "SimpMusic"

    override fun isEnabled(preferences: DesktopPreferences): Boolean = preferences.enableSimpMusic.value

    override suspend fun getLyrics(query: LyricsQuery): Result<String> = runCatching {
        val videoId = query.videoId ?: throw IllegalStateException("Missing video id for SimpMusic")
        SimpMusicLyrics.getLyrics(videoId, query.durationSec).getOrThrow()
    }

    override suspend fun getAllLyrics(query: LyricsQuery, callback: (String) -> Unit) {
        val videoId = query.videoId ?: return
        SimpMusicLyrics.getAllLyrics(videoId, query.durationSec, callback)
    }
}

private object LrcLibDesktopProvider : DesktopLyricsProvider {
    override val name: String = "LrcLib"

    override fun isEnabled(preferences: DesktopPreferences): Boolean = preferences.enableLrcLib.value

    override suspend fun getLyrics(query: LyricsQuery): Result<String> = runCatching {
        val tracks = queryLyrics(query.artist, query.title, query.album)
        val bestMatch = tracks.bestMatchingFor(query.durationSec, query.title, query.artist)
            ?: throw IllegalStateException("Lyrics unavailable")
        bestMatch.syncedLyrics ?: bestMatch.plainLyrics ?: throw IllegalStateException("Lyrics unavailable")
    }

    override suspend fun getAllLyrics(query: LyricsQuery, callback: (String) -> Unit) {
        val tracks = queryLyrics(query.artist, query.title, query.album)
        var count = 0
        var emittedPlainLyrics = false
        val sortedTracks = when {
            query.durationSec == -1 -> tracks.sortedByDescending { track ->
                var score = 0.0
                if (!track.syncedLyrics.isNullOrBlank()) score += 1.0
                val titleSimilarity = calculateSimilarity(query.title, track.trackName)
                val artistSimilarity = calculateSimilarity(query.artist, track.artistName)
                score + (titleSimilarity + artistSimilarity) / 2.0
            }

            else -> tracks.sortedBy { abs(it.duration.toInt() - query.durationSec) }
        }

        sortedTracks.forEach { track ->
            if (count > 4) return@forEach
            if (!track.syncedLyrics.isNullOrBlank() && isDurationMatch(query.durationSec, track.duration.toInt())) {
                count++
                callback(track.syncedLyrics)
                return@forEach
            }
            if (!track.plainLyrics.isNullOrBlank() &&
                isDurationMatch(query.durationSec, track.duration.toInt()) &&
                !emittedPlainLyrics
            ) {
                count++
                emittedPlainLyrics = true
                callback(track.plainLyrics)
            }
        }
    }

    private suspend fun queryLyrics(artist: String, title: String, album: String?): List<LrcLibTrack> =
        lrcLibClient.get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (!album.isNullOrBlank()) {
                parameter("album_name", album)
            }
        }.body()

    private fun List<LrcLibTrack>.bestMatchingFor(
        duration: Int,
        title: String,
        artist: String,
    ): LrcLibTrack? {
        if (isEmpty()) return null
        if (duration == -1) {
            return maxByOrNull { track ->
                val titleSimilarity = calculateSimilarity(title, track.trackName)
                val artistSimilarity = calculateSimilarity(artist, track.artistName)
                var score = (titleSimilarity + artistSimilarity) / 2.0
                if (!track.syncedLyrics.isNullOrBlank()) score += 0.1
                score
            }?.takeIf { track ->
                val titleSimilarity = calculateSimilarity(title, track.trackName)
                val artistSimilarity = calculateSimilarity(artist, track.artistName)
                (titleSimilarity + artistSimilarity) / 2.0 > 0.6
            }
        }
        return minByOrNull { abs(it.duration.toInt() - duration) }
            ?.takeIf { abs(it.duration.toInt() - duration) <= 2 }
    }
}

private object KuGouDesktopProvider : DesktopLyricsProvider {
    override val name: String = "Kugou"

    override fun isEnabled(preferences: DesktopPreferences): Boolean = preferences.enableKuGou.value

    override suspend fun getLyrics(query: LyricsQuery): Result<String> = runCatching {
        val keyword = generateKeyword(query.title, query.artist)
        val candidate = getLyricsCandidate(keyword, query.durationSec)
            ?: throw IllegalStateException("No lyrics candidate")
        downloadLyrics(candidate.id, candidate.accesskey)
            .content
            .decodeBase64()
            .normalize()
    }

    override suspend fun getAllLyrics(query: LyricsQuery, callback: (String) -> Unit) {
        val keyword = generateKeyword(query.title, query.artist)
        searchSongs(keyword).data.info.forEach { song ->
            if (isDurationMatch(query.durationSec, song.duration, tolerance = 8)) {
                val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull() ?: return@forEach
                callback(downloadLyrics(candidate.id, candidate.accesskey).content.decodeBase64().normalize())
            }
        }
        searchLyricsByKeyword(keyword, query.durationSec).candidates.forEach { candidate ->
            callback(downloadLyrics(candidate.id, candidate.accesskey).content.decodeBase64().normalize())
        }
    }

    private suspend fun getLyricsCandidate(keyword: Keyword, duration: Int): KuGouCandidate? {
        searchSongs(keyword).data.info.forEach { song ->
            if (isDurationMatch(duration, song.duration, tolerance = 8)) {
                val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
                if (candidate != null) return candidate
            }
        }
        return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
    }

    private suspend fun searchSongs(keyword: Keyword): SearchSongResponse =
        kuGouClient.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", 8)
            parameter("showtype", 0)
            url.encodedParameters.append(
                "keyword",
                "${keyword.title} - ${keyword.artist}".encodeURLParameter(spaceToPlus = false),
            )
        }.body()

    private suspend fun searchLyricsByKeyword(keyword: Keyword, duration: Int): SearchLyricsResponse =
        kuGouClient.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            if (duration != -1) {
                parameter("duration", duration * 1000)
            }
            url.encodedParameters.append(
                "keyword",
                "${keyword.title} - ${keyword.artist}".encodeURLParameter(spaceToPlus = false),
            )
        }.body()

    private suspend fun searchLyricsByHash(hash: String): SearchLyricsResponse =
        kuGouClient.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("hash", hash)
        }.body()

    private suspend fun downloadLyrics(id: Long, accessKey: String): DownloadLyricsResponse =
        kuGouClient.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accessKey)
        }.body()

    private fun generateKeyword(title: String, artist: String): Keyword =
        Keyword(
            title = normalizeTitle(title),
            artist = normalizeArtist(artist),
        )

    private fun normalizeTitle(title: String): String =
        title
            .replace("\\(.*\\)".toRegex(), "")
            .replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "")
            .replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "")
            .replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "")
            .replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String): String =
        artist
            .replace(", ", "、")
            .replace(" & ", "、")
            .replace(".", "")
            .replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "")
            .replace("（.*）".toRegex(), "")

    private fun String.normalize(): String {
        val acceptedRegex = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
        val bannedRegex = ".+].+[:：].+".toRegex()
        val lines = replace("&apos;", "'").lines().filter { line -> line.matches(acceptedRegex) }
        val headCutLimit = 30
        var headCutLine = 0
        for (index in minOf(headCutLimit, lines.lastIndex) downTo 0) {
            if (lines[index].matches(bannedRegex)) {
                headCutLine = index + 1
                break
            }
        }
        val filteredLines = lines.drop(headCutLine)
        var tailCutLine = 0
        for (index in minOf(lines.size - headCutLimit, lines.lastIndex) downTo 0) {
            if (lines[lines.lastIndex - index].matches(bannedRegex)) {
                tailCutLine = index + 1
                break
            }
        }
        return filteredLines.dropLast(tailCutLine).joinToString("\n")
    }

    private fun String.decodeBase64(): String =
        String(Base64.getDecoder().decode(this))
}

private object YouTubeSubtitleDesktopProvider : DesktopLyricsProvider {
    override val name: String = "YouTube Subtitle"

    override suspend fun getLyrics(query: LyricsQuery): Result<String> = runCatching {
        val videoId = query.videoId ?: throw IllegalStateException("Missing video id for YouTube transcript")
        YouTube.transcript(videoId).getOrThrow()
    }
}

private object YouTubeMusicDesktopProvider : DesktopLyricsProvider {
    override val name: String = "YouTube Music"

    override suspend fun getLyrics(query: LyricsQuery): Result<String> = runCatching {
        val videoId = query.videoId ?: throw IllegalStateException("Missing video id for YouTube lyrics")
        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
        YouTube.lyrics(
            endpoint = nextResult.lyricsEndpoint ?: throw IllegalStateException("Lyrics endpoint not found"),
        ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
    }
}

private val lrcLibClient by lazy {
    HttpClient(OkHttp) {
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

private val kuGouClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }
            json(jsonConfig)
            json(jsonConfig, ContentType.Text.Html)
            json(jsonConfig, ContentType.Text.Plain)
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
    }
}

private fun isDurationMatch(requestDuration: Int, candidateDuration: Int, tolerance: Int = 2): Boolean {
    return requestDuration == -1 || abs(candidateDuration - requestDuration) <= tolerance
}

private fun calculateSimilarity(first: String, second: String): Double {
    val firstNormalized = first.trim().lowercase()
    val secondNormalized = second.trim().lowercase()
    if (firstNormalized == secondNormalized) return 1.0
    if (firstNormalized.isEmpty() || secondNormalized.isEmpty()) return 0.0
    if (firstNormalized.contains(secondNormalized) || secondNormalized.contains(firstNormalized)) return 0.8
    val maxLength = maxOf(firstNormalized.length, secondNormalized.length)
    val distance = levenshteinDistance(firstNormalized, secondNormalized)
    return 1.0 - (distance.toDouble() / maxLength)
}

private fun levenshteinDistance(first: String, second: String): Int {
    val firstLength = first.length
    val secondLength = second.length
    val matrix = Array(firstLength + 1) { IntArray(secondLength + 1) }
    for (index in 0..firstLength) {
        matrix[index][0] = index
    }
    for (index in 0..secondLength) {
        matrix[0][index] = index
    }
    for (firstIndex in 1..firstLength) {
        for (secondIndex in 1..secondLength) {
            val substitutionCost = if (first[firstIndex - 1] == second[secondIndex - 1]) 0 else 1
            matrix[firstIndex][secondIndex] = minOf(
                matrix[firstIndex - 1][secondIndex] + 1,
                matrix[firstIndex][secondIndex - 1] + 1,
                matrix[firstIndex - 1][secondIndex - 1] + substitutionCost,
            )
        }
    }
    return matrix[firstLength][secondLength]
}

@Serializable
private data class LrcLibTrack(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

private data class Keyword(
    val title: String,
    val artist: String,
)

@Serializable
private data class SearchSongResponse(
    val status: Int,
    val errcode: Int,
    val error: String,
    val data: SearchSongData,
)

@Serializable
private data class SearchSongData(
    val info: List<SearchSongInfo>,
)

@Serializable
private data class SearchSongInfo(
    val duration: Int,
    val hash: String,
)

@Serializable
private data class SearchLyricsResponse(
    val status: Int,
    val info: String,
    val errcode: Int,
    val errmsg: String,
    val expire: Int,
    val candidates: List<KuGouCandidate>,
)

@Serializable
private data class KuGouCandidate(
    val id: Long,
    @SerialName("product_from")
    val productFrom: String,
    val duration: Long,
    val accesskey: String,
)

@Serializable
private data class DownloadLyricsResponse(
    val content: String,
)
