package com.anitail.desktop.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.model.CommunityPlaylistItem
import com.anitail.desktop.model.DailyDiscoverItem
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ExplorePage
import kotlin.math.min

// ==================== SPEED DIAL ====================

private val SpeedDialTileSize = 116.dp
private val SpeedDialSpacing = 8.dp
private val SpeedDialPageWidth = (SpeedDialTileSize * 3) + (SpeedDialSpacing * 2)

@Composable
fun SpeedDialSection(
    items: List<YTItem>,
    maxWidth: Dp,
    onItemClick: (YTItem) -> Unit,
    onSurpriseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val displayItems = remember(items) {
        val prepared = items.map { it as YTItem? }.toMutableList()
        prepared.add(min(8, prepared.size), null) // Insert lucky tile at slot 8
        prepared
    }
    val pages = remember(displayItems) { displayItems.chunked(9) }
    val pagerState = rememberLazyListState()
    val currentPage by remember(pagerState, pages.size) {
        derivedStateOf { pagerState.firstVisibleItemIndex.coerceAtMost((pages.size - 1).coerceAtLeast(0)) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = pagerState,
            flingBehavior = rememberSnapFlingBehavior(pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                count = pages.size,
                key = { "speed_dial_page_$it" },
            ) { pageIndex ->
                val pageItems = pages[pageIndex]
                Column(
                    verticalArrangement = Arrangement.spacedBy(SpeedDialSpacing),
                    modifier = Modifier.width(SpeedDialPageWidth),
                ) {
                    repeat(3) { rowIndex ->
                        Row(horizontalArrangement = Arrangement.spacedBy(SpeedDialSpacing)) {
                            repeat(3) { columnIndex ->
                                val tileIndex = rowIndex * 3 + columnIndex
                                if (tileIndex >= pageItems.size) {
                                    Spacer(modifier = Modifier.size(SpeedDialTileSize))
                                } else {
                                    val pageItem = pageItems[tileIndex]
                                    if (pageItem == null) {
                                        SpeedDialSurpriseTile(
                                            onClick = onSurpriseClick,
                                            modifier = Modifier.size(SpeedDialTileSize),
                                        )
                                    } else {
                                        SpeedDialTile(
                                            item = pageItem,
                                            onClick = { onItemClick(pageItem) },
                                            modifier = Modifier.size(SpeedDialTileSize),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SpeedDialSurpriseTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp),
        ) {
            Icon(
                imageVector = IconAssets.shuffle(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource("shuffle"),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ==================== COMMUNITY PLAYLISTS ====================

private val CommunityCardWidth = 320.dp

@Composable
fun CommunityPlaylistsSection(
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

    LazyRow(
        state = rowState,
        flingBehavior = rememberSnapFlingBehavior(rowState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier.width(CommunityCardWidth),
            )
        }
    }
}

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onOpenPlaylist: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: () -> Unit,
    onRadioClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onOpenPlaylist),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Cover + Title + Count
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    url = item.playlist.thumbnail,
                    contentDescription = item.playlist.title,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.playlist.author?.name ?: item.playlist.songCountText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Top 3 songs preview
            item.songs.take(3).forEach { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    RemoteImage(
                        url = song.thumbnail,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = stringResource("play"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = stringResource("play_all"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onRadioClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.radio(),
                        contentDescription = stringResource("radio"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.playlistAdd(),
                        contentDescription = stringResource("add_to_playlist"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==================== DAILY DISCOVER ====================

private val DailyDiscoverCardWidth = 200.dp
private val DailyDiscoverCardHeight = 240.dp

@Composable
fun DailyDiscoverSection(
    discoverItems: List<DailyDiscoverItem>,
    maxWidth: Dp,
    onItemClick: (DailyDiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (discoverItems.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = discoverItems,
            key = { it.recommendation.id },
        ) { item ->
            DailyDiscoverCard(
                item = item,
                onClick = { onItemClick(item) },
                modifier = Modifier
                    .width(DailyDiscoverCardWidth)
                    .height(DailyDiscoverCardHeight),
            )
        }
    }
}

@Composable
fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RemoteImage(
                url = item.recommendation.thumbnail,
                contentDescription = item.recommendation.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                ) {
                    Text(
                        text = stringResource("daily_discover"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.recommendation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.recommendation.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource("daily_discover_based_on", item.seed.title),
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ==================== MOOD AND GENRES ====================

@Composable
fun MoodAndGenresGrid(
    explorePage: ExplorePage?,
    onMoodGenreClick: (browseId: String, params: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = explorePage?.moodAndGenres ?: return
    if (items.isEmpty()) return

    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        items(
            items = items,
            key = { "${it.endpoint.browseId}:${it.endpoint.params.orEmpty()}" },
        ) { moodItem ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(160.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onMoodGenreClick(moodItem.endpoint.browseId, moodItem.endpoint.params)
                    },
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = moodItem.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
