package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.PlaylistSongMap
import com.anitail.desktop.db.entities.SongEntity
import java.time.LocalDateTime

data class LibraryPlaylist(
    val playlist: PlaylistEntity,
    val songCount: Int,
    val thumbnails: List<String>,
    val isAuto: Boolean = false,
)

fun buildLibraryPlaylists(
    playlists: List<PlaylistEntity>,
    playlistSongMaps: List<PlaylistSongMap>,
    songsById: Map<String, SongEntity>,
): List<LibraryPlaylist> {
    val mapsByPlaylist = playlistSongMaps.groupBy { it.playlistId }
    return playlists.map { playlist ->
        val maps = mapsByPlaylist[playlist.id].orEmpty().sortedBy { it.position }
        val songIds = maps.map { it.songId }
        val thumbnails = songIds.mapNotNull { songsById[it]?.thumbnailUrl }.distinct().take(4)
        val localCount = songIds.size
        val count = if (localCount > 0) localCount else playlist.remoteSongCount ?: 0
        val fallback = playlist.thumbnailUrl?.let { listOf(it) }.orEmpty()
        LibraryPlaylist(
            playlist = playlist,
            songCount = count,
            thumbnails = if (thumbnails.isNotEmpty()) thumbnails else fallback,
            isAuto = false,
        )
    }
}

fun buildAutoPlaylist(
    id: String,
    name: String,
    songs: List<SongEntity>,
    fallbackThumbnail: String? = null,
): LibraryPlaylist {
    val thumbnails = songs.mapNotNull { it.thumbnailUrl }.distinct().take(4)
    return LibraryPlaylist(
        playlist = PlaylistEntity(
            id = id,
            name = name,
            createdAt = LocalDateTime.now(),
            lastUpdateTime = LocalDateTime.now(),
            isEditable = false,
            thumbnailUrl = fallbackThumbnail,
        ),
        songCount = songs.size,
        thumbnails = if (thumbnails.isNotEmpty()) thumbnails else fallbackThumbnail?.let { listOf(it) }.orEmpty(),
        isAuto = true,
    )
}
