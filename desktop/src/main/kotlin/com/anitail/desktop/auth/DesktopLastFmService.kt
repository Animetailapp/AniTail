package com.anitail.desktop.auth

import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.shared.model.LibraryItem
import de.umass.lastfm.Artist
import de.umass.lastfm.Authenticator
import de.umass.lastfm.Caller
import de.umass.lastfm.Period
import de.umass.lastfm.Session
import de.umass.lastfm.Track
import de.umass.lastfm.User
import de.umass.lastfm.scrobble.ScrobbleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

sealed class LastFmServiceResult<out T> {
    data class Success<T>(val value: T) : LastFmServiceResult<T>()
    data class Error(val message: String) : LastFmServiceResult<Nothing>()
}

data class DesktopLastFmProfile(
    val username: String,
    val realName: String,
    val country: String,
    val age: Int,
    val playcount: Int,
    val imageUrl: String?,
)

private data class PendingScrobble(
    val artist: String,
    val title: String,
    val album: String?,
    val timestamp: Long,
    val duration: Int?,
    val addedAt: Long,
)

object DesktopLastFmService {
    private const val USER_AGENT = "AniTail Desktop"
    private const val DUPLICATE_WINDOW_MS = 30_000L
    private val preferences = DesktopPreferences.getInstance()
    private val pendingFile = Paths.get(
        System.getProperty("user.home") ?: ".",
        ".anitail",
        "lastfm_pending_scrobbles.json",
    ).toFile()

    private data class ApiConfig(
        val apiKey: String,
        val apiSecret: String,
    ) {
        val hasCredentials: Boolean
            get() = apiKey.isNotBlank() && apiSecret.isNotBlank()
    }

    private val apiConfig: ApiConfig by lazy { loadApiConfig() }
    private var session: Session? = null
    private var lastNowPlayingSignature: String? = null

    init {
        Caller.getInstance().userAgent = USER_AGENT
        runCatching { System.setProperty("lastfm.api.url", "https://ws.audioscrobbler.com/2.0/") }
    }

    fun isConfigured(): Boolean = apiConfig.hasCredentials

    fun isLoggedIn(): Boolean {
        return preferences.lastFmEnabled.value && preferences.lastFmSessionKey.value.isNotBlank()
    }

    fun setScrobblingEnabled(enabled: Boolean) {
        preferences.setLastFmScrobbleEnabled(enabled)
    }

    fun setLoveTracksEnabled(enabled: Boolean) {
        preferences.setLastFmLoveTracks(enabled)
    }

    fun setShowAvatar(enabled: Boolean) {
        preferences.setLastFmShowAvatar(enabled)
    }

    suspend fun authenticate(username: String, password: String): LastFmServiceResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext LastFmServiceResult.Error("Last.fm API keys are not configured.")
            }
            val safeUsername = username.trim()
            val safePassword = password.trim()
            if (safeUsername.isBlank() || safePassword.isBlank()) {
                return@withContext LastFmServiceResult.Error("Missing Last.fm credentials.")
            }

            runCatching {
                val authSession = Authenticator.getMobileSession(
                    safeUsername,
                    safePassword,
                    apiConfig.apiKey,
                    apiConfig.apiSecret,
                )
                if (authSession == null || authSession.key.isNullOrBlank()) {
                    return@runCatching LastFmServiceResult.Error("Authentication failed.")
                }

                session = authSession
                preferences.setLastFmUsername(safeUsername)
                preferences.setLastFmSessionKey(authSession.key)
                preferences.setLastFmEnabled(true)
                runCatching { retryPendingScrobbles() }
                LastFmServiceResult.Success(Unit)
            }.getOrElse {
                LastFmServiceResult.Error(it.message ?: "Last.fm authentication failed.")
            }
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        session = null
        lastNowPlayingSignature = null
        preferences.setLastFmEnabled(false)
        preferences.setLastFmSessionKey("")
        preferences.setLastFmUsername("")
    }

    suspend fun updateNowPlaying(item: LibraryItem): LastFmServiceResult<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured() || !isLoggedIn() || !preferences.lastFmScrobbleEnabled.value) {
            return@withContext LastFmServiceResult.Success(Unit)
        }
        val currentSession = getSession()
            ?: return@withContext LastFmServiceResult.Error("No Last.fm session available.")

        val artist = item.artist.trim()
        val title = item.title.trim()
        if (artist.isBlank() || title.isBlank()) {
            return@withContext LastFmServiceResult.Error("Missing artist or title.")
        }

        val signature = "$artist|$title"
        if (lastNowPlayingSignature == signature) {
            return@withContext LastFmServiceResult.Success(Unit)
        }

        runCatching {
            val payload = ScrobbleData(
                artist,
                title,
                (System.currentTimeMillis() / 1000L).toInt(),
            )
            item.durationMs
                ?.takeIf { it > 0L }
                ?.let { payload.duration = (it / 1000L).coerceAtLeast(1L).toInt() }
            val result = Track.updateNowPlaying(payload, currentSession)
            if (result.isSuccessful) {
                lastNowPlayingSignature = signature
                LastFmServiceResult.Success(Unit)
            } else {
                LastFmServiceResult.Error("Failed to update now playing.")
            }
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Failed to update now playing.")
        }
    }

    suspend fun scrobble(item: LibraryItem, timestamp: Long): LastFmServiceResult<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured() || !isLoggedIn() || !preferences.lastFmScrobbleEnabled.value) {
            return@withContext LastFmServiceResult.Success(Unit)
        }
        val currentSession = getSession()
            ?: return@withContext LastFmServiceResult.Error("No Last.fm session available.")

        val artist = item.artist.trim()
        val title = item.title.trim()
        if (artist.isBlank() || title.isBlank()) {
            return@withContext LastFmServiceResult.Error("Missing artist or title.")
        }

        val timestampSec = timestamp.coerceAtLeast(1L)
        val nowMs = System.currentTimeMillis()
        val recentDuplicate = readPendingScrobbles().any { pending ->
            pending.artist == artist &&
                pending.title == title &&
                ((pending.timestamp == timestampSec) || (nowMs - pending.addedAt) < DUPLICATE_WINDOW_MS)
        }
        if (recentDuplicate) {
            return@withContext LastFmServiceResult.Success(Unit)
        }

        runCatching {
            val payload = ScrobbleData(
                artist,
                title,
                timestampSec.toInt(),
            )
            item.durationMs
                ?.takeIf { it > 0L }
                ?.let { payload.duration = (it / 1000L).coerceAtLeast(1L).toInt() }

            val result = Track.scrobble(payload, currentSession)
            if (result.isSuccessful) {
                retryPendingScrobbles()
                LastFmServiceResult.Success(Unit)
            } else {
                enqueuePendingScrobble(
                    artist = artist,
                    title = title,
                    album = null,
                    timestamp = timestampSec,
                    duration = payload.duration.takeIf { it > 0 },
                )
                LastFmServiceResult.Error("Scrobble queued for retry.")
            }
        }.getOrElse { error ->
            if (isNetworkError(error)) {
                enqueuePendingScrobble(
                    artist = artist,
                    title = title,
                    album = null,
                    timestamp = timestampSec,
                    duration = item.durationMs
                        ?.takeIf { it > 0L }
                        ?.let { (it / 1000L).coerceAtLeast(1L).toInt() },
                )
                LastFmServiceResult.Error("Network error. Scrobble queued for retry.")
            } else {
                LastFmServiceResult.Error(error.message ?: "Failed to scrobble track.")
            }
        }
    }

    suspend fun getUserInfo(): LastFmServiceResult<DesktopLastFmProfile> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext LastFmServiceResult.Error("Last.fm API keys are not configured.")
        }
        val username = preferences.lastFmUsername.value.trim()
        if (username.isBlank()) {
            return@withContext LastFmServiceResult.Error("No Last.fm account connected.")
        }

        runCatching {
            val user = User.getInfo(username, apiConfig.apiKey)
                ?: return@runCatching LastFmServiceResult.Error("Unable to load Last.fm profile.")
            LastFmServiceResult.Success(
                DesktopLastFmProfile(
                    username = user.name ?: username,
                    realName = user.realname.orEmpty(),
                    country = user.country.orEmpty(),
                    age = user.age,
                    playcount = user.playcount,
                    imageUrl = user.imageURL,
                )
            )
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Unable to load Last.fm profile.")
        }
    }

    suspend fun getRecentTracks(limit: Int = 10): LastFmServiceResult<List<Track>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext LastFmServiceResult.Error("Last.fm API keys are not configured.")
        }
        val username = preferences.lastFmUsername.value.trim()
        if (username.isBlank()) {
            return@withContext LastFmServiceResult.Error("No Last.fm account connected.")
        }

        runCatching {
            val safeLimit = limit.coerceIn(1, 50)
            val tracks = User.getRecentTracks(username, 1, safeLimit, apiConfig.apiKey).toList()
            LastFmServiceResult.Success(tracks)
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Unable to load recent tracks.")
        }
    }

    suspend fun getTopTracks(limit: Int = 10): LastFmServiceResult<List<Track>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext LastFmServiceResult.Error("Last.fm API keys are not configured.")
        }
        val username = preferences.lastFmUsername.value.trim()
        if (username.isBlank()) {
            return@withContext LastFmServiceResult.Error("No Last.fm account connected.")
        }

        runCatching {
            val safeLimit = limit.coerceIn(1, 50)
            val tracks = User.getTopTracks(username, Period.OVERALL, apiConfig.apiKey)
                .take(safeLimit)
                .toList()
            LastFmServiceResult.Success(tracks)
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Unable to load top tracks.")
        }
    }

    suspend fun getTopArtists(limit: Int = 10): LastFmServiceResult<List<Artist>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext LastFmServiceResult.Error("Last.fm API keys are not configured.")
        }
        val username = preferences.lastFmUsername.value.trim()
        if (username.isBlank()) {
            return@withContext LastFmServiceResult.Error("No Last.fm account connected.")
        }

        runCatching {
            val safeLimit = limit.coerceIn(1, 50)
            val artists = User.getTopArtists(username, Period.OVERALL, apiConfig.apiKey)
                .take(safeLimit)
                .toList()
            LastFmServiceResult.Success(artists)
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Unable to load top artists.")
        }
    }

    suspend fun getPendingScrobblesCount(): Int = withContext(Dispatchers.IO) {
        readPendingScrobbles().size
    }

    suspend fun retryPendingScrobbles(): LastFmServiceResult<Int> = withContext(Dispatchers.IO) {
        val currentSession = getSession()
            ?: return@withContext LastFmServiceResult.Error("No Last.fm session available.")
        val pending = readPendingScrobbles()
        if (pending.isEmpty()) return@withContext LastFmServiceResult.Success(0)

        val remaining = mutableListOf<PendingScrobble>()
        var syncedCount = 0
        for (scrobble in pending) {
            val result = runCatching {
                val payload = ScrobbleData(
                    scrobble.artist,
                    scrobble.title,
                    scrobble.timestamp.toInt(),
                )
                scrobble.album?.takeIf { it.isNotBlank() }?.let { payload.album = it }
                scrobble.duration?.let { payload.duration = it }
                Track.scrobble(payload, currentSession)
            }.getOrNull()
            if (result?.isSuccessful == true) {
                syncedCount += 1
            } else {
                remaining += scrobble
            }
        }

        writePendingScrobbles(remaining)
        LastFmServiceResult.Success(syncedCount)
    }

    suspend fun clearPendingScrobbles() = withContext(Dispatchers.IO) {
        writePendingScrobbles(emptyList())
    }

    suspend fun enqueuePendingScrobble(
        artist: String,
        title: String,
        album: String?,
        timestamp: Long,
        duration: Int?,
    ) = withContext(Dispatchers.IO) {
        val safeArtist = artist.trim()
        val safeTitle = title.trim()
        if (safeArtist.isBlank() || safeTitle.isBlank()) return@withContext
        val nowMs = System.currentTimeMillis()
        val current = readPendingScrobbles().toMutableList()
        val duplicateExists = current.any { pending ->
            pending.artist == safeArtist &&
                pending.title == safeTitle &&
                ((pending.timestamp == timestamp) || (nowMs - pending.addedAt) < 120_000L)
        }
        if (duplicateExists) return@withContext
        current += PendingScrobble(
            artist = safeArtist,
            title = safeTitle,
            album = album?.trim()?.takeIf { it.isNotBlank() },
            timestamp = timestamp,
            duration = duration,
            addedAt = nowMs,
        )
        writePendingScrobbles(current)
    }

    suspend fun loveTrack(artist: String, title: String): LastFmServiceResult<Unit> = withContext(Dispatchers.IO) {
        val currentSession = getSession()
            ?: return@withContext LastFmServiceResult.Error("No Last.fm session available.")
        if (!preferences.lastFmLoveTracks.value) {
            return@withContext LastFmServiceResult.Success(Unit)
        }
        runCatching {
            val result = Track.love(artist, title, currentSession)
            if (result.isSuccessful) {
                LastFmServiceResult.Success(Unit)
            } else {
                LastFmServiceResult.Error("Failed to love track on Last.fm.")
            }
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Failed to love track on Last.fm.")
        }
    }

    suspend fun unloveTrack(artist: String, title: String): LastFmServiceResult<Unit> = withContext(Dispatchers.IO) {
        val currentSession = getSession()
            ?: return@withContext LastFmServiceResult.Error("No Last.fm session available.")
        if (!preferences.lastFmLoveTracks.value) {
            return@withContext LastFmServiceResult.Success(Unit)
        }
        runCatching {
            val result = Track.unlove(artist, title, currentSession)
            if (result.isSuccessful) {
                LastFmServiceResult.Success(Unit)
            } else {
                LastFmServiceResult.Error("Failed to unlove track on Last.fm.")
            }
        }.getOrElse {
            LastFmServiceResult.Error(it.message ?: "Failed to unlove track on Last.fm.")
        }
    }

    private fun getSession(): Session? {
        if (!isConfigured()) return null
        session?.let { return it }
        val sessionKey = preferences.lastFmSessionKey.value.trim()
        if (sessionKey.isBlank()) return null
        val restored = Session.createSession(
            apiConfig.apiKey,
            apiConfig.apiSecret,
            sessionKey,
            preferences.lastFmUsername.value.trim().ifBlank { null },
            false,
        )
        session = restored
        return restored
    }

    private fun loadApiConfig(): ApiConfig {
        val envKey = System.getenv("LASTFM_API_KEY").orEmpty().trim()
        val envSecret = System.getenv("LASTFM_API_SECRET").orEmpty().trim()
        if (envKey.isNotBlank() && envSecret.isNotBlank()) {
            return ApiConfig(apiKey = envKey, apiSecret = envSecret)
        }

        val fromLocalProperties = readLocalProperties()
        return ApiConfig(
            apiKey = fromLocalProperties.first.orEmpty().trim(),
            apiSecret = fromLocalProperties.second.orEmpty().trim(),
        )
    }

    private fun readLocalProperties(): Pair<String?, String?> {
        val candidates = listOf(
            File("local.properties"),
            File(System.getProperty("user.dir") ?: ".", "local.properties"),
            File(System.getProperty("user.home") ?: ".", ".anitail/local.properties"),
        )

        val propertiesFile = candidates.firstOrNull { it.exists() } ?: return null to null
        return runCatching {
            val properties = Properties()
            propertiesFile.inputStream().use { properties.load(it) }
            properties.getProperty("LASTFM_API_KEY") to properties.getProperty("LASTFM_API_SECRET")
        }.getOrDefault(null to null)
    }

    private fun readPendingScrobbles(): List<PendingScrobble> {
        if (!pendingFile.exists()) return emptyList()
        return runCatching {
            val content = Files.readString(pendingFile.toPath(), StandardCharsets.UTF_8)
            if (content.isBlank()) return@runCatching emptyList()
            val json = JSONArray(content)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val artist = item.optString("artist", "").trim()
                    val title = item.optString("title", "").trim()
                    if (artist.isBlank() || title.isBlank()) continue
                    add(
                        PendingScrobble(
                            artist = artist,
                            title = title,
                            album = item.optString("album", "").takeIf { it.isNotBlank() },
                            timestamp = item.optLong("timestamp", 0L),
                            duration = item.optInt("duration", -1).takeIf { it > 0 },
                            addedAt = item.optLong(
                                "addedAt",
                                item.optLong("timestamp", System.currentTimeMillis() / 1000L) * 1000L,
                            ),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writePendingScrobbles(items: List<PendingScrobble>) {
        runCatching {
            pendingFile.parentFile?.mkdirs()
            val json = JSONArray()
            items.forEach { item ->
                json.put(
                    JSONObject().apply {
                        put("artist", item.artist)
                        put("title", item.title)
                        put("album", item.album)
                        put("timestamp", item.timestamp)
                        put("duration", item.duration)
                        put("addedAt", item.addedAt)
                    }
                )
            }
            Files.writeString(pendingFile.toPath(), json.toString(2), StandardCharsets.UTF_8)
        }
    }

    private fun isNetworkError(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty()
        return throwable is java.io.IOException ||
            message.contains("network", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("unreachable", ignoreCase = true)
    }
}
