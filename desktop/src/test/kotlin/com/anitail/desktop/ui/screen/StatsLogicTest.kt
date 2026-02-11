import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.ui.screen.OptionStats
import com.anitail.desktop.ui.screen.computeSongStats
import com.anitail.desktop.ui.screen.statToPeriod
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsLogicTest {
    @Test
    fun statToPeriodUsesWeekOffsetForWeeklySelection() {
        val now = LocalDateTime.of(2026, 2, 11, 0, 0)
        val result = statToPeriod(OptionStats.WEEKS, 1, now)
        val expected = now.minusWeeks(1).minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, result)
    }

    @Test
    fun computeSongStatsAggregatesPlayTimeAndCounts() {
        val now = LocalDateTime.of(2026, 2, 11, 12, 0)
        val songA = SongEntity(id = "a", title = "Alpha")
        val songB = SongEntity(id = "b", title = "Beta")
        val songsById = mapOf(songA.id to songA, songB.id to songB)

        val events = listOf(
            EventEntity(id = 1, songId = "a", timestamp = now.minusDays(1), playTime = 1_000),
            EventEntity(id = 2, songId = "a", timestamp = now.minusDays(2), playTime = 2_000),
            EventEntity(id = 3, songId = "b", timestamp = now.minusDays(1), playTime = 5_000),
            EventEntity(id = 4, songId = "missing", timestamp = now.minusDays(1), playTime = 9_000),
            EventEntity(id = 5, songId = "b", timestamp = now.minusDays(10), playTime = 1_000),
        )

        val fromMs = now.minusDays(3).toInstant(ZoneOffset.UTC).toEpochMilli()
        val toMs = now.toInstant(ZoneOffset.UTC).toEpochMilli()

        val results = computeSongStats(events, songsById, fromMs, toMs)

        assertEquals(listOf("b", "a"), results.map { it.song.id })
        assertEquals(1, results[0].songCountListened)
        assertEquals(5_000L, results[0].timeListened)
        assertEquals(2, results[1].songCountListened)
        assertEquals(3_000L, results[1].timeListened)
    }
}
