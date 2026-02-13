package com.anitail.desktop.sync

import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.PlaylistContinuationPage
import com.anitail.innertube.pages.PlaylistPage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySyncServiceTest {
    @Test
    fun collectPlaylistSongsPaginatesContinuations() = runBlocking {
        val initial = PlaylistPage(
            playlist = PlaylistItem(
                id = "LM",
                title = "Liked",
                author = null,
                songCountText = "3 canciones",
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
                isEditable = false,
            ),
            songs = listOf(
                SongItem(
                    id = "s1",
                    title = "One",
                    artists = listOf(Artist("A", null)),
                    thumbnail = "t1",
                ),
            ),
            songsContinuation = "c1",
            continuation = null,
        )

        val continuations = mapOf(
            "c1" to PlaylistContinuationPage(
                songs = listOf(
                    SongItem(
                        id = "s2",
                        title = "Two",
                        artists = listOf(Artist("B", null)),
                        thumbnail = "t2",
                    ),
                ),
                continuation = "c2",
            ),
            "c2" to PlaylistContinuationPage(
                songs = listOf(
                    SongItem(
                        id = "s3",
                        title = "Three",
                        artists = listOf(Artist("C", null)),
                        thumbnail = "t3",
                    ),
                ),
                continuation = null,
            ),
        )

        val result = collectPlaylistSongs(
            initial = initial,
            fetchContinuation = { token ->
                continuations[token] ?: PlaylistContinuationPage(emptyList(), null)
            },
        )

        assertEquals(listOf("s1", "s2", "s3"), result.map { it.id })
    }

    @Test
    fun mapPlaylistForSyncPreservesRemoteCount() {
        val entity = mapPlaylistForSync(
            playlist = PlaylistItem(
                id = "PL1",
                title = "Mix",
                author = null,
                songCountText = "12 canciones",
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
                isEditable = false,
            ),
            now = java.time.LocalDateTime.of(2025, 1, 1, 0, 0),
        )

        assertEquals(12, entity.remoteSongCount)
        assertEquals("PL1", entity.id)
    }

    @Test
    fun parseSongCountTextParsesDigits() {
        assertEquals(85, parseSongCountText("85 canciones"))
        assertEquals(1200, parseSongCountText("1.200 canciones"))
        assertNull(parseSongCountText(null))
    }
}
