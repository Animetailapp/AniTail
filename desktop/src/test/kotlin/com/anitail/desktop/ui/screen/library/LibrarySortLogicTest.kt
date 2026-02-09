package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDateTime

class LibrarySortLogicTest {
    @Test
    fun sortSongsByCreateDateDescending() {
        val older = SongEntity(
            id = "s1",
            title = "Alpha",
            artistName = "B",
            inLibrary = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val newer = SongEntity(
            id = "s2",
            title = "Beta",
            artistName = "A",
            inLibrary = LocalDateTime.of(2025, 1, 1, 0, 0),
        )

        val result = sortSongs(
            songs = listOf(older, newer),
            sortType = SongSortType.CREATE_DATE,
            descending = true,
        )

        assertEquals(listOf("s2", "s1"), result.map { it.id })
    }

    @Test
    fun filterSongsByLikedAndDownloaded() {
        val liked = SongEntity(id = "s1", title = "Liked", artistName = "A", liked = true)
        val library = SongEntity(
            id = "s2",
            title = "Library",
            artistName = "B",
            inLibrary = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val downloaded = SongEntity(id = "s3", title = "Downloaded", artistName = "C")

        val likedOnly = filterSongs(
            songs = listOf(liked, library, downloaded),
            filter = SongFilter.LIKED,
            downloadedSongIds = setOf("s3"),
        )
        assertEquals(listOf("s1"), likedOnly.map { it.id })

        val downloadedOnly = filterSongs(
            songs = listOf(liked, library, downloaded),
            filter = SongFilter.DOWNLOADED,
            downloadedSongIds = setOf("s3"),
        )
        assertEquals(listOf("s3"), downloadedOnly.map { it.id })
    }

    @Test
    fun sortPlaylistsBySongCount() {
        val p1 = PlaylistEntity(id = "p1", name = "One")
        val p2 = PlaylistEntity(id = "p2", name = "Two")

        val result = sortPlaylists(
            playlists = listOf(p1, p2),
            sortType = PlaylistSortType.SONG_COUNT,
            descending = true,
            songCounts = mapOf("p1" to 3, "p2" to 10),
        )

        assertEquals(listOf("p2", "p1"), result.map { it.id })
    }
}
