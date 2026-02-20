package com.anitail.desktop.db

import com.anitail.desktop.db.entities.SongEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.LocalDateTime

class SongMergeTest {
    @Test
    fun mergePreservesLikedState() {
        val existing = SongEntity(
            id = "s1",
            title = "Old",
            liked = true,
            likedDate = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val incoming = SongEntity(
            id = "s1",
            title = "New",
            liked = false,
            likedDate = null,
        )

        val merged = DesktopDatabase.getInstance().mergeSong(existing, incoming)

        assertEquals(true, merged.liked)
        assertNotNull(merged.likedDate)
    }

    @Test
    fun mergeKeepsLibraryAndPlayTime() {
        val existing = SongEntity(
            id = "s1",
            title = "Old",
            inLibrary = LocalDateTime.of(2024, 1, 1, 0, 0),
            totalPlayTime = 10_000L,
        )
        val incoming = SongEntity(
            id = "s1",
            title = "New",
            totalPlayTime = 1_000L,
        )

        val merged = DesktopDatabase.getInstance().mergeSong(existing, incoming)

        assertNotNull(merged.inLibrary)
        assertEquals(10_000L, merged.totalPlayTime)
    }
}
