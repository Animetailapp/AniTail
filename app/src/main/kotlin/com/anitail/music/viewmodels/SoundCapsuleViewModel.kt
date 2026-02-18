package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.EventWithSong
import com.anitail.music.db.entities.Song
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
            if (monthEvents.isEmpty()) {
                return@map emptyMonthState(month)
            }

            val totalPlayTimeMs = monthEvents.sumOf { effectivePlayTimeMs(it) }

            val topArtists = topArtistsForMonth(month, monthEvents, limit = 20)
            val previousMonthEvents = eventsByMonth[month.minusMonths(1)].orEmpty()
            val previousTopFiveIds = topArtistsForMonth(month.minusMonths(1), previousMonthEvents, limit = 5)
                .map { it.id }
                .toSet()

            val rankedArtists = topArtists.mapIndexed { index, artist ->
                RankedArtistUi(
                    rank = index + 1,
                    id = artist.id,
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    isNewTopFive = index < 5 && artist.id !in previousTopFiveIds,
                )
            }

            val rankedSongs = topSongsForMonth(month, monthEvents, limit = 20)
            val topSong = rankedSongs.firstOrNull()
            val previousTopSong = topSongsForMonth(month.minusMonths(1), previousMonthEvents, limit = 1).firstOrNull()

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
                            subtitle = song.subtitle,
                            isNew = song.id != previousTopSong?.id,
                        )
                    },
                rankedSongs = rankedSongs,
                rankedArtists = rankedArtists,
                dailyPlayTimeMs = buildDailyPlayTime(month = month, monthEvents = monthEvents),
                periodPlayTimeMs = buildPeriodPlayTime(monthEvents = monthEvents),
            )
        }
    }

    private suspend fun topSongsForMonth(
        month: YearMonth,
        monthEvents: List<EventWithSong>,
        limit: Int,
    ): List<TopSongUi> {
        val (fromTimestamp, toTimestamp) = month.toRoomRange()
        val queriedSongs = database.mostPlayedSongs(
            fromTimeStamp = fromTimestamp,
            limit = limit,
            toTimeStamp = toTimestamp,
        ).first()
        if (queriedSongs.isNotEmpty()) {
            return queriedSongs.map { song ->
                TopSongUi(
                    id = song.id,
                    title = song.title,
                    thumbnailUrl = song.thumbnailUrl,
                    subtitle = song.artists.firstOrNull()?.name,
                    isNew = false,
                )
            }
        }
        return buildTopSongsFallback(monthEvents, limit)
    }

    private suspend fun topArtistsForMonth(
        month: YearMonth,
        monthEvents: List<EventWithSong>,
        limit: Int,
    ): List<ArtistStat> {
        val (fromTimestamp, toTimestamp) = month.toRoomRange()
        val queriedArtists = database.mostPlayedArtists(
            fromTimeStamp = fromTimestamp,
            limit = limit,
            toTimeStamp = toTimestamp,
        ).first()
        if (queriedArtists.isNotEmpty()) {
            return queriedArtists.map { artist ->
                ArtistStat(
                    id = artist.id,
                    name = artist.artist.name,
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    playTimeMs = artist.timeListened?.toLong() ?: 0L,
                )
            }
        }
        return buildTopArtistsFallback(monthEvents, limit)
    }

    private fun buildTopSongsFallback(monthEvents: List<EventWithSong>, limit: Int): List<TopSongUi> {
        return monthEvents
            .groupBy { event -> event.song?.id.orEmpty() }
            .mapNotNull { (songId, groupedEvents) ->
                val validSong: Song = groupedEvents.firstNotNullOfOrNull { it.song } ?: return@mapNotNull null
                if (songId.isBlank()) return@mapNotNull null
                TopSongUi(
                    id = songId,
                    title = validSong.title,
                    thumbnailUrl = validSong.thumbnailUrl,
                    subtitle = validSong.artists.firstOrNull()?.name ?: validSong.song.artistName,
                    isNew = false,
                ) to groupedEvents.sumOf(::effectivePlayTimeMs)
            }
            .sortedByDescending { (_, playTimeMs) -> playTimeMs }
            .take(limit)
            .map { (song, _) -> song }
    }

    private fun buildTopArtistsFallback(monthEvents: List<EventWithSong>, limit: Int): List<ArtistStat> {
        if (monthEvents.isEmpty()) return emptyList()

        val artistStats = linkedMapOf<String, ArtistStat>()
        monthEvents.forEach { event ->
            val playTimeMs = effectivePlayTimeMs(event)
            val artists = event.song?.artists.orEmpty()
            if (artists.isNotEmpty()) {
                artists.forEach { artist ->
                    val current = artistStats[artist.id]
                    artistStats[artist.id] =
                        ArtistStat(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = artist.thumbnailUrl ?: current?.thumbnailUrl,
                            playTimeMs = (current?.playTimeMs ?: 0L) + playTimeMs,
                        )
                }
            } else {
                val fallbackName =
                    event.song?.song?.artistName
                        ?.split(",", "&", ";")
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                if (fallbackName.isNotBlank()) {
                    val fallbackId = "local:${fallbackName.lowercase()}"
                    val current = artistStats[fallbackId]
                    artistStats[fallbackId] =
                        ArtistStat(
                            id = fallbackId,
                            name = fallbackName,
                            thumbnailUrl = current?.thumbnailUrl,
                            playTimeMs = (current?.playTimeMs ?: 0L) + playTimeMs,
                        )
                }
            }
        }

        return artistStats
            .values
            .sortedByDescending { it.playTimeMs }
            .take(limit)
    }

    private fun buildDailyPlayTime(month: YearMonth, monthEvents: List<EventWithSong>): List<Long> {
        val dailyTotals = MutableList(month.lengthOfMonth()) { 0L }
        monthEvents.forEach { event ->
            val dayIndex = event.event.timestamp.dayOfMonth - 1
            if (dayIndex in dailyTotals.indices) {
                dailyTotals[dayIndex] += effectivePlayTimeMs(event)
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
            totals[period] = totals.getValue(period) + effectivePlayTimeMs(event)
        }
        return totals
    }

    private fun effectivePlayTimeMs(event: EventWithSong): Long {
        val loggedPlayTime = event.event.playTime
        if (loggedPlayTime > 0L) return loggedPlayTime
        val durationSeconds = event.song?.song?.duration ?: 0
        return if (durationSeconds > 0) durationSeconds * 1_000L else 0L
    }

    private fun emptyMonthState(month: YearMonth): SoundCapsuleMonthUiState =
        SoundCapsuleMonthUiState(
            year = month.year,
            month = month.monthValue,
            totalPlayTimeMs = 0L,
            totalSongsPlayed = 0,
            topArtist = null,
            topSong = null,
            rankedSongs = emptyList(),
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

private data class ArtistStat(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
)

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
