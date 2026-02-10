package com.anitail.desktop.sync

import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toPlaylistEntity
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.PlaylistContinuationPage
import com.anitail.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Servicio de sincronización de biblioteca con YouTube Music.
 * Trae canciones favoritas y playlists del usuario a la base de datos local.
 */
class LibrarySyncService(
    private val database: DesktopDatabase = DesktopDatabase.getInstance(),
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    /**
     * Sincroniza la biblioteca completa.
     */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        if (YouTube.cookie.isNullOrBlank()) return@withContext

        _isSyncing.value = true
        _lastSyncError.value = null

        try {
            println("LibrarySyncService: Iniciando sincronización completa...")
            
            // 1. Sincronizar canciones favoritas (LM)
            syncLikedSongs()
            
            // 2. Sincronizar playlists del usuario
            syncUserPlaylists()
            
            println("LibrarySyncService: Sincronización finalizada con éxito.")
        } catch (e: Exception) {
            println("LibrarySyncService: Error durante la sincronización: ${e.message}")
            _lastSyncError.value = e.message
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Sincroniza las canciones con "Me gusta".
     */
    suspend fun syncLikedSongs() {
        println("LibrarySyncService: Sincronizando 'Tus me gusta'...")
        val page = YouTube.playlist("LM").getOrThrow()
        val allSongs = collectPlaylistSongs(
            initial = page,
            fetchContinuation = { continuation ->
                YouTube.playlistContinuation(continuation).getOrThrow()
            },
        )
        allSongs.forEach { song ->
            val base = song.toSongEntity()
            val entity = base.copy(
                liked = true,
                likedDate = LocalDateTime.now(),
                inLibrary = base.inLibrary ?: LocalDateTime.now()
            )
            database.insertSong(entity)
            database.insertSongArtistMaps(song.toSongArtistMaps())
        }
    }

    /**
     * Sincroniza las playlists creadas/guardadas por el usuario.
     */
    suspend fun syncUserPlaylists() {
        println("LibrarySyncService: Sincronizando playlists del usuario...")
        // FEmusic_liked_playlists contiene las playlists en la biblioteca del usuario
        val page = runCatching { YouTube.library("FEmusic_liked_playlists").getOrThrow() }
            .getOrElse {
                // No fallamos toda la sincronización si esto falla, pero lo logueamos
                println("LibrarySyncService: Error al sincronizar playlists: ${it.message}")
                return
            }

        page.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { it.id == "SE" }
            .forEach { playlist ->
                var entity = mapPlaylistForSync(playlist)
                if (entity.remoteSongCount == null) {
                    val headerCount = runCatching {
                        YouTube.playlist(playlist.id).getOrThrow().playlist.songCountText
                    }.getOrNull()
                    val parsed = parseSongCountText(headerCount)
                    if (parsed != null) {
                        entity = entity.copy(remoteSongCount = parsed)
                    }
                }
                database.insertPlaylist(entity)
            }
    }
}

internal fun mapPlaylistForSync(
    playlist: PlaylistItem,
    now: LocalDateTime = LocalDateTime.now(),
) = playlist.toPlaylistEntity().copy(
    createdAt = now,
    lastUpdateTime = now,
)

internal suspend fun collectPlaylistSongs(
    initial: PlaylistPage,
    fetchContinuation: suspend (String) -> PlaylistContinuationPage,
    maxPages: Int = 100,
): List<SongItem> {
    val songs = initial.songs.toMutableList()
    var continuation = initial.songsContinuation ?: initial.continuation
    var pages = 0
    while (!continuation.isNullOrBlank() && pages < maxPages) {
        val next = fetchContinuation(continuation)
        songs += next.songs
        continuation = next.continuation
        pages += 1
    }
    return songs
}

internal fun parseSongCountText(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    return Regex("""\d+""").find(text)?.value?.toIntOrNull()
}
