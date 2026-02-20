package com.anitail.desktop.home

import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeActionsTest {
    @Test
    fun selectsNoneWhenNoCandidates() {
        val source = selectShuffleSource(localCount = 0, ytCount = 0, randomValue = 0.2f)

        assertEquals(ShuffleSource.NONE, source)
    }

    @Test
    fun selectsLocalWhenOnlyLocal() {
        val source = selectShuffleSource(localCount = 3, ytCount = 0, randomValue = 0.9f)

        assertEquals(ShuffleSource.LOCAL, source)
    }

    @Test
    fun selectsYtWhenOnlyYt() {
        val source = selectShuffleSource(localCount = 0, ytCount = 2, randomValue = 0.1f)

        assertEquals(ShuffleSource.YT, source)
    }

    @Test
    fun selectsByRandomWhenBothAvailable() {
        val local = selectShuffleSource(localCount = 2, ytCount = 2, randomValue = 0.4f)
        val yt = selectShuffleSource(localCount = 2, ytCount = 2, randomValue = 0.6f)

        assertEquals(ShuffleSource.LOCAL, local)
        assertEquals(ShuffleSource.YT, yt)
    }

    @Test
    fun classifiesLocalItemTypesFromUrls() {
        val song = LibraryItem(
            id = "song123",
            title = "Song",
            artist = "Artist",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/watch?v=song123",
        )
        val album = LibraryItem(
            id = "MPREb_abcdef",
            title = "Album",
            artist = "Artist",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/playlist?list=PL123",
        )
        val playlist = LibraryItem(
            id = "PL987",
            title = "Playlist",
            artist = "Author",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/playlist?list=PL987",
        )
        val artist = LibraryItem(
            id = "UC123",
            title = "Artist",
            artist = "Artist",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/channel/UC123",
        )

        assertEquals(LocalItemType.SONG, resolveLocalItemType(song))
        assertEquals(LocalItemType.ALBUM, resolveLocalItemType(album))
        assertEquals(LocalItemType.PLAYLIST, resolveLocalItemType(playlist))
        assertEquals(LocalItemType.ARTIST, resolveLocalItemType(artist))
    }

    @Test
    fun convertsLocalItemToYtItem() {
        val song = LibraryItem(
            id = "song123",
            title = "Song",
            artist = "Artist",
            artworkUrl = "song.png",
            playbackUrl = "https://music.youtube.com/watch?v=song123",
        )
        val album = LibraryItem(
            id = "MPREb_abcdef",
            title = "Album",
            artist = "Artist",
            artworkUrl = "album.png",
            playbackUrl = "https://music.youtube.com/playlist?list=PL123",
        )
        val artist = LibraryItem(
            id = "UC123",
            title = "Artist",
            artist = "Artist",
            artworkUrl = "artist.png",
            playbackUrl = "https://music.youtube.com/channel/UC123",
        )

        assertTrue(localItemToYtItem(song) is SongItem)
        assertTrue(localItemToYtItem(album) is AlbumItem)
        assertTrue(localItemToYtItem(artist) is ArtistItem)
    }

    @Test
    fun buildsAllYtItemsFromHomeAccountAndSimilar() {
        val sectionSong = SongItem(
            id = "s1",
            title = "Song 1",
            artists = emptyList(),
            thumbnail = "s1.png",
            explicit = false,
        )
        val playlist = PlaylistItem(
            id = "pl1",
            title = "Playlist",
            author = null,
            songCountText = null,
            thumbnail = "pl.png",
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        val similarSong = SongItem(
            id = "s2",
            title = "Song 2",
            artists = emptyList(),
            thumbnail = "s2.png",
            explicit = false,
        )
        val homePage = HomePage(
            chips = null,
            sections = listOf(
                HomePage.Section(
                    title = "Section",
                    label = null,
                    thumbnail = null,
                    endpoint = null,
                    items = listOf(sectionSong),
                )
            ),
        )
        val similar = SimilarRecommendation(
            title = "Similar",
            thumbnailUrl = null,
            isArtist = false,
            items = listOf(similarSong),
        )

        val result = buildAllYtItems(
            homePage = homePage,
            accountPlaylists = listOf(playlist),
            similarRecommendations = listOf(similar),
        )

        assertEquals(listOf("s1", "pl1", "s2"), result.map { it.id })
    }
}
