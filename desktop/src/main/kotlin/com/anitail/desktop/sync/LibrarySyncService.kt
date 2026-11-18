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
        val now = LocalDateTime.now()
        val batch = allSongs.map { song ->
            val base = song.toSongEntity()
            val entity = base.copy(
                liked = true,
                likedDate = now,
                inLibrary = base.inLibrary ?: now,
            )
            entity to song.toSongArtistMaps()
        }
        database.insertSongsBatch(batch)
    }

    /**
     * Sincroniza las playlists creadas/guardadas por el usuario.
     */
    suspend fun syncUserPlaylists() {
        println("LibrarySyncService: Sincronizando playlists del usuario...")
        database.deletePlaylist("LM")
        database.deletePlaylist("SE")
        // FEmusic_liked_playlists contiene las playlists en la biblioteca del usuario
        val page = runCatching { YouTube.library("FEmusic_liked_playlists").getOrThrow() }
            .getOrElse {
                // No fallamos toda la sincronización si esto falla, pero lo logueamos
                println("LibrarySyncService: Error al sincronizar playlists: ${it.message}")
                return
            }

        page.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { it.id == "SE" || it.id == "LM" }
            .forEach { playlist ->
                val (playlistPage, remoteSongs) = fetchRemotePlaylistWithSongs(playlist)
                var entity = mapPlaylistForSync(playlist)
                val resolvedCount = entity.remoteSongCount
                    ?: parseSongCountText(playlistPage?.playlist?.songCountText)
                    ?: remoteSongs.size.takeIf { it > 0 }
                val resolvedThumbnail = entity.thumbnailUrl?.takeIf { it.isNotBlank() }
                    ?: playlistPage?.playlist?.thumbnail?.takeIf { it.isNotBlank() }
                    ?: remoteSongs.firstOrNull()?.thumbnail?.takeIf { it.isNotBlank() }
                entity = entity.copy(
                    remoteSongCount = resolvedCount,
                    thumbnailUrl = resolvedThumbnail,
                )
                database.insertPlaylist(entity)

                if (remoteSongs.isNotEmpty()) {
                    val storedId = database.playlist(entity.id).first()?.id ?: entity.id
                    val batch = remoteSongs.map { song ->
                        song.toSongEntity(inLibrary = false) to song.toSongArtistMaps()
                    }
                    database.insertSongsBatch(batch)
                    database.replacePlaylistSongs(storedId, remoteSongs.map { it.id })
                }
            }
    }

    private suspend fun fetchRemotePlaylistWithSongs(
        playlist: PlaylistItem,
    ): Pair<PlaylistPage?, List<SongItem>> {
        val normalizedId = playlist.id.removePrefix("VL")
        if (normalizedId == "RDPN" || normalizedId == "FEmusic_new_episodes") {
            val info = runCatching {
                com.anitail.innertube.YouTube.newEpisodesPlaylistInfo().getOrNull()
            }.getOrNull()
            val episodes = runCatching {
                com.anitail.innertube.YouTube.newEpisodes().getOrThrow()
            }.getOrNull().orEmpty().distinctBy { it.id }
            val syntheticPage = PlaylistPage(
                playlist = info ?: playlist,
                songs = episodes,
                songsContinuation = null,
                continuation = null,
            )
            return syntheticPage to episodes
        }

        val initialPage = runCatching {
            YouTube.playlist(playlist.id).getOrThrow()
        }.getOrNull() ?: return null to emptyList()

        val allSongs = runCatching {
            collectPlaylistSongs(
                initial = initialPage,
                fetchContinuation = { continuation ->
                    com.anitail.innertube.YouTube.playlistContinuation(continuation).getOrThrow()
                },
            )
        }.getOrElse { initialPage.songs }

        return initialPage to allSongs
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

    val songBatch = mutableListOf<Pair<com.anitail.desktop.db.entities.SongEntity, List<com.anitail.desktop.db.entities.SongArtistMap>>>()

    sections.forEach { section ->
        val sectionDate = parseHistorySectionDate(section.title, today)
        section.songs.forEachIndexed { index, song ->
            val entity = song.toSongEntity(inLibrary = false)
            songBatch.add(entity to song.toSongArtistMaps())

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

    if (songBatch.isNotEmpty()) {
        database.insertSongsBatch(songBatch)
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
