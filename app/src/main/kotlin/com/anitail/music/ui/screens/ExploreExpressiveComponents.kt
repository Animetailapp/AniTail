@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.MoodAndGenres
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.ui.menu.YouTubeAlbumMenu
import com.anitail.music.ui.menu.YouTubeArtistMenu
import com.anitail.music.ui.menu.YouTubePlaylistMenu
import com.anitail.music.ui.menu.YouTubeSongMenu
import com.anitail.music.ui.screens.artist.ArtistCarouselCard
import com.anitail.music.ui.screens.artist.ArtistSectionTitle
import com.anitail.music.ui.screens.artist.ArtistSongRow
import com.anitail.music.ui.screens.artist.ArtistVideoShelf
import com.anitail.music.ui.screens.artist.artistArtworkShape
import com.anitail.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExploreChipShelfHeight = 48.dp
private val ExpressiveCardCornerRadius = 16.dp
private val CarouselCardWidth = 152.dp

internal class ExploreItemActions(
    val onItemClick: (YTItem) -> Unit,
    val onItemMenu: (YTItem) -> Unit,
    val onAlbumPlay: (AlbumItem) -> Unit,
)

@Composable
internal fun rememberExploreItemActions(
    navController: NavController,
    currentSongId: String?,
): ExploreItemActions {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    val openItem: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == currentSongId) {
                    playerConnection?.player?.togglePlayPause()
                } else {
                    playerConnection?.playQueue(
                        YouTubeQueue(
                            endpoint = WatchEndpoint(videoId = item.id),
                            preloadItem = item.toMediaMetadata(),
                        ),
                    )
                }
            }
            is EpisodeItem -> {
                val asSong = item.asSongItem()
                if (asSong.id == currentSongId) {
                    playerConnection?.player?.togglePlayPause()
                } else {
                    playerConnection?.playQueue(
                        YouTubeQueue(
                            endpoint = WatchEndpoint(videoId = asSong.id),
                            preloadItem = asSong.toMediaMetadata(),
                        ),
                    )
                }
            }
            is AlbumItem -> navController.navigate("album/${item.browseId}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is PodcastItem -> navController.navigate("online_podcast/${item.id}")
        }
    }

    val openMenu: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (item) {
            is SongItem -> menuState.show {
                YouTubeSongMenu(
                    song = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
            is EpisodeItem -> menuState.show {
                YouTubeSongMenu(
                    song = item.asSongItem(),
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
            is AlbumItem -> menuState.show {
                YouTubeAlbumMenu(
                    albumItem = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
            is ArtistItem -> menuState.show {
                YouTubeArtistMenu(
                    artist = item,
                    onDismiss = menuState::dismiss,
                )
            }
            is PlaylistItem -> menuState.show {
                YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
            is PodcastItem -> navController.navigate("online_podcast/${item.id}")
        }
    }

    val playAlbum: (AlbumItem) -> Unit = { album ->
        coroutineScope.launch(Dispatchers.IO) {
            var albumWithSongs = database.albumWithSongs(album.browseId).first()
            var fetchedSongs: List<SongItem> = emptyList()
            if (albumWithSongs?.songs.isNullOrEmpty()) {
                YouTube.album(album.browseId)
                    .onSuccess { page ->
                        fetchedSongs = page.songs
                        runCatching { database.transaction { insert(page) } }
                        albumWithSongs = database.albumWithSongs(album.browseId).first()
                    }
                    .onFailure { reportException(it) }
            }
            val queue = albumWithSongs?.songs?.takeIf { it.isNotEmpty() }?.let { localSongs ->
                ListQueue(
                    title = album.title,
                    items = localSongs.map { it.toMediaItem() },
                )
            } ?: fetchedSongs.takeIf { it.isNotEmpty() }?.let { remoteSongs ->
                ListQueue(
                    title = album.title,
                    items = remoteSongs.map { it.toMediaMetadata().toMediaItem() },
                )
            }
            if (queue != null && playerConnection != null) {
                withContext(Dispatchers.Main) {
                    playerConnection.playQueue(queue)
                }
            }
        }
    }

    return remember(navController, currentSongId, playerConnection) {
        ExploreItemActions(
            onItemClick = openItem,
            onItemMenu = openMenu,
            onAlbumPlay = playAlbum,
        )
    }
}

@Composable
internal fun ExploreChartSectionShelf(
    section: ChartsPage.ChartSection,
    kind: ChartShelfKind,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    navController: NavController,
    actions: ExploreItemActions,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = if (section.title.equals("Trending", ignoreCase = true)) {
        stringResource(R.string.trending)
    } else {
        section.title
    }
    Column(modifier = modifier.fillMaxWidth()) {
        val moreEndpoint = section.moreEndpoint
        ArtistSectionTitle(
            title = sectionTitle,
            onClick = moreEndpoint?.let { endpoint ->
                {
                    navController.navigate(
                        "youtube_browse/${endpoint.browseId}?params=${endpoint.params}",
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when (kind) {
            ChartShelfKind.SONG_PAGER -> {
                val chartSongs = remember(section.items) {
                    section.items.filterIsInstance<SongItem>()
                }
                ExploreChartSongPager(
                    songs = chartSongs,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onSongClick = actions.onItemClick,
                    onSongMenu = actions.onItemMenu,
                )
            }
            ChartShelfKind.VIDEO_CAROUSEL -> {
                ArtistVideoShelf(
                    items = section.items,
                    onItemClick = actions.onItemClick,
                    onItemLongClick = actions.onItemMenu,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                )
            }
            ChartShelfKind.MULTI_BROWSE_CAROUSEL -> {
                ExploreMultiBrowseShelf(
                    items = section.items,
                    currentSongId = currentSongId,
                    currentAlbumId = currentAlbumId,
                    isPlaying = isPlaying,
                    onItemClick = actions.onItemClick,
                    onItemMenu = actions.onItemMenu,
                    onAlbumPlay = actions.onAlbumPlay,
                )
            }
        }
    }
}

@Composable
internal fun ExploreFilterChipShelf(
    selectedFilter: ExploreFilterChip?,
    onSelectionChange: (ExploreFilterChip?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val labels = mapOf(
        ExploreFilterChip.CHARTS to stringResource(R.string.charts),
        ExploreFilterChip.NEW_RELEASES to stringResource(R.string.new_release_albums),
        ExploreFilterChip.MOODS_AND_GENRES to stringResource(R.string.mood_and_genres),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ExploreChipShelfHeight)
            .clipToBounds()
            .focusGroup()
            .selectableGroup()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreFilterChip.entries.forEach { option ->
            val isSelected = selectedFilter == option
            val text = labels.getValue(option)
            val chipDescription = stringResource(R.string.chip_content_description, text)
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelectionChange(if (isSelected) null else option) },
                shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier
                    .height(ButtonDefaults.ExtraSmallContainerHeight)
                    .clearAndSetSemantics {
                        contentDescription = chipDescription
                        role = Role.Button
                        selected = isSelected
                        onClick {
                            onSelectionChange(if (isSelected) null else option)
                            true
                        }
                    },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ExploreQuickAccessRow(
    onChartsClick: () -> Unit,
    onNewReleasesClick: () -> Unit,
    onMoodsAndGenresClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreQuickAccessCard(
            title = stringResource(R.string.charts),
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
            onClick = onChartsClick,
        )
        ExploreQuickAccessCard(
            title = stringResource(R.string.new_release_albums),
            icon = Icons.Outlined.Album,
            onClick = onNewReleasesClick,
        )
        ExploreQuickAccessCard(
            title = stringResource(R.string.mood_and_genres),
            icon = Icons.Outlined.Category,
            onClick = onMoodsAndGenresClick,
        )
    }
}

@Composable
private fun ExploreQuickAccessCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ExpressiveCardCornerRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ExploreChartSongPager(
    songs: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onSongMenu: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val rowCount = minOf(4, songs.size)
    val columns = remember(songs, rowCount) { songs.chunked(rowCount) }
    val pagerState = rememberPagerState { columns.size }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val pageWidth = ((screenWidthDp.dp - 32.dp) * if (screenWidthDp >= 640) 0.46f else 0.9f)
        .coerceAtLeast(220.dp)

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(pageWidth),
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) { pageIndex ->
        val columnSongs = columns[pageIndex]
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            columnSongs.forEachIndexed { rowIndex, song ->
                val rank = song.chartPosition ?: (pageIndex * rowCount + rowIndex + 1)
                val isCurrent = song.id == currentSongId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(26.dp),
                    )
                    ArtistSongRow(
                        item = song,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongMenu(song) },
                        onShowMenu = { onSongMenu(song) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExploreMultiBrowseShelf(
    items: List<YTItem>,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    onAlbumPlay: ((AlbumItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val carouselState = rememberCarouselState { items.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        flingBehavior = flingBehavior,
        preferredItemWidth = CarouselCardWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { index ->
        val item = items[index]
        val maskShape = when (item) {
            is ArtistItem -> artistArtworkShape(item.id)
            is PodcastItem -> CircleShape
            else -> RoundedCornerShape(12.dp)
        }
        val isCurrent = when (item) {
            is SongItem -> item.id == currentSongId
            is AlbumItem -> item.id == currentAlbumId || item.browseId == currentAlbumId
            else -> false
        }
        ArtistCarouselCard(
            item = item,
            onClick = { onItemClick(item) },
            onLongClick = { onItemMenu(item) },
            imageSize = CarouselCardWidth,
            artworkAspectRatio = 1f,
            artworkModifier = Modifier.maskClip(maskShape),
            carouselItemDrawInfo = carouselItemDrawInfo,
            isCurrent = isCurrent,
            isPlaying = isPlaying && isCurrent,
            onAlbumPlay = if (item is AlbumItem && onAlbumPlay != null) {
                { onAlbumPlay(item) }
            } else {
                null
            },
        )
    }
}

@Composable
internal fun ExpressiveMoodGenreCard(
    title: String,
    stripeColor: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = stripeColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ExpressiveCardCornerRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp,
        modifier = modifier.height(56.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun ExploreMoodGenreCarousel(
    items: List<MoodAndGenres.Item>,
    onItemClick: (MoodAndGenres.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val rowCount = minOf(3, items.size)
    val itemHeight = 56.dp
    val spacing = 10.dp
    val gridHeight = itemHeight * rowCount + spacing * (rowCount - 1)

    LazyHorizontalGrid(
        rows = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight),
    ) {
        items(items) { item ->
            ExpressiveMoodGenreCard(
                title = item.title,
                stripeColor = item.stripeColor,
                onClick = { onItemClick(item) },
                modifier = Modifier.width(176.dp),
            )
        }
    }
}

@Composable
internal fun ExploreExpressiveShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(110.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(ExpressiveCardCornerRadius))
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
            Box(
                Modifier
                    .width(200.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .width(180.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
            }
        }
    }
}
