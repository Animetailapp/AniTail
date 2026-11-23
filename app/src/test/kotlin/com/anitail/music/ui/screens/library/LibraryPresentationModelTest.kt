package com.anitail.music.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.AlbumEntity
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.PlaylistEntity
import com.anitail.music.db.entities.PodcastEntity
import com.anitail.music.db.entities.Song
import com.anitail.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LibraryPresentationModelTest {

    private val t1 = LocalDateTime.of(2025, 1, 10, 12, 0)
    private val t2 = LocalDateTime.of(2025, 2, 10, 12, 0)
    private val t3 = LocalDateTime.of(2025, 3, 10, 12, 0)

    private fun songEntry(id: String, title: String, artist: String = "Artist") =
        LibraryEntry.SongEntry(
            Song(
                song = SongEntity(id = id, title = title, artistName = artist, inLibrary = t1),
                artists = listOf(ArtistEntity(id = "AR_$id", name = artist)),
            ),
        )

    private fun albumEntry(
        id: String,
        title: String,
        artist: String = "Alpha",
        year: Int = 2024,
        added: LocalDateTime = t1,
    ) = LibraryEntry.AlbumEntry(
        Album(
            album = AlbumEntity(
                id = id,
                title = title,
                year = year,
                songCount = 10,
                duration = 1800,
                bookmarkedAt = added,
                lastUpdateTime = added,
            ),
            artists = listOf(ArtistEntity(id = "AR_$id", name = artist)),
        ),
    )

    private fun artistEntry(id: String, name: String, added: LocalDateTime = t2) =
        LibraryEntry.ArtistEntry(
            Artist(
                artist = ArtistEntity(id = id, name = name, bookmarkedAt = added, lastUpdateTime = added),
                songCount = 12,
            ),
        )

    private fun playlistEntry(id: String, name: String, added: LocalDateTime = t3) =
        LibraryEntry.PlaylistEntry(
            Playlist(
                playlist = PlaylistEntity(id = id, name = name, createdAt = added, bookmarkedAt = added, lastUpdateTime = added),
                songCount = 5,
                songThumbnails = emptyList(),
            ),
        )

    private fun podcastEntry(id: String, title: String) =
        LibraryEntry.PodcastEntry(
            PodcastEntity(id = id, title = title, author = "Host", bookmarkedAt = t1, lastUpdateTime = t1),
        )

    @Test
    fun `grid pinch zooms columns within bounds`() {
        val pinch = LibraryGridPinch(initialColumns = 3)
        assertEquals(3, pinch.zoomBy(1.1f))
        assertEquals(2, pinch.zoomBy(1.25f))
        assertEquals(2, pinch.zoomBy(2.0f)) // lower clamp
        assertEquals(3, pinch.zoomBy(0.75f))
        assertEquals(4, pinch.zoomBy(0.75f))
        assertEquals(5, pinch.zoomBy(0.75f))
        assertEquals(5, pinch.zoomBy(0.5f)) // upper clamp
    }

    @Test
    fun `sort map round-trips cleanly and falls back when sort unsupported for filter`() {
        val map = updateLibrarySortMap(
            emptyMap(),
            LibraryExpressiveFilter.ALBUMS,
            LibrarySortConfig(LibraryExpressiveSortType.YEAR, ascending = false),
        )
        val decoded = decodeLibrarySortMap(encodeLibrarySortMap(map))
        assertEquals(
            LibrarySortConfig(LibraryExpressiveSortType.YEAR, ascending = false),
            resolveLibrarySortConfig(decoded, LibraryExpressiveFilter.ALBUMS),
        )
        assertFalse(LibraryExpressiveSortType.YEAR.isAvailableFor(LibraryExpressiveFilter.ARTISTS))
        // When explicitly queried for an incompatible filter, resolve falls back safely
        val raw = encodeLibrarySortMap(mapOf("ARTISTS" to LibrarySortConfig(LibraryExpressiveSortType.YEAR, true)))
        assertEquals(
            LibraryExpressiveSortType.RECENTLY_ADDED,
            resolveLibrarySortConfig(decodeLibrarySortMap(raw), LibraryExpressiveFilter.ARTISTS).sort,
        )
    }

    @Test
    fun `hidden items round-trip and filter entries plus auto playlists`() {
        val keys = setOf(
            HiddenLibraryItemKey(HiddenLibraryItemType.AUTO, "cached"),
            HiddenLibraryItemKey(HiddenLibraryItemType.ARTIST, "UC1"),
        )
        val decoded = decodeHiddenLibraryItems(encodeHiddenLibraryItems(keys))
        assertEquals(keys, decoded)

        val autoPlaylists = listOf(
            LibraryAutoPlaylistItem("liked", "Liked", "Favorites", null, Icons.Outlined.FavoriteBorder, "auto_playlist/liked"),
            LibraryAutoPlaylistItem("cached", "Cache", "Cached", null, Icons.Outlined.FavoriteBorder, "cache_playlist/cached"),
        )
        val visibleAuto = filterAutoPlaylists(autoPlaylists, filter = null, hiddenKeys = decoded, searchQuery = "")
        assertEquals(listOf("liked"), visibleAuto.map { it.type })
        assertTrue(filterAutoPlaylists(autoPlaylists, filter = LibraryExpressiveFilter.ALBUMS, hiddenKeys = emptySet(), searchQuery = "").isEmpty())
    }

    @Test
    fun `filter and search hide loose tracks when overview is unfiltered but include them on query`() {
        val entries = listOf(
            songEntry("S1", "Midnight Track"),
            albumEntry("AL1", "Midnight Album", artist = "Zeta", added = t2),
            artistEntry("AR1", "Alfa", added = t1),
            playlistEntry("PL1", "Roadtrip", added = t3),
            podcastEntry("POD1", "Quality Podcast"),
        )

        val overview = filterAndSortLibraryEntries(
            entries = entries,
            filter = null,
            hiddenKeys = setOf(artistEntry("AR1", "Alfa").hiddenKey),
            sortType = LibraryExpressiveSortType.RECENTLY_ADDED,
            ascending = false,
            manualOrder = "",
            searchQuery = "",
        )
        assertEquals(listOf("PL1", "AL1", "POD1"), overview.map { it.hiddenKey.id })

        val searched = filterAndSortLibraryEntries(
            entries = entries,
            filter = null,
            hiddenKeys = emptySet(),
            sortType = LibraryExpressiveSortType.NAME,
            ascending = true,
            manualOrder = "",
            searchQuery = "Midnight",
        )
        assertEquals(listOf("Midnight Album", "Midnight Track"), searched.map { it.title })
    }

    @Test
    fun `manual reordering moves visible items while preserving hidden positions`() {
        val a = albumEntry("A", "Album A")
        val b = artistEntry("B", "Artist B")
        val c = playlistEntry("C", "Playlist C")
        val all = listOf(a, b, c)

        // Move C before A while B is visible in the subset [a, c]
        val updatedOrder = moveLibraryEntries(
            allEntries = all,
            visibleEntries = listOf(a, c),
            storedOrder = "",
            fromKey = c.hiddenKey,
            toKey = a.hiddenKey,
        )
        val ordered = orderLibraryEntries(all, updatedOrder)
        assertEquals(listOf(c.hiddenKey, b.hiddenKey, a.hiddenKey), ordered.map { it.hiddenKey })
    }
}

