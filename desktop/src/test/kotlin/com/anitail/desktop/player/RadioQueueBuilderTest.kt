package com.anitail.desktop.player

import com.anitail.innertube.models.Album
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.NextResult
import com.anitail.shared.model.LibraryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RadioQueueBuilderTest {
    private fun song(
        id: String,
        artistId: String = "artist-$id",
        albumId: String? = null,
    ): SongItem {
        return SongItem(
            id = id,
            title = "Title $id",
            artists = listOf(Artist(name = "Artist $id", id = artistId)),
            album = albumId?.let { Album(name = "Album $id", id = it) },
            duration = 180,
            thumbnail = "thumb-$id",
        )
    }

    private fun nextResult(items: List<SongItem>, currentIndex: Int? = null): NextResult {
        val endpoint = WatchEndpoint(videoId = items.firstOrNull()?.id ?: "fallback")
        return NextResult(
            title = "Radio",
            items = items,
            currentIndex = currentIndex,
            lyricsEndpoint = null,
            relatedEndpoint = null,
            continuation = null,
            endpoint = endpoint,
        )
    }

    @Test
    fun usesCurrentIndexWhenPresent() {
        val items = listOf(song("a"), song("b"), song("c"))
        val currentItem = LibraryItem(
            id = "b",
            title = "Title b",
            artist = "Artist b",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/watch?v=b",
        )

        val plan = buildRadioQueuePlan(currentItem, nextResult(items, currentIndex = 1))

        assertEquals(3, plan.items.size)
        assertEquals("b", plan.items[1].id)
        assertEquals(1, plan.startIndex)
        assertNotNull(plan.currentSong)
        assertEquals("b", plan.currentSong?.id)
    }

    @Test
    fun prependsCurrentWhenMissingFromNextResult() {
        val items = listOf(song("a"), song("c"))
        val currentItem = LibraryItem(
            id = "b",
            title = "Title b",
            artist = "Artist b",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/watch?v=b",
        )

        val plan = buildRadioQueuePlan(currentItem, nextResult(items, currentIndex = null))

        assertEquals(3, plan.items.size)
        assertEquals("b", plan.items.first().id)
        assertEquals(0, plan.startIndex)
    }

    @Test
    fun fallsBackToCurrentWhenNextResultEmpty() {
        val currentItem = LibraryItem(
            id = "b",
            title = "Title b",
            artist = "Artist b",
            artworkUrl = null,
            playbackUrl = "https://music.youtube.com/watch?v=b",
        )

        val plan = buildRadioQueuePlan(currentItem, nextResult(emptyList(), currentIndex = null))

        assertEquals(1, plan.items.size)
        assertEquals("b", plan.items.first().id)
        assertEquals(0, plan.startIndex)
    }
}
