package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.EventWithSong
import com.anitail.music.db.entities.SongWithStats
import com.anitail.music.ui.screens.ListeningPeriod
import com.anitail.music.ui.screens.RankedArtistUi
import com.anitail.music.ui.screens.SoundCapsuleMonthUiState
import com.anitail.music.ui.screens.TopArtistUi
import com.anitail.music.ui.screens.TopSongUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SoundCapsuleViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val monthlyCapsules =
        database.events()
            .mapLatest(::buildMonthlyCapsules)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val totalSongsSinceJoining =
        monthlyCapsules
            .map { months -> months.sumOf { it.totalSongsPlayed } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    fun monthState(year: Int, month: Int): Flow<SoundCapsuleMonthUiState?> =
        monthlyCapsules.map { months ->
            months.firstOrNull { it.year == year && it.month == month }
        }

    private suspend fun buildMonthlyCapsules(events: List<EventWithSong>): List<SoundCapsuleMonthUiState> {
        val currentMonth = YearMonth.now(Clock.systemUTC())
        if (events.isEmpty()) {
            return listOf(emptyMonthState(currentMonth))
        }

        val firstMonth =
            events.minByOrNull { it.event.timestamp }
                ?.event
                ?.timestamp
                ?.let { YearMonth.of(it.year, it.monthValue) }
                ?: currentMonth

        val eventsByMonth = events.groupBy { YearMonth.of(it.event.timestamp.year, it.event.timestamp.monthValue) }
        val months = generateMonthsDescending(start = currentMonth, end = firstMonth)
        return months.map { month ->
            val monthEvents = eventsByMonth[month].orEmpty()
            val totalPlayTimeMs = monthEvents.sumOf { it.event.playTime }

            if (totalPlayTimeMs <= 0L) {
                return@map emptyMonthState(month)
            }

            val topArtists = topArtistsForMonth(month = month, limit = 20)
            val previousTopFiveIds = topArtistsForMonth(month = month.minusMonths(1), limit = 5)
                .map { artist -> artist.id }
                .toSet()

            val rankedArtists = topArtists.mapIndexed { index, artist ->
                RankedArtistUi(
                    rank = index + 1,
                    id = artist.id,
                    name = artist.artist.name,
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    isNewTopFive = index < 5 && artist.id !in previousTopFiveIds,
                )
            }

            val topSong = topSongsForMonth(month = month, limit = 1).firstOrNull()
            val previousTopSong = topSongsForMonth(month = month.minusMonths(1), limit = 1).firstOrNull()

            SoundCapsuleMonthUiState(
                year = month.year,
                month = month.monthValue,
                totalPlayTimeMs = totalPlayTimeMs,
                totalSongsPlayed = monthEvents.size,
                topArtist =
                    rankedArtists.firstOrNull()?.let { topArtist ->
                        TopArtistUi(
                            id = topArtist.id,
                            name = topArtist.name,
                            thumbnailUrl = topArtist.thumbnailUrl,
                            isNewTopFive = topArtist.isNewTopFive,
                        )
                    },
                topSong =
                    topSong?.let { song ->
                        TopSongUi(
                            id = song.id,
                            title = song.title,
                            thumbnailUrl = song.thumbnailUrl,
                            isNew = song.id != previousTopSong?.id,
                        )
                    },
                rankedArtists = rankedArtists,
                dailyPlayTimeMs = buildDailyPlayTime(month = month, monthEvents = monthEvents),
                periodPlayTimeMs = buildPeriodPlayTime(monthEvents = monthEvents),
            )
        }
    }

    private suspend fun topArtistsForMonth(month: YearMonth, limit: Int): List<Artist> {
        val (fromTimestamp, toTimestamp) = month.toRoomRange()
        return database.mostPlayedArtists(
            fromTimeStamp = fromTimestamp,
            limit = limit,
            toTimeStamp = toTimestamp,
        ).first()
    }

    private suspend fun topSongsForMonth(month: YearMonth, limit: Int): List<SongWithStats> {
        val (fromTimestamp, toTimestamp) = month.toRoomRange()
        return database.mostPlayedSongsStats(
            fromTimeStamp = fromTimestamp,
            limit = limit,
            toTimeStamp = toTimestamp,
        ).first()
    }

    private fun buildDailyPlayTime(month: YearMonth, monthEvents: List<EventWithSong>): List<Long> {
        val dailyTotals = MutableList(month.lengthOfMonth()) { 0L }
        monthEvents.forEach { event ->
            val dayIndex = event.event.timestamp.dayOfMonth - 1
            if (dayIndex in dailyTotals.indices) {
                dailyTotals[dayIndex] += event.event.playTime
            }
        }
        return dailyTotals
    }

    private fun buildPeriodPlayTime(monthEvents: List<EventWithSong>): Map<ListeningPeriod, Long> {
        val totals = ListeningPeriod.entries.associateWith { 0L }.toMutableMap()
        monthEvents.forEach { event ->
            val period =
                when (event.event.timestamp.hour) {
                    in 5..11 -> ListeningPeriod.MORNING
                    in 12..16 -> ListeningPeriod.AFTERNOON
                    in 17..20 -> ListeningPeriod.EVENING
                    else -> ListeningPeriod.NIGHT
                }
            totals[period] = totals.getValue(period) + event.event.playTime
        }
        return totals
    }

    private fun emptyMonthState(month: YearMonth): SoundCapsuleMonthUiState =
        SoundCapsuleMonthUiState(
            year = month.year,
            month = month.monthValue,
            totalPlayTimeMs = 0L,
            totalSongsPlayed = 0,
            topArtist = null,
            topSong = null,
            rankedArtists = emptyList(),
            dailyPlayTimeMs = List(month.lengthOfMonth()) { 0L },
            periodPlayTimeMs = ListeningPeriod.entries.associateWith { 0L },
        )

    private fun generateMonthsDescending(start: YearMonth, end: YearMonth): List<YearMonth> {
        val months = mutableListOf<YearMonth>()
        var current = start
        while (!current.isBefore(end)) {
            months += current
            current = current.minusMonths(1)
        }
        return months
    }
}

private fun YearMonth.toRoomRange(): Pair<Long, Long> {
    val startInclusive = atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val fromTimestamp = startInclusive - 1L
    val toTimestamp =
        plusMonths(1)
            .atDay(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli() - 1L
    return fromTimestamp to toTimestamp
}
