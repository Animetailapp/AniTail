package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.PlaylistSongMap
import com.anitail.desktop.db.entities.SongEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPlaylistDataTest {
    @Test
    fun buildsSongCountAndThumbnailsFromMaps() {
        val playlist = PlaylistEntity(id = "p1", name = "Mix")
        val songs = listOf(
            SongEntity(id = "s1", title = "One", artistName = "A", thumbnailUrl = "t1"),
            SongEntity(id = "s2", title = "Two", artistName = "B", thumbnailUrl = "t2"),
            SongEntity(id = "s3", title = "Three", artistName = "C", thumbnailUrl = "t3"),
        )
        val maps = listOf(
            PlaylistSongMap(playlistId = "p1", songId = "s2", position = 0),
            PlaylistSongMap(playlistId = "p1", songId = "s1", position = 1),
            PlaylistSongMap(playlistId = "p1", songId = "s3", position = 2),
        )

        val result = buildLibraryPlaylists(
            playlists = listOf(playlist),
            playlistSongMaps = maps,
            songsById = songs.associateBy { it.id },
        )

        assertEquals(1, result.size)
        assertEquals(3, result.first().songCount)
        assertEquals(listOf("t2", "t1", "t3"), result.first().thumbnails)
    }
}
