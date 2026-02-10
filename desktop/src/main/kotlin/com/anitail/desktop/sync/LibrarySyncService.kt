package com.anitail.desktop.sync

import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
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
        YouTube.playlist("LM").onSuccess { page ->
            page.songs.forEach { song ->
                val base = song.toSongEntity()
                val entity = base.copy(
                    liked = true,
                    likedDate = LocalDateTime.now(),
                    inLibrary = base.inLibrary ?: LocalDateTime.now()
                )
                database.insertSong(entity)
            }
            
            // Si hay continuación, podríamos traer más, pero por ahora las primeras son suficientes
            // para una sincronización inicial rápida.
        }.onFailure {
            throw it
        }
    }

    /**
     * Sincroniza las playlists creadas/guardadas por el usuario.
     */
    suspend fun syncUserPlaylists() {
        println("LibrarySyncService: Sincronizando playlists del usuario...")
        // FEmusic_liked_playlists contiene las playlists en la biblioteca del usuario
        YouTube.library("FEmusic_liked_playlists").onSuccess { page ->
            page.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                // Insertar o actualizar playlist en DB
                val entity = PlaylistEntity(
                    id = playlist.id,
                    name = playlist.title,
                    browseId = playlist.id,
                    createdAt = LocalDateTime.now(),
                    lastUpdateTime = LocalDateTime.now(),
                    isEditable = playlist.isEditable,
                    thumbnailUrl = playlist.thumbnail,
                    playEndpointParams = playlist.playEndpoint?.params,
                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                    radioEndpointParams = playlist.radioEndpoint?.params
                )
                database.insertPlaylist(entity)
            }
        }.onFailure {
            // No fallamos toda la sincronización si esto falla, pero lo logueamos
            println("LibrarySyncService: Error al sincronizar playlists: ${it.message}")
        }
    }
}
