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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.model.CommunityPlaylistItem
import com.anitail.desktop.model.DailyDiscoverItem
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle
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
        prepared.add(min(8, prepared.size), null) // Insert lucky tile at slot 8
        prepared
    }
    val pages = remember(displayItems) { displayItems.chunked(9) }
    val pagerState = rememberLazyListState()
    val currentPage by remember(pagerState, pages.size) {
        derivedStateOf { pagerState.firstVisibleItemIndex.coerceAtMost((pages.size - 1).coerceAtLeast(0)) }
    }
    val pageWidth = (maxWidth - 32.dp).coerceAtLeast(300.dp)
    val tileSize = ((pageWidth - 16.dp) / 3).coerceAtLeast(80.dp)

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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF5E4B8B))
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp),
        ) {
            Icon(
                imageVector = IconAssets.shuffle(),
                contentDescription = "Sorpréndeme",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "¡Sorpréndeme!",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ==================== COMMUNITY PLAYLISTS ====================

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
    val cardWidth = (maxWidth * 0.45f).coerceIn(320.dp, 440.dp)

    LazyRow(
        state = rowState,
        flingBehavior = rememberSnapFlingBehavior(rowState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Cover + Title + Count
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    url = item.playlist.thumbnail,
                    contentDescription = item.playlist.title,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.playlist.author?.name ?: item.playlist.songCountText ?: "Playlist comunitaria",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSongClick(song) }
                        .padding(4.dp),
                ) {
                    RemoteImage(
                        url = song.thumbnail,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = "Reproducir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = "Reproducir todo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onRadioClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.radio(),
                        contentDescription = "Radio",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = IconAssets.playlistAdd(),
                        contentDescription = "Añadir a biblioteca",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
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

    val cardWidth = (maxWidth * 0.32f).coerceIn(240.dp, 340.dp)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    .width(cardWidth)
                    .height(260.dp),
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
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RemoteImage(
                url = item.recommendation.thumbnail,
                contentDescription = item.recommendation.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                ) {
                    Text(
                        text = "Descubrimiento",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.recommendation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.recommendation.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Basado en: ${item.seed.title}",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                    .height(42.dp)
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
