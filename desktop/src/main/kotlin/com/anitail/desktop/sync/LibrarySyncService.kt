package com.anitail.desktop.sync

import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toPlaylistEntity
import com.anitail.desktop.YouTube
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.HistoryPage
import com.anitail.innertube.pages.PlaylistContinuationPage
import com.anitail.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Servicio de sincronización de biblioteca con YouTube Music.
 * Trae canciones favoritas y playlists del usuario a la base de datos local.
 */
class LibrarySyncService(
    internal val database: DesktopDatabase = DesktopDatabase.getInstance(),
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

            // 3. Sincronizar historial remoto (YouTube Music)
            syncHistory()
            
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
    val digits = text.filter { it.isDigit() }
    return digits.toIntOrNull()
}

/**
 * Sincroniza las reproducciones de FEmusic_history de YouTube Music en la base de datos local.
 * Replica el comportamiento de Metrolist para unificar el historial de reproducciones.
 */
suspend fun LibrarySyncService.syncHistory(): Int = withContext(Dispatchers.IO) {
    if (YouTube.cookie.isNullOrBlank()) return@withContext 0
    try {
        println("LibrarySyncService: Sincronizando historial de YouTube Music...")
        val historyPage = YouTube.musicHistory().getOrNull() ?: return@withContext 0
        syncHistoryPageToDatabase(historyPage, database)
    } catch (e: Exception) {
        println("LibrarySyncService: Error al sincronizar historial: ${e.message}")
        0
    }
}

internal suspend fun syncHistoryPageToDatabase(
    historyPage: HistoryPage,
    database: DesktopDatabase,
): Int {
    val sections = historyPage.sections.orEmpty()
    if (sections.isEmpty()) return 0

    val existingEvents = database.events.first()
    val existingSongDays = existingEvents.map { it.songId to it.timestamp.toLocalDate() }.toSet()
    val today = LocalDate.now()
    val newEvents = mutableListOf<EventEntity>()
    var addedCount = 0

    sections.forEach { section ->
        val sectionDate = parseHistorySectionDate(section.title, today)
        section.songs.forEachIndexed { index, song ->
            val entity = song.toSongEntity(inLibrary = false)
            database.insertSong(entity, song.toSongArtistMaps())

            val key = song.id to sectionDate.toLocalDate()
            if (key !in existingSongDays) {
                newEvents.add(
                    EventEntity(
                        id = 0,
                        songId = song.id,
                        timestamp = sectionDate.minusMinutes(index.toLong() * 3L),
                        playTime = 0,
                    ),
                )
                addedCount++
            }
        }
    }

    if (newEvents.isNotEmpty()) {
        database.insertEvents(newEvents)
    }
    println("LibrarySyncService: Sincronizadas $addedCount nuevas reproducciones del historial.")
    return addedCount
}

internal fun parseHistorySectionDate(title: String, today: LocalDate): LocalDateTime {
    val lower = title.lowercase().trim()
    return when {
        lower.contains("hoy") || lower.contains("today") -> today.atTime(21, 0)
        lower.contains("ayer") || lower.contains("yesterday") -> today.minusDays(1).atTime(21, 0)
        lower.contains("esta semana") || lower.contains("this week") -> today.minusDays(3).atTime(20, 0)
        lower.contains("semana pasada") || lower.contains("last week") -> today.minusDays(7).atTime(20, 0)
        else -> today.minusDays(14).atTime(12, 0)
    }
}
