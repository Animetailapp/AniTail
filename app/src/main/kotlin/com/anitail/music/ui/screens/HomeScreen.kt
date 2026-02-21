package com.anitail.music.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.utils.parseCookieString
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.GridThumbnailHeight
import com.anitail.music.constants.InnerTubeCookieKey
import com.anitail.music.constants.ListItemHeight
import com.anitail.music.constants.ListThumbnailSize
import com.anitail.music.constants.ThumbnailCornerRadius
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.LocalAlbumRadio
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.YouTubeAlbumRadio
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.AlbumGridItem
import com.anitail.music.ui.component.ArtistGridItem
import com.anitail.music.ui.component.ChipsRow
import com.anitail.music.ui.component.HideOnScrollFAB
import com.anitail.music.ui.component.LocalBottomSheetPageState
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.NavigationTitle
import com.anitail.music.ui.component.SongGridItem
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.component.YouTubeGridItem
import com.anitail.music.ui.component.shimmer.GridItemPlaceHolder
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.ui.component.shimmer.TextPlaceholder
import com.anitail.music.ui.menu.AlbumMenu
import com.anitail.music.ui.menu.ArtistMenu
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.ui.menu.YouTubeAlbumMenu
import com.anitail.music.ui.menu.YouTubeArtistMenu
import com.anitail.music.ui.menu.YouTubePlaylistMenu
import com.anitail.music.ui.menu.YouTubeSongMenu
import com.anitail.music.ui.utils.SnapLayoutInfoProvider
import com.anitail.music.ui.utils.tvCombinedClickable
import com.anitail.music.ui.utils.isScrollingUp
import com.anitail.music.ui.utils.resize
import com.anitail.music.utils.rememberPreference
import com.anitail.music.viewmodels.CommunityPlaylistItem
import com.anitail.music.viewmodels.DailyDiscoverItem
import com.anitail.music.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

private fun highResThumbnail(
    url: String?,
    width: Int,
    height: Int,
): String? {
    if (url == null) return null
    val resized = url.replace(Regex("=w\\d+-h\\d+"), "=w$width-h$height")
    return resized.resize(width, height)
}

private fun ytItemStableKey(item: YTItem): String {
    return when (item) {
        is SongItem -> "song:${item.id}"
        is AlbumItem -> "album:${item.id}"
        is ArtistItem -> "artist:${item.id}"
        is PlaylistItem -> "playlist:${item.id}"
    }
}

private fun localItemStableKey(item: LocalItem): String {
    return when (item) {
        is Song -> "song:${item.id}"
        is Album -> "album:${item.id}"
        is Artist -> "artist:${item.id}"
        is Playlist -> "playlist:${item.id}"
    }
}

@Composable
private fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 11.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick),
    ) {
        AsyncImage(
            model = highResThumbnail(item.thumbnail, 544, 544) ?: item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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

@OptIn(ExperimentalFoundationApi::class)
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
            .combinedClickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2D8F3)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
        }
    }
}

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
                    AsyncImage(
                        model = highResThumbnail(cells[index], 544, 544) ?: cells[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityActionButton(
    iconRes: Int,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
            )
            .combinedClickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (emphasized) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onOpenPlaylist: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: () -> Unit,
    onRadioClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .combinedClickable(onClick = onOpenPlaylist),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistCollage(
                    thumbnails = item.songs.take(4).map { it.thumbnail } + item.playlist.thumbnail,
                )
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
                        text = item.playlist.songCountText ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            item.songs.take(3).forEach { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { onSongClick(song) }),
                ) {
                    AsyncImage(
                        model = highResThumbnail(song.thumbnail, 544, 544) ?: song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CommunityActionButton(
                    iconRes = R.drawable.play,
                    emphasized = true,
                    onClick = onPlayAllClick,
                )
                CommunityActionButton(
                    iconRes = R.drawable.radio,
                    onClick = onRadioClick,
                )
                CommunityActionButton(
                    iconRes = R.drawable.playlist_add,
                    onClick = onAddClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = highResThumbnail(item.recommendation.thumbnail, 1200, 1200)
                    ?: item.recommendation.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
                    text = stringResource(R.string.daily_discover_based_on, item.seed.title),
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SpeedDialSection(
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

    Column(modifier = modifier) {
        LazyRow(
            state = pagerState,
            flingBehavior = rememberSnapFlingBehavior(pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pages.size, key = { "speed_dial_page_$it" }) { pageIndex ->
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

        Spacer(modifier = Modifier.height(12.dp))

        PagerDots(
            pageCount = pages.size,
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun CommunityPlaylistsSection(
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
private fun DailyDiscoverSection(
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

    Column(modifier = modifier) {
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

@Composable
private fun SyncStatusSnackbar(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    if (syncStatus.isNullOrBlank()) return

    Snackbar(
        modifier = modifier
            .padding(16.dp)
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(0.15f)
                        .padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.check_circle),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(
                text = syncStatus.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val contentUiState by viewModel.contentUiState.collectAsStateWithLifecycle()

    val quickPicks = contentUiState.quickPicks
    val forgottenFavorites = contentUiState.forgottenFavorites
    val keepListening = contentUiState.keepListening
    val similarRecommendations = contentUiState.similarRecommendations
    val accountPlaylists = contentUiState.accountPlaylists
    val dailyDiscover = contentUiState.dailyDiscover
    val communityPlaylists = contentUiState.communityPlaylists
    val speedDialItems = contentUiState.speedDialItems
    val homePage = contentUiState.homePage
    val explorePage = contentUiState.explorePage

    val allLocalItems = contentUiState.allLocalItems
    val allYtItems = contentUiState.allYtItems
    val selectedChip = contentUiState.selectedChip

    val isLoading = contentUiState.isLoading
    val isMoodAndGenresLoading = isLoading && explorePage?.moodAndGenres == null
    val isRefreshing = contentUiState.isRefreshing
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()
    val keepListeningLazyGridState = rememberLazyGridState()

    val accountName = contentUiState.accountName
    val accountImageUrl = contentUiState.accountImageUrl
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTopState =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()
    val scrollToTop = scrollToTopState?.value == true
    val latestContinuation by rememberUpdatedState(homePage?.continuation)

    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged() // Evitar emitir el mismo valor repetidamente
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(latestContinuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvCombinedClickable(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvCombinedClickable(
                        onClick = {
                            navController.navigate("album/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvCombinedClickable(
                        onClick = {
                            navController.navigate("artist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> {}
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id == mediaMetadata?.id || item.id == mediaMetadata?.album?.id,
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .tvCombinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(
                                        videoId = item.id
                                    ), item.toMediaMetadata()
                                )
                            )

                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is AlbumItem -> YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is ArtistItem -> YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )

                                is PlaylistItem -> YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                )
        )
    }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val chips = remember(homePage?.chips) {
            homePage?.chips?.map { it to it.title }.orEmpty()
        }
        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
        val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
            SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
            SnapLayoutInfoProvider(
                lazyGridState = forgottenFavoritesLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }

        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item {
                ChipsRow(
                    chips = chips,
                    currentValue = selectedChip,
                    onValueUpdate = {
                        viewModel.toggleChip(it)
                    }
                )
            }
            if (isLoading && homePage?.chips.isNullOrEmpty()) {
                item(key = "chips_shimmer") {
                    ShimmerHost {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(5) {
                                TextPlaceholder(
                                    height = 30.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.width(72.dp),
                                )
                            }
                        }
                    }
                }
            }
            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                item {
                    NavigationTitle(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4)
                            .animateItem()
                    ) {
                        items(
                            items = quickPicks,
                            key = { it.id }
                        ) { originalSong ->
                            // fetch song from database to keep updated
                            val song by database.song(originalSong.id)
                                .collectAsStateWithLifecycle(initialValue = originalSong)

                            SongListItem(
                                song = song!!,
                                showInLibraryIcon = true,
                                isActive = song!!.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song!!,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .tvCombinedClickable(
                                        onClick = {
                                            if (song!!.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song!!,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                item {
                    NavigationTitle(
                        title = stringResource(R.string.keep_listening),
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    val rows = if (keepListening.size > 6) 2 else 1
                    LazyHorizontalGrid(
                        state = keepListeningLazyGridState,
                        rows = GridCells.Fixed(rows),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((GridThumbnailHeight + with(LocalDensity.current) {
                                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                        MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                            }) * rows)
                            .animateItem()
                    ) {
                        items(
                            items = keepListening,
                            key = { localItemStableKey(it) },
                        ) {
                            localGridItem(it)
                        }
                    }
                }
            }

            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
                item {
                    NavigationTitle(
                        label = stringResource(R.string.your_ytb_playlists),
                        title = accountName,
                        thumbnail = {
                            if (url != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .diskCacheKey(url)
                                        .crossfade(false) // Remover crossfade para mejor rendimiento
                                        .build(),
                                    placeholder = painterResource(id = R.drawable.person),
                                    error = painterResource(id = R.drawable.person),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.person),
                                    contentDescription = null,
                                    modifier = Modifier.size(ListThumbnailSize)
                                )
                            }
                        },
                        onClick = {
                            navController.navigate("account")
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = accountPlaylists,
                            key = { it.id },
                        ) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                item {
                    NavigationTitle(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    // take min in case list size is less than 4
                    val rows = min(4, forgottenFavorites.size)
                    LazyHorizontalGrid(
                        state = forgottenFavoritesLazyGridState,
                        rows = GridCells.Fixed(rows),
                        flingBehavior = rememberSnapFlingBehavior(
                            forgottenFavoritesSnapLayoutInfoProvider
                        ),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * rows)
                            .animateItem()
                    ) {
                        items(
                            items = forgottenFavorites,
                            key = { it.id }
                        ) { originalSong ->
                            val song by database.song(originalSong.id)
                                .collectAsStateWithLifecycle(initialValue = originalSong)

                            SongListItem(
                                song = song!!,
                                showInLibraryIcon = true,
                                isActive = song!!.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song!!,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .tvCombinedClickable(
                                        onClick = {
                                            if (song!!.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song!!,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            similarRecommendations?.forEachIndexed { index, recommendation ->
                val recommendationKey = "${recommendation.title.id}_$index"
                item(key = "similar_title_$recommendationKey") {
                    NavigationTitle(
                        label = stringResource(R.string.similar_to),
                        title = recommendation.title.title,
                        thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
                                        ThumbnailCornerRadius
                                    )
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .clip(shape)
                                )
                            }
                        },
                        onClick = {
                            when (recommendation.title) {
                                is Song -> navController.navigate("album/${recommendation.title.album!!.id}")
                                is Album -> navController.navigate("album/${recommendation.title.id}")
                                is Artist -> navController.navigate("artist/${recommendation.title.id}")
                                is Playlist -> {}
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "similar_items_$recommendationKey") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = recommendation.items,
                            key = { ytItemStableKey(it) },
                        ) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            speedDialItems.takeIf { it.isNotEmpty() }?.let { speedDialList ->
                item(key = "speed_dial_title") {
                    HomeSectionHeader(
                        title = stringResource(R.string.speed_dial),
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "speed_dial_content") {
                    SpeedDialSection(
                        items = speedDialList,
                        maxWidth = maxWidth,
                        onItemClick = { item ->
                            when (item) {
                                is SongItem -> playerConnection.playQueue(
                                    YouTubeQueue(
                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                        item.toMediaMetadata(),
                                    ),
                                )

                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id.removePrefix("VL")}")
                            }
                        },
                        onSurpriseClick = {
                            val luckyItem = speedDialList.randomOrNull()
                            if (luckyItem != null) {
                                when (luckyItem) {
                                    is SongItem -> playerConnection.playQueue(
                                        YouTubeQueue(
                                            luckyItem.endpoint ?: WatchEndpoint(videoId = luckyItem.id),
                                            luckyItem.toMediaMetadata(),
                                        ),
                                    )

                                    is AlbumItem -> navController.navigate("album/${luckyItem.id}")
                                    is ArtistItem -> navController.navigate("artist/${luckyItem.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${luckyItem.id.removePrefix("VL")}")
                                }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
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
                        onOpenPlaylist = { playlistItem ->
                            navController.navigate("online_playlist/${playlistItem.playlist.id.removePrefix("VL")}")
                        },
                        onSongClick = { song ->
                            playerConnection.playQueue(
                                YouTubeQueue(
                                    song.endpoint ?: WatchEndpoint(videoId = song.id),
                                    song.toMediaMetadata(),
                                ),
                            )
                        },
                        onPlayAllClick = { playlistItem ->
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlistItem.playlist.title,
                                    items = playlistItem.songs.map { it.toMediaItem() },
                                ),
                            )
                        },
                        onRadioClick = { playlistItem ->
                            val radioEndpoint = playlistItem.playlist.radioEndpoint
                            if (radioEndpoint != null) {
                                playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                            } else {
                                navController.navigate("online_playlist/${playlistItem.playlist.id.removePrefix("VL")}")
                            }
                        },
                        onAddClick = { playlistItem ->
                            navController.navigate("online_playlist/${playlistItem.playlist.id.removePrefix("VL")}")
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
                item(key = "daily_discover_title") {
                    HomeSectionHeader(
                        title = stringResource(R.string.daily_discover),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "daily_discover_content") {
                    DailyDiscoverSection(
                        discoverItems = discoverList,
                        maxWidth = maxWidth,
                        onItemClick = { discoverItem ->
                            playerConnection.playQueue(
                                YouTubeQueue(
                                    discoverItem.recommendation.endpoint
                                        ?: WatchEndpoint(videoId = discoverItem.recommendation.id),
                                    discoverItem.recommendation.toMediaMetadata(),
                                ),
                            )
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            explorePage?.moodAndGenres?.let { moodAndGenres ->
                item(key = "mood_and_genres_title") {
                    NavigationTitle(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = {
                            navController.navigate("mood_and_genres")
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "mood_and_genres_list") {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier
                            .height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp)
                            .animateItem(),
                    ) {
                        items(
                            items = moodAndGenres,
                            key = { "${it.endpoint.browseId}:${it.endpoint.params.orEmpty()}" },
                        ) {
                            MoodAndGenresButton(
                                title = it.title,
                                onClick = {
                                    navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                },
                                modifier = Modifier
                                    .padding(6.dp)
                                    .width(180.dp),
                            )
                        }
                    }
                }
            }

            if (isMoodAndGenresLoading) {
                item(key = "mood_and_genres_shimmer") {
                    ShimmerHost(
                        modifier = Modifier.animateItem(),
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                        )

                        repeat(4) {
                            Row {
                                repeat(2) {
                                    TextPlaceholder(
                                        height = MoodAndGenresButtonHeight,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .width(200.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            homePage?.sections?.forEachIndexed { index, section ->
                val sectionSongs = section.items.filterIsInstance<SongItem>()
                val hasPlayableSongs = sectionSongs.isNotEmpty()
                val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"

                item(key = "home_section_title_$sectionKey") {
                    NavigationTitle(
                        title = section.title,
                        label = section.label,
                        thumbnail = section.thumbnail?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (section.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                        ThumbnailCornerRadius
                                    )
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .clip(shape)
                                )
                            }
                        },
                        onClick = section.endpoint?.browseId?.let { browseId ->
                            if (homePage != null) {
                                {
                                    when (browseId) {
                                        "FEmusic_moods_and_genres" -> navController.navigate("mood_and_genres")
                                        "FEmusic_charts" -> navController.navigate("charts_screen")
                                        else -> navController.navigate("browse/$browseId")
                                    }
                                }
                            } else {
                                null
                            }
                        },
                        onPlayAllClick = if (hasPlayableSongs) {
                            {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = section.title,
                                        items = sectionSongs.map { it.toMediaItem() }
                                    )
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "home_section_items_$sectionKey") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = section.items,
                            key = { ytItemStableKey(it) },
                        ) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            if (isLoading || homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true) {
                item(key = "loading_shimmer") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        repeat(3) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .width(250.dp),
                            )
                            LazyRow(
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                            ) {
                                items(4) {
                                    GridItemPlaceHolder()
                                }
                            }
                        }
                    }
                }
            }
        }

        HideOnScrollFAB(
            visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
            lazyListState = lazylistState,
            icon = R.drawable.shuffle,
            onClick = {
                val local = when {
                    allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                    allLocalItems.isNotEmpty() -> true
                    else -> false
                }
                scope.launch(Dispatchers.Main) {
                    if (local) {
                        when (val luckyItem = allLocalItems.random()) {
                            is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                            is Album -> {
                                val albumWithSongs = withContext(Dispatchers.IO) {
                                    database.albumWithSongs(luckyItem.id).first()
                                }
                                albumWithSongs?.let {
                                    playerConnection.playQueue(LocalAlbumRadio(it))
                                }
                            }
                            is Artist -> {}
                            is Playlist -> {}
                        }
                    } else {
                        when (val luckyItem = allYtItems.random()) {
                            is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                            is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                            is ArtistItem -> luckyItem.radioEndpoint?.let {
                                playerConnection.playQueue(YouTubeQueue(it))
                            }
                            is PlaylistItem -> luckyItem.playEndpoint?.let {
                                playerConnection.playQueue(YouTubeQueue(it))
                            }
                        }
                    }
                }
            }
        )

        // Recognition Floating Action Button (above the shuffle FAB)
        AnimatedVisibility(
            visible = (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty()) && lazylistState.isScrollingUp(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
        ) {
            FloatingActionButton(
                modifier = Modifier.padding(16.dp).padding(bottom = 72.dp),
                onClick = { navController.navigate("recognition") }
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                )
            }
        }

        Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )

        SyncStatusSnackbar(
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
