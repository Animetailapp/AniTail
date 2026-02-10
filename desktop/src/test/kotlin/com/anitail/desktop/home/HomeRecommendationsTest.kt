package com.anitail.desktop.home

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.RelatedSongMap
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeRecommendationsTest {
    @Test
    fun quickPicksOrdersByRelatedSongCount() {
        val now = LocalDateTime.of(2026, 1, 1, 12, 0)
        val songs = listOf(
            SongEntity(id = "s1", title = "Seed 1", artistName = "Artist", totalPlayTime = 5000),
            SongEntity(id = "s2", title = "Seed 2", artistName = "Artist", totalPlayTime = 3000),
            SongEntity(id = "r1", title = "Related 1", artistName = "Artist", totalPlayTime = 100),
            SongEntity(id = "r2", title = "Related 2", artistName = "Artist", totalPlayTime = 50),
        )
        val events = listOf(
            EventEntity(songId = "s1", timestamp = now.minusDays(1), playTime = 1200),
            EventEntity(songId = "s2", timestamp = now.minusDays(2), playTime = 900),
        )
        val relatedMaps = listOf(
            RelatedSongMap(songId = "s1", relatedSongId = "r1"),
            RelatedSongMap(songId = "s2", relatedSongId = "r1"),
            RelatedSongMap(songId = "s1", relatedSongId = "r2"),
        )

        val result = buildHomeRecommendations(
            songs = songs,
            albums = emptyList(),
            artists = emptyList(),
            songArtistMaps = emptyList(),
            relatedSongMaps = relatedMaps,
            events = events,
            now = now,
            hideExplicit = false,
        )

        assertEquals(listOf("r1", "r2"), result.quickPicks.map { it.id })
    }

    @Test
    fun keepListeningIncludesSongsAlbumsAndArtists() {
        val now = LocalDateTime.of(2026, 1, 1, 12, 0)
        val album1 = AlbumEntity(id = "A1", playlistId = "PL1", title = "Album 1", thumbnailUrl = "t1")
        val album2 = AlbumEntity(id = "A2", playlistId = "PL2", title = "Album 2", thumbnailUrl = "t2")
        val album3 = AlbumEntity(id = "A3", playlistId = "PL3", title = "Album 3", thumbnailUrl = "t3")

        val artist1 = ArtistEntity(id = "UC1", name = "Artist 1", thumbnailUrl = "a1")
        val artist2 = ArtistEntity(id = "UC2", name = "Artist 2", thumbnailUrl = "a2")
        val artist3 = ArtistEntity(id = "UC3", name = "Artist 3", thumbnailUrl = "a3")

        val songs = listOf(
            SongEntity(id = "s1", title = "Song 1", artistName = "Artist 1", albumId = "A1"),
            SongEntity(id = "s2", title = "Song 2", artistName = "Artist 1", albumId = "A1"),
            SongEntity(id = "s3", title = "Song 3", artistName = "Artist 2", albumId = "A2"),
            SongEntity(id = "s4", title = "Song 4", artistName = "Artist 2", albumId = "A2"),
            SongEntity(id = "s5", title = "Song 5", artistName = "Artist 3", albumId = "A3"),
            SongEntity(id = "s6", title = "Song 6", artistName = "Artist 3", albumId = "A3"),
            SongEntity(id = "s7", title = "Song 7", artistName = "Artist 3", albumId = "A3"),
        )
        val songArtistMaps = listOf(
            SongArtistMap(songId = "s1", artistId = "UC1", position = 0),
            SongArtistMap(songId = "s2", artistId = "UC1", position = 0),
            SongArtistMap(songId = "s3", artistId = "UC2", position = 0),
            SongArtistMap(songId = "s4", artistId = "UC2", position = 0),
            SongArtistMap(songId = "s5", artistId = "UC3", position = 0),
            SongArtistMap(songId = "s6", artistId = "UC3", position = 0),
            SongArtistMap(songId = "s7", artistId = "UC3", position = 0),
        )

        val events = listOf(
            EventEntity(songId = "s1", timestamp = now.minusDays(2), playTime = 9000),
            EventEntity(songId = "s2", timestamp = now.minusDays(2), playTime = 8000),
            EventEntity(songId = "s3", timestamp = now.minusDays(2), playTime = 7000),
            EventEntity(songId = "s4", timestamp = now.minusDays(2), playTime = 6000),
            EventEntity(songId = "s5", timestamp = now.minusDays(2), playTime = 5000),
            EventEntity(songId = "s6", timestamp = now.minusDays(2), playTime = 4000),
            EventEntity(songId = "s7", timestamp = now.minusDays(2), playTime = 3000),
        )

        val result = buildHomeRecommendations(
            songs = songs,
            albums = listOf(album1, album2, album3),
            artists = listOf(artist1, artist2, artist3),
            songArtistMaps = songArtistMaps,
            relatedSongMaps = emptyList(),
            events = events,
            now = now,
            hideExplicit = false,
        )

        val songIds = result.keepListening.filterIsInstance<HomeListenSong>().map { it.song.id }
        val albumIds = result.keepListening.filterIsInstance<HomeListenAlbum>().map { it.album.id }
        val artistIds = result.keepListening.filterIsInstance<HomeListenArtist>().map { it.artist.id }

        assertTrue("s6" in songIds && "s7" in songIds)
        assertEquals(listOf("A3"), albumIds)
        assertTrue("UC3" in artistIds)
    }
}
