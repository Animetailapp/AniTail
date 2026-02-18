package com.anitail.music.ui.screens

import java.time.YearMonth

enum class ListeningPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

data class TopArtistUi(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val isNewTopFive: Boolean,
)

data class TopSongUi(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val isNew: Boolean,
)

data class RankedArtistUi(
    val rank: Int,
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val isNewTopFive: Boolean,
)

data class SoundCapsuleMonthUiState(
    val year: Int,
    val month: Int,
    val totalPlayTimeMs: Long,
    val totalSongsPlayed: Int,
    val topArtist: TopArtistUi?,
    val topSong: TopSongUi?,
    val rankedArtists: List<RankedArtistUi>,
    val dailyPlayTimeMs: List<Long>,
    val periodPlayTimeMs: Map<ListeningPeriod, Long>,
) {
    val yearMonth: YearMonth = YearMonth.of(year, month)
    val monthKey: String = "$year-$month"
    val totalMinutes: Int = (totalPlayTimeMs / 60_000L).toInt()
}
