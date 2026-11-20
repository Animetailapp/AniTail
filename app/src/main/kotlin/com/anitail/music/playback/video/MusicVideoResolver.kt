package com.anitail.music.playback.video

import com.anitail.innertube.YouTube
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.AlbumPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

private fun isOfficialOrLiveVideoType(type: String?): Boolean =
    isOfficialVideoType(type) || type?.contains("LIVE", ignoreCase = true) == true

private fun acceptsMusicVideoType(type: String?): Boolean =
    type == null || isOfficialOrLiveVideoType(type)

private fun acceptsMusicTrackType(type: String?): Boolean =
    type == null || parseMusicVideoType(type) == MusicVideoType.ATV || !isVideoType(type)

data class VideoSearchPage(
    val items: List<YTItem>,
    val onlineSearchFailed: Boolean = false,
)

sealed interface MusicVideoResult {
    data class Found(
        val videoId: String,
        val positionMap: CounterpartPositionMap? = null,
        val source: CounterpartResolutionSource = CounterpartResolutionSource.HEURISTIC,
    ) : MusicVideoResult

    data object NotFound : MusicVideoResult

    data object Error : MusicVideoResult
}

sealed interface MusicTrackResult {
    data class Found(
        val song: SongItem,
        val positionMap: CounterpartPositionMap? = null,
        val source: CounterpartResolutionSource = CounterpartResolutionSource.HEURISTIC,
    ) : MusicTrackResult

    data object NotFound : MusicTrackResult

    data object Error : MusicTrackResult
}

class MusicVideoResolver(
    private val search: suspend (query: String, filter: YouTube.SearchFilter) -> VideoSearchPage = { query, filter ->
        YouTube.search(query, filter).fold(
            onSuccess = { VideoSearchPage(it.items) },
            onFailure = { VideoSearchPage(emptyList(), onlineSearchFailed = true) },
        )
    },
    private val counterpartLookup: suspend (SongItem) -> MusicVideoCounterpart? = { item ->
        YouTube.nextRawJson(item.id).getOrNull()?.let { parseMusicVideoCounterpart(it, item.id) }
    },
    private val albumLookup: suspend (String) -> AlbumPage? = { albumId ->
        YouTube.album(albumId).getOrNull()
    },
    private val songLookup: suspend (String) -> SongItem? = { videoId ->
        YouTube.queue(videoIds = listOf(videoId)).getOrNull()?.firstOrNull()
    },
    private val readCacheJson: suspend () -> String = { "{}" },
    private val writeCacheJson: suspend (String) -> Unit = {},
    private val cacheSessionKey: suspend () -> List<String> = { emptyList() },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class CacheEntry(
        val videoId: String?,
        val cachedAtMs: Long,
        val positionMap: CounterpartPositionMap? = null,
    )

    private data class TrackLookupKey(
        val videoId: String,
        val title: String,
        val artists: List<Artist>,
        val duration: Int?,
        val session: List<String>,
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val authoritativeCache = mutableMapOf<String, Pair<Long, MusicVideoCounterpart>>()
    private val trackCache = mutableMapOf<TrackLookupKey, Pair<Long, MusicTrackResult.Found>>()
    private val trackInFlight = mutableMapOf<TrackLookupKey, CompletableDeferred<MusicTrackResult>>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<MusicVideoResult>>()
    private val cacheMutex = Mutex()
    private var cacheGeneration = 0L
    private var cacheLoaded = false
    @Volatile
    private var latestRawCacheJson: String = "{}"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun resolveMusicVideo(song: SongItem): MusicVideoResult {
        if (!song.canSwitchToMusicVideo) return MusicVideoResult.NotFound
        while (true) {
            val request = CompletableDeferred<MusicVideoResult>()
            val (inFlightRequest, generation) =
                cacheMutex.withLock {
                    ensureCacheLoadedLocked()
                    inFlight.getOrPut(song.id) { request } to cacheGeneration
                }
            if (inFlightRequest !== request) {
                try {
                    return inFlightRequest.await()
                } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    cacheMutex.withLock { if (inFlight[song.id] === inFlightRequest) inFlight.remove(song.id) }
                    continue
                }
            }
            return try {
                val result = resolveMusicVideoUncoalesced(song, generation)
                cacheMutex.withLock {
                    val current = if (generation == cacheGeneration) result else MusicVideoResult.Error
                    request.complete(current)
                    current
                }
            } catch (e: CancellationException) {
                request.completeExceptionally(e)
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("media link lookup failed type=${e::class.simpleName ?: "unknown"}")
                request.complete(MusicVideoResult.Error)
                MusicVideoResult.Error
            } finally {
                withContext(NonCancellable) {
                    cacheMutex.withLock { if (inFlight[song.id] === request) inFlight.remove(song.id) }
                }
            }
        }
    }

    private suspend fun resolveMusicVideoUncoalesced(
        song: SongItem,
        generation: Long,
    ): MusicVideoResult {
        Timber.tag(TAG).d("resolveMusicVideo start")
        val cachedResult = cachedMusicVideoResult(song)
        if (cachedResult is MusicVideoResult.Found) return cachedResult
        lookupAuthoritative(song, generation)?.let { counterpart ->
            val type = counterpart.targetMusicVideoType
            if (counterpart.targetVideoId != song.id && acceptsMusicVideoType(type)) {
                val result =
                    MusicVideoResult.Found(
                        videoId = counterpart.targetVideoId,
                        positionMap = counterpart.positionMap,
                        source = CounterpartResolutionSource.AUTHORITATIVE,
                    )
                cacheResult(song, result, generation)
                return result
            }
        }
        val albumId = song.album?.id ?: songLookup(song.id)?.album?.id
        if (albumId != null) {
            lookupAlbumVideo(song, albumId)?.takeIf { it != song.id }?.let { mvId ->
                val result =
                    MusicVideoResult.Found(
                        videoId = mvId,
                        source = CounterpartResolutionSource.ALBUM_RELEASE,
                    )
                cacheResult(song, result, generation)
                return result
            }
        }
        cachedResult?.let { return it }

        val result = runLookup(song)
        cacheResult(song, result, generation)
        return result
    }

    private suspend fun lookupAlbumVideo(
        song: SongItem,
        albumId: String,
    ): String? {
        val albumPage =
            try {
                albumLookup(albumId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("album video lookup failed type=${e::class.simpleName ?: "unknown"}")
                null
            } ?: return null

        if (albumPage.songs.isEmpty()) return null

        val cleanSongTitle = MusicVideoMatcher.cleanCoreTitle(song.title)
        val normSongTitle = MusicVideoMatcher.normalizeTitle(song.title)

        val match =
            if (albumPage.songs.size == 1) {
                val single = albumPage.songs.first()
                if (single.id != song.id &&
                    (single.musicVideoType?.contains("OMV", ignoreCase = true) == true || single.isVideoSong)
                ) {
                    single
                } else {
                    null
                }
            } else {
                albumPage.songs.firstOrNull { albumSong ->
                    if (albumSong.id == song.id) return@firstOrNull false
                    val isVideo =
                        albumSong.musicVideoType?.contains("OMV", ignoreCase = true) == true ||
                            albumSong.isVideoSong
                    if (!isVideo) return@firstOrNull false
                    val cleanAlbumTitle = MusicVideoMatcher.cleanCoreTitle(albumSong.title)
                    val normAlbumTitle = MusicVideoMatcher.normalizeTitle(albumSong.title)
                    cleanAlbumTitle == cleanSongTitle ||
                        normAlbumTitle == normSongTitle ||
                        albumSong.title.equals(song.title, ignoreCase = true)
                }
            }

        if (match != null && match.id != song.id) {
            Timber.tag(TAG).d("resolved official music video from album release")
            return match.id
        }
        return null
    }

    suspend fun resolveMusicTrack(video: SongItem): MusicTrackResult {
        if (!video.canSwitchToAudioTrack) return MusicTrackResult.NotFound
        while (true) {
            val session = cacheSessionKey()
            val key = TrackLookupKey(video.id, video.title, video.artists, video.duration, session)
            val request = CompletableDeferred<MusicTrackResult>()
            val (cached, pending, generation) =
                cacheMutex.withLock {
                    ensureCacheLoadedLocked()
                    val now = nowMs()
                    trackCache.entries.removeAll { now - it.value.first >= TRACK_CACHE_TTL_MS || it.key.session != key.session }
                    val cached = trackCache[key]?.second
                    Triple(cached, if (cached != null) request else trackInFlight.getOrPut(key) { request }, cacheGeneration)
                }
            if (pending !== request) {
                try {
                    return pending.await()
                } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    cacheMutex.withLock { if (trackInFlight[key] === pending) trackInFlight.remove(key) }
                    continue
                }
            }
            return try {
                val result = cached ?: resolveMusicTrackUncoalesced(video, generation)
                val currentSession = cacheSessionKey()
                val current =
                    cacheMutex.withLock {
                        val current = if (generation == cacheGeneration && key.session == currentSession) result else MusicTrackResult.Error
                        if (current is MusicTrackResult.Found) {
                            if (cached == null) trackCache[key] = nowMs() to current
                            while (trackCache.size > CACHE_MAX_ENTRIES) {
                                trackCache.minByOrNull { it.value.first }?.key?.let(trackCache::remove)
                            }
                            if (current.song.canSwitchToMusicVideo) {
                                // The audio search need not return this video when searched in reverse.
                                cache[buildCacheKey(current.song)] = CacheEntry(video.id, nowMs(), current.positionMap?.reversed())
                                evictExpiredAndOversizedLocked()
                            }
                        }
                        request.complete(current)
                        current
                    }
                if (current is MusicTrackResult.Found) saveCacheToDisk()
                current
            } catch (error: CancellationException) {
                request.completeExceptionally(error)
                throw error
            } catch (error: Exception) {
                Timber.tag(TAG).w("music track lookup failed type=${error::class.simpleName ?: "unknown"}")
                request.complete(MusicTrackResult.Error)
                MusicTrackResult.Error
            } finally {
                withContext(NonCancellable) {
                    cacheMutex.withLock { if (trackInFlight[key] === request) trackInFlight.remove(key) }
                }
            }
        }
    }

    private suspend fun resolveMusicTrackUncoalesced(
        video: SongItem,
        generation: Long,
    ): MusicTrackResult {
        lookupAuthoritative(video, generation)?.let { counterpart ->
            val type = counterpart.targetMusicVideoType
            if (counterpart.targetVideoId != video.id && acceptsMusicTrackType(type)) {
                val song =
                    try {
                        songLookup(counterpart.targetVideoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("counterpart track metadata failed type=${e::class.simpleName ?: "unknown"}")
                        null
                    }
                if (song != null && song.id != video.id) {
                    return MusicTrackResult.Found(
                        song = song,
                        positionMap = counterpart.positionMap,
                        source = CounterpartResolutionSource.AUTHORITATIVE,
                    )
                }
            }
        }
        val page =
            try {
                search(buildTrackSearchQuery(video), YouTube.SearchFilter.FILTER_SONG)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("music track search failed type=${e::class.simpleName ?: "unknown"}")
                return MusicTrackResult.Error
            }
        if (page.onlineSearchFailed) return MusicTrackResult.Error
        val match = MusicVideoMatcher.findBestTrackMatch(video, page.items)
        return if (match != null && match.id != video.id) MusicTrackResult.Found(match) else MusicTrackResult.NotFound
    }

    suspend fun evictMapping(song: SongItem) {
        cacheMutex.withLock {
            ensureCacheLoadedLocked()
            cacheGeneration++
            cache.entries.removeAll { it.key == buildCacheKey(song) || it.value.videoId == song.id }
            authoritativeCache.remove(song.id)
            trackCache.entries.removeAll { it.key.videoId == song.id || it.value.second.song.id == song.id }
            trackInFlight.keys.filter { it.videoId == song.id }.forEach { key ->
                trackInFlight.remove(key)?.complete(MusicTrackResult.Error)
            }
            inFlight.remove(song.id)?.let { if (!it.isCompleted) it.complete(MusicVideoResult.Error) }
        }
        saveCacheToDisk()
    }

    suspend fun getCacheEntryCount(): Int =
        cacheMutex.withLock {
            ensureCacheLoadedLocked()
            cache.size
        }

    suspend fun getCacheSizeBytes(): Long {
        cacheMutex.withLock { ensureCacheLoadedLocked() }
        val raw = latestRawCacheJson
        return if (raw.isBlank() || raw == "{}") 0L else raw.encodeToByteArray().size.toLong()
    }

    suspend fun clearCache() {
        cacheMutex.withLock {
            cacheLoaded = true
            cacheGeneration++
            cache.clear()
            authoritativeCache.clear()
            trackCache.clear()
            trackInFlight.values.forEach { it.complete(MusicTrackResult.Error) }
            trackInFlight.clear()
            inFlight.values.forEach { if (!it.isCompleted) it.complete(MusicVideoResult.Error) }
            inFlight.clear()
            latestRawCacheJson = "{}"
            writeCacheJson("{}")
        }
        Timber.tag(TAG).d("media link cache cleared")
    }

    private suspend fun runLookup(item: SongItem): MusicVideoResult {
        val officialPage =
            try {
                search(buildVideoSearchQuery(item, includeOfficial = true), YouTube.SearchFilter.FILTER_VIDEO)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("media link search failed type=${e::class.simpleName ?: "unknown"}")
                return MusicVideoResult.Error
            }
        if (officialPage.onlineSearchFailed) return MusicVideoResult.Error
        val officialMatch = MusicVideoMatcher.findBestMatch(item, officialPage.items)
        if (officialMatch != null && isOfficialVideoType(officialMatch.musicVideoType)) {
            return MusicVideoResult.Found(officialMatch.id)
        }

        val widePage =
            try {
                search(buildVideoSearchQuery(item, includeOfficial = false), YouTube.SearchFilter.FILTER_VIDEO)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("media link search (wide) failed type=${e::class.simpleName ?: "unknown"}")
                return MusicVideoResult.Error
            }
        if (widePage.onlineSearchFailed) return MusicVideoResult.Error
        val wideMatch = MusicVideoMatcher.findBestMatch(item, widePage.items)

        if (wideMatch != null && isOfficialVideoType(wideMatch.musicVideoType)) {
            return MusicVideoResult.Found(wideMatch.id)
        }

        val bestMatch =
            officialMatch?.takeIf { acceptsMusicVideoType(it.musicVideoType) }
                ?: wideMatch?.takeIf { acceptsMusicVideoType(it.musicVideoType) }

        return bestMatch?.let { MusicVideoResult.Found(it.id) } ?: MusicVideoResult.NotFound
    }

    private suspend fun lookupAuthoritative(
        item: SongItem,
        generation: Long,
    ): MusicVideoCounterpart? {
        cacheMutex
            .withLock {
                evictExpiredAndOversizedLocked()
                authoritativeCache[item.id]?.second
            }?.let { return it }
        val counterpart =
            try {
                counterpartLookup(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("authoritative counterpart lookup unavailable type=${e::class.simpleName ?: "unknown"}")
                null
            }
        if (counterpart != null) {
            cacheMutex.withLock {
                if (generation == cacheGeneration) {
                    authoritativeCache[item.id] = nowMs() to counterpart
                    evictExpiredAndOversizedLocked()
                }
            }
        }
        return counterpart
    }

    private suspend fun cacheResult(
        item: SongItem,
        result: MusicVideoResult,
        generation: Long,
    ) {
        if (result is MusicVideoResult.Error) return
        val found = result as? MusicVideoResult.Found
        cacheMutex.withLock {
            if (generation != cacheGeneration) return
            cache[buildCacheKey(item)] =
                CacheEntry(
                    videoId = found?.videoId,
                    cachedAtMs = nowMs(),
                    positionMap = found?.positionMap,
                )
            evictExpiredAndOversizedLocked()
        }
        saveCacheToDisk()
    }

    private suspend fun cachedMusicVideoResult(item: SongItem): MusicVideoResult? {
        val cached =
            cacheMutex.withLock { cache[buildCacheKey(item)]?.takeIf { !isExpired(it) } }
                ?: return null
        Timber.tag(TAG).d("media link lookup cache hit")
        return cached.videoId?.let { MusicVideoResult.Found(it, cached.positionMap) } ?: MusicVideoResult.NotFound
    }

    private fun isExpired(entry: CacheEntry): Boolean {
        val ttlMs = if (entry.videoId != null) CACHE_FOUND_TTL_MS else CACHE_NEGATIVE_TTL_MS
        return nowMs() - entry.cachedAtMs > ttlMs
    }

    private fun evictExpiredAndOversizedLocked() {
        val currentMs = nowMs()
        authoritativeCache.entries.removeAll { currentMs - it.value.first > CACHE_FOUND_TTL_MS }
        while (authoritativeCache.size > CACHE_MAX_ENTRIES) {
            authoritativeCache.minByOrNull { it.value.first }?.key?.let(authoritativeCache::remove)
        }
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val ttlMs = if (entry.videoId != null) CACHE_FOUND_TTL_MS else CACHE_NEGATIVE_TTL_MS
            if (currentMs - entry.cachedAtMs > ttlMs) iterator.remove()
        }
        while (cache.size > CACHE_MAX_ENTRIES) {
            val oldestKey = cache.minByOrNull { (_, entry) -> entry.cachedAtMs }?.key ?: return
            cache.remove(oldestKey)
        }
    }

    private suspend fun saveCacheToDisk() {
        try {
            val serialized =
                cacheMutex.withLock {
                    val map =
                        buildJsonObject {
                            put(CACHE_SCHEMA_KEY, CACHE_SCHEMA_VERSION)
                            for ((key, entry) in cache) {
                                put(key, encodeCacheEntry(entry))
                            }
                        }
                    val encoded = json.encodeToString(JsonObject(map))
                    latestRawCacheJson = encoded
                    encoded
                }
            writeCacheJson(serialized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("failed to persist media link cache (${e::class.simpleName})")
        }
    }

    private fun encodeCacheEntry(entry: CacheEntry): JsonElement =
        buildJsonObject {
            put("v", entry.videoId?.let(::JsonPrimitive) ?: JsonNull)
            put("t", entry.cachedAtMs)
            entry.positionMap?.let { put("p", json.encodeToJsonElement(it)) }
        }

    private suspend fun ensureCacheLoadedLocked() {
        if (cacheLoaded) return
        cacheLoaded = true
        try {
            val raw = readCacheJson()
            latestRawCacheJson = raw
            if (raw.isBlank() || raw == "{}") return
            val obj = json.parseToJsonElement(raw).jsonObject
            if (obj[CACHE_SCHEMA_KEY]?.jsonPrimitive?.contentOrNull != CACHE_SCHEMA_VERSION) {
                latestRawCacheJson = "{}"
                writeCacheJson("{}")
                return
            }
            for ((key, value) in obj) {
                if (key == CACHE_SCHEMA_KEY) continue
                cache[key] = decodeCacheEntry(value)
            }
            evictExpiredAndOversizedLocked()
            Timber.tag(TAG).d("loaded ${cache.size} music video cache entries")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("failed to load music video cache (${e::class.simpleName})")
        }
    }

    private fun decodeCacheEntry(value: JsonElement): CacheEntry {
        val obj = (value as? JsonObject) ?: return CacheEntry(videoId = null, cachedAtMs = 0L)
        val videoId = (obj["v"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val cachedAtMs = obj["t"]?.jsonPrimitive?.longOrNull ?: 0L
        val positionMap =
            obj["p"]?.let { runCatching { json.decodeFromJsonElement<CounterpartPositionMap>(it) }.getOrNull() }
        return CacheEntry(videoId = videoId, cachedAtMs = cachedAtMs, positionMap = positionMap)
    }

    private fun buildVideoSearchQuery(
        song: SongItem,
        includeOfficial: Boolean,
    ): String {
        val artists = song.artists.map { it.name.trim() }.filter { it.isNotBlank() }
        val artistPart =
            when {
                artists.isEmpty() -> ""
                artists.size == 1 -> artists[0]
                else -> artists.take(2).joinToString(" ")
            }
        val cleanTitle = song.title.trim()
        val base = if (artistPart.isBlank()) cleanTitle else "$cleanTitle $artistPart"
        return if (includeOfficial) "$base official video" else base
    }

    private fun buildCacheKey(item: SongItem): String {
        val artists =
            item.artists
                .map { MusicVideoMatcher.normalizeName(it.name) }
                .filter { it.isNotBlank() }
                .sorted()
        val artistPart = if (artists.isEmpty()) "" else artists.joinToString(",")
        return "v:${item.id}|$artistPart|${MusicVideoMatcher.normalizeTitle(item.title)}"
    }

    private fun buildTrackSearchQuery(video: SongItem): String {
        val artist = video.artists.firstOrNull()?.name
        return listOf(video.title.trim(), artist?.trim()).filterNot { it.isNullOrBlank() }.joinToString(" ")
    }

    companion object {
        private const val TAG = "MusicVideoResolver"
        private const val CACHE_FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val CACHE_NEGATIVE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val CACHE_MAX_ENTRIES = 200
        private const val TRACK_CACHE_TTL_MS = 5L * 60 * 1000
        private const val CACHE_SCHEMA_KEY = "_schema"
        private const val CACHE_SCHEMA_VERSION = "3"
    }
}

