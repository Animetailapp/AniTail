package com.anitail.music.ui.screens

import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem

internal enum class HomeDynamicShelfKind {
    VIDEO_CAROUSEL,
    MULTI_BROWSE_CAROUSEL,
}

internal fun isWideVideoThumbnailUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return "/vi/" in lower ||
        "/vi_webp/" in lower ||
        "hqdefault" in lower ||
        "mqdefault" in lower ||
        "sddefault" in lower ||
        "maxresdefault" in lower ||
        Regex("=w(320|400|480|560|640|800|1280)-h(180|225|270|315|360|450|720)").containsMatchIn(lower)
}

internal fun classifyHomeDynamicSection(items: List<YTItem>): HomeDynamicShelfKind? {
    if (items.isEmpty()) return null
    val allVideosOrEpisodes = items.all { item ->
        item is EpisodeItem || (item is SongItem && isWideVideoThumbnailUrl(item.thumbnail))
    }
    return if (allVideosOrEpisodes) {
        HomeDynamicShelfKind.VIDEO_CAROUSEL
    } else {
        HomeDynamicShelfKind.MULTI_BROWSE_CAROUSEL
    }
}

internal fun resolveHomeSectionRoute(browseId: String): String =
    when {
        browseId == "FEmusic_moods_and_genres" -> "mood_and_genres"
        browseId == "FEmusic_charts" -> "charts_screen"
        browseId == "FEmusic_library_non_music_audio_channels_list" ||
            browseId == "FEmusic_new_episodes" -> "youtube_browse/$browseId"
        browseId.startsWith("MPSP") -> "online_podcast/$browseId"
        else -> "browse/$browseId"
    }

internal fun computeHomePagerWidthDp(screenWidthDp: Int): Int {
    val usable = (screenWidthDp - 32).coerceAtLeast(220)
    val factor = if (screenWidthDp >= 640) 0.46f else 0.90f
    return (usable * factor).toInt().coerceAtLeast(220)
}

