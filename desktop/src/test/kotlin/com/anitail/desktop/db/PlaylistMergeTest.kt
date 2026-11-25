package com.anitail.desktop.db

import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PlaylistMergeTest {
    @Test
    fun mergePreservesRemoteCountWhenMissing() {
        val existing = PlaylistEntity(
            id = "p1",
            name = "Mix",
            browseId = "PL1",
            remoteSongCount = 85,
        )
        val incoming = PlaylistEntity(
            id = "p1",
            name = "Mix",
            browseId = "PL1",
            remoteSongCount = null,
        )

        val merged = DesktopDatabase.getInstance().mergePlaylist(existing, incoming)

        assertEquals(85, merged.remoteSongCount)
    }

    @Test
    fun mergeKeepsEditableAndBookmark() {
        val existing = PlaylistEntity(
            id = "p1",
            name = "Mix",
            isEditable = true,
            bookmarkedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val incoming = PlaylistEntity(
            id = "p1",
            name = "Mix",
            isEditable = false,
            bookmarkedAt = null,
        )

        val merged = DesktopDatabase.getInstance().mergePlaylist(existing, incoming)

        assertEquals(true, merged.isEditable)
        assertNotNull(merged.bookmarkedAt)
    }

    @Test
    fun replacePlaylistSongsReplacesAndOrdersSongs() = runBlocking {
        val tempDir = Files.createTempDirectory("anitail-playlist-songs-test")
        val database = DesktopDatabase.createForTests(tempDir)
        database.initialize()

        database.insertPlaylist(PlaylistEntity(id = "local-pl", name = "Love It", browseId = "PL_REMOTE"))
        database.insertSongsBatch(
            listOf(
                SongEntity(id = "s1", title = "One", artistName = "A") to emptyList(),
                SongEntity(id = "s2", title = "Two", artistName = "B") to emptyList(),
                SongEntity(id = "s3", title = "Three", artistName = "C") to emptyList(),
            ),
        )
        database.addSongToPlaylist("local-pl", "s1")

        database.replacePlaylistSongs("local-pl", listOf("s3", "s1", "s2"))
        assertEquals(
            listOf("s3", "s1", "s2"),
            database.songsInPlaylist("local-pl").first().map { it.id },
        )

        database.replacePlaylistSongs("local-pl", listOf("s2", "s3"))
        assertEquals(
            listOf("s2", "s3"),
            database.songsInPlaylist("local-pl").first().map { it.id },
        )
        assertEquals("local-pl", database.playlist("PL_REMOTE").first()?.id)
    }
}
