package com.anitail.desktop.db

import com.anitail.desktop.db.entities.PlaylistEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.LocalDateTime

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
}
