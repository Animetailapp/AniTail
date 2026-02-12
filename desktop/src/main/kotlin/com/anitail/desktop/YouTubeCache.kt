package com.anitail.desktop

import com.anitail.innertube.YouTube as InnertubeYouTube
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.YouTubeLocale
import com.anitail.innertube.pages.*
import java.net.Proxy
import kotlinx.coroutines.runBlocking

object YouTube {
    private val cache = mutableMapOf<String, Pair<Long, Any>>()
    private val lock = Any()

    // TTLs in milliseconds
    private const val TTL_SHORT = 30_000L
    private const val TTL_MEDIUM = 60_000L
    private const val TTL_LONG = 300_000L

    // Delegate properties
    var locale: YouTubeLocale
        get() = InnertubeYouTube.locale
        set(value) { InnertubeYouTube.locale = value }

    var visitorData: String?
        get() = InnertubeYouTube.visitorData
        set(value) { InnertubeYouTube.visitorData = value }

    var dataSyncId: String?
        get() = InnertubeYouTube.dataSyncId
        set(value) { InnertubeYouTube.dataSyncId = value }

    var cookie: String?
        get() = InnertubeYouTube.cookie
        set(value) { InnertubeYouTube.cookie = value }

    var proxy: Proxy?
        get() = InnertubeYouTube.proxy
        set(value) { InnertubeYouTube.proxy = value }

    var proxyAuth: String?
        get() = InnertubeYouTube.proxyAuth
        set(value) { InnertubeYouTube.proxyAuth = value }

    var useLoginForBrowse: Boolean
        get() = InnertubeYouTube.useLoginForBrowse
        set(value) { InnertubeYouTube.useLoginForBrowse = value }

    private fun <T> getCached(key: String, ttl: Long, loader: suspend () -> Result<T>): Result<T> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = cache[key]
            if (entry != null && entry.first > now) {
                @Suppress("UNCHECKED_CAST")
                return entry.second as Result<T>
            }
        }
        val result: Result<T> = runBlocking { loader() }

        synchronized(lock) {
            cache[key] = Pair(System.currentTimeMillis() + ttl, result as Any)
        }
        @Suppress("UNCHECKED_CAST")
        return result as Result<T>
    }

    fun clear(keyPrefix: String? = null) {
        synchronized(lock) {
            if (keyPrefix == null) cache.clear()
            else cache.keys.removeIf { it.startsWith(keyPrefix) }
        }
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> {
        val key = "next:${endpoint.videoId}:${endpoint.playlistId}:${endpoint.playlistSetVideoId}:${endpoint.index}:${endpoint.params}:${continuation}"
        return getCached(key, TTL_SHORT) { InnertubeYouTube.next(endpoint, continuation) }
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> {
        val key = "related:${endpoint.browseId}:${endpoint.params}"
        return getCached(key, TTL_SHORT) { InnertubeYouTube.related(endpoint) }
    }

    suspend fun artist(browseId: String): Result<ArtistPage> {
        val key = "artist:$browseId"
        return getCached(key, TTL_MEDIUM) { InnertubeYouTube.artist(browseId) }
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> {
        val key = "album:$browseId:$withSongs"
        return getCached(key, TTL_MEDIUM) { InnertubeYouTube.album(browseId, withSongs) }
    }

    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> {
        val key = "home:${continuation ?: ""}:${params ?: ""}"
        val ttl = if (continuation == null) TTL_MEDIUM else TTL_SHORT
        return getCached(key, ttl) { InnertubeYouTube.home(continuation, params) }
    }

    suspend fun library(browseId: String, tabIndex: Int = 0) = getCached("library:$browseId:$tabIndex", TTL_MEDIUM) { InnertubeYouTube.library(browseId, tabIndex) }

    suspend fun playlist(playlistId: String) = getCached("playlist:$playlistId", TTL_MEDIUM) { InnertubeYouTube.playlist(playlistId) }

    suspend fun player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null) =
        getCached("player:$videoId:$playlistId:${client.clientName}:$signatureTimestamp", TTL_SHORT) { InnertubeYouTube.player(videoId, playlistId, client, signatureTimestamp) }

    // Delegate non-cached methods
    suspend fun searchSuggestions(query: String) = InnertubeYouTube.searchSuggestions(query)
    suspend fun searchSummary(query: String) = InnertubeYouTube.searchSummary(query)
    suspend fun search(query: String, filter: InnertubeYouTube.SearchFilter) = InnertubeYouTube.search(query, filter)
    suspend fun searchContinuation(continuation: String) = InnertubeYouTube.searchContinuation(continuation)
    suspend fun albumSongs(playlistId: String, album: com.anitail.innertube.models.AlbumItem? = null) = InnertubeYouTube.albumSongs(playlistId, album)
    suspend fun artistItems(endpoint: BrowseEndpoint) = InnertubeYouTube.artistItems(endpoint)
    suspend fun artistItemsContinuation(continuation: String) = InnertubeYouTube.artistItemsContinuation(continuation)
    suspend fun playlistContinuation(continuation: String) = InnertubeYouTube.playlistContinuation(continuation)
    suspend fun explore() = InnertubeYouTube.explore()
    suspend fun newReleaseAlbums() = InnertubeYouTube.newReleaseAlbums()
    suspend fun moodAndGenres() = InnertubeYouTube.moodAndGenres()
    suspend fun browse(browseId: String, params: String?) = InnertubeYouTube.browse(browseId, params)
    suspend fun libraryContinuation(continuation: String) = InnertubeYouTube.libraryContinuation(continuation)
    suspend fun libraryRecentActivity() = InnertubeYouTube.libraryRecentActivity()
    suspend fun getChartsPage(continuation: String? = null) = InnertubeYouTube.getChartsPage(continuation)
    suspend fun musicHistory() = InnertubeYouTube.musicHistory()
    suspend fun likeVideo(videoId: String, like: Boolean) = InnertubeYouTube.likeVideo(videoId, like)
    suspend fun likePlaylist(playlistId: String, like: Boolean) = InnertubeYouTube.likePlaylist(playlistId, like)
    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) = InnertubeYouTube.subscribeChannel(channelId, subscribe)
    suspend fun getChannelId(browseId: String) = InnertubeYouTube.getChannelId(browseId)
    suspend fun addToPlaylist(playlistId: String, videoId: String) = InnertubeYouTube.addToPlaylist(playlistId, videoId)
    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = InnertubeYouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) = InnertubeYouTube.removeFromPlaylist(playlistId, videoId, setVideoId)
    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?) = InnertubeYouTube.moveSongPlaylist(playlistId, setVideoId, successorSetVideoId)
    fun createPlaylist(title: String) = InnertubeYouTube.createPlaylist(title)
    suspend fun renamePlaylist(playlistId: String, name: String) = InnertubeYouTube.renamePlaylist(playlistId, name)
    suspend fun deletePlaylist(playlistId: String) = InnertubeYouTube.deletePlaylist(playlistId)
    suspend fun registerPlayback(playlistId: String? = null, playbackTracking: String) = InnertubeYouTube.registerPlayback(playlistId, playbackTracking)
    suspend fun lyrics(endpoint: BrowseEndpoint) = InnertubeYouTube.lyrics(endpoint)
    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null) = InnertubeYouTube.queue(videoIds, playlistId)
    suspend fun transcript(videoId: String) = InnertubeYouTube.transcript(videoId)
    suspend fun visitorData() = InnertubeYouTube.visitorData()
    suspend fun accountInfo() = InnertubeYouTube.accountInfo()
    suspend fun getMediaInfo(videoId: String) = InnertubeYouTube.getMediaInfo(videoId)
}
