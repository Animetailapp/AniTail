package com.anitail.desktop.ui.screen

import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.SongEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class HistoryEvent(
    val event: EventEntity,
    val song: SongEntity,
)

internal sealed class DateAgo {
    data object Today : DateAgo()
    data object Yesterday : DateAgo()
    data object ThisWeek : DateAgo()
    data object LastWeek : DateAgo()

    data class Other(val date: LocalDate) : DateAgo()
}

internal fun groupHistoryEvents(
    events: List<EventEntity>,
    songsById: Map<String, SongEntity>,
    today: LocalDate = LocalDate.now(),
    query: String = "",
): Map<DateAgo, List<HistoryEvent>> {
    if (events.isEmpty()) return emptyMap()

    val normalizedQuery = query.trim()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val lastMonday = thisMonday.minusDays(7)

    val filtered = events
        .sortedByDescending { it.timestamp }
        .mapNotNull { event ->
            val song = songsById[event.songId] ?: return@mapNotNull null
            if (normalizedQuery.isNotEmpty()) {
                val matches = song.title.contains(normalizedQuery, ignoreCase = true) ||
                    (song.artistName?.contains(normalizedQuery, ignoreCase = true) == true)
                if (!matches) return@mapNotNull null
            }
            HistoryEvent(event, song)
        }

    val grouped = filtered.groupBy { history ->
        val date = history.event.timestamp.toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
        when {
            daysAgo == 0 -> DateAgo.Today
            daysAgo == 1 -> DateAgo.Yesterday
            date >= thisMonday -> DateAgo.ThisWeek
            date >= lastMonday -> DateAgo.LastWeek
            else -> DateAgo.Other(date.withDayOfMonth(1))
        }
    }

    val comparator = compareBy<DateAgo> { dateAgo ->
        when (dateAgo) {
            DateAgo.Today -> 0L
            DateAgo.Yesterday -> 1L
            DateAgo.ThisWeek -> 2L
            DateAgo.LastWeek -> 3L
            is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
        }
    }

    return grouped
        .mapValues { (_, items) -> items.distinctBy { it.song.id } }
        .toSortedMap(comparator)
}
