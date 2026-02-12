package com.anitail.desktop.db

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.RelatedSongMap
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.PlaylistSongMap
import com.anitail.desktop.db.entities.SearchHistory
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Desktop database implementation using JSON file storage.
 * This provides a simple yet functional database layer that mirrors
 * the Android Room database structure.
 *
 * Data is stored in ~/.anitail/database/ directory.
 */
class DesktopDatabase private constructor(
    private val basePath: Path = defaultDatabasePath(),
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // In-memory caches with StateFlow for reactivity
    private val _songs = MutableStateFlow<Map<String, SongEntity>>(emptyMap())
    private val _artists = MutableStateFlow<Map<String, ArtistEntity>>(emptyMap())
    private val _albums = MutableStateFlow<Map<String, AlbumEntity>>(emptyMap())
    private val _playlists = MutableStateFlow<Map<String, PlaylistEntity>>(emptyMap())
    private val _playlistSongMaps = MutableStateFlow<List<PlaylistSongMap>>(emptyList())
    private val _songArtistMaps = MutableStateFlow<List<SongArtistMap>>(emptyList())
    private val _relatedSongMaps = MutableStateFlow<List<RelatedSongMap>>(emptyList())
    private val _events = MutableStateFlow<List<EventEntity>>(emptyList())
    private val _searchHistory = MutableStateFlow<List<SearchHistory>>(emptyList())

    val songs: Flow<List<SongEntity>> = _songs.map { it.values.toList() }
    val artists: Flow<List<ArtistEntity>> = _artists.map { it.values.toList() }
    val albums: Flow<List<AlbumEntity>> = _albums.map { it.values.toList() }
    val playlists: Flow<List<PlaylistEntity>> = _playlists.map { it.values.toList() }
    val playlistSongMaps: Flow<List<PlaylistSongMap>> = _playlistSongMaps
    val songArtistMaps: Flow<List<SongArtistMap>> = _songArtistMaps
    val relatedSongMaps: Flow<List<RelatedSongMap>> = _relatedSongMaps
    val events: Flow<List<EventEntity>> = _events
    val searchHistory: Flow<List<SearchHistory>> = _searchHistory

    // === Song Operations ===

    fun song(songId: String): Flow<SongEntity?> = _songs.map { it[songId] }

    fun songsInLibrary(): Flow<List<SongEntity>> = _songs.map { songs ->
        songs.values.filter { it.inLibrary != null }.sortedByDescending { it.inLibrary }
    }

    fun likedSongs(): Flow<List<SongEntity>> = _songs.map { songs ->
        songs.values.filter { it.liked }.sortedByDescending { it.likedDate }
    }

    suspend fun insertSong(song: SongEntity) = withContext(Dispatchers.IO) {
        val existing = _songs.value[song.id]
        val merged = if (existing != null) mergeSong(existing, song) else song
        _songs.value = _songs.value + (song.id to merged)
        saveSongs()
    }

    suspend fun insertSong(song: SongEntity, artistMaps: List<SongArtistMap>) {
        insertSong(song)
        insertSongArtistMaps(artistMaps)
    }

    suspend fun updateSong(song: SongEntity) = withContext(Dispatchers.IO) {
        _songs.value = _songs.value + (song.id to song)
        saveSongs()
    }

    suspend fun deleteSong(songId: String) = withContext(Dispatchers.IO) {
        _songs.value = _songs.value - songId
        _songArtistMaps.value = _songArtistMaps.value.filterNot { it.songId == songId }
        saveSongs()
        saveSongArtistMaps()
    }

    suspend fun toggleSongLike(songId: String) = withContext(Dispatchers.IO) {
        val song = _songs.value[songId] ?: return@withContext
        val updated = song.toggleLike()
        _songs.value = _songs.value + (songId to updated)
        saveSongs()
    }

    suspend fun toggleSongInLibrary(songId: String) = withContext(Dispatchers.IO) {
        val song = _songs.value[songId] ?: return@withContext
        val updated = song.toggleLibrary()
        _songs.value = _songs.value + (songId to updated)
        saveSongs()
    }

    internal fun mergeSong(existing: SongEntity, incoming: SongEntity): SongEntity {
        val liked = existing.liked || incoming.liked
        val likedDate = if (liked) incoming.likedDate ?: existing.likedDate else null
        return incoming.copy(
            liked = liked,
            likedDate = likedDate,
            inLibrary = incoming.inLibrary ?: existing.inLibrary,
            totalPlayTime = maxOf(existing.totalPlayTime, incoming.totalPlayTime),
            dateDownload = incoming.dateDownload ?: existing.dateDownload,
            explicit = existing.explicit || incoming.explicit,
            isLocal = existing.isLocal || incoming.isLocal,
            romanizeLyrics = existing.romanizeLyrics,
            mediaStoreUri = incoming.mediaStoreUri ?: existing.mediaStoreUri,
        )
    }

    suspend fun insertSongArtistMaps(maps: List<SongArtistMap>) = withContext(Dispatchers.IO) {
        if (maps.isEmpty()) return@withContext
        val existing = _songArtistMaps.value
        val existingPairs = existing.map { it.songId to it.artistId }.toSet()
        val uniqueMaps = maps.filterNot { (it.songId to it.artistId) in existingPairs }
        if (uniqueMaps.isEmpty()) return@withContext
        _songArtistMaps.value = existing + uniqueMaps
        saveSongArtistMaps()
    }

    // === Artist Operations ===

    fun artist(artistId: String): Flow<ArtistEntity?> = _artists.map { it[artistId] }

    fun bookmarkedArtists(): Flow<List<ArtistEntity>> = _artists.map { artists ->
        artists.values.filter { it.bookmarkedAt != null }.sortedByDescending { it.bookmarkedAt }
    }

    suspend fun insertArtist(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        _artists.value = _artists.value + (artist.id to artist)
        saveArtists()
    }

    suspend fun updateArtist(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        _artists.value = _artists.value + (artist.id to artist)
        saveArtists()
    }

    suspend fun deleteArtist(artistId: String) = withContext(Dispatchers.IO) {
        _artists.value = _artists.value - artistId
        _songArtistMaps.value = _songArtistMaps.value.filterNot { it.artistId == artistId }
        saveArtists()
        saveSongArtistMaps()
    }

    suspend fun toggleArtistBookmark(artistId: String) = withContext(Dispatchers.IO) {
        val artist = _artists.value[artistId] ?: return@withContext
        val updated = artist.toggleLike()
        _artists.value = _artists.value + (artistId to updated)
        saveArtists()
    }

    // === Album Operations ===

    fun album(albumId: String): Flow<AlbumEntity?> = _albums.map { it[albumId] }

    fun bookmarkedAlbums(): Flow<List<AlbumEntity>> = _albums.map { albums ->
        albums.values.filter { it.bookmarkedAt != null }.sortedByDescending { it.bookmarkedAt }
    }

    fun albumsInLibrary(): Flow<List<AlbumEntity>> = _albums.map { albums ->
        albums.values.filter { it.inLibrary != null }.sortedByDescending { it.inLibrary }
    }

    suspend fun insertAlbum(album: AlbumEntity) = withContext(Dispatchers.IO) {
        _albums.value = _albums.value + (album.id to album)
        saveAlbums()
    }

    suspend fun updateAlbum(album: AlbumEntity) = withContext(Dispatchers.IO) {
        _albums.value = _albums.value + (album.id to album)
        saveAlbums()
    }

    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        _albums.value = _albums.value - albumId
        saveAlbums()
    }

    suspend fun toggleAlbumBookmark(albumId: String) = withContext(Dispatchers.IO) {
        val album = _albums.value[albumId] ?: return@withContext
        val updated = album.toggleLike()
        _albums.value = _albums.value + (albumId to updated)
        saveAlbums()
    }

    // === Playlist Operations ===

    fun playlist(playlistId: String): Flow<PlaylistEntity?> = _playlists.map { it[playlistId] }

    fun allPlaylists(): Flow<List<PlaylistEntity>> = _playlists.map { playlists ->
        playlists.values.sortedByDescending { it.createdAt }
    }

    fun bookmarkedPlaylists(): Flow<List<PlaylistEntity>> = _playlists.map { playlists ->
        playlists.values.filter { it.bookmarkedAt != null }.sortedByDescending { it.bookmarkedAt }
    }

    suspend fun insertPlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        upsertPlaylistResolvingDuplicates(playlist)
        savePlaylists()
        savePlaylistSongMaps()
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        upsertPlaylistResolvingDuplicates(playlist)
        savePlaylists()
        savePlaylistSongMaps()
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        _playlists.value = _playlists.value - playlistId
        _playlistSongMaps.value = _playlistSongMaps.value.filter { it.playlistId != playlistId }
        savePlaylists()
        savePlaylistSongMaps()
    }

    suspend fun togglePlaylistBookmark(playlistId: String) = withContext(Dispatchers.IO) {
        val playlist = _playlists.value[playlistId] ?: return@withContext
        val updated = playlist.toggleLike()
        _playlists.value = _playlists.value + (playlistId to updated)
        savePlaylists()
    }

    internal fun mergePlaylist(existing: PlaylistEntity, incoming: PlaylistEntity): PlaylistEntity {
        return incoming.copy(
            name = incoming.name.ifBlank { existing.name },
            browseId = incoming.browseId ?: existing.browseId,
            createdAt = incoming.createdAt ?: existing.createdAt,
            lastUpdateTime = incoming.lastUpdateTime ?: existing.lastUpdateTime,
            isEditable = existing.isEditable || incoming.isEditable,
            bookmarkedAt = incoming.bookmarkedAt ?: existing.bookmarkedAt,
            remoteSongCount = incoming.remoteSongCount ?: existing.remoteSongCount,
            playEndpointParams = incoming.playEndpointParams ?: existing.playEndpointParams,
            thumbnailUrl = incoming.thumbnailUrl ?: existing.thumbnailUrl,
            shuffleEndpointParams = incoming.shuffleEndpointParams ?: existing.shuffleEndpointParams,
            radioEndpointParams = incoming.radioEndpointParams ?: existing.radioEndpointParams,
            backgroundImageUrl = incoming.backgroundImageUrl ?: existing.backgroundImageUrl,
        )
    }

    private fun upsertPlaylistResolvingDuplicates(playlist: PlaylistEntity) {
        val current = _playlists.value.toMutableMap()
        val duplicateKeyByBrowseId = resolveDuplicatePlaylistKeyByBrowseId(
            browseId = playlist.browseId,
            excludeId = playlist.id,
            playlists = current,
        )

        if (duplicateKeyByBrowseId != null) {
            val existing = current[duplicateKeyByBrowseId]
            if (existing != null) {
                val merged = mergePlaylist(existing, playlist).copy(
                    id = duplicateKeyByBrowseId,
                    browseId = existing.browseId ?: playlist.browseId,
                )
                current[duplicateKeyByBrowseId] = merged
                if (playlist.id != duplicateKeyByBrowseId) {
                    current.remove(playlist.id)
                    _playlistSongMaps.value = _playlistSongMaps.value.filterNot { it.playlistId == playlist.id }
                }
                _playlists.value = current
                normalizeDuplicatePlaylistsByBrowseId()
                return
            }
        }

        val existing = current[playlist.id]
        val merged = if (existing != null) mergePlaylist(existing, playlist) else playlist
        current[playlist.id] = merged
        _playlists.value = current
        normalizeDuplicatePlaylistsByBrowseId()
    }

    private fun resolveDuplicatePlaylistKeyByBrowseId(
        browseId: String?,
        excludeId: String,
        playlists: Map<String, PlaylistEntity>,
    ): String? {
        val normalizedBrowseId = browseId?.trim().orEmpty()
        if (normalizedBrowseId.isBlank()) return null
        return playlists.entries.firstOrNull { (id, candidate) ->
            id != excludeId &&
                candidate.browseId?.trim().orEmpty() == normalizedBrowseId
        }?.key
    }

    private fun normalizeDuplicatePlaylistsByBrowseId() {
        val playlists = _playlists.value
        if (playlists.size < 2) return

        val grouped = playlists.entries
            .filter { it.value.browseId?.isNotBlank() == true }
            .groupBy { it.value.browseId!!.trim() }
            .filterValues { it.size > 1 }
        if (grouped.isEmpty()) return

        val songCountByPlaylist = _playlistSongMaps.value.groupingBy { it.playlistId }.eachCount()
        val updatedPlaylists = playlists.toMutableMap()
        var updatedMaps = _playlistSongMaps.value

        grouped.values.forEach { entries ->
            val canonical = selectCanonicalPlaylist(entries, songCountByPlaylist)
            var canonicalPlaylist = canonical.value
            entries.forEach { duplicate ->
                if (duplicate.key == canonical.key) return@forEach
                canonicalPlaylist = mergePlaylist(canonicalPlaylist, duplicate.value).copy(
                    id = canonical.key,
                    browseId = canonicalPlaylist.browseId ?: duplicate.value.browseId,
                )
                updatedPlaylists.remove(duplicate.key)
                updatedMaps = updatedMaps.filterNot { it.playlistId == duplicate.key }
            }
            updatedPlaylists[canonical.key] = canonicalPlaylist
        }

        _playlists.value = updatedPlaylists
        _playlistSongMaps.value = updatedMaps
    }

    private fun selectCanonicalPlaylist(
        entries: List<Map.Entry<String, PlaylistEntity>>,
        songCountByPlaylist: Map<String, Int>,
    ): Map.Entry<String, PlaylistEntity> {
        return entries.maxWithOrNull(
            compareBy<Map.Entry<String, PlaylistEntity>>(
                { songCountByPlaylist[it.key] ?: 0 },
                { if (it.value.bookmarkedAt != null) 1 else 0 },
                { it.value.remoteSongCount ?: 0 },
                { it.value.lastUpdateTime ?: LocalDateTime.MIN },
                { it.value.createdAt ?: LocalDateTime.MIN },
            ),
        ) ?: entries.first()
    }

    // === Playlist Song Map Operations ===

    fun songsInPlaylist(playlistId: String): Flow<List<SongEntity>> =
        _playlistSongMaps.map { maps ->
            val songIds = maps.filter { it.playlistId == playlistId }
                .sortedBy { it.position }
                .map { it.songId }
            songIds.mapNotNull { _songs.value[it] }
        }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) =
        withContext(Dispatchers.IO) {
            val existingMaps = _playlistSongMaps.value.filter { it.playlistId == playlistId }
            val maxPosition = existingMaps.maxOfOrNull { it.position } ?: -1
            val nextId = (_playlistSongMaps.value.maxOfOrNull { it.id } ?: 0) + 1
            val newMap = PlaylistSongMap(
                id = nextId,
                playlistId = playlistId,
                songId = songId,
                position = maxPosition + 1,
            )
            _playlistSongMaps.value = _playlistSongMaps.value + newMap
            savePlaylistSongMaps()
        }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) =
        withContext(Dispatchers.IO) {
            _playlistSongMaps.value = _playlistSongMaps.value.filter {
                !(it.playlistId == playlistId && it.songId == songId)
            }
            savePlaylistSongMaps()
        }

    // === Related Song Map Operations ===

    fun hasRelatedSongs(songId: String): Boolean =
        _relatedSongMaps.value.any { it.songId == songId }

    fun relatedSongs(songId: String): List<SongEntity> {
        val relatedIds = _relatedSongMaps.value
            .filter { it.songId == songId }
            .map { it.relatedSongId }
            .distinct()
        return relatedIds.mapNotNull { _songs.value[it] }
    }

    suspend fun insertRelatedSongs(songId: String, relatedSongIds: List<String>) =
        withContext(Dispatchers.IO) {
            if (relatedSongIds.isEmpty()) return@withContext
            val existing = _relatedSongMaps.value
                .filter { it.songId == songId }
                .map { it.relatedSongId }
                .toSet()
            val uniqueIds = relatedSongIds.filterNot { it in existing }.distinct()
            if (uniqueIds.isEmpty()) return@withContext

            val startId = (_relatedSongMaps.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val newMaps = uniqueIds.mapIndexed { index, relatedId ->
                RelatedSongMap(
                    id = startId + index,
                    songId = songId,
                    relatedSongId = relatedId,
                )
            }
            _relatedSongMaps.value = _relatedSongMaps.value + newMaps
            saveRelatedSongMaps()
        }

    // === Event Operations (History) ===

    fun recentEvents(limit: Int = 100): Flow<List<EventEntity>> = _events.map { events ->
        events.sortedByDescending { it.timestamp }.take(limit)
    }

    fun eventsForSong(songId: String): Flow<List<EventEntity>> = _events.map { events ->
        events.filter { it.songId == songId }.sortedByDescending { it.timestamp }
    }

    suspend fun insertEvent(event: EventEntity) = withContext(Dispatchers.IO) {
        val newId = (_events.value.maxOfOrNull { it.id } ?: 0) + 1
        val eventWithId = event.copy(id = newId)
        _events.value = _events.value + eventWithId
        saveEvents()
    }

    suspend fun clearEvents() = withContext(Dispatchers.IO) {
        _events.value = emptyList()
        saveEvents()
    }

    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        _events.value = _events.value.filterNot { it.id == eventId }
        saveEvents()
    }

    // === Search History Operations ===

    fun recentSearches(limit: Int = 20): Flow<List<SearchHistory>> = _searchHistory.map { history ->
        history.sortedByDescending { it.id }.take(limit)
    }

    suspend fun insertSearch(query: String) = withContext(Dispatchers.IO) {
        // Remove duplicate if exists
        _searchHistory.value = _searchHistory.value.filter { it.query != query }
        val newId = (_searchHistory.value.maxOfOrNull { it.id } ?: 0) + 1
        val newEntry = SearchHistory(id = newId, query = query)
        _searchHistory.value = _searchHistory.value + newEntry
        saveSearchHistory()
    }

    suspend fun deleteSearch(query: String) = withContext(Dispatchers.IO) {
        _searchHistory.value = _searchHistory.value.filter { it.query != query }
        saveSearchHistory()
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        _searchHistory.value = emptyList()
        saveSearchHistory()
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        _songs.value = emptyMap()
        _artists.value = emptyMap()
        _albums.value = emptyMap()
        _playlists.value = emptyMap()
        _playlistSongMaps.value = emptyList()
        _songArtistMaps.value = emptyList()
        _relatedSongMaps.value = emptyList()
        _events.value = emptyList()
        _searchHistory.value = emptyList()

        saveSongs()
        saveArtists()
        saveAlbums()
        savePlaylists()
        savePlaylistSongMaps()
        saveSongArtistMaps()
        saveRelatedSongMaps()
        saveEvents()
        saveSearchHistory()
    }

    suspend fun snapshot(): DesktopDatabaseSnapshot = withContext(Dispatchers.IO) {
        DesktopDatabaseSnapshot(
            songs = _songs.value.values.toList(),
            artists = _artists.value.values.toList(),
            albums = _albums.value.values.toList(),
            playlists = _playlists.value.values.toList(),
            playlistSongMaps = _playlistSongMaps.value.toList(),
            songArtistMaps = _songArtistMaps.value.toList(),
            relatedSongMaps = _relatedSongMaps.value.toList(),
            events = _events.value.toList(),
            searchHistory = _searchHistory.value.toList(),
        )
    }

    suspend fun replaceAll(snapshot: DesktopDatabaseSnapshot) = withContext(Dispatchers.IO) {
        _songs.value = snapshot.songs.associateBy { it.id }
        _artists.value = snapshot.artists.associateBy { it.id }
        _albums.value = snapshot.albums.associateBy { it.id }
        _playlists.value = snapshot.playlists.associateBy { it.id }
        _playlistSongMaps.value = snapshot.playlistSongMaps
        normalizeDuplicatePlaylistsByBrowseId()
        _songArtistMaps.value = snapshot.songArtistMaps
        _relatedSongMaps.value = snapshot.relatedSongMaps
        _events.value = snapshot.events
        _searchHistory.value = snapshot.searchHistory

        saveSongs()
        saveArtists()
        saveAlbums()
        savePlaylists()
        savePlaylistSongMaps()
        saveSongArtistMaps()
        saveRelatedSongMaps()
        saveEvents()
        saveSearchHistory()
    }

    // === Stats Queries ===

    fun mostPlayedSongs(limit: Int = 50): Flow<List<SongEntity>> = _songs.map { songs ->
        songs.values.sortedByDescending { it.totalPlayTime }.take(limit)
    }

    fun totalPlayTimeForSong(songId: String): Flow<Long> = _events.map { events ->
        events.filter { it.songId == songId }.sumOf { it.playTime }
    }

    // === Initialization ===

    suspend fun initialize() = withContext(Dispatchers.IO) {
        ensureDirectoryExists()
        loadSongs()
        loadSongArtistMaps()
        loadArtists()
        loadAlbums()
        loadPlaylists()
        loadPlaylistSongMaps()
        normalizeDuplicatePlaylistsByBrowseId()
        savePlaylists()
        savePlaylistSongMaps()
        loadRelatedSongMaps()
        loadEvents()
        loadSearchHistory()
    }

    private fun ensureDirectoryExists() {
        if (!Files.exists(basePath)) {
            Files.createDirectories(basePath)
        }
    }

    // === Serialization Helpers ===

    private fun LocalDateTime?.toJsonString(): String = this?.format(formatter) ?: ""
    private fun String.toLocalDateTime(): LocalDateTime? = takeIf { it.isNotBlank() }?.let {
        LocalDateTime.parse(it, formatter)
    }

    // === Song Persistence ===

    private fun loadSongs() {
        val file = basePath.resolve("songs.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val songs = mutableMapOf<String, SongEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val song = SongEntity(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    duration = obj.optInt("duration", -1),
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    albumId = obj.optString("albumId").takeIf { it.isNotBlank() },
                    albumName = obj.optString("albumName").takeIf { it.isNotBlank() },
                    artistName = obj.optString("artistName").takeIf { it.isNotBlank() },
                    explicit = obj.optBoolean("explicit", false),
                    year = obj.optInt("year", 0).takeIf { it > 0 },
                    date = obj.optString("date").toLocalDateTime(),
                    dateModified = obj.optString("dateModified").toLocalDateTime(),
                    liked = obj.optBoolean("liked", false),
                    likedDate = obj.optString("likedDate").toLocalDateTime(),
                    totalPlayTime = obj.optLong("totalPlayTime", 0),
                    inLibrary = obj.optString("inLibrary").toLocalDateTime(),
                    dateDownload = obj.optString("dateDownload").toLocalDateTime(),
                    isLocal = obj.optBoolean("isLocal", false),
                    romanizeLyrics = obj.optBoolean("romanizeLyrics", true),
                    mediaStoreUri = obj.optString("mediaStoreUri").takeIf { it.isNotBlank() },
                )
                songs[song.id] = song
            }
            _songs.value = songs
        }
    }

    private fun saveSongs() {
        val file = basePath.resolve("songs.json")
        val array = JSONArray()
        _songs.value.values.forEach { song ->
            array.put(JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("duration", song.duration)
                put("thumbnailUrl", song.thumbnailUrl ?: "")
                put("albumId", song.albumId ?: "")
                put("albumName", song.albumName ?: "")
                put("artistName", song.artistName ?: "")
                put("explicit", song.explicit)
                put("year", song.year ?: 0)
                put("date", song.date.toJsonString())
                put("dateModified", song.dateModified.toJsonString())
                put("liked", song.liked)
                put("likedDate", song.likedDate.toJsonString())
                put("totalPlayTime", song.totalPlayTime)
                put("inLibrary", song.inLibrary.toJsonString())
                put("dateDownload", song.dateDownload.toJsonString())
                put("isLocal", song.isLocal)
                put("romanizeLyrics", song.romanizeLyrics)
                put("mediaStoreUri", song.mediaStoreUri ?: "")
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Artist Persistence ===

    private fun loadArtists() {
        val file = basePath.resolve("artists.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val artists = mutableMapOf<String, ArtistEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val artist = ArtistEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    channelId = obj.optString("channelId").takeIf { it.isNotBlank() },
                    lastUpdateTime = obj.optString("lastUpdateTime").toLocalDateTime()
                        ?: LocalDateTime.now(),
                    bookmarkedAt = obj.optString("bookmarkedAt").toLocalDateTime(),
                )
                artists[artist.id] = artist
            }
            _artists.value = artists
        }
    }

    private fun saveArtists() {
        val file = basePath.resolve("artists.json")
        val array = JSONArray()
        _artists.value.values.forEach { artist ->
            array.put(JSONObject().apply {
                put("id", artist.id)
                put("name", artist.name)
                put("thumbnailUrl", artist.thumbnailUrl ?: "")
                put("channelId", artist.channelId ?: "")
                put("lastUpdateTime", artist.lastUpdateTime.toJsonString())
                put("bookmarkedAt", artist.bookmarkedAt.toJsonString())
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Album Persistence ===

    private fun loadAlbums() {
        val file = basePath.resolve("albums.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val albums = mutableMapOf<String, AlbumEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val album = AlbumEntity(
                    id = obj.getString("id"),
                    playlistId = obj.optString("playlistId").takeIf { it.isNotBlank() },
                    title = obj.getString("title"),
                    year = obj.optInt("year", 0).takeIf { it > 0 },
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    themeColor = obj.optInt("themeColor", 0).takeIf { it != 0 },
                    songCount = obj.optInt("songCount", 0),
                    duration = obj.optInt("duration", 0),
                    lastUpdateTime = obj.optString("lastUpdateTime").toLocalDateTime()
                        ?: LocalDateTime.now(),
                    bookmarkedAt = obj.optString("bookmarkedAt").toLocalDateTime(),
                    likedDate = obj.optString("likedDate").toLocalDateTime(),
                    inLibrary = obj.optString("inLibrary").toLocalDateTime(),
                )
                albums[album.id] = album
            }
            _albums.value = albums
        }
    }

    private fun saveAlbums() {
        val file = basePath.resolve("albums.json")
        val array = JSONArray()
        _albums.value.values.forEach { album ->
            array.put(JSONObject().apply {
                put("id", album.id)
                put("playlistId", album.playlistId ?: "")
                put("title", album.title)
                put("year", album.year ?: 0)
                put("thumbnailUrl", album.thumbnailUrl ?: "")
                put("themeColor", album.themeColor ?: 0)
                put("songCount", album.songCount)
                put("duration", album.duration)
                put("lastUpdateTime", album.lastUpdateTime.toJsonString())
                put("bookmarkedAt", album.bookmarkedAt.toJsonString())
                put("likedDate", album.likedDate.toJsonString())
                put("inLibrary", album.inLibrary.toJsonString())
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Playlist Persistence ===

    private fun loadPlaylists() {
        val file = basePath.resolve("playlists.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val playlists = mutableMapOf<String, PlaylistEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val playlist = PlaylistEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    browseId = obj.optString("browseId").takeIf { it.isNotBlank() },
                    createdAt = obj.optString("createdAt").toLocalDateTime(),
                    lastUpdateTime = obj.optString("lastUpdateTime").toLocalDateTime(),
                    isEditable = obj.optBoolean("isEditable", true),
                    bookmarkedAt = obj.optString("bookmarkedAt").toLocalDateTime(),
                    remoteSongCount = obj.optInt("remoteSongCount", 0).takeIf { it > 0 },
                    playEndpointParams = obj.optString("playEndpointParams")
                        .takeIf { it.isNotBlank() },
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    shuffleEndpointParams = obj.optString("shuffleEndpointParams")
                        .takeIf { it.isNotBlank() },
                    radioEndpointParams = obj.optString("radioEndpointParams")
                        .takeIf { it.isNotBlank() },
                    backgroundImageUrl = obj.optString("backgroundImageUrl")
                        .takeIf { it.isNotBlank() },
                )
                playlists[playlist.id] = playlist
            }
            _playlists.value = playlists
        }
    }

    private fun savePlaylists() {
        val file = basePath.resolve("playlists.json")
        val array = JSONArray()
        _playlists.value.values.forEach { playlist ->
            array.put(JSONObject().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("browseId", playlist.browseId ?: "")
                put("createdAt", playlist.createdAt.toJsonString())
                put("lastUpdateTime", playlist.lastUpdateTime.toJsonString())
                put("isEditable", playlist.isEditable)
                put("bookmarkedAt", playlist.bookmarkedAt.toJsonString())
                put("remoteSongCount", playlist.remoteSongCount ?: 0)
                put("playEndpointParams", playlist.playEndpointParams ?: "")
                put("thumbnailUrl", playlist.thumbnailUrl ?: "")
                put("shuffleEndpointParams", playlist.shuffleEndpointParams ?: "")
                put("radioEndpointParams", playlist.radioEndpointParams ?: "")
                put("backgroundImageUrl", playlist.backgroundImageUrl ?: "")
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Playlist Song Map Persistence ===

    private fun loadPlaylistSongMaps() {
        val file = basePath.resolve("playlist_songs.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val maps = mutableListOf<PlaylistSongMap>()
            var nextId = 1
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val storedId = obj.optInt("id", 0)
                val id = if (storedId > 0) storedId else nextId++
                if (storedId >= nextId) {
                    nextId = storedId + 1
                }
                maps.add(
                    PlaylistSongMap(
                        id = id,
                        playlistId = obj.getString("playlistId"),
                        songId = obj.getString("songId"),
                        position = obj.optInt("position", 0),
                        setVideoId = obj.optString("setVideoId").takeIf { it.isNotBlank() },
                    )
                )
            }
            _playlistSongMaps.value = maps
        }
    }

    private fun savePlaylistSongMaps() {
        val file = basePath.resolve("playlist_songs.json")
        val array = JSONArray()
        _playlistSongMaps.value.forEach { map ->
            array.put(JSONObject().apply {
                put("id", map.id)
                put("playlistId", map.playlistId)
                put("songId", map.songId)
                put("position", map.position)
                put("setVideoId", map.setVideoId ?: "")
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Song Artist Map Persistence ===

    private fun loadSongArtistMaps() {
        val file = basePath.resolve("song_artist_map.json")
        if (!Files.exists(file)) {
            val legacy = loadLegacySongArtistMapsFromSongs()
            _songArtistMaps.value = legacy
            if (legacy.isNotEmpty()) {
                saveSongArtistMaps()
            }
            return
        }
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val maps = mutableListOf<SongArtistMap>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                maps.add(
                    SongArtistMap(
                        songId = obj.getString("songId"),
                        artistId = obj.getString("artistId"),
                        position = obj.optInt("position", 0),
                    )
                )
            }
            if (maps.isEmpty()) {
                val legacy = loadLegacySongArtistMapsFromSongs()
                if (legacy.isNotEmpty()) {
                    _songArtistMaps.value = legacy
                    saveSongArtistMaps()
                    return
                }
            }
            _songArtistMaps.value = maps
        }
    }

    private fun saveSongArtistMaps() {
        val file = basePath.resolve("song_artist_map.json")
        val array = JSONArray()
        _songArtistMaps.value.forEach { map ->
            array.put(JSONObject().apply {
                put("songId", map.songId)
                put("artistId", map.artistId)
                put("position", map.position)
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    private fun loadLegacySongArtistMapsFromSongs(): List<SongArtistMap> {
        val file = basePath.resolve("songs.json")
        if (!Files.exists(file)) return emptyList()
        return runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            val maps = mutableListOf<SongArtistMap>()
            val seen = mutableSetOf<Pair<String, String>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val songId = obj.optString("id")
                val artistId = obj.optString("artistId")
                if (songId.isBlank() || artistId.isBlank()) continue
                val key = songId to artistId
                if (seen.add(key)) {
                    maps.add(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                }
            }
            maps
        }.getOrDefault(emptyList())
    }

    // === Related Song Map Persistence ===

    private fun loadRelatedSongMaps() {
        val file = basePath.resolve("related_song_map.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val maps = mutableListOf<RelatedSongMap>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                maps.add(
                    RelatedSongMap(
                        id = obj.getLong("id"),
                        songId = obj.getString("songId"),
                        relatedSongId = obj.getString("relatedSongId"),
                    )
                )
            }
            _relatedSongMaps.value = maps
        }
    }

    private fun saveRelatedSongMaps() {
        val file = basePath.resolve("related_song_map.json")
        val array = JSONArray()
        _relatedSongMaps.value.forEach { map ->
            array.put(JSONObject().apply {
                put("id", map.id)
                put("songId", map.songId)
                put("relatedSongId", map.relatedSongId)
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Event Persistence ===

    private fun loadEvents() {
        val file = basePath.resolve("events.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val events = mutableListOf<EventEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                events.add(
                    EventEntity(
                        id = obj.getLong("id"),
                        songId = obj.getString("songId"),
                        timestamp = obj.getString("timestamp").toLocalDateTime()
                            ?: LocalDateTime.now(),
                        playTime = obj.getLong("playTime"),
                    )
                )
            }
            _events.value = events
        }
    }

    private fun saveEvents() {
        val file = basePath.resolve("events.json")
        val array = JSONArray()
        _events.value.forEach { event ->
            array.put(JSONObject().apply {
                put("id", event.id)
                put("songId", event.songId)
                put("timestamp", event.timestamp.toJsonString())
                put("playTime", event.playTime)
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    // === Search History Persistence ===

    private fun loadSearchHistory() {
        val file = basePath.resolve("search_history.json")
        if (!Files.exists(file)) return
        runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val array = JSONArray(raw)
            val history = mutableListOf<SearchHistory>()
            var nextId = 1L
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val storedId = obj.optLong("id", 0L)
                val id = if (storedId > 0) storedId else nextId++
                if (storedId >= nextId) {
                    nextId = storedId + 1
                }
                history.add(
                    SearchHistory(
                        id = id,
                        query = obj.getString("query"),
                    )
                )
            }
            _searchHistory.value = history
        }
    }

    private fun saveSearchHistory() {
        val file = basePath.resolve("search_history.json")
        val array = JSONArray()
        _searchHistory.value.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("query", entry.query)
            })
        }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    companion object {
        @Volatile
        private var INSTANCE: DesktopDatabase? = null

        fun getInstance(): DesktopDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DesktopDatabase().also { INSTANCE = it }
            }
        }

        internal fun createForTests(basePath: Path): DesktopDatabase = DesktopDatabase(basePath)

        private fun defaultDatabasePath(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".anitail", "database")
        }
    }
}

data class DesktopDatabaseSnapshot(
    val songs: List<SongEntity>,
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val playlists: List<PlaylistEntity>,
    val playlistSongMaps: List<PlaylistSongMap>,
    val songArtistMaps: List<SongArtistMap>,
    val relatedSongMaps: List<RelatedSongMap>,
    val events: List<EventEntity>,
    val searchHistory: List<SearchHistory>,
)
