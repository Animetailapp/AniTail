package com.anitail.desktop.ui.screen

import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.BrowseResult
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseItemsTest {
    @Test
    fun flattenBrowseItemsFiltersExplicitWhenEnabled() {
        val cleanSong = SongItem(
            id = "clean",
            title = "Clean",
            artists = listOf(Artist(name = "Artist", id = "artist")),
            thumbnail = "https://example.com/clean.jpg",
            explicit = false,
        )
        val explicitSong = SongItem(
            id = "explicit",
            title = "Explicit",
            artists = listOf(Artist(name = "Artist", id = "artist")),
            thumbnail = "https://example.com/explicit.jpg",
            explicit = true,
        )
        val album = AlbumItem(
            browseId = "album",
            playlistId = "album",
            title = "Album",
            artists = listOf(Artist(name = "Album Artist", id = "album-artist")),
            thumbnail = "https://example.com/album.jpg",
        )

        val result = BrowseResult(
            title = "Browse",
            items = listOf(
                BrowseResult.Item(
                    title = "Group",
                    items = listOf(cleanSong, explicitSong, album),
                )
            ),
        )

        val items = flattenBrowseItems(result, hideExplicit = true)

        assertEquals(listOf(cleanSong, album), items)
    }

    @Test
    fun flattenBrowseItemsKeepsExplicitWhenDisabled() {
        val explicitSong = SongItem(
            id = "explicit",
            title = "Explicit",
            artists = listOf(Artist(name = "Artist", id = "artist")),
            thumbnail = "https://example.com/explicit.jpg",
            explicit = true,
        )
        val result = BrowseResult(
            title = "Browse",
            items = listOf(
                BrowseResult.Item(
                    title = "Group",
                    items = listOf(explicitSong),
                )
            ),
        )

        val items = flattenBrowseItems(result, hideExplicit = false)

        assertEquals(listOf(explicitSong), items)
    }
}
