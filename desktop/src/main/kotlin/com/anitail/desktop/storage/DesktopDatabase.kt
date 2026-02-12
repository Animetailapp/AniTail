package com.anitail.desktop.storage

import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/**
 * Sistema de persistencia mejorado para Desktop.
 * Almacena canciones, playlists, historial, y más en formato JSON.
 * Emula la funcionalidad de MusicDatabase de Android.
 */
class DesktopDatabase(
    private val baseDir: Path = defaultDataPath(),
) {
    // Archivos de datos
    private val songsFile = baseDir.resolve("songs.json")
    private val playlistsFile = baseDir.resolve("playlists.json")
    private val searchHistoryFile = baseDir.resolve("search_history.json")
    private val playHistoryFile = baseDir.resolve("play_history.json")
    private val artistsFile = baseDir.resolve("artists.json")
    private val albumsFile = baseDir.resolve("albums.json")
    private val likedSongsFile = baseDir.resolve("liked_songs.json")
    private val settingsFile = baseDir.resolve("settings.json")

    init {
        ensureDirectory()
    }

    private fun ensureDirectory() {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir)
        }
    }

    // ==================== SONGS ====================

    suspend fun getSongs(): List<SongEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(songsFile) { obj ->
            SongEntry(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                artistId = obj.optString("artistId").takeIf { it.isNotBlank() },
                albumId = obj.optString("albumId").takeIf { it.isNotBlank() },
                albumName = obj.optString("albumName").takeIf { it.isNotBlank() },
                artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                durationMs = obj.optLong("durationMs", 0L),
                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
                liked = obj.optBoolean("liked", false),
            )
        }
    }

    suspend fun insertSong(song: SongEntry) = withContext(Dispatchers.IO) {
        val songs = getSongs().toMutableList()
        if (songs.none { it.id == song.id }) {
            songs.add(song)
            saveSongs(songs)
        }
    }

    suspend fun updateSong(song: SongEntry) = withContext(Dispatchers.IO) {
        val songs = getSongs().toMutableList()
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            songs[index] = song
            saveSongs(songs)
        }
    }

    suspend fun deleteSong(songId: String) = withContext(Dispatchers.IO) {
        val songs = getSongs().filterNot { it.id == songId }
        saveSongs(songs)
    }

    suspend fun toggleLike(songId: String) = withContext(Dispatchers.IO) {
        val songs = getSongs().toMutableList()
        val index = songs.indexOfFirst { it.id == songId }
        if (index >= 0) {
            songs[index] = songs[index].copy(liked = !songs[index].liked)
            saveSongs(songs)
        }
    }

    private suspend fun saveSongs(songs: List<SongEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(songsFile, songs) { song ->
            JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("artist", song.artist)
                put("artistId", song.artistId ?: "")
                put("albumId", song.albumId ?: "")
                put("albumName", song.albumName ?: "")
                put("artworkUrl", song.artworkUrl ?: "")
                put("durationMs", song.durationMs)
                put("dateAdded", song.dateAdded)
                put("liked", song.liked)
            }
        }
    }

    // ==================== LIKED SONGS ====================

    suspend fun getLikedSongs(): List<SongEntry> = withContext(Dispatchers.IO) {
        getSongs().filter { it.liked }
    }

    // ==================== PLAYLISTS ====================

    suspend fun getPlaylists(): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(playlistsFile) { obj ->
            PlaylistEntry(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.optString("description").takeIf { it.isNotBlank() },
                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                songIds = obj.optJSONArray("songIds")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                dateCreated = obj.optLong("dateCreated", System.currentTimeMillis()),
                dateModified = obj.optLong("dateModified", System.currentTimeMillis()),
            )
        }
    }

    suspend fun createPlaylist(name: String, description: String? = null): PlaylistEntry = withContext(Dispatchers.IO) {
        val playlists = getPlaylists().toMutableList()
        val newPlaylist = PlaylistEntry(
            id = "local_${System.currentTimeMillis()}",
            name = name,
            description = description,
            thumbnailUrl = null,
            songIds = emptyList(),
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
        )
        playlists.add(newPlaylist)
        savePlaylists(playlists)
        newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val playlists = getPlaylists().filterNot { it.id == playlistId }
        savePlaylists(playlists)
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val playlist = playlists[index]
            if (songId !in playlist.songIds) {
                playlists[index] = playlist.copy(
                    songIds = playlist.songIds + songId,
                    dateModified = System.currentTimeMillis(),
                )
                savePlaylists(playlists)
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val playlist = playlists[index]
            playlists[index] = playlist.copy(
                songIds = playlist.songIds - songId,
                dateModified = System.currentTimeMillis(),
            )
            savePlaylists(playlists)
        }
    }

    private suspend fun savePlaylists(playlists: List<PlaylistEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(playlistsFile, playlists) { playlist ->
            JSONObject().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("description", playlist.description ?: "")
                put("thumbnailUrl", playlist.thumbnailUrl ?: "")
                put("songIds", JSONArray(playlist.songIds))
                put("dateCreated", playlist.dateCreated)
                put("dateModified", playlist.dateModified)
            }
        }
    }

    // ==================== SEARCH HISTORY ====================

    suspend fun getSearchHistory(): List<SearchHistoryEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(searchHistoryFile) { obj ->
            SearchHistoryEntry(
                query = obj.getString("query"),
                timestamp = obj.getLong("timestamp"),
            )
        }.sortedByDescending { it.timestamp }
    }

    suspend fun addSearchHistory(query: String) = withContext(Dispatchers.IO) {
        val history = getSearchHistory().toMutableList()
        // Remover duplicados
        history.removeAll { it.query.equals(query, ignoreCase = true) }
        // Agregar al inicio
        history.add(0, SearchHistoryEntry(query, System.currentTimeMillis()))
        // Limitar a 50 entradas
        val limited = history.take(50)
        saveSearchHistory(limited)
    }

    suspend fun removeSearchHistory(query: String) = withContext(Dispatchers.IO) {
        val history = getSearchHistory().filterNot { it.query.equals(query, ignoreCase = true) }
        saveSearchHistory(history)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        saveSearchHistory(emptyList())
    }

    private suspend fun saveSearchHistory(history: List<SearchHistoryEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(searchHistoryFile, history) { entry ->
            JSONObject().apply {
                put("query", entry.query)
                put("timestamp", entry.timestamp)
            }
        }
    }

    // ==================== PLAY HISTORY ====================

    suspend fun getPlayHistory(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(playHistoryFile) { obj ->
            PlayHistoryEntry(
                songId = obj.getString("songId"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                timestamp = obj.getLong("timestamp"),
            )
        }.sortedByDescending { it.timestamp }
    }

    suspend fun addPlayHistory(songId: String, title: String, artist: String, artworkUrl: String?) = withContext(Dispatchers.IO) {
        val history = getPlayHistory().toMutableList()
        // Agregar nueva entrada
        history.add(0, PlayHistoryEntry(songId, title, artist, artworkUrl, System.currentTimeMillis()))
        // Limitar a 200 entradas
        val limited = history.take(200)
        savePlayHistory(limited)
    }

    suspend fun clearPlayHistory() = withContext(Dispatchers.IO) {
        savePlayHistory(emptyList())
    }

    private suspend fun savePlayHistory(history: List<PlayHistoryEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(playHistoryFile, history) { entry ->
            JSONObject().apply {
                put("songId", entry.songId)
                put("title", entry.title)
                put("artist", entry.artist)
                put("artworkUrl", entry.artworkUrl ?: "")
                put("timestamp", entry.timestamp)
            }
        }
    }

    // ==================== ARTISTS ====================

    suspend fun getSavedArtists(): List<ArtistEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(artistsFile) { obj ->
            ArtistEntry(
                id = obj.getString("id"),
                name = obj.getString("name"),
                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                subscriberCount = obj.optString("subscriberCount").takeIf { it.isNotBlank() },
                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
            )
        }
    }

    suspend fun saveArtist(artist: ArtistEntry) = withContext(Dispatchers.IO) {
        val artists = getSavedArtists().toMutableList()
        if (artists.none { it.id == artist.id }) {
            artists.add(artist)
            saveArtists(artists)
        }
    }

    suspend fun removeArtist(artistId: String) = withContext(Dispatchers.IO) {
        val artists = getSavedArtists().filterNot { it.id == artistId }
        saveArtists(artists)
    }

    private suspend fun saveArtists(artists: List<ArtistEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(artistsFile, artists) { artist ->
            JSONObject().apply {
                put("id", artist.id)
                put("name", artist.name)
                put("thumbnailUrl", artist.thumbnailUrl ?: "")
                put("subscriberCount", artist.subscriberCount ?: "")
                put("dateAdded", artist.dateAdded)
            }
        }
    }

    // ==================== ALBUMS ====================

    suspend fun getSavedAlbums(): List<AlbumEntry> = withContext(Dispatchers.IO) {
        loadJsonArray(albumsFile) { obj ->
            AlbumEntry(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artistName = obj.optString("artistName").takeIf { it.isNotBlank() },
                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                year = obj.optInt("year", 0).takeIf { it > 0 },
                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
            )
        }
    }

    suspend fun saveAlbum(album: AlbumEntry) = withContext(Dispatchers.IO) {
        val albums = getSavedAlbums().toMutableList()
        if (albums.none { it.id == album.id }) {
            albums.add(album)
            saveAlbums(albums)
        }
    }

    suspend fun removeAlbum(albumId: String) = withContext(Dispatchers.IO) {
        val albums = getSavedAlbums().filterNot { it.id == albumId }
        saveAlbums(albums)
    }

    private suspend fun saveAlbums(albums: List<AlbumEntry>) = withContext(Dispatchers.IO) {
        saveJsonArray(albumsFile, albums) { album ->
            JSONObject().apply {
                put("id", album.id)
                put("title", album.title)
                put("artistName", album.artistName ?: "")
                put("thumbnailUrl", album.thumbnailUrl ?: "")
                put("year", album.year ?: 0)
                put("dateAdded", album.dateAdded)
            }
        }
    }

    // ==================== SETTINGS ====================

    suspend fun getSetting(key: String, default: String = ""): String = withContext(Dispatchers.IO) {
        runCatching {
            if (!Files.exists(settingsFile)) return@withContext default
            val raw = Files.readString(settingsFile, StandardCharsets.UTF_8)
            if (raw.isBlank()) return@withContext default
            JSONObject(raw).optString(key, default)
        }.getOrDefault(default)
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        runCatching {
            val settings = if (Files.exists(settingsFile)) {
                val raw = Files.readString(settingsFile, StandardCharsets.UTF_8)
                if (raw.isNotBlank()) JSONObject(raw) else JSONObject()
            } else {
                JSONObject()
            }
            settings.put(key, value)
            Files.writeString(settingsFile, settings.toString(), StandardCharsets.UTF_8)
        }
    }

    // ==================== UTILITIES ====================

    private fun <T> loadJsonArray(file: Path, mapper: (JSONObject) -> T): List<T> {
        if (!Files.exists(file)) return emptyList()
        return runCatching {
            val raw = Files.readString(file, StandardCharsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(mapper(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun <T> saveJsonArray(file: Path, items: List<T>, mapper: (T) -> JSONObject) {
        val array = JSONArray()
        items.forEach { array.put(mapper(it)) }
        Files.writeString(file, array.toString(), StandardCharsets.UTF_8)
    }

    companion object {
        private fun defaultDataPath(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".anitail", "data")
        }
    }
}

// ==================== DATA CLASSES ====================

data class SongEntry(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val liked: Boolean = false,
) {
    fun toLibraryItem() = LibraryItem(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        playbackUrl = "https://music.youtube.com/watch?v=$id",
        durationMs = durationMs,
    )
}

data class PlaylistEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val songIds: List<String>,
    val dateCreated: Long,
    val dateModified: Long,
)

data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long,
)

data class PlayHistoryEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val timestamp: Long,
)

data class ArtistEntry(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
)

data class AlbumEntry(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val year: Int? = null,
    val dateAdded: Long = System.currentTimeMillis(),
)
