package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.anitail.innertube.YouTube
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.EventWithSong
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.ui.screens.ListeningPeriod
import com.anitail.music.ui.screens.RankedArtistUi
import com.anitail.music.ui.screens.SoundCapsuleMonthUiState
import com.anitail.music.ui.screens.TopArtistUi
import com.anitail.music.ui.screens.TopSongUi
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SoundCapsuleViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    private val artistRefreshInProgress = mutableSetOf<String>()
    private val artistRefreshVersion = MutableStateFlow(0)

    val monthlyCapsules =
        combine(database.events(), artistRefreshVersion) { events, _ -> events }
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

    init {
        viewModelScope.launch {
            monthlyCapsules
                .map { months ->
                    months
                        .flatMap { it.rankedArtists }
                        .map { it.id }
                        .distinct()
                }
                .collectLatest { artistIds ->
                    artistIds.forEach { artistId ->
                        refreshArtistThumbnailIfNeeded(artistId)
                    }
                }
        }
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
        val fallbackThumbnails = artistThumbnailFallback(monthEvents)
        val (fromTimestamp, toTimestamp) = month.toRoomRange()
        val queriedArtists = database.mostPlayedArtists(
            fromTimeStamp = fromTimestamp,
            limit = limit,
            toTimeStamp = toTimestamp,
        ).first()
        if (queriedArtists.isNotEmpty()) {
            return mergeArtistStatsByName(
                stats = queriedArtists.map { artist ->
                    ArtistStat(
                        id = artist.id,
                        name = artist.artist.name,
                        thumbnailUrl = artist.artist.thumbnailUrl ?: fallbackThumbnails[artist.id],
                        playTimeMs = artist.timeListened?.toLong() ?: 0L,
                    )
                },
                limit = limit,
            )
        }
        return mergeArtistStatsByName(
            stats = buildTopArtistsFallback(monthEvents, fallbackThumbnails),
            limit = limit,
        )
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

    private fun buildTopArtistsFallback(
        monthEvents: List<EventWithSong>,
        fallbackThumbnails: Map<String, String?>,
    ): List<ArtistStat> {
        if (monthEvents.isEmpty()) return emptyList()

        val artistStats = linkedMapOf<String, ArtistStat>()
        monthEvents.forEach { event ->
            val playTimeMs = effectivePlayTimeMs(event)
            val songThumbnail = event.song?.thumbnailUrl
            val artists = event.song?.artists.orEmpty()
            if (artists.isNotEmpty()) {
                artists.forEach { artist ->
                    val current = artistStats[artist.id]
                    artistStats[artist.id] =
                        ArtistStat(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = artist.thumbnailUrl ?: current?.thumbnailUrl ?: fallbackThumbnails[artist.id] ?: songThumbnail,
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
                            thumbnailUrl = current?.thumbnailUrl ?: songThumbnail,
                            playTimeMs = (current?.playTimeMs ?: 0L) + playTimeMs,
                        )
                }
            }
        }

        return artistStats
            .values
            .sortedByDescending { it.playTimeMs }
    }

    private fun artistThumbnailFallback(monthEvents: List<EventWithSong>): Map<String, String?> {
        val bestByArtist = linkedMapOf<String, Pair<Long, String?>>()
        monthEvents.forEach { event ->
            val playTimeMs = effectivePlayTimeMs(event)
            val songThumbnail = event.song?.thumbnailUrl
            event.song?.artists.orEmpty().forEach { artist ->
                val current = bestByArtist[artist.id]
                if (current == null || playTimeMs > current.first) {
                    bestByArtist[artist.id] = playTimeMs to (songThumbnail ?: artist.thumbnailUrl)
                }
            }
        }
        return bestByArtist.mapValues { (_, value) -> value.second }
    }

    private fun mergeArtistStatsByName(stats: List<ArtistStat>, limit: Int): List<ArtistStat> {
        if (stats.isEmpty()) return emptyList()

        val mergedByName = linkedMapOf<String, ArtistStat>()
        stats.forEach { stat ->
            val normalizedName = stat.name.trim().lowercase(Locale.ROOT)
            if (normalizedName.isBlank()) return@forEach

            val current = mergedByName[normalizedName]
            if (current == null) {
                mergedByName[normalizedName] = stat
                return@forEach
            }

            val preferred =
                when {
                    current.id.isGeneratedArtistId() && !stat.id.isGeneratedArtistId() -> stat
                    !current.id.isGeneratedArtistId() && stat.id.isGeneratedArtistId() -> current
                    stat.playTimeMs > current.playTimeMs -> stat
                    else -> current
                }
            mergedByName[normalizedName] =
                preferred.copy(
                    thumbnailUrl = preferred.thumbnailUrl ?: current.thumbnailUrl ?: stat.thumbnailUrl,
                    playTimeMs = current.playTimeMs + stat.playTimeMs,
                )
        }

        return mergedByName
            .values
            .sortedByDescending { it.playTimeMs }
            .take(limit)
    }

    private fun String.isGeneratedArtistId(): Boolean = startsWith("LA") || startsWith("local:")

    private suspend fun refreshArtistThumbnailIfNeeded(artistId: String) {
        if (!artistRefreshInProgress.add(artistId)) return
        try {
            val localArtist = database.artist(artistId).first()?.artist ?: return
            if (!localArtist.isYouTubeArtist) return

            val shouldRefresh =
                localArtist.thumbnailUrl.isNullOrBlank() ||
                    Duration.between(localArtist.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
            if (!shouldRefresh) return

            YouTube.artist(localArtist.id)
                .onSuccess { artistPage ->
                    database.query {
                        update(localArtist, artistPage)
                    }
                    artistRefreshVersion.value += 1
                }
                .onFailure { reportException(it) }
        } finally {
            artistRefreshInProgress.remove(artistId)
        }
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

    suspend fun mediaItemForSong(songId: String): MediaItem? {
        if (songId.isBlank()) return null
        return database.getSongById(songId)?.toMediaItem()
    }

    suspend fun mediaItemsForSongs(songIds: List<String>): List<MediaItem> {
        val validIds = songIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyList()
        return database.getSongsByIds(validIds).map { it.toMediaItem() }
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
