package com.anitail.desktop.ui.screen

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    val totals = mutableMapOf<String, StatsAccumulator>()
    events.forEach { event ->
        if (!eventInRange(event, fromTimeMs, toTimeMs)) return@forEach
        val song = songsById[event.songId] ?: return@forEach
        val artistId = primaryArtistIdForSong(song.id, songArtistMaps) ?: return@forEach
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
