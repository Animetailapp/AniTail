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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
        prepared.add(min(8, prepared.size), null)
        prepared
    }
    val pages = remember(displayItems) { displayItems.chunked(9) }
    val pagerState = rememberLazyListState()
    val currentPage by remember(pagerState, pages.size) {
        derivedStateOf { pagerState.firstVisibleItemIndex.coerceAtMost((pages.size - 1).coerceAtLeast(0)) }
    }

    // On Desktop, size each 3x3 page nicely (e.g. 440.dp - 520.dp wide)
    val pageWidth = (maxWidth - 48.dp).coerceIn(340.dp, 520.dp)
    val tileSize = (pageWidth - 16.dp) / 3

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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(pageWidth),
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

        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF6A5D82))
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2D8F3)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
        }
    }
}

// ==================== COMMUNITY PLAYLISTS ====================

@Composable
private fun PlaylistCollage(
    thumbnails: List<String?>,
    modifier: Modifier = Modifier,
) {
    val cells = remember(thumbnails) {
        val prepared = thumbnails.filterNotNull().toMutableList()
        if (prepared.isEmpty()) prepared += ""
        while (prepared.size < 4) prepared += prepared.last()
        prepared.take(4)
    }

    Column(
        modifier = modifier
            .size(90.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        repeat(2) { rowIndex ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(2) { columnIndex ->
                    val index = rowIndex * 2 + columnIndex
                    RemoteImage(
                        url = cells[index],
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

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
    val cardWidth = (maxWidth * 0.40f).coerceIn(340.dp, 440.dp)

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
                modifier = Modifier.width(cardWidth),
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: 2x2 Collage + Title + Count
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val collageThumbnails = remember(item.songs, item.playlist.thumbnail) {
                    item.songs.take(4).map { it.thumbnail } + item.playlist.thumbnail
                }
                PlaylistCollage(thumbnails = collageThumbnails)
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.playlist.author?.name ?: item.playlist.songCountText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Top 3 songs preview
            item.songs.take(3).forEach { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    RemoteImage(
                        url = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = stringResource("play_all"),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onRadioClick,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = IconAssets.radio(),
                        contentDescription = stringResource("radio"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = IconAssets.playlistAdd(),
                        contentDescription = stringResource("add_to_playlist"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ==================== DAILY DISCOVER ====================

@Composable
fun DailyDiscoverSection(
    discoverItems: List<DailyDiscoverItem>,
    maxWidth: Dp,
    onItemClick: (DailyDiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (discoverItems.isEmpty()) return

    val preferredCardWidth = (maxWidth * 0.35f).coerceIn(260.dp, 360.dp)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    .width(preferredCardWidth)
                    .height(340.dp),
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
            .clip(RoundedCornerShape(30.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RemoteImage(
                url = item.recommendation.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(30.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                Text(
                    text = item.recommendation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.recommendation.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource("daily_discover_based_on", item.seed.title),
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp),
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
            .height(200.dp),
    ) {
        items(
            items = items,
            key = { "${it.endpoint.browseId}:${it.endpoint.params.orEmpty()}" },
        ) { moodItem ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(180.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onMoodGenreClick(moodItem.endpoint.browseId, moodItem.endpoint.params)
                    },
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = moodItem.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
