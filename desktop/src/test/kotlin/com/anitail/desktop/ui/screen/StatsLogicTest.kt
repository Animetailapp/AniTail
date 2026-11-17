import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.ui.screen.ListeningPeriod
import com.anitail.desktop.ui.screen.OptionStats
import com.anitail.desktop.ui.screen.buildSoundCapsuleOverview
import com.anitail.desktop.ui.screen.computeSongStats
import com.anitail.desktop.ui.screen.statToPeriod
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun buildSoundCapsuleOverviewGroupsMonthsPeriodsAndNewBadges() {
        val song1 = SongEntity(id = "s1", title = "Song One", artistName = "Artist A", duration = 180)
        val song2 = SongEntity(id = "s2", title = "Song Two", artistName = "Artist B", duration = 200)
        val artistA = ArtistEntity(id = "UC1", name = "Artist A")
        val artistB = ArtistEntity(id = "UC2", name = "Artist B")
        val maps = listOf(
            SongArtistMap(songId = "s1", artistId = "UC1", position = 0),
            SongArtistMap(songId = "s2", artistId = "UC2", position = 0),
        )

        val events = listOf(
            EventEntity(id = 1, songId = "s1", timestamp = LocalDateTime.of(2026, 2, 10, 9, 0), playTime = 120_000L),
            EventEntity(id = 2, songId = "s2", timestamp = LocalDateTime.of(2026, 3, 5, 22, 0), playTime = 240_000L),
            EventEntity(id = 3, songId = "s2", timestamp = LocalDateTime.of(2026, 3, 5, 14, 0), playTime = 60_000L),
        )

        val overview = buildSoundCapsuleOverview(
            events = events,
            songsById = mapOf(song1.id to song1, song2.id to song2),
            songArtistMaps = maps,
            artistsById = mapOf(artistA.id to artistA, artistB.id to artistB),
            now = YearMonth.of(2026, 3),
        )

        assertEquals(3, overview.totalSongsSinceJoining)
        assertEquals(420_000L, overview.totalPlayTimeMs)
        assertEquals(2, overview.uniqueArtistsCount)
        assertEquals(2, overview.uniqueSongsCount)
        assertEquals(2, overview.months.size)

        val march = overview.months[0]
        assertEquals(2026, march.year)
        assertEquals(3, march.month)
        assertEquals(300_000L, march.totalPlayTimeMs)
        assertEquals("UC2", march.topArtist?.id)
        assertTrue(march.topArtist?.isNewTopFive == true)
        assertEquals("s2", march.topSong?.id)
        assertTrue(march.topSong?.isNew == true)
        assertEquals(240_000L, march.periodPlayTimeMs[ListeningPeriod.NIGHT])
        assertEquals(60_000L, march.periodPlayTimeMs[ListeningPeriod.AFTERNOON])
        assertEquals(300_000L, march.dailyPlayTimeMs[4])

        val feb = overview.months[1]
        assertEquals("UC1", feb.topArtist?.id)
        assertFalse(feb.totalSongsPlayed == 0)
    }
}
