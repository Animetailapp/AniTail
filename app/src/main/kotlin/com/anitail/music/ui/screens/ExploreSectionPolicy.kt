package com.anitail.music.ui.screens

import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.ChartsPage

enum class ExploreFilterChip {
    CHARTS,
    NEW_RELEASES,
    MOODS_AND_GENRES,
}

enum class ChartShelfKind {
    SONG_PAGER,
    VIDEO_CAROUSEL,
    MULTI_BROWSE_CAROUSEL,
}

data class ExploreVisibility(
    val showCharts: Boolean,
    val showNewReleases: Boolean,
    val showMoodsAndGenres: Boolean,
)

private val VIDEO_CHART_TITLE_HINTS = listOf(
    "music video",
    "top videos",
    "classifiche dei video",
    "video musicali",
    "videos musicales",
    "vídeos musicales",
    "videoclip",
)

fun isVideoChartTitle(title: String): Boolean {
    val normalized = title.trim().lowercase()
    return VIDEO_CHART_TITLE_HINTS.any { normalized.contains(it) }
}

fun classifyChartSection(section: ChartsPage.ChartSection): ChartShelfKind? {
    val items = section.items
    if (items.isEmpty()) return null

    val songItems = items.filterIsInstance<SongItem>()
    if (songItems.size == items.size) {
        val hasExplicitVideos = songItems.any { it.isVideoSong }
        val untypedVideoShelf = songItems.all { it.musicVideoType == null } && isVideoChartTitle(section.title)
        return if (hasExplicitVideos || untypedVideoShelf) {
            ChartShelfKind.VIDEO_CAROUSEL
        } else {
            ChartShelfKind.SONG_PAGER
        }
    }

    return if (songItems.size > items.size / 2) {
        ChartShelfKind.SONG_PAGER
    } else {
        ChartShelfKind.MULTI_BROWSE_CAROUSEL
    }
}

fun filterExploreVisibility(selectedChip: ExploreFilterChip?): ExploreVisibility =
    when (selectedChip) {
        null -> ExploreVisibility(
            showCharts = true,
            showNewReleases = true,
            showMoodsAndGenres = true,
        )
        ExploreFilterChip.CHARTS -> ExploreVisibility(
            showCharts = true,
            showNewReleases = false,
            showMoodsAndGenres = false,
        )
        ExploreFilterChip.NEW_RELEASES -> ExploreVisibility(
            showCharts = false,
            showNewReleases = true,
            showMoodsAndGenres = false,
        )
        ExploreFilterChip.MOODS_AND_GENRES -> ExploreVisibility(
            showCharts = false,
            showNewReleases = false,
            showMoodsAndGenres = true,
        )
    }

