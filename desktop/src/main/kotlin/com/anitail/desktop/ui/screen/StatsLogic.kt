package com.anitail.desktop.ui.screen

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.relations.indexArtistIdsBySongId
import com.anitail.desktop.db.relations.indexPrimaryArtistIdBySongId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

internal enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }

internal enum class StatPeriod {
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toEpochMillis(now: LocalDateTime = LocalDateTime.now()): Long =
        when (this) {
            WEEK_1 -> now.minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            MONTH_1 -> now.minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            MONTH_3 -> now.minusMonths(3).toInstant(ZoneOffset.UTC).toEpochMilli()
            MONTH_6 -> now.minusMonths(6).toInstant(ZoneOffset.UTC).toEpochMilli()
            YEAR_1 -> now.minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
            ALL -> 0L
        }
}

internal fun statToPeriod(
    selection: OptionStats,
    index: Int,
    now: LocalDateTime = LocalDateTime.now(),
): Long =
    when (selection) {
        OptionStats.WEEKS -> now.minusWeeks(index.toLong()).minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        OptionStats.MONTHS -> now.withDayOfMonth(1).minusMonths(index.toLong()).toInstant(ZoneOffset.UTC).toEpochMilli()
        OptionStats.YEARS ->
            now.withDayOfMonth(1).withMonth(1).minusYears(index.toLong()).toInstant(ZoneOffset.UTC).toEpochMilli()

        OptionStats.CONTINUOUS -> {
            val safeIndex = if (index > StatPeriod.entries.size) 0 else index
            StatPeriod.entries[safeIndex].toEpochMillis(now)
        }
    }

internal data class SongStatsEntry(
    val song: SongEntity,
    val songCountListened: Int,
    val timeListened: Long,
)

internal data class ArtistStatsEntry(
    val artist: ArtistEntity,
    val songCount: Int,
    val timeListened: Long,
)

internal data class AlbumStatsEntry(
    val album: AlbumEntity,
    val songCountListened: Int,
    val timeListened: Long,
)

internal fun computeSongStats(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    fromTimeMs: Long,
    toTimeMs: Long,
): List<SongStatsEntry> {
    val totals = mutableMapOf<String, StatsAccumulator>()
    events.forEach { event ->
        if (!eventInRange(event, fromTimeMs, toTimeMs)) return@forEach
        if (!songsById.containsKey(event.songId)) return@forEach
        val acc = totals.getOrPut(event.songId) { StatsAccumulator() }
        acc.count += 1
        acc.timeMs += event.playTime
    }
    return totals.entries.mapNotNull { (songId, acc) ->
        val song = songsById[songId] ?: return@mapNotNull null
        SongStatsEntry(song, acc.count, acc.timeMs)
    }.sortedByDescending { it.timeListened }
}

internal fun computeArtistStats(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    artistsById: Map<String, ArtistEntity>,
    fromTimeMs: Long,
    toTimeMs: Long,
): List<ArtistStatsEntry> {
    val primaryArtistBySongId = indexPrimaryArtistIdBySongId(songArtistMaps)
    val totals = mutableMapOf<String, StatsAccumulator>()
    events.forEach { event ->
        if (!eventInRange(event, fromTimeMs, toTimeMs)) return@forEach
        val song = songsById[event.songId] ?: return@forEach
        val artistId = primaryArtistBySongId[song.id] ?: return@forEach
        val acc = totals.getOrPut(artistId) { StatsAccumulator() }
        acc.count += 1
        acc.timeMs += event.playTime
    }
    return totals.entries.mapNotNull { (artistId, acc) ->
        val artist = artistsById[artistId] ?: return@mapNotNull null
        ArtistStatsEntry(artist, acc.count, acc.timeMs)
    }.sortedByDescending { it.timeListened }
}

internal fun computeAlbumStats(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    albumsById: Map<String, AlbumEntity>,
    fromTimeMs: Long,
    toTimeMs: Long,
): List<AlbumStatsEntry> {
    val totals = mutableMapOf<String, StatsAccumulator>()
    events.forEach { event ->
        if (!eventInRange(event, fromTimeMs, toTimeMs)) return@forEach
        val song = songsById[event.songId] ?: return@forEach
        val albumId = song.albumId ?: return@forEach
        val acc = totals.getOrPut(albumId) { StatsAccumulator() }
        acc.count += 1
        acc.timeMs += event.playTime
    }
    return totals.entries.mapNotNull { (albumId, acc) ->
        val album = albumsById[albumId] ?: return@mapNotNull null
        AlbumStatsEntry(album, acc.count, acc.timeMs)
    }.sortedByDescending { it.timeListened }
}

private data class StatsAccumulator(
    var count: Int = 0,
    var timeMs: Long = 0L,
)

private fun eventInRange(
    event: EventEntity,
    fromTimeMs: Long,
    toTimeMs: Long,
): Boolean {
    val timeMs = event.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli()
    return timeMs > fromTimeMs && timeMs <= toTimeMs
}

internal data class PeriodActivityBucket(
    val label: String,
    val playTimeMs: Long,
    val playCount: Int,
)

internal data class PeriodInsights(
    val totalTimeMs: Long,
    val totalPlays: Int,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val activeDays: Int,
    val longestStreakDays: Int,
    val peakHour: Int?,
    val periodBreakdown: Map<ListeningPeriod, Long>,
    val activityBuckets: List<PeriodActivityBucket>,
)

internal fun computePeriodInsights(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    fromTimeMs: Long,
    toTimeMs: Long,
): PeriodInsights {
    val filtered = events.filter { eventInRange(it, fromTimeMs, toTimeMs) && songsById.containsKey(it.songId) }
    if (filtered.isEmpty()) {
        return PeriodInsights(
            totalTimeMs = 0L,
            totalPlays = 0,
            uniqueSongs = 0,
            uniqueArtists = 0,
            activeDays = 0,
            longestStreakDays = 0,
            peakHour = null,
            periodBreakdown = ListeningPeriod.entries.associateWith { 0L },
            activityBuckets = emptyList(),
        )
    }

    val artistIdsBySongId = indexArtistIdsBySongId(songArtistMaps)
    var totalTimeMs = 0L
    val uniqueSongs = HashSet<String>()
    val uniqueArtists = HashSet<String>()
    val activeDates = HashSet<LocalDate>()
    val hourTotals = LongArray(24)
    val periodTotals = ListeningPeriod.entries.associateWith { 0L }.toMutableMap()

    filtered.forEach { event ->
        val song = songsById[event.songId]
        val playTime = effectivePlayTimeMs(event, song)
        totalTimeMs += playTime
        uniqueSongs.add(event.songId)

        val artistIds = artistIdsBySongId[event.songId].orEmpty()
        if (artistIds.isNotEmpty()) {
            uniqueArtists.addAll(artistIds)
        } else {
            val fallbackName = song?.artistName?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (fallbackName.isNotBlank()) uniqueArtists.add(fallbackName)
        }

        val date = event.timestamp.toLocalDate()
        activeDates.add(date)

        val hour = event.timestamp.hour.coerceIn(0, 23)
        hourTotals[hour] += playTime

        val period = when (hour) {
            in 5..11 -> ListeningPeriod.MORNING
            in 12..16 -> ListeningPeriod.AFTERNOON
            in 17..20 -> ListeningPeriod.EVENING
            else -> ListeningPeriod.NIGHT
        }
        periodTotals[period] = periodTotals.getValue(period) + playTime
    }

    val sortedDates = activeDates.sorted()
    var longestStreak = 0
    var currentStreak = 0
    var prevDate: LocalDate? = null
    sortedDates.forEach { date ->
        currentStreak = if (prevDate != null && date == prevDate!!.plusDays(1)) {
            currentStreak + 1
        } else {
            1
        }
        if (currentStreak > longestStreak) longestStreak = currentStreak
        prevDate = date
    }

    val peakHour = hourTotals.indices.maxByOrNull { hourTotals[it] }?.takeIf { hourTotals[it] > 0L }

    val effectiveFromMs = if (fromTimeMs <= 0L) {
        filtered.minOf { it.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli() }
    } else {
        fromTimeMs
    }
    val spanDays = ((toTimeMs - effectiveFromMs) / 86_400_000L).coerceAtLeast(1L)

    val dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val monthFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
    val weekFields = WeekFields.of(Locale.getDefault())

    val bucketsMap = linkedMapOf<String, StatsAccumulator>()
    filtered.sortedBy { it.timestamp }.forEach { event ->
        val date = event.timestamp.toLocalDate()
        val key = when {
            spanDays <= 35L -> dayFormatter.format(date)
            spanDays <= 185L -> {
                val week = date.get(weekFields.weekOfWeekBasedYear())
                "S$week ${date.year % 100}"
            }
            else -> monthFormatter.format(date)
        }
        val acc = bucketsMap.getOrPut(key) { StatsAccumulator() }
        acc.count += 1
        acc.timeMs += effectivePlayTimeMs(event, songsById[event.songId])
    }

    val activityBuckets = bucketsMap.entries.map { (label, acc) ->
        PeriodActivityBucket(
            label = label,
            playTimeMs = acc.timeMs,
            playCount = acc.count,
        )
    }

    return PeriodInsights(
        totalTimeMs = totalTimeMs,
        totalPlays = filtered.size,
        uniqueSongs = uniqueSongs.size,
        uniqueArtists = uniqueArtists.size,
        activeDays = activeDates.size,
        longestStreakDays = longestStreak,
        peakHour = peakHour,
        periodBreakdown = periodTotals,
        activityBuckets = activityBuckets,
    )
}

internal enum class ListeningPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

internal data class TopArtistUi(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playCount: Int,
    val timeListenedMs: Long,
    val isNewTopFive: Boolean,
)

internal data class TopSongUi(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val subtitle: String? = null,
    val playCount: Int,
    val timeListenedMs: Long,
    val isNew: Boolean,
)

internal data class RankedArtistUi(
    val rank: Int,
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playCount: Int,
    val timeListenedMs: Long,
    val isNewTopFive: Boolean,
)

internal data class SoundCapsuleMonthUiState(
    val year: Int,
    val month: Int,
    val totalPlayTimeMs: Long,
    val totalSongsPlayed: Int,
    val topArtist: TopArtistUi?,
    val topSong: TopSongUi?,
    val rankedSongs: List<TopSongUi>,
    val rankedArtists: List<RankedArtistUi>,
    val dailyPlayTimeMs: List<Long>,
    val periodPlayTimeMs: Map<ListeningPeriod, Long>,
) {
    val yearMonth: YearMonth
        get() = YearMonth.of(year, month)
    val monthKey: String
        get() = "$year-$month"
    val totalMinutes: Int
        get() = (totalPlayTimeMs / 60_000L).toInt()
}

internal data class SoundCapsuleOverview(
    val months: List<SoundCapsuleMonthUiState>,
    val totalSongsSinceJoining: Int,
    val totalPlayTimeMs: Long,
    val uniqueArtistsCount: Int,
    val uniqueSongsCount: Int,
)

internal fun buildSoundCapsuleOverview(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    artistsById: Map<String, ArtistEntity>,
    now: YearMonth = YearMonth.now(),
): SoundCapsuleOverview {
    if (events.isEmpty()) {
        return SoundCapsuleOverview(
            months = listOf(emptySoundCapsuleMonth(now)),
            totalSongsSinceJoining = 0,
            totalPlayTimeMs = 0L,
            uniqueArtistsCount = 0,
            uniqueSongsCount = 0,
        )
    }

    val firstMonth = events.minOf { YearMonth.of(it.timestamp.year, it.timestamp.monthValue) }
    val startMonth = if (now.isBefore(firstMonth)) firstMonth else now
    val eventsByMonth = events.groupBy { YearMonth.of(it.timestamp.year, it.timestamp.monthValue) }
    val monthsDescending = generateMonthsDescending(start = startMonth, end = firstMonth)
    val artistIdsBySongId = indexArtistIdsBySongId(songArtistMaps)

    val monthTopArtistIds = mutableMapOf<YearMonth, List<String>>()
    val monthTopSongIds = mutableMapOf<YearMonth, String?>()

    val rawMonths = monthsDescending.map { month ->
        val monthEvents = eventsByMonth[month].orEmpty()
        if (monthEvents.isEmpty()) {
            monthTopArtistIds[month] = emptyList()
            monthTopSongIds[month] = null
            return@map emptySoundCapsuleMonth(month)
        }

        val totalPlayTimeMs = monthEvents.sumOf { effectivePlayTimeMs(it, songsById[it.songId]) }
        val rankedSongsRaw = computeRankedSongsForMonth(monthEvents, songsById, artistIdsBySongId, artistsById, limit = 20)
        val rankedArtistsRaw = computeRankedArtistsForMonth(monthEvents, songsById, artistIdsBySongId, artistsById, limit = 20)

        monthTopArtistIds[month] = rankedArtistsRaw.take(5).map { it.id }
        monthTopSongIds[month] = rankedSongsRaw.firstOrNull()?.id

        SoundCapsuleMonthUiState(
            year = month.year,
            month = month.monthValue,
            totalPlayTimeMs = totalPlayTimeMs,
            totalSongsPlayed = monthEvents.size,
            topArtist = null,
            topSong = rankedSongsRaw.firstOrNull(),
            rankedSongs = rankedSongsRaw,
            rankedArtists = rankedArtistsRaw.mapIndexed { index, stat ->
                RankedArtistUi(
                    rank = index + 1,
                    id = stat.id,
                    name = stat.name,
                    thumbnailUrl = stat.thumbnailUrl,
                    playCount = stat.playCount,
                    timeListenedMs = stat.playTimeMs,
                    isNewTopFive = false,
                )
            },
            dailyPlayTimeMs = buildDailyPlayTime(month, monthEvents, songsById),
            periodPlayTimeMs = buildPeriodPlayTime(monthEvents, songsById),
        )
    }

    val monthsWithBadges = rawMonths.map { state ->
        if (state.totalSongsPlayed <= 0) return@map state
        val prevMonth = state.yearMonth.minusMonths(1)
        val prevTopFive = monthTopArtistIds[prevMonth].orEmpty().toSet()
        val prevTopSongId = monthTopSongIds[prevMonth]

        val updatedArtists = state.rankedArtists.map { artist ->
            artist.copy(isNewTopFive = artist.rank <= 5 && artist.id !in prevTopFive)
        }
        val updatedTopSong = state.topSong?.let { top ->
            top.copy(isNew = top.id != prevTopSongId)
        }
        val updatedSongs = state.rankedSongs.mapIndexed { index, song ->
            if (index == 0 && updatedTopSong != null) updatedTopSong else song
        }

        state.copy(
            topArtist = updatedArtists.firstOrNull()?.let { first ->
                TopArtistUi(
                    id = first.id,
                    name = first.name,
                    thumbnailUrl = first.thumbnailUrl,
                    playCount = first.playCount,
                    timeListenedMs = first.timeListenedMs,
                    isNewTopFive = first.isNewTopFive,
                )
            },
            topSong = updatedTopSong,
            rankedSongs = updatedSongs,
            rankedArtists = updatedArtists,
        )
    }

    val totalPlayTime = events.sumOf { effectivePlayTimeMs(it, songsById[it.songId]) }
    val uniqueSongs = events.mapNotNull { event -> songsById[event.songId]?.id }.toSet().size
    val uniqueArtists = events.flatMap { event ->
        val song = songsById[event.songId] ?: return@flatMap emptyList()
        val ids = artistIdsBySongId[song.id].orEmpty()
        if (ids.isNotEmpty()) ids else listOfNotNull(song.artistName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() })
    }.toSet().size

    return SoundCapsuleOverview(
        months = monthsWithBadges,
        totalSongsSinceJoining = events.size,
        totalPlayTimeMs = totalPlayTime,
        uniqueArtistsCount = uniqueArtists,
        uniqueSongsCount = uniqueSongs,
    )
}

private data class CapsuleArtistAccumulator(
    val id: String,
    val name: String,
    var thumbnailUrl: String?,
    var playCount: Int = 0,
    var playTimeMs: Long = 0L,
)

private fun computeRankedSongsForMonth(
    monthEvents: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    artistIdsBySongId: Map<String, List<String>>,
    artistsById: Map<String, ArtistEntity>,
    limit: Int,
): List<TopSongUi> {
    val totals = linkedMapOf<String, StatsAccumulator>()
    monthEvents.forEach { event ->
        val song = songsById[event.songId] ?: return@forEach
        val acc = totals.getOrPut(song.id) { StatsAccumulator() }
        acc.count += 1
        acc.timeMs += effectivePlayTimeMs(event, song)
    }
    return totals.entries
        .mapNotNull { (songId, acc) ->
            val song = songsById[songId] ?: return@mapNotNull null
            val primaryArtistName = artistIdsBySongId[song.id]?.firstOrNull()
                ?.let { artistsById[it]?.name }
                ?: song.artistName
            TopSongUi(
                id = song.id,
                title = song.title,
                thumbnailUrl = song.thumbnailUrl,
                subtitle = primaryArtistName,
                playCount = acc.count,
                timeListenedMs = acc.timeMs,
                isNew = false,
            )
        }
        .sortedWith(compareByDescending<TopSongUi> { it.timeListenedMs }.thenByDescending { it.playCount })
        .take(limit)
}

private fun computeRankedArtistsForMonth(
    monthEvents: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    artistIdsBySongId: Map<String, List<String>>,
    artistsById: Map<String, ArtistEntity>,
    limit: Int,
): List<CapsuleArtistAccumulator> {
    val rawStats = linkedMapOf<String, CapsuleArtistAccumulator>()
    monthEvents.forEach { event ->
        val song = songsById[event.songId] ?: return@forEach
        val playTimeMs = effectivePlayTimeMs(event, song)
        val mappedArtistIds = artistIdsBySongId[song.id].orEmpty()
        if (mappedArtistIds.isNotEmpty()) {
            mappedArtistIds.forEach { artistId ->
                val artist = artistsById[artistId]
                val name = artist?.name ?: song.artistName?.split(",", "&", ";")?.firstOrNull()?.trim().orEmpty()
                if (name.isBlank()) return@forEach
                val acc = rawStats.getOrPut(artistId) {
                    CapsuleArtistAccumulator(
                        id = artistId,
                        name = name,
                        thumbnailUrl = artist?.thumbnailUrl ?: song.thumbnailUrl,
                    )
                }
                if (acc.thumbnailUrl.isNullOrBlank()) {
                    acc.thumbnailUrl = artist?.thumbnailUrl ?: song.thumbnailUrl
                }
                acc.playCount += 1
                acc.playTimeMs += playTimeMs
            }
        } else {
            val fallbackName = song.artistName
                ?.split(",", "&", ";")
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (fallbackName.isNotBlank()) {
                val fallbackId = "local:${fallbackName.lowercase(Locale.ROOT)}"
                val acc = rawStats.getOrPut(fallbackId) {
                    CapsuleArtistAccumulator(
                        id = fallbackId,
                        name = fallbackName,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                }
                if (acc.thumbnailUrl.isNullOrBlank()) {
                    acc.thumbnailUrl = song.thumbnailUrl
                }
                acc.playCount += 1
                acc.playTimeMs += playTimeMs
            }
        }
    }

    val mergedByName = linkedMapOf<String, CapsuleArtistAccumulator>()
    rawStats.values.forEach { stat ->
        val normalizedName = stat.name.trim().lowercase(Locale.ROOT)
        if (normalizedName.isBlank()) return@forEach
        val current = mergedByName[normalizedName]
        if (current == null) {
            mergedByName[normalizedName] = stat.copy()
        } else {
            val currentGenerated = current.id.startsWith("LA") || current.id.startsWith("local:")
            val statGenerated = stat.id.startsWith("LA") || stat.id.startsWith("local:")
            val preferredId = when {
                currentGenerated && !statGenerated -> stat.id
                !currentGenerated && statGenerated -> current.id
                stat.playTimeMs > current.playTimeMs -> stat.id
                else -> current.id
            }
            val preferredName = if (preferredId == stat.id) stat.name else current.name
            val preferredThumb = current.thumbnailUrl?.takeIf { it.isNotBlank() } ?: stat.thumbnailUrl
            mergedByName[normalizedName] = CapsuleArtistAccumulator(
                id = preferredId,
                name = preferredName,
                thumbnailUrl = preferredThumb,
                playCount = current.playCount + stat.playCount,
                playTimeMs = current.playTimeMs + stat.playTimeMs,
            )
        }
    }

    return mergedByName.values
        .sortedWith(compareByDescending<CapsuleArtistAccumulator> { it.playTimeMs }.thenByDescending { it.playCount })
        .take(limit)
}

private fun buildDailyPlayTime(
    month: YearMonth,
    monthEvents: List<EventEntity>,
    songsById: Map<String, SongEntity>,
): List<Long> {
    val dailyTotals = MutableList(month.lengthOfMonth()) { 0L }
    monthEvents.forEach { event ->
        val dayIndex = event.timestamp.dayOfMonth - 1
        if (dayIndex in dailyTotals.indices) {
            dailyTotals[dayIndex] += effectivePlayTimeMs(event, songsById[event.songId])
        }
    }
    return dailyTotals
}

private fun buildPeriodPlayTime(
    monthEvents: List<EventEntity>,
    songsById: Map<String, SongEntity>,
): Map<ListeningPeriod, Long> {
    val totals = ListeningPeriod.entries.associateWith { 0L }.toMutableMap()
    monthEvents.forEach { event ->
        val period = when (event.timestamp.hour) {
            in 5..11 -> ListeningPeriod.MORNING
            in 12..16 -> ListeningPeriod.AFTERNOON
            in 17..20 -> ListeningPeriod.EVENING
            else -> ListeningPeriod.NIGHT
        }
        totals[period] = totals.getValue(period) + effectivePlayTimeMs(event, songsById[event.songId])
    }
    return totals
}

private fun effectivePlayTimeMs(event: EventEntity, song: SongEntity?): Long {
    if (event.playTime > 0L) return event.playTime
    val durationSeconds = song?.duration ?: 0
    return if (durationSeconds > 0) durationSeconds * 1_000L else 0L
}

private fun emptySoundCapsuleMonth(month: YearMonth): SoundCapsuleMonthUiState =
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
