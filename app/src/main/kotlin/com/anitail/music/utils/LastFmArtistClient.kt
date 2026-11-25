package com.anitail.music.utils

import android.content.Context
import com.anitail.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

const val LASTFM_PLACEHOLDER_HASH = "2a96cbd8b46e442fc41c2b86b821562f"
private const val MAX_PHOTO_BYTES = 32 * 1_024 * 1_024
private const val MAX_SCRAPED_HTML_BYTES = 2 * 1_024 * 1_024
private const val TOP_TITLES_LIMIT = 50
private const val MIN_SHARED_TITLES = 2
private const val MAX_SCRAPED_MATCHES = 100

@Serializable
data class ArtistPhoto(
    val url: String,
    val pageUrl: String? = null,
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
data class LastFmPhotoDetails(
    val title: String? = null,
    val description: String? = null,
    val authorName: String? = null,
    val authorProfileUrl: String? = null,
    val authorAvatarUrl: String? = null,
    val uploadDate: String? = null,
)

@Serializable
private data class LastFmUserImage(
    @SerialName("#text") val url: String = "",
)

@Serializable
private data class LastFmUserInfo(
    val name: String = "",
    val image: List<LastFmUserImage> = emptyList(),
)

@Serializable
private data class LastFmUserInfoResponse(
    val user: LastFmUserInfo = LastFmUserInfo(),
)

internal class LastFmPhotoBackoff(
    val retryAfterMs: Long,
    val details: LastFmPhotoDetails? = null,
) : Exception()

private class LastFmHttpException(
    val statusCode: Int,
    val apiErrorCode: Int? = null,
    val retryAfterMs: Long? = null,
    message: String,
) : Exception(message)

fun toFullResolutionLastFmUrl(url: String): String =
    url.replace(Regex("i/u/[a-zA-Z0-9]+/"), "i/u/ar0/")

internal fun mergeLastFmArtistPhotos(vararg sources: List<String>): List<String> =
    sources
        .asSequence()
        .flatten()
        .filter { it.isNotBlank() && !it.contains(LASTFM_PLACEHOLDER_HASH) }
        .distinct()
        .toList()

private data class TextResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: String?,
)

private suspend fun HttpResponse.bodyAsBytesLimited(maxBytes: Int): ByteArray? {
    require(maxBytes > 0)
    if (headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > maxBytes } == true) return null
    val channel = bodyAsChannel()
    try {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            if (read == 0) continue
            if (out.size() + read > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    } finally {
        channel.cancel(null)
    }
}

private suspend fun HttpResponse.bodyAsTextLimited(maxBytes: Int): String? =
    bodyAsBytesLimited(maxBytes)?.decodeToString()

private suspend fun HttpStatement.readTextLimited(maxBytes: Int): TextResponse =
    execute { response ->
        TextResponse(response.status, response.headers, response.bodyAsTextLimited(maxBytes))
    }

object LastFmArtistClient {
    val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    var renderedPageLoader: (suspend (String) -> String?)? = null

    fun contentLanguage(): String {
        val raw = runCatching { YouTube.locale.hl }.getOrNull().orEmpty()
            .ifBlank { Locale.getDefault().language }
        return primaryLanguage(raw).ifBlank { "en" }
    }

    internal fun primaryLanguage(tag: String): String =
        tag.substringBefore('-').substringBefore('_').lowercase()

    fun artistPageUrl(artist: String): String =
        "https://www.last.fm/music/${artist.trim().encodeURLParameter(spaceToPlus = true)}"

    fun photoPageUrl(
        artist: String,
        imageUrl: String,
    ): String? =
        Regex("""/i/u/[^/]+/([0-9a-f]{32})""")
            .find(imageUrl)
            ?.let { "${artistPageUrl(artist)}/+images/${it.groupValues[1]}" }

    private fun apiKeyOrEmpty(): String =
        runCatching { LastFmKeyManager.getApiKey() }
            .getOrDefault("")
            .takeIf { it.isNotBlank() && it != "PLACEHOLDER_API_KEY" && it != "PUT_YOUR_API_KEY_HERE" }
            .orEmpty()

    suspend fun getArtistInfo(
        client: HttpClient = httpClient,
        artist: String,
        language: String = contentLanguage(),
        apiKey: String = apiKeyOrEmpty(),
    ): JsonObject? {
        if (apiKey.isBlank()) return null
        return try {
            val response = client.prepareGet("https://ws.audioscrobbler.com/2.0/") {
                headers.append(HttpHeaders.Accept, "application/json")
                headers.append(HttpHeaders.AcceptLanguage, language)
                parameter("method", "artist.getInfo")
                parameter("autocorrect", 1)
                parameter("artist", artist)
                parameter("api_key", apiKey)
                parameter("lang", language)
                parameter("format", "json")
            }
            val limited = response.readTextLimited(MAX_SCRAPED_HTML_BYTES)
            if (limited.status.value !in 200..299) return null
            val body = limited.body ?: return null
            val parsed = json.parseToJsonElement(body).jsonObject
            if (parsed.containsKey("error")) return null
            parsed["artist"]?.jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getArtistTopTitles(
        client: HttpClient = httpClient,
        artist: String,
        language: String = contentLanguage(),
        apiKey: String = apiKeyOrEmpty(),
    ): List<String>? {
        if (apiKey.isBlank()) return null
        val titles = listOf(
            Triple("artist.getTopTracks", "toptracks", "track"),
            Triple("artist.getTopAlbums", "topalbums", "album"),
        ).map { (method, root, item) ->
            try {
                val response = client.prepareGet("https://ws.audioscrobbler.com/2.0/") {
                    headers.append(HttpHeaders.Accept, "application/json")
                    headers.append(HttpHeaders.AcceptLanguage, language)
                    parameter("method", method)
                    parameter("artist", artist)
                    parameter("limit", TOP_TITLES_LIMIT)
                    parameter("api_key", apiKey)
                    parameter("format", "json")
                }
                val limited = response.readTextLimited(MAX_SCRAPED_HTML_BYTES)
                val body = limited.body?.takeIf { limited.status.value in 200..299 } ?: return@map null
                json.parseToJsonElement(body)
                    .jsonObject[root]
                    ?.jsonObject
                    ?.get(item)
                    ?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        return if (titles.all { it == null }) null else titles.flatMap { it.orEmpty() }
    }

    internal fun extractCatalogTitles(
        html: String,
        artist: String,
    ): List<String> =
        Regex("""href="/music/([^"/]+)/([^"/?#]+)(?:/([^"/?#]+))?"""")
            .findAll(html)
            .mapNotNull { match ->
                val (linkArtist, first, second) = match.destructured
                when {
                    normalizeCatalogTitle(linkArtist.decodeURLQueryComponent(plusIsSpace = true)) != normalizeCatalogTitle(artist) -> null
                    first == "_" && second.isNotEmpty() -> second
                    second.isEmpty() && !first.startsWith("+") && first != "_" -> first
                    else -> null
                }
            }.map { it.decodeURLQueryComponent(plusIsSpace = true) }
            .distinct()
            .take(MAX_SCRAPED_MATCHES)
            .toList()

    internal fun normalizeCatalogTitle(title: String): String =
        title
            .lowercase()
            .replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""\s+-\s+.*$"""), "")
            .filter(Char::isLetterOrDigit)

    /** Last.fm merges same-name artists; require shared chart titles to verify the YouTube Music match. */
    fun catalogsMatch(
        youTubeTitles: Collection<String>,
        lastFmTitles: Collection<String>,
    ): Boolean {
        val youTube = youTubeTitles.mapTo(mutableSetOf(), ::normalizeCatalogTitle).apply { remove("") }
        val lastFm = lastFmTitles.mapTo(mutableSetOf(), ::normalizeCatalogTitle).apply { remove("") }
        val required = minOf(MIN_SHARED_TITLES, youTube.size, lastFm.size)
        return required > 0 && youTube.count { it in lastFm } >= required
    }

    data class MatchedArtist(
        val info: JsonObject?,
        val page: ScrapedArtistPage?,
    )

    suspend fun getMatchedArtist(
        client: HttpClient = httpClient,
        artist: String,
        youTubeTitles: Collection<String>,
        language: String = contentLanguage(),
        apiKey: String = apiKeyOrEmpty(),
    ): MatchedArtist? {
        val info = getArtistInfo(client, artist, language, apiKey)
        val apiTitles = if (info != null) getArtistTopTitles(client, artist, language, apiKey) else null
        val page = if (apiTitles == null) scrapeArtistPage(client, artist, language) else null
        val lastFmTitles = apiTitles ?: page?.topTitles.orEmpty()
        return MatchedArtist(info, page).takeIf { catalogsMatch(youTubeTitles, lastFmTitles) }
    }

    suspend fun getArtistPhotos(
        client: HttpClient = httpClient,
        artist: String,
        language: String = contentLanguage(),
        renderPage: (suspend (String) -> String?)? = renderedPageLoader,
    ): List<String> =
        try {
            val html = fetchArtistHtml(client, "${artistPageUrl(artist)}/+images", language, renderPage).orEmpty()
            extractGalleryPhotos(html).ifEmpty {
                extractGalleryPhotos(fetchArtistHtml(client, artistPageUrl(artist), language, renderPage).orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    internal fun extractGalleryPhotos(html: String): List<String> {
        val gallery = Regex(
            """<li[^>]*class="[^"]*image-list-item[^"]*"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(html)
            .map { it.groupValues[1] }
            .toList()
        return gallery
            .ifEmpty {
                Regex("""https://lastfm(?:-img)?\.freetls\.fastly\.net/i/u/[a-zA-Z0-9]+/[a-zA-Z0-9]+\.(?:jpg|png|webp|jpeg)""")
                    .findAll(html)
                    .take(5)
                    .map { it.value }
                    .toList()
            }.asSequence()
            .filter { url ->
                Regex(
                    """https://lastfm(?:-img)?\.freetls\.fastly\.net/i/u/[a-zA-Z0-9]+/[a-zA-Z0-9]+(?:\.(?:jpg|png|webp|jpeg))?""",
                ).matches(url) &&
                    !url.contains(LASTFM_PLACEHOLDER_HASH)
            }.map(::toFullResolutionLastFmUrl)
            .distinct()
            .take(20)
            .toList()
    }

    data class ScrapedArtistPage(
        val bio: String? = null,
        val tags: List<String> = emptyList(),
        val listeners: String? = null,
        val scrobbles: String? = null,
        val avatarUrl: String? = null,
        val topTitles: List<String> = emptyList(),
        val language: String? = null,
    )

    data class ArtistAudience(
        val genres: List<String> = emptyList(),
        val listeners: String? = null,
        val scrobbles: String? = null,
        val similarArtists: List<String> = emptyList(),
    )

    fun parseArtistAudience(
        info: JsonObject?,
        scraped: ScrapedArtistPage? = null,
    ): ArtistAudience {
        fun names(
            group: String,
            key: String,
        ): List<String> {
            val entries = (info?.get(group) as? JsonObject)?.get(key)
            val objects = when (entries) {
                is JsonArray -> entries
                is JsonObject -> listOf(entries)
                else -> emptyList()
            }
            return objects
                .mapNotNull {
                    ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
                }.distinct()
        }
        val stats = info?.get("stats") as? JsonObject
        fun count(key: String) = (stats?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { (it.toLongOrNull() ?: -1) >= 0 }
        return ArtistAudience(
            genres = names("tags", "tag").ifEmpty { scraped?.tags.orEmpty() },
            listeners = count("listeners") ?: scraped?.listeners,
            scrobbles = count("playcount") ?: scraped?.scrobbles,
            similarArtists = names("similar", "artist"),
        )
    }

    fun parseArtistImage(info: JsonObject?): String? =
        info?.get("image")
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["#text"]?.jsonPrimitive?.contentOrNull }
            ?.lastOrNull { it.isNotBlank() && !it.contains(LASTFM_PLACEHOLDER_HASH) }
            ?.let(::toFullResolutionLastFmUrl)

    fun parseArtistBio(
        info: JsonObject?,
        scraped: ScrapedArtistPage? = null,
        language: String = contentLanguage(),
    ): String? =
        ((info?.get("bio") as? JsonObject)?.get("summary") as? JsonPrimitive)
            ?.contentOrNull
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("\\s*Read more on Last\\.fm\\s*$"), "")
            ?.trim()
            ?.ifBlank { null }
            ?: scraped?.takeIf { it.language == language }?.bio

    suspend fun scrapeArtistPage(
        client: HttpClient = httpClient,
        artist: String,
        language: String = contentLanguage(),
    ): ScrapedArtistPage =
        try {
            val url = artistPageUrl(artist)
            val html = fetchArtistHtml(client, url, language, renderedPageLoader) ?: return ScrapedArtistPage()

            val bio = html
                .let { page ->
                    Regex("""truncate-4-lines[^>]*>(.*?)</(?:p|div)""", RegexOption.DOT_MATCHES_ALL)
                        .find(page)
                        ?.groupValues
                        ?.get(1)
                }?.let { raw ->
                    raw.replace(Regex("<[^>]+>"), "")
                        .replace(Regex("\\s*read more\\s*$", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s*…\\s*$"), "")
                        .replace("&amp;", "&")
                        .replace("&#x27;", "'")
                        .replace("&quot;", "\"")
                        .trim()
                        .ifBlank { null }
                }
            val metadataItems = Regex(
                """header-metadata-tnew-title[^>]*>\s*(\w+)\s*</h4.*?intabbr[^"]*"\s*title="([^"]+)""",
                RegexOption.DOT_MATCHES_ALL,
            ).findAll(html).take(MAX_SCRAPED_MATCHES).associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[2]
            }
            val listeners = metadataItems["listeners"]
            val scrobbles = metadataItems["scrobbles"]

            val tags = Regex("""href="/tag/([^"]+)"""")
                .findAll(html)
                .map { it.groupValues[1].replace("+", " ") }
                .distinct()
                .take(MAX_SCRAPED_MATCHES)
                .toList()

            val avatar = Regex("""<img[^>]*src="([^"]*avatar[^"]+)" """)
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.replace(Regex("""\d+s/"""), "300x300/")

            ScrapedArtistPage(
                language = Regex("""<html\b[^>]*\blang=["']([a-zA-Z_-]+)""")
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.let(::primaryLanguage),
                bio = bio,
                tags = tags,
                listeners = listeners,
                scrobbles = scrobbles,
                avatarUrl = avatar,
                topTitles = extractCatalogTitles(html, artist),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ScrapedArtistPage()
        }

    suspend fun getPhotoDetails(
        client: HttpClient = httpClient,
        pageUrl: String,
        renderPage: (suspend (String) -> String?)? = renderedPageLoader,
        apiKey: String = apiKeyOrEmpty(),
    ): LastFmPhotoDetails? {
        if (!Regex("""https://www\.last\.fm/music/[^/?#]+/\+images/[0-9a-f]{32}""").matches(pageUrl)) return null
        return try {
            val response = client.prepareGet(pageUrl).readTextLimited(MAX_SCRAPED_HTML_BYTES)
            if (response.status.value == 429 || response.status.value == 503) {
                throw LastFmPhotoBackoff(parseRetryAfter(response.headers[HttpHeaders.RetryAfter]) ?: 60_000)
            }
            val fetched = response.body.orEmpty()
            if (response.status.value !in 200..299 && !isClientChallenge(fetched)) return null
            val html = if (isClientChallenge(fetched)) renderPage?.invoke(pageUrl).orEmpty() else fetched
            val details = parsePhotoDetails(html) ?: return null
            val username = details.authorProfileUrl?.substringAfterLast('/')?.decodeURLQueryComponent() ?: return details
            val avatar = if (apiKey.isNotBlank()) {
                getUserInfo(client, username, apiKey)
                    .onFailure { error ->
                        if (error is LastFmHttpException &&
                            (error.statusCode == 429 || error.statusCode == 503 || error.apiErrorCode == 29)
                        ) {
                            throw LastFmPhotoBackoff(error.retryAfterMs ?: 60_000, details)
                        }
                    }.getOrNull()
                    ?.image
                    ?.lastOrNull { it.url.isNotBlank() && !it.url.contains(LASTFM_PLACEHOLDER_HASH) }
                    ?.url
            } else {
                null
            }
            details.copy(authorAvatarUrl = avatar)
        } catch (e: LastFmPhotoBackoff) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getUserInfo(
        client: HttpClient,
        username: String,
        apiKey: String,
    ): Result<LastFmUserInfo> =
        runCatching {
            val response = client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "user.getinfo")
                parameter("user", username)
                parameter("api_key", apiKey)
                parameter("format", "json")
            }
            val statusCode = response.status.value
            val retryAfter = parseRetryAfter(response.headers[HttpHeaders.RetryAfter])
            val body = response.bodyAsText()
            val payload = json.parseToJsonElement(body)
            val obj = payload as? JsonObject
            val apiError = obj?.get("error")?.jsonPrimitive?.intOrNull
            if (statusCode !in 200..299 || apiError != null) {
                throw LastFmHttpException(
                    statusCode = statusCode,
                    apiErrorCode = apiError,
                    retryAfterMs = retryAfter,
                    message = obj?.get("message")?.jsonPrimitive?.contentOrNull ?: "HTTP $statusCode",
                )
            }
            json.decodeFromJsonElement<LastFmUserInfoResponse>(payload).user
        }

    internal fun isClientChallenge(html: String): Boolean =
        html.contains("Client Challenge", ignoreCase = true) ||
            html.contains("cf-chl-", ignoreCase = true) ||
            html.contains("Rate Limited", ignoreCase = true)

    private suspend fun fetchArtistHtml(
        client: HttpClient,
        url: String,
        language: String,
        renderPage: (suspend (String) -> String?)? = null,
    ): String? {
        val response = client.prepareGet(url) {
            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0")
            headers.append(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            headers.append(HttpHeaders.AcceptLanguage, language)
        }.readTextLimited(MAX_SCRAPED_HTML_BYTES)
        val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
        if (type != null && type != "text/html" && type != "application/xhtml+xml") return null
        val html = response.body ?: return null
        if (isClientChallenge(html)) {
            return renderPage
                ?.invoke(url)
                ?.takeIf { it.encodeToByteArray().size <= MAX_SCRAPED_HTML_BYTES }
                ?.takeUnless(::isClientChallenge)
        }
        if (response.status.value !in 200..299) return null
        return html
    }
}

internal fun parseRetryAfter(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    text.toLongOrNull()?.let { return it.coerceAtLeast(0L) * 1000L }
    return runCatching {
        val instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text))
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
    }.getOrNull()
        ?: runCatching {
            val zdt = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC))
            (zdt.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
        }.getOrNull()
}

private val PHOTO_UPLOAD_DATE_PARSER =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun parsePhotoUploadDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim(), PHOTO_UPLOAD_DATE_PARSER) }.getOrNull()

internal fun formatPhotoUploadDate(
    text: String,
    locale: Locale = Locale.getDefault(),
): String? {
    val date = parsePhotoUploadDate(text) ?: return null
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}

internal fun parsePhotoDetails(html: String): LastFmPhotoDetails? {
    fun section(className: String): String? =
        Regex("""<(h3|p|div)\b[^>]*class=["'][^"']*\b$className\b[^"']*["'][^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.get(2)

    val title = section("gallery-image-title")?.photoHtmlText()
    val description = section("gallery-image-description")?.photoHtmlText()
    val uploadedBy = section("gallery-image-uploaded-by")
    val author = uploadedBy?.let {
        Regex("""<a\b[^>]*href=["']/user/([^/"'?#]+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(it)
    }
    val username = author?.groupValues?.get(1)?.decodeURLQueryComponent()
    val date = author?.let {
        uploadedBy.substring(it.range.last + 1).replace(Regex("""^\s*on\s+"""), "").photoHtmlText()
    }
    return LastFmPhotoDetails(
        title = title,
        description = description,
        authorName = author?.groupValues?.get(2)?.photoHtmlText(),
        authorProfileUrl = username?.let { "https://www.last.fm/user/${it.encodeURLParameter()}" },
        uploadDate = date,
    ).takeIf { title != null || description != null || author != null }
}

private val PHOTO_HTML_ENTITIES = mapOf(
    "amp" to "&",
    "quot" to "\"",
    "apos" to "'",
    "lt" to "<",
    "gt" to ">",
    "nbsp" to " ",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "mdash" to "—",
    "ndash" to "–",
    "hellip" to "…",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "laquo" to "«",
    "raquo" to "»",
)

private fun String.photoHtmlText(): String? =
    replace(Regex("""<(?:br\b[^>]*|/?(?:p|div)\b[^>]*)>"""), " ")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("""&(#[xX][0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")) { match ->
            val entity = match.groupValues[1]
            if (entity[0] != '#') return@replace PHOTO_HTML_ENTITIES[entity] ?: match.value
            val code = if (entity[1] in "xX") entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
            when {
                code == null || code !in 1..0x10ffff || code in 0xd800..0xdfff -> match.value
                code <= 0xffff -> code.toChar().toString()
                else -> ((code - 0x10000) / 0x400 + 0xd800).toChar().toString() + ((code - 0x10000) % 0x400 + 0xdc00).toChar()
            }
        }.replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { null }

private const val MAX_CACHED_ARTISTS = 50
private const val ARTIST_CACHE_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1_000L

@Serializable
internal data class CachedLastFmArtist(
    val data: LastFmArtistData,
    val fetchedAtMillis: Long = 0,
    val language: String = "",
)

class LastFmArtistCache internal constructor(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val contentLanguage: () -> String = LastFmArtistClient::contentLanguage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val entries = MutableStateFlow(
        runCatching { json.decodeFromString<Map<String, CachedLastFmArtist>>(read()) }.getOrDefault(emptyMap()),
    )

    private val refreshLock = Mutex()

    internal fun currentLanguage(): String = contentLanguage()

    fun get(artistId: String): LastFmArtistData? = entries.value[artistId]?.current()

    fun artist(artistId: String): Flow<LastFmArtistData?> =
        entries.map { it[artistId]?.current() }.distinctUntilChanged()

    private fun CachedLastFmArtist.current() =
        if (language == contentLanguage()) data else data.copy(summary = null)

    suspend fun refresh(
        artistId: String,
        artistName: String,
        catalogTitles: List<String>,
        client: HttpClient = LastFmArtistClient.httpClient,
    ) = withContext(dispatcher) {
        refreshLock.withLock {
            val cached = entries.value[artistId]
            val language = contentLanguage()
            val isRecent = cached != null && cached.data.photos.isNotEmpty() && cached.fetchedAtMillis > 0 &&
                now() - cached.fetchedAtMillis < ARTIST_CACHE_REFRESH_INTERVAL_MS
            if (isRecent && cached.language == language) return@withLock
            val matched = LastFmArtistClient.getMatchedArtist(client, artistName, catalogTitles, language) ?: return@withLock
            val audience = LastFmArtistClient.parseArtistAudience(matched.info, matched.page)
            val fallbackPhotos = listOfNotNull(LastFmArtistClient.parseArtistImage(matched.info), matched.page?.avatarUrl)

            fun toPhotos(vararg sources: List<String>) =
                mergeLastFmArtistPhotos(*sources).map { ArtistPhoto(it, LastFmArtistClient.photoPageUrl(artistName, it)) }

            val metadata = LastFmArtistData(
                photos = cached?.data?.photos.orEmpty().ifEmpty { toPhotos(fallbackPhotos) },
                tags = audience.genres,
                summary = LastFmArtistClient.parseArtistBio(matched.info, matched.page, language),
                listeners = audience.listeners,
                scrobbles = audience.scrobbles,
                similarArtists = audience.similarArtists,
            )
            store(artistId, metadata, 0, language)
            val photos = LastFmArtistClient.getArtistPhotos(client, artistName, language)
            if (photos.isNotEmpty()) {
                store(artistId, metadata.copy(photos = toPhotos(photos, fallbackPhotos)), now(), language)
            }
        }
    }

    private fun store(
        artistId: String,
        data: LastFmArtistData,
        fetchedAtMillis: Long,
        language: String,
    ) {
        val entry = CachedLastFmArtist(data, fetchedAtMillis, language)
        if (entries.value[artistId] == entry) return
        entries.update { current ->
            (current - artistId + (artistId to entry)).toList().takeLast(MAX_CACHED_ARTISTS).toMap()
        }
        write(json.encodeToString(entries.value))
    }

    companion object {
        @Volatile
        private var instance: LastFmArtistCache? = null

        fun getInstance(context: Context): LastFmArtistCache =
            instance ?: synchronized(this) {
                instance ?: run {
                    val file = File(context.applicationContext.filesDir, "lastfm_artist_cache.json")
                    LastFmArtistCache(
                        read = { runCatching { if (file.exists()) file.readText() else "{}" }.getOrDefault("{}") },
                        write = { payload -> runCatching { file.writeText(payload) } },
                    ).also { instance = it }
                }
            }
    }
}

internal class LastFmPhotoCache internal constructor(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val load: suspend (String) -> LastFmPhotoDetails?,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallNow: () -> Long = { System.currentTimeMillis() },
) {
    @Serializable
    private data class Entry(
        val details: LastFmPhotoDetails,
        val expiresAt: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val entries = MutableStateFlow(
        runCatching {
            json.decodeFromString<Map<String, Entry>>(read())
                .toList()
                .takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList()),
    )

    private val mutex = Mutex()
    private var nextRequestAt = 0L
    private var nextPrefetchAt = 0L
    private var cooldownUntil = 0L

    fun peek(url: String): LastFmPhotoDetails? = entries.value.entry(url)?.details

    private fun fresh(url: String): LastFmPhotoDetails? {
        var hit: Entry? = null
        entries.update { current ->
            hit = current.entry(url)?.takeIf { wallNow() < it.expiresAt }
            hit?.let { current.without(url) + (url to it) } ?: current
        }
        return hit?.details
    }

    private fun List<Pair<String, Entry>>.entry(url: String) = firstOrNull { it.first == url }?.second

    private fun List<Pair<String, Entry>>.without(url: String) = filter { it.first != url }

    suspend fun get(
        url: String,
        prefetch: Boolean = false,
    ): LastFmPhotoDetails? {
        fresh(url)?.let { return it }
        if (prefetch) {
            if (now() < cooldownUntil) return peek(url)
            delay((nextPrefetchAt - now()).coerceAtLeast(0))
        }
        return mutex.withLock {
            fresh(url)?.let { return@withLock it }
            val stale = peek(url)
            if (prefetch && now() < maxOf(nextPrefetchAt, cooldownUntil)) return@withLock stale
            delay((maxOf(nextRequestAt, cooldownUntil) - now()).coerceAtLeast(0))

            var backoffMs: Long? = null

            suspend fun fetch(): LastFmPhotoDetails? {
                nextRequestAt = now() + 1_000
                backoffMs = null
                return try {
                    load(url)
                } catch (error: LastFmPhotoBackoff) {
                    val wait = error.retryAfterMs.coerceAtLeast(5_000)
                    backoffMs = wait
                    cooldownUntil = now() + wait
                    error.details
                } finally {
                    nextPrefetchAt = now() + 5_000
                }
            }

            var details = fetch()
            if (details == null && !prefetch && now() < cooldownUntil) {
                delay((maxOf(nextRequestAt, cooldownUntil) - now()).coerceAtLeast(0))
                details = fetch()
            }
            details ?: return@withLock stale
            val partial = backoffMs != null
            val stored = if (partial) details.copy(authorAvatarUrl = details.authorAvatarUrl ?: stale?.authorAvatarUrl) else details
            entries.update { (it.without(url) + (url to Entry(stored, wallNow() + (backoffMs ?: TTL_MS)))).takeLast(MAX_ENTRIES) }
            write(json.encodeToString(entries.value.toMap()))
            stored
        }
    }

    companion object {
        private const val MAX_ENTRIES = 100
        private const val TTL_MS = 24L * 60L * 60L * 1_000L

        @Volatile
        private var instance: LastFmPhotoCache? = null

        fun getInstance(context: Context): LastFmPhotoCache =
            instance ?: synchronized(this) {
                instance ?: run {
                    val file = File(context.applicationContext.filesDir, "lastfm_photo_cache.json")
                    LastFmPhotoCache(
                        read = { runCatching { if (file.exists()) file.readText() else "{}" }.getOrDefault("{}") },
                        write = { payload -> runCatching { file.writeText(payload) } },
                        load = { url -> LastFmArtistClient.getPhotoDetails(LastFmArtistClient.httpClient, url) },
                    ).also { instance = it }
                }
            }
    }
}

class ArtistPhotoDownload(
    val bytes: ByteArray,
    val mimeType: String,
    val baseName: String,
    val extension: String,
)

suspend fun downloadArtistPhoto(
    photo: ArtistPhoto,
    artistName: String,
    client: HttpClient = LastFmArtistClient.httpClient,
): ArtistPhotoDownload =
    client.prepareGet(photo.url).execute { response ->
        check(response.status.isSuccess()) { "Photo download failed with HTTP ${response.status.value}" }
        val responseMimeType = response.contentType()?.withoutParameters()?.toString()
        val mimeType = checkNotNull(responseMimeType?.takeIf { it.startsWith("image/") }) { "Photo response is not an image" }
        val extension = artistPhotoExtension(mimeType)
        val bytes = checkNotNull(response.bodyAsBytesLimited(MAX_PHOTO_BYTES)) { "Photo exceeds $MAX_PHOTO_BYTES bytes" }
        ArtistPhotoDownload(bytes, mimeType, artistPhotoBaseName(artistName, photo.url), extension)
    }

internal fun artistPhotoBaseName(
    artistName: String,
    url: String,
): String =
    "$artistName ${Url(url).encodedPath.substringAfterLast('/').substringBefore('.')}"
        .replace(Regex("""[\\/:*?"<>|\x00-\x1f\x7f]"""), "_")

internal fun artistPhotoExtension(mimeType: String): String =
    mimeType
        .substringAfter('/')
        .substringBefore('+')
        .let { if (it == "jpeg") "jpg" else it }
        .also { require(it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9.-]*"))) { "Invalid photo extension" } }

