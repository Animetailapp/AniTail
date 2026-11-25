@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.music.R
import com.anitail.music.viewmodels.CommunityPlaylistItem
import com.anitail.music.viewmodels.DailyDiscoverItem
import kotlin.math.min

@Composable
internal fun SpeedDialSection(
    items: List<YTItem>,
    maxWidth: Dp,
    currentSongId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    onItemClick: (YTItem) -> Unit,
    onSurpriseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = remember(items) {
        val prepared = items.map { it as YTItem? }.toMutableList()
        prepared.add(min(8, prepared.size), null)
        prepared
    }
    val pages = remember(displayItems) { displayItems.chunked(9) }
    val pagerState = rememberPagerState { pages.size }
    val pageWidth = (maxWidth - 32.dp).coerceAtLeast(220.dp)
    val tileSize = (pageWidth - 16.dp) / 3

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            key = { "speed_dial_page_$it" },
            modifier = Modifier.fillMaxWidth(),
        ) { pageIndex ->
            val pageItems = pages[pageIndex]
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) { rowIndex ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { columnIndex ->
                            val tileIndex = rowIndex * 3 + columnIndex
                            if (tileIndex >= pageItems.size) {
                                Spacer(modifier = Modifier.size(tileSize))
                            } else {
                                val pageItem = pageItems[tileIndex]
                                if (pageItem == null) {
                                    SpeedDialSurpriseTile(
                                        onClick = onSurpriseClick,
                                        modifier = Modifier.size(tileSize),
                                    )
                                } else {
                                    val isTileActive = when (pageItem) {
                                        is SongItem -> pageItem.id == currentSongId
                                        is EpisodeItem -> pageItem.id == currentSongId
                                        is AlbumItem -> pageItem.id == currentAlbumId || pageItem.browseId == currentAlbumId
                                        else -> false
                                    }
                                    SpeedDialTile(
                                        item = pageItem,
                                        isActive = isTileActive,
                                        isPlaying = isPlaying && isTileActive,
                                        onClick = { onItemClick(pageItem) },
                                        modifier = Modifier.size(tileSize),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PagerDots(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
internal fun CommunityPlaylistsSection(
    playlists: List<CommunityPlaylistItem>,
    maxWidth: Dp,
    onOpenPlaylist: (CommunityPlaylistItem) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: (CommunityPlaylistItem) -> Unit,
    onRadioClick: (CommunityPlaylistItem) -> Unit,
    onAddClick: (CommunityPlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val cardWidth = (maxWidth - 76.dp).coerceIn(260.dp, 360.dp)

    LazyRow(
        state = rowState,
        flingBehavior = rememberSnapFlingBehavior(rowState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        items(
            items = playlists,
            key = { it.playlist.id },
        ) { playlistItem ->
            CommunityPlaylistCard(
                item = playlistItem,
                onOpenPlaylist = { onOpenPlaylist(playlistItem) },
                onSongClick = onSongClick,
                onPlayAllClick = { onPlayAllClick(playlistItem) },
                onRadioClick = { onRadioClick(playlistItem) },
                onAddClick = { onAddClick(playlistItem) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
internal fun DailyDiscoverSection(
    discoverItems: List<DailyDiscoverItem>,
    maxWidth: Dp,
    onItemClick: (DailyDiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleDiscoverItems = remember(discoverItems) { discoverItems.take(6) }
    val preferredCardWidth = (maxWidth - 92.dp).coerceAtLeast(220.dp)
    val carouselState = rememberCarouselState { visibleDiscoverItems.size }

    HorizontalUncontainedCarousel(
        state = carouselState,
        itemWidth = preferredCardWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) { index ->
        val discoverItem = visibleDiscoverItems[index]
        DailyDiscoverCard(
            item = discoverItem,
            onClick = { onItemClick(discoverItem) },
            emphasizeContent = index == carouselState.currentItem,
            modifier = Modifier
                .height(360.dp)
                .maskClip(RoundedCornerShape(28.dp)),
        )
    }
}

internal fun LazyListScope.SpeedDialBlock(
    speedDialItems: List<YTItem>,
    maxWidth: Dp,
    currentSongId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    onItemClick: (YTItem) -> Unit,
    onSurpriseClick: () -> Unit,
) {
    if (speedDialItems.isEmpty()) return

    item(key = "speed_dial_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            title = stringResource(R.string.speed_dial),
        )
    }

    item(key = "speed_dial_content", contentType = "home_speed_dial") {
        SpeedDialSection(
            items = speedDialItems,
            maxWidth = maxWidth,
            currentSongId = currentSongId,
            currentAlbumId = currentAlbumId,
            isPlaying = isPlaying,
            onItemClick = onItemClick,
            onSurpriseClick = onSurpriseClick,
        )
    }
}

internal fun LazyListScope.CommunityPlaylistsBlock(
    playlists: List<CommunityPlaylistItem>?,
    maxWidth: Dp,
    onOpenPlaylist: (CommunityPlaylistItem) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: (CommunityPlaylistItem) -> Unit,
    onRadioClick: (CommunityPlaylistItem) -> Unit,
    onAddClick: (CommunityPlaylistItem) -> Unit,
) {
    if (playlists.isNullOrEmpty()) return

    item(key = "community_playlists_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            title = stringResource(R.string.from_the_community),
        )
    }

    item(key = "community_playlists_content", contentType = "home_community") {
        CommunityPlaylistsSection(
            playlists = playlists,
            maxWidth = maxWidth,
            onOpenPlaylist = onOpenPlaylist,
            onSongClick = onSongClick,
            onPlayAllClick = onPlayAllClick,
            onRadioClick = onRadioClick,
            onAddClick = onAddClick,
        )
    }
}

internal fun LazyListScope.DailyDiscoverBlock(
    discoverItems: List<DailyDiscoverItem>?,
    maxWidth: Dp,
    onItemClick: (DailyDiscoverItem) -> Unit,
) {
    if (discoverItems.isNullOrEmpty()) return

    item(key = "daily_discover_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            title = stringResource(R.string.daily_discover),
        )
    }
    item(key = "daily_discover_content", contentType = "home_daily_discover") {
        DailyDiscoverSection(
            discoverItems = discoverItems,
            maxWidth = maxWidth,
            onItemClick = onItemClick,
        )
    }
}
