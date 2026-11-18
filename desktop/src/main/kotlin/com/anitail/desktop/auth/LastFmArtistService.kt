package com.anitail.desktop.auth

import com.anitail.desktop.security.DesktopPaths
import com.anitail.desktop.storage.DesktopPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val LASTFM_PLACEHOLDER_HASH = "2a96cbd8b46e442fc41c2b86b821562f"
private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
private const val MAX_CACHED_ARTISTS = 50

@Serializable
data class ArtistPhoto(
    val url: String,
    val pageUrl: String? = null,
)

@Serializable
data class LastFmPhotoDetails(
    val title: String? = null,
    val description: String? = null,
    val authorName: String? = null,
    val authorProfileUrl: String? = null,
    val uploadDate: String? = null,
)

data class ParsedAudienceCount(
    val text: String,
    val isCountOnly: Boolean,
)

@Serializable
data class LastFmArtistData(
    val photos: List<ArtistPhoto> = emptyList(),
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val listeners: String? = null,
    val scrobbles: String? = null,
    val similarArtists: List<String> = emptyList(),
)

@Serializable
private data class CachedLastFmArtistEntry(
    val data: LastFmArtistData,
    val fetchedAtMillis: Long = 0L,
    val language: String = "",
)

object LastFmArtistService {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile: File by lazy {
        DesktopPaths.lastFmPendingFile().parent.resolve("lastfm_artist_cache.json").toFile()
    }
    private val cacheLock = Any()
    private var cache: MutableMap<String, CachedLastFmArtistEntry>? = null
    private val photoDetailsCache = java.util.concurrent.ConcurrentHashMap<String, LastFmPhotoDetails>()

    fun peekPhotoDetails(pageUrl: String): LastFmPhotoDetails? = photoDetailsCache[pageUrl]

    suspend fun fetchPhotoDetails(pageUrl: String): LastFmPhotoDetails? = withContext(Dispatchers.IO) {
        photoDetailsCache[pageUrl]?.let { return@withContext it }
        if (!Regex("""https://www\.last\.fm/music/[^/?#]+/\+images/[0-9a-f]{32}""").matches(pageUrl)) {
            return@withContext null
        }
        val html = runCatching {
            val res = client.get(pageUrl) {
                header(HttpHeaders.UserAgent, "AniTail Desktop")
                header(HttpHeaders.AcceptLanguage, currentLanguage())
            }
            if (res.status.value in 200..299) res.bodyAsText() else null
        }.getOrNull() ?: return@withContext null

        val details = parsePhotoDetailsHtml(html) ?: return@withContext null
        photoDetailsCache[pageUrl] = details
        details
    }

    fun parseAudienceCount(raw: String?): ParsedAudienceCount? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val match = Regex("""^([0-9][0-9.,\s]*[KMBkmb]?)\b""").find(trimmed)
        return if (match != null) {
            val count = match.groupValues[1].trim()
            val remainder = trimmed.removePrefix(match.groupValues[0]).trim()
            ParsedAudienceCount(text = count, isCountOnly = remainder.isNotBlank())
        } else {
            ParsedAudienceCount(text = trimmed, isCountOnly = false)
        }
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                }
            }
        }
    }

    fun artistPageUrl(artistName: String): String {
        val encoded = URLEncoder.encode(artistName.trim(), StandardCharsets.UTF_8)
        return "https://www.last.fm/music/$encoded"
    }

    fun photoPageUrl(artistName: String, imageUrl: String): String? {
        val match = Regex("""/i/u/[^/]+/([0-9a-f]{32})""").find(imageUrl) ?: return null
        return "${artistPageUrl(artistName)}/+images/${match.groupValues[1]}"
    }

    fun currentLanguage(): String {
        val lang = DesktopPreferences.getInstance().appLanguage.value
        return lang.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }
    }

    fun getCached(artistId: String): LastFmArtistData? {
        val entry = synchronized(cacheLock) { loadCacheLocked()[artistId] } ?: return null
        return if (entry.language == currentLanguage()) entry.data else entry.data.copy(summary = null)
    }

    suspend fun fetchArtistData(
        artistId: String,
        artistName: String,
        catalogTitles: List<String> = emptyList(),
    ): Result<LastFmArtistData> = withContext(Dispatchers.IO) {
        val language = currentLanguage()
        val cached = synchronized(cacheLock) { loadCacheLocked()[artistId] }
        val isFresh = cached != null &&
            cached.language == language &&
            cached.fetchedAtMillis > 0L &&
            System.currentTimeMillis() - cached.fetchedAtMillis < CACHE_TTL_MS &&
            cached.data.photos.size > 1
        if (isFresh) {
            return@withContext Result.success(cached!!.data)
        }

        try {
            val apiKey = DesktopLastFmService.currentApiKey()
            val artistJson = if (apiKey.isNotBlank()) {
                fetchArtistInfoJson(artistName, apiKey, language)
            } else {
                null
            }

            if (artistJson != null && catalogTitles.isNotEmpty() && apiKey.isNotBlank()) {
                val topTitles = fetchTopTitles(artistName, apiKey)
                if (topTitles != null && topTitles.isNotEmpty() && !catalogsMatch(catalogTitles, topTitles)) {
                    // Homonymous artist mismatch; return empty data without polluting cache
                    return@withContext Result.success(LastFmArtistData())
                }
            }

            val bio = artistJson?.optJSONObject("bio")?.let { bioObj ->
                val raw = bioObj.optString("content").takeIf { it.isNotBlank() }
                    ?: bioObj.optString("summary").takeIf { it.isNotBlank() }
                raw?.let(::cleanLastFmBio)
            }

            val stats = artistJson?.optJSONObject("stats")
            val listeners = stats?.optString("listeners")?.toLongOrNull()?.let { formatCount(it, language) }
            val scrobbles = stats?.optString("playcount")?.toLongOrNull()?.let { formatCount(it, language) }

            val tags = buildList {
                val tagArray = artistJson?.optJSONObject("tags")?.optJSONArray("tag")
                if (tagArray != null) {
                    for (i in 0 until tagArray.length()) {
                        val name = tagArray.optJSONObject(i)?.optString("name")?.trim()
                        if (!name.isNullOrBlank() && name !in this) add(name)
                    }
                }
            }

            val similarArtists = buildList {
                val simArray = artistJson?.optJSONObject("similar")?.optJSONArray("artist")
                if (simArray != null) {
                    for (i in 0 until simArray.length()) {
                        val name = simArray.optJSONObject(i)?.optString("name")?.trim()
                        if (!name.isNullOrBlank() && name !in this) add(name)
                    }
                }
            }

            val fallbackImage = extractBestApiImage(artistJson)
            val galleryUrls = fetchGalleryPhotos(artistName, language)
            val allPhotoUrls = (galleryUrls + listOfNotNull(fallbackImage))
                .filter { it.isNotBlank() && !it.contains(LASTFM_PLACEHOLDER_HASH) }
                .distinct()
            val photos = allPhotoUrls.map { url ->
                ArtistPhoto(url = url, pageUrl = photoPageUrl(artistName, url))
            }

            val data = LastFmArtistData(
                photos = photos,
                tags = tags,
                summary = bio,
                listeners = listeners,
                scrobbles = scrobbles,
                similarArtists = similarArtists,
            )

            if (artistJson != null || photos.isNotEmpty()) {
                saveCache(artistId, data, language)
            }
            Result.success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) {
                Result.success(cached.data)
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchArtistInfoJson(
        artistName: String,
        apiKey: String,
        language: String,
    ): JSONObject? {
        val response = client.get("https://ws.audioscrobbler.com/2.0/") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.AcceptLanguage, language)
            parameter("method", "artist.getInfo")
            parameter("autocorrect", 1)
            parameter("artist", artistName)
            parameter("api_key", apiKey)
            parameter("lang", language)
            parameter("format", "json")
        }
        if (response.status.value !in 200..299) return null
        val root = JSONObject(response.bodyAsText())
        if (root.has("error")) return null
        return root.optJSONObject("artist")
    }

    private suspend fun fetchTopTitles(artistName: String, apiKey: String): List<String>? {
        val titles = mutableListOf<String>()
        val queries = listOf(
            Triple("artist.getTopTracks", "toptracks", "track"),
            Triple("artist.getTopAlbums", "topalbums", "album"),
        )
        for ((method, rootKey, itemKey) in queries) {
            runCatching {
                val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                    parameter("method", method)
                    parameter("autocorrect", 1)
                    parameter("artist", artistName)
                    parameter("api_key", apiKey)
                    parameter("limit", 25)
                    parameter("format", "json")
                }
                if (response.status.value in 200..299) {
                    val arr = JSONObject(response.bodyAsText())
                        .optJSONObject(rootKey)
                        ?.optJSONArray(itemKey)
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val name = arr.optJSONObject(i)?.optString("name")?.trim()
                            if (!name.isNullOrBlank()) titles += name
                        }
                    }
                }
            }
        }
        return titles.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchGalleryPhotos(artistName: String, language: String): List<String> {
        val baseUrl = artistPageUrl(artistName)
        val imagesHtml = fetchLastFmHtml("$baseUrl/+images", language).orEmpty()
        val fromGallery = extractGalleryPhotos(imagesHtml)
        if (fromGallery.isNotEmpty()) return fromGallery

        val mainHtml = fetchLastFmHtml(baseUrl, language).orEmpty()
        return extractGalleryPhotos(mainHtml)
    }

    private suspend fun fetchLastFmHtml(url: String, language: String): String? =
        runCatching {
            val res = client.get(url) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0")
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, language)
            }
            if (res.status.value in 200..299) res.bodyAsText() else null
        }.getOrNull()

    private fun extractGalleryPhotos(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val gallery = Regex(
            """<li[^>]*class="[^"]*image-list-item[^"]*"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(html)
            .map { it.groupValues[1] }
            .toList()

        val candidates = gallery.ifEmpty {
            Regex("""https://lastfm(?:-img)?\.freetls\.fastly\.net/i/u/[a-zA-Z0-9]+/[a-zA-Z0-9]+(?:\.(?:jpg|png|webp|jpeg))?""")
                .findAll(html)
                .take(10)
                .map { it.value }
                .toList()
        }

        val urlRegex = Regex("""https://lastfm(?:-img)?\.freetls\.fastly\.net/i/u/[a-zA-Z0-9]+/[a-zA-Z0-9]+(?:\.(?:jpg|png|webp|jpeg))?""")
        return candidates
            .asSequence()
            .filter { url -> urlRegex.matches(url) && !url.contains(LASTFM_PLACEHOLDER_HASH) }
            .map { url -> url.replace(Regex("i/u/[a-zA-Z0-9]+/"), "i/u/ar0/") }
            .distinct()
            .take(20)
            .toList()
    }

    private fun extractBestApiImage(artistJson: JSONObject?): String? {
        val images = artistJson?.optJSONArray("image") ?: return null
        for (i in images.length() - 1 downTo 0) {
            val url = images.optJSONObject(i)?.optString("#text")?.trim()
            if (!url.isNullOrBlank() && !url.contains(LASTFM_PLACEHOLDER_HASH)) {
                return url
            }
        }
        return null
    }

    private fun catalogsMatch(youTubeTitles: List<String>, lastFmTitles: List<String>): Boolean {
        fun normalize(s: String): String =
            s.lowercase()
                .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        val ytSet = youTubeTitles.map(::normalize).filter { it.length >= 2 }.toSet()
        if (ytSet.isEmpty()) return true
        val lfmSet = lastFmTitles.map(::normalize).filter { it.length >= 2 }.toSet()
        if (lfmSet.isEmpty()) return true
        return ytSet.any { yt -> lfmSet.any { lfm -> yt == lfm || yt.contains(lfm) || lfm.contains(yt) } }
    }

    private fun cleanLastFmBio(raw: String): String? {
        var text = raw
            .replace(Regex("""<a\s+href=["']https?://(?:www\.)?last\.fm/[^"']*["'][^>]*>.*?</a>.*$""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""User-contributed text is available under the Creative Commons.*$""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        return text.takeIf { it.isNotBlank() }
    }

    private fun formatCount(count: Long, language: String): String {
        val locale = Locale.forLanguageTag(language)
        return NumberFormat.getIntegerInstance(locale).format(count)
    }

    private fun parsePhotoDetailsHtml(html: String): LastFmPhotoDetails? {
        fun section(className: String): String? =
            Regex("""<(h3|p|div)\b[^>]*class=["'][^"']*\b$className\b[^"']*["'][^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)
                ?.groupValues
                ?.get(2)

        val title = section("gallery-image-title")?.let(::cleanHtmlText)
        val description = section("gallery-image-description")?.let(::cleanHtmlText)
        val uploadedBy = section("gallery-image-uploaded-by")
        val authorMatch = uploadedBy?.let {
            Regex("""<a\b[^>]*href=["']/user/([^/"'?#]+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(it)
        }
        val username = authorMatch?.groupValues?.get(1)?.let {
            runCatching { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8) }.getOrNull()
        }
        val date = authorMatch?.let {
            uploadedBy.substring(it.range.last + 1).replace(Regex("""^\s*on\s+"""), "").let(::cleanHtmlText)
        }
        return LastFmPhotoDetails(
            title = title,
            description = description,
            authorName = authorMatch?.groupValues?.get(2)?.let(::cleanHtmlText),
            authorProfileUrl = username?.let { "https://www.last.fm/user/${URLEncoder.encode(it, StandardCharsets.UTF_8)}" },
            uploadDate = date,
        ).takeIf { title != null || description != null || authorMatch != null }
    }

    private fun cleanHtmlText(raw: String): String? =
        raw.replace(Regex("""<(?:br\b[^>]*|/?(?:p|div)\b[^>]*)>"""), " ")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { null }

    private fun loadCacheLocked(): MutableMap<String, CachedLastFmArtistEntry> {
        cache?.let { return it }
        val loaded = runCatching {
            if (cacheFile.exists()) {
                json.decodeFromString<Map<String, CachedLastFmArtistEntry>>(cacheFile.readText()).toMutableMap()
            } else {
                mutableMapOf()
            }
        }.getOrDefault(mutableMapOf())
        cache = loaded
        return loaded
    }

    private fun saveCache(artistId: String, data: LastFmArtistData, language: String) {
        synchronized(cacheLock) {
            val map = loadCacheLocked()
            map.remove(artistId)
            map[artistId] = CachedLastFmArtistEntry(
                data = data,
                fetchedAtMillis = System.currentTimeMillis(),
                language = language,
            )
            while (map.size > MAX_CACHED_ARTISTS) {
                val oldestKey = map.keys.firstOrNull() ?: break
                map.remove(oldestKey)
            }
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(map.toMap()))
            }
        }
    }
}

