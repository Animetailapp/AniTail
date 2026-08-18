package com.anitail.desktop.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    // Calculate dynamic columns & rows to fill wide desktop screens
    val columns = when {
        maxWidth >= 1350.dp -> 6
        maxWidth >= 1080.dp -> 5
        maxWidth >= 750.dp -> 4
        else -> 3
    }
    val rows = 3
    val pageSize = columns * rows

    val displayItems = remember(items, pageSize) {
        val prepared = items.map { it as YTItem? }.toMutableList()
        val insertPos = min(pageSize - 1, prepared.size)
        prepared.add(insertPos, null) // Dice lucky tile
        prepared
    }
    val pages = remember(displayItems, pageSize) { displayItems.chunked(pageSize) }
    var currentPage by remember { mutableStateOf(0) }
    val activePageIndex = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    val spacing = 10.dp
    val maxAvailableWidth = (maxWidth - 120.dp).coerceAtLeast(320.dp)
    val tileSize = ((maxAvailableWidth - (spacing * (columns - 1))) / columns).coerceIn(110.dp, 160.dp)
    val gridWidth = (tileSize * columns) + (spacing * (columns - 1))

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                if (pages.size > 1) {
                    IconButton(
                        onClick = { if (activePageIndex > 0) currentPage-- },
                        enabled = activePageIndex > 0,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (activePageIndex > 0) 0.85f else 0.3f)),
                    ) {
                        Icon(
                            imageVector = IconAssets.arrowBack(),
                            contentDescription = "Anterior",
                            tint = if (activePageIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedContent(
                    targetState = activePageIndex,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SpeedDialPageAnimation"
                ) { pageIdx ->
                    val pageItems = pages.getOrElse(pageIdx) { emptyList() }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.width(gridWidth),
                    ) {
                        repeat(rows) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                repeat(columns) { columnIndex ->
                                    val tileIndex = rowIndex * columns + columnIndex
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

                if (pages.size > 1) {
                    IconButton(
                        onClick = { if (activePageIndex < pages.size - 1) currentPage++ },
                        enabled = activePageIndex < pages.size - 1,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (activePageIndex < pages.size - 1) 0.85f else 0.3f)),
                    ) {
                        Icon(
                            imageVector = IconAssets.arrowForward(),
                            contentDescription = "Siguiente",
                            tint = if (activePageIndex < pages.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (pages.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == activePageIndex) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == activePageIndex)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable { currentPage = index }
                        )
                    }
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
                            Color.Black.copy(alpha = 0.76f),
                        ),
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 13.sp,
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF6A5D82))
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2D8F3)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
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
            .size(86.dp)
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
    val cardWidth = (maxWidth * 0.32f).coerceIn(320.dp, 440.dp)

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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: 2x2 Collage + Title + Count
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // Top 3 songs preview
            item.songs.take(3).forEach { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    RemoteImage(
                        url = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = stringResource("play_all"),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onRadioClick,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = IconAssets.radio(),
                        contentDescription = stringResource("radio"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = IconAssets.playlistAdd(),
                        contentDescription = stringResource("add_to_playlist"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
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

    val preferredCardWidth = (maxWidth * 0.28f).coerceIn(240.dp, 360.dp)

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
                    .width(preferredCardWidth)
                    .height(320.dp),
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
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RemoteImage(
                url = item.recommendation.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(26.dp),
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        items(
            items = items,
            key = { "${it.endpoint.browseId}:${it.endpoint.params.orEmpty()}" },
        ) { moodItem ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(190.dp)
                    .height(46.dp)
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
