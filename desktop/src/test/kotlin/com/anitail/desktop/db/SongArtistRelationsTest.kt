package com.anitail.desktop.db

import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.relations.artistIdsForSong
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongArtistRelationsTest {
    @Test
    fun artistIdsForSongOrdersByPosition() {
        val maps = listOf(
            SongArtistMap(songId = "s1", artistId = "a2", position = 1),
            SongArtistMap(songId = "s1", artistId = "a1", position = 0),
            SongArtistMap(songId = "s2", artistId = "b1", position = 0),
        )

        val result = artistIdsForSong(songId = "s1", maps = maps)

        assertEquals(listOf("a1", "a2"), result)
    }

    @Test
    fun primaryArtistIdForSongUsesFirstPosition() {
        val maps = listOf(
            SongArtistMap(songId = "s1", artistId = "a2", position = 1),
            SongArtistMap(songId = "s1", artistId = "a1", position = 0),
        )

        val result = primaryArtistIdForSong(songId = "s1", maps = maps)

        assertEquals("a1", result)
    }

    @Test
    fun primaryArtistIdForSongReturnsNullWhenMissing() {
        val maps = listOf(
            SongArtistMap(songId = "s2", artistId = "a1", position = 0),
        )

        val result = primaryArtistIdForSong(songId = "s1", maps = maps)

        assertNull(result)
    }
}
