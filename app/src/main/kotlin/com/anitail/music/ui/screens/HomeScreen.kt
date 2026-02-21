package com.anitail.music.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
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
import com.anitail.music.constants.InnerTubeCookieKey
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
import com.anitail.music.utils.rememberPreference
import com.anitail.music.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

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
    val quickPicksFirstId = quickPicks.firstOrNull()?.id
    val forgottenFavoritesFirstId = forgottenFavorites.firstOrNull()?.id

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
                if (lastVisibleIndex != null && len > 0 && lastVisibleIndex >= len - 3) {
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

    val playOrToggleSong: (Song) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.player.togglePlayPause()
        } else {
            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        }
    }

    val showSongMenu: (Song, Boolean) -> Unit = { song, withHaptic ->
        if (withHaptic) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvCombinedClickable(
                        onClick = { playOrToggleSong(it) },
                        onLongClick = { showSongMenu(it, true) },
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

    LaunchedEffect(quickPicksFirstId) {
        if (quickPicksFirstId != null && quickPicksLazyGridState.firstVisibleItemIndex > 0) {
            quickPicksLazyGridState.scrollToItem(0)
        }
    }

    LaunchedEffect(forgottenFavoritesFirstId) {
        if (
            forgottenFavoritesFirstId != null &&
            forgottenFavoritesLazyGridState.firstVisibleItemIndex > 0
        ) {
            forgottenFavoritesLazyGridState.scrollToItem(0)
        }
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
        val quickPicksSnapLayoutInfoProvider = remember(
            quickPicksLazyGridState,
            horizontalLazyGridItemWidthFactor,
        ) {
            SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider = remember(
            forgottenFavoritesLazyGridState,
            horizontalLazyGridItemWidthFactor,
        ) {
            SnapLayoutInfoProvider(
                lazyGridState = forgottenFavoritesLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val quickPicksFlingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider)
        val forgottenFavoritesFlingBehavior =
            rememberSnapFlingBehavior(forgottenFavoritesSnapLayoutInfoProvider)

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
                            items(
                                count = 5,
                            ) {
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
            SongsGridBlock(
                sectionKey = "quick_picks",
                titleRes = R.string.quick_picks,
                songs = quickPicks,
                rows = 4,
                lazyGridState = quickPicksLazyGridState,
                flingBehavior = quickPicksFlingBehavior,
                itemWidth = horizontalLazyGridItemWidth,
                mediaMetadataId = mediaMetadata?.id,
                isPlaying = isPlaying,
                onSongClick = playOrToggleSong,
                onSongLongClick = { song -> showSongMenu(song, true) },
                onSongMenuClick = { song -> showSongMenu(song, false) },
            )

            KeepListeningBlock(
                keepListening = keepListening,
                keepListeningLazyGridState = keepListeningLazyGridState,
                localGridItem = localGridItem,
            )

            AccountPlaylistsBlock(
                accountPlaylists = accountPlaylists,
                accountName = accountName,
                accountImageUrl = url,
                onAccountClick = { navController.navigate("account") },
                ytGridItem = ytGridItem,
            )

            SongsGridBlock(
                sectionKey = "forgotten_favorites",
                titleRes = R.string.forgotten_favorites,
                songs = forgottenFavorites,
                rows = min(4, forgottenFavorites.size).coerceAtLeast(1),
                lazyGridState = forgottenFavoritesLazyGridState,
                flingBehavior = forgottenFavoritesFlingBehavior,
                itemWidth = horizontalLazyGridItemWidth,
                mediaMetadataId = mediaMetadata?.id,
                isPlaying = isPlaying,
                onSongClick = playOrToggleSong,
                onSongLongClick = { song -> showSongMenu(song, true) },
                onSongMenuClick = { song -> showSongMenu(song, true) },
            )

            SimilarRecommendationsBlock(
                similarRecommendations = similarRecommendations,
                onTitleClick = { localItem ->
                    when (localItem) {
                        is Song -> navController.navigate("album/${localItem.album!!.id}")
                        is Album -> navController.navigate("album/${localItem.id}")
                        is Artist -> navController.navigate("artist/${localItem.id}")
                        is Playlist -> {}
                    }
                },
                ytGridItem = ytGridItem,
            )

            SpeedDialBlock(
                speedDialItems = speedDialItems,
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
                    val luckyItem = speedDialItems.randomOrNull()
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
            )

            CommunityPlaylistsBlock(
                playlists = communityPlaylists,
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
            )

            DailyDiscoverBlock(
                discoverItems = dailyDiscover,
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
            )

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
                    YtItemsRowSection(
                        ytItems = section.items,
                        itemContent = ytGridItem,
                        modifier = Modifier.animateItem(),
                    )
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
                                items(
                                    count = 4,
                                ) {
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
