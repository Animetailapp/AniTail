package com.anitail.desktop.db

import com.anitail.desktop.db.entities.EventEntity
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class EventDeleteTest {
    @Test
    fun deleteEventRemovesOnlyTarget() = runBlocking {
        val tempDir = Files.createTempDirectory("anitail-db-test")
        val database = DesktopDatabase.createForTests(tempDir)
        database.initialize()

        database.insertEvent(
            EventEntity(
                songId = "s1",
                timestamp = LocalDateTime.of(2026, 2, 11, 10, 0),
                playTime = 10,
            ),
        )
        database.insertEvent(
            EventEntity(
                songId = "s2",
                timestamp = LocalDateTime.of(2026, 2, 11, 11, 0),
                playTime = 10,
            ),
        )

        val initial = database.events.first()
        val targetId = initial.first { it.songId == "s1" }.id

        database.deleteEvent(targetId)

        val remaining = database.events.first()
        assertEquals(listOf("s2"), remaining.map { it.songId })
    }
}
