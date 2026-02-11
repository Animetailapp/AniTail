package com.anitail.desktop.ui.screen

import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongEntity
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryLogicTest {
    @Test
    fun groupHistoryEventsGroupsByDateAgoAndRemovesDuplicates() {
        val today = LocalDate.of(2026, 2, 11)
        val events = listOf(
            EventEntity(id = 1, songId = "s1", timestamp = LocalDateTime.of(2026, 2, 11, 10, 0), playTime = 10),
            EventEntity(id = 2, songId = "s1", timestamp = LocalDateTime.of(2026, 2, 11, 12, 0), playTime = 10),
            EventEntity(id = 3, songId = "s2", timestamp = LocalDateTime.of(2026, 2, 10, 9, 0), playTime = 10),
            EventEntity(id = 4, songId = "s3", timestamp = LocalDateTime.of(2026, 2, 9, 9, 0), playTime = 10),
            EventEntity(id = 5, songId = "s4", timestamp = LocalDateTime.of(2026, 2, 4, 9, 0), playTime = 10),
            EventEntity(id = 6, songId = "s5", timestamp = LocalDateTime.of(2026, 1, 1, 9, 0), playTime = 10),
        )
        val songsById = mapOf(
            "s1" to SongEntity(id = "s1", title = "One", artistName = "A"),
            "s2" to SongEntity(id = "s2", title = "Two", artistName = "B"),
            "s3" to SongEntity(id = "s3", title = "Three", artistName = "C"),
            "s4" to SongEntity(id = "s4", title = "Four", artistName = "D"),
            "s5" to SongEntity(id = "s5", title = "Five", artistName = "E"),
        )

        val grouped = groupHistoryEvents(events, songsById, today, query = "")

        assertEquals(
            listOf(
                DateAgo.Today,
                DateAgo.Yesterday,
                DateAgo.ThisWeek,
                DateAgo.LastWeek,
                DateAgo.Other(LocalDate.of(2026, 1, 1)),
            ),
            grouped.keys.toList(),
        )
        assertEquals(listOf("s1"), grouped[DateAgo.Today]?.map { it.song.id })
        assertEquals(listOf("s2"), grouped[DateAgo.Yesterday]?.map { it.song.id })
        assertEquals(listOf("s3"), grouped[DateAgo.ThisWeek]?.map { it.song.id })
        assertEquals(listOf("s4"), grouped[DateAgo.LastWeek]?.map { it.song.id })
        assertEquals(listOf("s5"), grouped[DateAgo.Other(LocalDate.of(2026, 1, 1))]?.map { it.song.id })
    }

    @Test
    fun groupHistoryEventsFiltersByQuery() {
        val today = LocalDate.of(2026, 2, 11)
        val events = listOf(
            EventEntity(id = 1, songId = "s1", timestamp = LocalDateTime.of(2026, 2, 11, 10, 0), playTime = 10),
            EventEntity(id = 2, songId = "s2", timestamp = LocalDateTime.of(2026, 2, 11, 11, 0), playTime = 10),
        )
        val songsById = mapOf(
            "s1" to SongEntity(id = "s1", title = "Hello", artistName = "Artist A"),
            "s2" to SongEntity(id = "s2", title = "World", artistName = "Artist B"),
        )

        val grouped = groupHistoryEvents(events, songsById, today, query = "wor")

        assertEquals(listOf(DateAgo.Today), grouped.keys.toList())
        assertEquals(listOf("s2"), grouped[DateAgo.Today]?.map { it.song.id })
        assertTrue(grouped[DateAgo.Today]?.all { it.song.title.contains("World") } == true)
    }
}
