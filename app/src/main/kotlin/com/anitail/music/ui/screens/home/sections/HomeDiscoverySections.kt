package com.anitail.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val pagerState = rememberLazyListState()
    val currentPage by remember(pagerState, pages.size) {
        derivedStateOf { pagerState.firstVisibleItemIndex.coerceAtMost((pages.size - 1).coerceAtLeast(0)) }
    }
    val pageWidth = maxWidth - 32.dp
    val tileSize = (pageWidth - 16.dp) / 3

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        LazyRow(
            state = pagerState,
            flingBehavior = rememberSnapFlingBehavior(pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = pages.size,
                key = { "speed_dial_page_$it" },
                contentType = { "speed_dial_page" },
            ) { pageIndex ->
                val pageItems = pages[pageIndex]
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(pageWidth),
                ) {
                    repeat(3) { rowIndex ->
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        SpeedDialTile(
                                            item = pageItem,
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        PagerDots(
            pageCount = pages.size,
            currentPage = currentPage,
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
    val cardWidth = maxWidth - 90.dp

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
            contentType = { "community_playlist_card" },
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
    val visibleDiscoverItems = remember(discoverItems) { discoverItems.take(3) }
    val rowState = rememberLazyListState()
    val currentPage by remember(rowState, visibleDiscoverItems.size) {
        derivedStateOf {
            rowState.firstVisibleItemIndex.coerceAtMost((visibleDiscoverItems.size - 1).coerceAtLeast(0))
        }
    }
    val cardWidth = maxWidth - 110.dp

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        PagerDots(
            pageCount = visibleDiscoverItems.size,
            currentPage = currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp),
        )

        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(rowState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = visibleDiscoverItems,
                key = { it.recommendation.id },
                contentType = { "daily_discover_card" },
            ) { discoverItem ->
                DailyDiscoverCard(
                    item = discoverItem,
                    onClick = { onItemClick(discoverItem) },
                    modifier = Modifier
                        .width(cardWidth)
                        .height(360.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.SpeedDialBlock(
    speedDialItems: List<YTItem>,
    maxWidth: Dp,
    onItemClick: (YTItem) -> Unit,
    onSurpriseClick: () -> Unit,
) {
    if (speedDialItems.isEmpty()) return

    item(key = "speed_dial_title") {
        HomeSectionHeader(
            title = stringResource(R.string.speed_dial),
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "speed_dial_content") {
        SpeedDialSection(
            items = speedDialItems,
            maxWidth = maxWidth,
            onItemClick = onItemClick,
            onSurpriseClick = onSurpriseClick,
            modifier = Modifier.animateItem(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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

    item(key = "community_playlists_title") {
        HomeSectionHeader(
            title = stringResource(R.string.from_the_community),
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "community_playlists_content") {
        CommunityPlaylistsSection(
            playlists = playlists,
            maxWidth = maxWidth,
            onOpenPlaylist = onOpenPlaylist,
            onSongClick = onSongClick,
            onPlayAllClick = onPlayAllClick,
            onRadioClick = onRadioClick,
            onAddClick = onAddClick,
            modifier = Modifier.animateItem(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.DailyDiscoverBlock(
    discoverItems: List<DailyDiscoverItem>?,
    maxWidth: Dp,
    onItemClick: (DailyDiscoverItem) -> Unit,
) {
    if (discoverItems.isNullOrEmpty()) return

    item(key = "daily_discover_title") {
        HomeSectionHeader(
            title = stringResource(R.string.daily_discover),
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "daily_discover_content") {
        DailyDiscoverSection(
            discoverItems = discoverItems,
            maxWidth = maxWidth,
            onItemClick = onItemClick,
            modifier = Modifier.animateItem(),
        )
    }
}
