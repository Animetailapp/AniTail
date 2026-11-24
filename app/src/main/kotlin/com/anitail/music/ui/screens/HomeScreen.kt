@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.anitail.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.utils.parseCookieString
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.InnerTubeCookieKey
import com.anitail.music.constants.SwipeToSongKey
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.LocalAlbumRadio
import com.anitail.music.playback.queues.YouTubeAlbumRadio
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.ui.menu.AlbumMenu
import com.anitail.music.ui.menu.ArtistMenu
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.ui.screens.artist.rememberArtistReducedMotion
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
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberArtistReducedMotion()

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val contentUiState by viewModel.contentUiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

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
    val isRefreshing = contentUiState.isRefreshing
    val accountName = contentUiState.accountName
    val accountAvatarUrl = if (isLoggedIn) contentUiState.accountImageUrl else null

    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTopState =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()
    val scrollToTop = scrollToTopState?.value == true
    val latestContinuation by rememberUpdatedState(homePage?.continuation)

    val syncCompletedMessage = stringResource(R.string.sync_completed)
    val syncInitialUploadMessage = stringResource(R.string.sync_initial_uploaded)
    var syncSucceeded by remember { mutableStateOf(false) }
    LaunchedEffect(syncStatus) {
        if (syncStatus != null) {
            syncSucceeded = syncStatus == syncCompletedMessage || syncStatus == syncInitialUploadMessage
        }
    }
    val hasAnyItems = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty()
    val showSyncIndicator = (isSyncing || syncStatus != null) && hasAnyItems

    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && len > 0 && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(latestContinuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            viewModel.toggleChip(selectedChip)
        }
    }

    val latestCurrentSongId by rememberUpdatedState(mediaMetadata?.id)
    val playOrToggleSong: (Song) -> Unit = remember(playerConnection) {
        { song ->
            if (song.id == latestCurrentSongId) {
                playerConnection.player.togglePlayPause()
            } else {
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
            }
        }
    }

    val showSongMenu: (Song, Boolean) -> Unit = remember(haptic, menuState, navController) {
        { song, withHaptic ->
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
    }

    val itemActions = rememberExploreItemActions(
        navController = navController,
        currentSongId = mediaMetadata?.id,
    )

    val quickPicksTitle = stringResource(R.string.quick_picks)
    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxWidth = screenWidthDp.dp
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = insets.calculateTopPadding()),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazylistState,
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 4.dp,
                bottom = insets.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val chips = homePage?.chips.orEmpty()
            if (chips.isNotEmpty()) {
                item(key = "home_filter_chips", contentType = "home_chips") {
                    HomeFilterChipShelf(
                        chips = chips,
                        selectedChip = selectedChip,
                        onToggleChip = viewModel::toggleChip,
                    )
                }
            }

            SongsGridBlock(
                sectionKey = "quick_picks",
                titleRes = R.string.quick_picks,
                songs = quickPicks,
                rows = 4,
                screenWidthDp = screenWidthDp,
                mediaMetadataId = mediaMetadata?.id,
                isPlaying = isPlaying,
                swipeEnabled = swipeToSongEnabled,
                onPlayAllClick = {
                    playerConnection.playQueue(
                        ListQueue.fromSongs(
                            title = quickPicksTitle,
                            songs = quickPicks,
                        ),
                    )
                },
                onSongClick = playOrToggleSong,
                onSongLongClick = { song -> showSongMenu(song, true) },
                onSongMenuClick = { song -> showSongMenu(song, false) },
            )

            KeepListeningBlock(
                keepListening = keepListening,
                currentSongId = mediaMetadata?.id,
                currentAlbumId = mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                onSongClick = playOrToggleSong,
                onSongLongClick = { song -> showSongMenu(song, true) },
                onAlbumClick = { album -> navController.navigate("album/${album.id}") },
                onAlbumLongClick = { album ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(
                            originalAlbum = album,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                onAlbumPlay = { album ->
                    scope.launch(Dispatchers.IO) {
                        val albumWithSongs = database.albumWithSongs(album.id).first()
                        if (albumWithSongs != null) {
                            withContext(Dispatchers.Main) {
                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                            }
                        }
                    }
                },
                onArtistClick = { artist -> navController.navigate("artist/${artist.id}") },
                onArtistLongClick = { artist ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        ArtistMenu(
                            originalArtist = artist,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )

            AccountPlaylistsBlock(
                accountPlaylists = accountPlaylists,
                accountName = accountName,
                accountImageUrl = accountAvatarUrl,
                currentSongId = mediaMetadata?.id,
                currentAlbumId = mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                actions = itemActions,
                onAccountClick = { navController.navigate("account") },
            )

            SongsGridBlock(
                sectionKey = "forgotten_favorites",
                titleRes = R.string.forgotten_favorites,
                songs = forgottenFavorites,
                rows = min(4, forgottenFavorites.size).coerceAtLeast(1),
                screenWidthDp = screenWidthDp,
                mediaMetadataId = mediaMetadata?.id,
                isPlaying = isPlaying,
                swipeEnabled = swipeToSongEnabled,
                onPlayAllClick = {
                    playerConnection.playQueue(
                        ListQueue.fromSongs(
                            title = forgottenFavoritesTitle,
                            songs = forgottenFavorites,
                        ),
                    )
                },
                onSongClick = playOrToggleSong,
                onSongLongClick = { song -> showSongMenu(song, true) },
                onSongMenuClick = { song -> showSongMenu(song, false) },
            )

            SimilarRecommendationsBlock(
                similarRecommendations = similarRecommendations,
                currentSongId = mediaMetadata?.id,
                currentAlbumId = mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                actions = itemActions,
                onTitleClick = { localItem ->
                    when (localItem) {
                        is Song -> localItem.album?.id?.let { navController.navigate("album/$it") }
                        is Album -> navController.navigate("album/${localItem.id}")
                        is Artist -> navController.navigate("artist/${localItem.id}")
                        is Playlist -> Unit
                    }
                },
            )

            SpeedDialBlock(
                speedDialItems = speedDialItems,
                maxWidth = maxWidth,
                currentSongId = mediaMetadata?.id,
                currentAlbumId = mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                onItemClick = { item ->
                    when (item) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(
                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                item.toMediaMetadata(),
                            ),
                        )
                        is EpisodeItem -> {
                            val episodeSong = item.asSongItem()
                            playerConnection.playQueue(
                                YouTubeQueue(
                                    episodeSong.endpoint ?: WatchEndpoint(videoId = episodeSong.id),
                                    episodeSong.toMediaMetadata(),
                                ),
                            )
                        }
                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> navController.navigate("online_playlist/${item.id.removePrefix("VL")}")
                        is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                    }
                },
                onSurpriseClick = {
                    val luckyItem = speedDialItems.randomOrNull() ?: return@SpeedDialBlock
                    when (luckyItem) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(
                                luckyItem.endpoint ?: WatchEndpoint(videoId = luckyItem.id),
                                luckyItem.toMediaMetadata(),
                            ),
                        )
                        is EpisodeItem -> {
                            val episodeSong = luckyItem.asSongItem()
                            playerConnection.playQueue(
                                YouTubeQueue(
                                    episodeSong.endpoint ?: WatchEndpoint(videoId = episodeSong.id),
                                    episodeSong.toMediaMetadata(),
                                ),
                            )
                        }
                        is AlbumItem -> navController.navigate("album/${luckyItem.id}")
                        is ArtistItem -> navController.navigate("artist/${luckyItem.id}")
                        is PlaylistItem -> navController.navigate("online_playlist/${luckyItem.id.removePrefix("VL")}")
                        is PodcastItem -> navController.navigate("online_podcast/${luckyItem.id}")
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

            explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moodAndGenres ->
                item(key = "mood_and_genres_title", contentType = "home_section_header") {
                    HomeExpressiveSectionHeader(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = { navController.navigate("mood_and_genres") },
                    )
                }
                item(key = "mood_and_genres_list", contentType = "home_mood_genres") {
                    ExploreMoodGenreCarousel(
                        items = moodAndGenres,
                        onItemClick = {
                            navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                        },
                    )
                }
            }

            homePage?.sections?.forEachIndexed { index, section ->
                val sectionSongs = section.items.filterIsInstance<SongItem>()
                val playAllAction: (() -> Unit)? = if (sectionSongs.isNotEmpty()) {
                    {
                        playerConnection.playQueue(
                            ListQueue(
                                title = section.title,
                                items = sectionSongs.map { it.toMediaItem() },
                            ),
                        )
                    }
                } else {
                    null
                }
                val headerClickAction: (() -> Unit)? = section.endpoint?.browseId?.let { browseId ->
                    { navController.navigate(resolveHomeSectionRoute(browseId)) }
                }

                HomeDynamicSectionBlock(
                    section = section,
                    index = index,
                    currentSongId = mediaMetadata?.id,
                    currentAlbumId = mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    actions = itemActions,
                    onHeaderClick = headerClickAction,
                    onPlayAllClick = playAllAction,
                )
            }

            val hasHomeContinuation = homePage?.continuation != null && homePage.sections.isNotEmpty()
            if (isLoading || hasHomeContinuation) {
                item(key = "loading_shimmer", contentType = "home_shimmer") {
                    if (!hasAnyItems) {
                        HomeExpressiveShimmer()
                    } else {
                        HomeContinuationShimmer()
                    }
                }
            }
        }

        val fabSpatial = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastSpatialSpec()
        val fabEffects = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastEffectsSpec()
        val isScrollingUp = lazylistState.isScrollingUp()

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(16.dp),
        ) {
            AnimatedVisibility(
                visible = showSyncIndicator,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        when {
                            isSyncing -> ContainedLoadingIndicator(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            )
                            syncSucceeded -> Icon(
                                painter = painterResource(R.drawable.check_circle),
                                contentDescription = null,
                                tint = Color(0xFF34A853),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                            )
                            else -> Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasAnyItems && isScrollingUp,
                enter = fadeIn(fabEffects) + scaleIn(initialScale = 0.8f, animationSpec = fabSpatial),
                exit = fadeOut(fabEffects) + scaleOut(targetScale = 0.8f, animationSpec = fabEffects),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = { navController.navigate("recognition") },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.mic),
                            contentDescription = null,
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            val local = when {
                                allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5f
                                allLocalItems.isNotEmpty() -> true
                                else -> false
                            }
                            scope.launch(Dispatchers.Main) {
                                if (local) {
                                    when (val luckyItem = allLocalItems.random()) {
                                        is Song -> playerConnection.playQueue(
                                            YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                        )
                                        is Album -> {
                                            val albumWithSongs = withContext(Dispatchers.IO) {
                                                database.albumWithSongs(luckyItem.id).first()
                                            }
                                            albumWithSongs?.let {
                                                playerConnection.playQueue(LocalAlbumRadio(it))
                                            }
                                        }
                                        is Artist, is Playlist -> Unit
                                    }
                                } else {
                                    when (val luckyItem = allYtItems.random()) {
                                        is SongItem -> playerConnection.playQueue(
                                            YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                        )
                                        is EpisodeItem -> {
                                            val episodeSong = luckyItem.asSongItem()
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(episodeSong.toMediaMetadata()),
                                            )
                                        }
                                        is AlbumItem -> playerConnection.playQueue(
                                            YouTubeAlbumRadio(luckyItem.playlistId),
                                        )
                                        is ArtistItem -> luckyItem.radioEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                        is PlaylistItem -> luckyItem.playEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                        is PodcastItem -> luckyItem.playEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeExpressiveShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        Modifier
                            .width(84.dp)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
            HomeShimmerHeaderBar()
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
                                    .fillMaxWidth(0.58f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.36f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                }
            }
            HomeShimmerCarouselRow()
            HomeContinuationShimmer(modifier = Modifier.padding(horizontal = 0.dp))
        }
    }
}

@Composable
private fun HomeContinuationShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeShimmerHeaderBar()
            HomeShimmerCarouselRow()
        }
    }
}

@Composable
private fun HomeShimmerHeaderBar() {
    Box(
        Modifier
            .width(196.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun HomeShimmerCarouselRow() {
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.78f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}
