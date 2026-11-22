@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.anitail.music.ui.screens.artist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ArtistPage
import com.anitail.innertube.pages.ArtistSection
import com.anitail.innertube.pages.LATEST_RELEASE_SECTION_KEY
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.LocalAlbumRadio
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.ui.menu.YouTubeAlbumMenu
import com.anitail.music.ui.menu.YouTubeArtistMenu
import com.anitail.music.ui.menu.YouTubePlaylistMenu
import com.anitail.music.ui.menu.YouTubeSongMenu
import com.anitail.music.utils.DeviceUtils
import com.anitail.music.utils.formatArtistSubscriberCount
import com.anitail.music.utils.highResolutionArtworkUrl
import com.anitail.music.utils.monthlyAudienceText
import com.anitail.music.utils.reportException
import com.anitail.music.viewmodels.ArtistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ArtistScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val uiState by viewModel.uiState.collectAsState()
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()

    val onlinePage = uiState.artistPage
    val offlineFallbackArtist = remember(onlinePage, uiState.isLoading, libraryArtist) {
        if (onlinePage == null && !uiState.isLoading) {
            libraryArtist?.let { artist ->
                ArtistItem(
                    id = artist.id,
                    title = artist.artist.name,
                    thumbnail = artist.artist.thumbnailUrl,
                    channelId = artist.artist.channelId,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                )
            }
        } else {
            null
        }
    }

    val displayArtist = onlinePage?.artist ?: offlineFallbackArtist
    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null || onlinePage?.isSubscribed == true

    val localLibraryItems = remember(librarySongs, libraryAlbums) {
        val albumItems = libraryAlbums.map { album ->
            AlbumItem(
                browseId = album.id,
                playlistId = album.album.playlistId ?: album.id,
                title = album.album.title,
                artists = album.artists.map { Artist(id = it.id, name = it.name) }.ifEmpty { null },
                year = album.album.year,
                thumbnail = album.album.thumbnailUrl.orEmpty(),
            )
        }
        val songItems = librarySongs.map { song ->
            SongItem(
                id = song.id,
                title = song.song.title,
                artists = song.artists.map { Artist(id = it.id, name = it.name) },
                album = song.album?.let { com.anitail.innertube.models.Album(name = it.title, id = it.id) },
                duration = song.song.duration,
                thumbnail = song.thumbnailUrl.orEmpty(),
                explicit = song.song.explicit,
                isEpisode = song.song.isEpisode,
            )
        }
        albumItems + songItems
    }

    val sections = remember(onlinePage?.sections, localLibraryItems) {
        val base = onlinePage?.sections.orEmpty()
        val hasRemoteLibrary = base.any { it.moreEndpoint?.browseId?.startsWith("MPLA") == true }
        if (!hasRemoteLibrary && localLibraryItems.isNotEmpty()) {
            base + ArtistSection(
                title = LOCAL_LIBRARY_SECTION_KEY,
                items = localLibraryItems,
                moreEndpoint = null,
            )
        } else {
            base
        }
    }

    val classification = remember(sections) { classifyArtistSections(sections) }
    val bottomContentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    var appBarVisible by remember { mutableStateOf(false) }

    val hasHeaderImage = !onlinePage?.banner.isNullOrBlank() ||
        !displayArtist?.thumbnail.isNullOrBlank()
    val defaultLightStatusBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    DisposableEffect(view, appBarVisible, hasHeaderImage, defaultLightStatusBars) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.isAppearanceLightStatusBars = if (!appBarVisible && hasHeaderImage) {
                false
            } else {
                defaultLightStatusBars
            }
        }
        onDispose {
            controller?.isAppearanceLightStatusBars = defaultLightStatusBars
        }
    }

    fun playAlbumItem(album: AlbumItem) {
        coroutineScope.launch(Dispatchers.IO) {
            var albumWithSongs = database.albumWithSongs(album.browseId).first()
            var fetchedAlbumSongs: List<SongItem> = emptyList()
            if (albumWithSongs?.songs.isNullOrEmpty()) {
                YouTube.album(album.browseId)
                    .onSuccess { albumPage ->
                        fetchedAlbumSongs = albumPage.songs
                        runCatching { database.transaction { insert(albumPage) } }
                        albumWithSongs = database.albumWithSongs(album.browseId).first()
                    }
                    .onFailure { reportException(it) }
            }
            withContext(Dispatchers.Main) {
                val localAlbum = albumWithSongs
                if (localAlbum != null && localAlbum.songs.isNotEmpty()) {
                    playerConnection.playQueue(LocalAlbumRadio(localAlbum))
                } else if (fetchedAlbumSongs.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = album.title,
                            items = fetchedAlbumSongs.map { it.toMediaItem() },
                        )
                    )
                } else {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun playArtistSongs(shuffle: Boolean) {
        val artistTitle = displayArtist?.title ?: context.getString(R.string.unknown_artist)
        val endpoint = if (shuffle) displayArtist?.shuffleEndpoint else displayArtist?.playEndpoint
        if (endpoint != null && onlinePage != null) {
            playerConnection.playQueue(YouTubeQueue(endpoint))
            return
        }
        coroutineScope.launch {
            val remoteSongs = viewModel.resolveArtistSongs()
            if (remoteSongs.isNotEmpty()) {
                val queueItems = if (shuffle) remoteSongs.shuffled() else remoteSongs
                playerConnection.playQueue(
                    ListQueue(
                        title = artistTitle,
                        items = queueItems.map { it.toMediaItem() },
                    )
                )
            } else if (librarySongs.isNotEmpty()) {
                val queueItems = if (shuffle) librarySongs.shuffled() else librarySongs
                playerConnection.playQueue(
                    ListQueue(
                        title = artistTitle,
                        items = queueItems.map { it.toMediaItem() },
                    )
                )
            } else {
                Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openItemMenu(item: YTItem) {
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(
                    song = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
                is EpisodeItem -> YouTubeSongMenu(
                    song = item.asSongItem(),
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
                is AlbumItem -> YouTubeAlbumMenu(
                    albumItem = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
                is ArtistItem -> YouTubeArtistMenu(
                    artist = item,
                    onDismiss = menuState::dismiss,
                )
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
                is PodcastItem -> YouTubePlaylistMenu(
                    playlist = item.asPlaylistItem(),
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    fun openItem(item: YTItem, section: ArtistSection?) {
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else if (section != null && !classification.videoSections.contains(section.title) && !item.isVideoSong) {
                    coroutineScope.launch {
                        val queueSongs = viewModel.resolveShelfQueue(item, section)
                        val startIndex = queueSongs.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                        playerConnection.playQueue(
                            ListQueue(
                                title = section.title.takeUnless { it == LOCAL_LIBRARY_SECTION_KEY }
                                    ?: displayArtist?.title.orEmpty(),
                                items = queueSongs.map { it.toMediaItem() },
                                startIndex = startIndex,
                            )
                        )
                    }
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue(
                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                            item.toMediaMetadata(),
                        )
                    )
                }
            }
            is EpisodeItem -> {
                val songItem = item.asSongItem()
                if (songItem.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue(
                            songItem.endpoint ?: WatchEndpoint(videoId = songItem.id),
                            songItem.toMediaMetadata(),
                        )
                    )
                }
            }
            is AlbumItem -> navController.navigate("album/${item.browseId}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is PodcastItem -> navController.navigate("online_podcast/${item.id}")
        }
    }

    val isTelevision = remember(context) { DeviceUtils.isTelevisionDevice(context) }

    ArtistScreenLayout(
        isLoading = uiState.isLoading,
        hasContent = displayArtist != null,
        errorMessage = uiState.errorMessage,
        onRefresh = viewModel::refresh,
        onBackClick = navController::navigateUp,
    ) {
        ArtistScreenContent(
            artist = displayArtist ?: ArtistItem(
                id = viewModel.artistId,
                title = "",
                thumbnail = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            ),
            bannerUrl = onlinePage?.banner,
            subscriberCountText = onlinePage?.subscriberCountText,
            monthlyListenerCount = onlinePage?.monthlyListenerCount,
            description = onlinePage?.description?.takeIf { it.isNotBlank() } ?: uiState.lastFmBio,
            aboutImageUrl = uiState.lastFmPhotoUrl ?: displayArtist?.thumbnail ?: onlinePage?.banner,
            isPlaceholder = displayArtist == null,
            currentSongId = mediaMetadata?.id,
            isPlaying = isPlaying,
            sections = sections,
            classification = classification,
            likedSongIds = likedSongIds,
            listState = listState,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            isSubscribed = isSubscribed,
            subscribeSuccessCount = uiState.subscribeSuccessCount,
            onBackClick = navController::navigateUp,
            onMenuClick = {
                val menuArtist = displayArtist ?: ArtistItem(
                    id = viewModel.artistId,
                    title = "",
                    thumbnail = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                )
                menuState.show {
                    YouTubeArtistMenu(
                        artist = menuArtist,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onRefreshClick = if (isTelevision) viewModel::refresh else null,
            onPlayAllClick = { playArtistSongs(shuffle = false) },
            onShuffleClick = { playArtistSongs(shuffle = true) },
            onMixClick = {
                val radioEndpoint = displayArtist?.radioEndpoint
                if (radioEndpoint != null) {
                    playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                } else {
                    playArtistSongs(shuffle = false)
                }
            },
            onSubscribeClick = viewModel::toggleSubscription,
            onPlayAlbumClick = ::playAlbumItem,
            onLikeSong = viewModel::toggleSongLike,
            onItemClick = { item, section -> openItem(item, section) },
            onItemLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                openItemMenu(item)
            },
            onSectionClick = { section ->
                if (section.title == LOCAL_LIBRARY_SECTION_KEY) {
                    val target = if (librarySongs.isNotEmpty()) {
                        "artist/${viewModel.artistId}/songs"
                    } else {
                        "artist/${viewModel.artistId}/albums"
                    }
                    navController.navigate(target)
                } else {
                    section.moreEndpoint?.let { endpoint ->
                        navController.navigate(
                            "artist/${viewModel.artistId}/items?browseId=${endpoint.browseId}?params=${endpoint.params}"
                        )
                    }
                }
            },
            onAboutClick = {
                navController.navigate("artist/${viewModel.artistId}/about")
            },
            onAppBarVisibleChange = { appBarVisible = it },
        )
    }
}

@Composable
private fun ArtistScreenLayout(
    isLoading: Boolean,
    hasContent: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        if (hasContent && errorMessage != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        if (!hasContent && !isLoading) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets.statusBars,
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.error_occurred),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMessage?.takeIf(String::isNotBlank) ?: stringResource(R.string.error_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRefresh, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else {
            var userRefreshing by remember { mutableStateOf(false) }
            val isLoadingNow by rememberUpdatedState(isLoading && hasContent)
            LaunchedEffect(userRefreshing) {
                if (!userRefreshing) return@LaunchedEffect
                withTimeoutOrNull(1_500L) { snapshotFlow { isLoadingNow }.first { it } }
                snapshotFlow { isLoadingNow }.first { !it }
                userRefreshing = false
            }
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = userRefreshing,
                onRefresh = {
                    userRefreshing = true
                    if (!isLoadingNow) onRefresh()
                },
                state = refreshState,
                indicator = {
                    Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)) {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = refreshState,
                            isRefreshing = userRefreshing,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ArtistScreenContent(
    artist: ArtistItem,
    bannerUrl: String?,
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    description: String?,
    aboutImageUrl: String?,
    isPlaceholder: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    sections: List<ArtistSection>,
    classification: ArtistSectionClassification,
    likedSongIds: Set<String>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    isSubscribed: Boolean,
    subscribeSuccessCount: Int,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onRefreshClick: (() -> Unit)?,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onPlayAlbumClick: (AlbumItem) -> Unit,
    onLikeSong: (SongItem) -> Unit,
    onItemClick: (YTItem, ArtistSection?) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    onSectionClick: (ArtistSection) -> Unit,
    onAboutClick: () -> Unit,
    onAppBarVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingText = stringResource(R.string.loading)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isWide = maxWidth >= 960.dp
        val isMedium = maxWidth >= 600.dp && !isWide
        val squareHeader = !isWide && !isMedium
        val headerImageHeight = if (squareHeader) {
            minOf(maxWidth, maxHeight * 0.6f)
        } else {
            minOf(maxHeight / 2, if (isWide) 260.dp else 360.dp)
        }
        val actionSize = when {
            maxWidth >= 840.dp -> ArtistActionSize.Expanded
            maxWidth >= 600.dp -> ArtistActionSize.Medium
            else -> ArtistActionSize.Compact
        }

        val titleBottom = remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
        val surface = MaterialTheme.colorScheme.surface
        val headerImageUrl = if (squareHeader) {
            artist.thumbnail ?: bannerUrl
        } else {
            bannerUrl ?: artist.thumbnail
        }

        ArtistHeaderImage(
            imageUrl = headerImageUrl,
            square = squareHeader,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerImageHeight)
                .clipToBounds()
                .then(
                    if (isPlaceholder && headerImageUrl == null) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    } else {
                        Modifier
                    },
                ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val coverTop = if (listState.firstVisibleItemIndex == 0) {
                        headerImageHeight.toPx() - listState.firstVisibleItemScrollOffset
                    } else {
                        0f
                    }
                    val top = coverTop.coerceAtLeast(0f)
                    val radius = minOf(28.dp.toPx(), top)
                    drawRoundRect(
                        color = surface,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top + radius),
                        cornerRadius = CornerRadius(radius),
                    )
                }
                .then(
                    if (isPlaceholder) {
                        Modifier.semantics {
                            contentDescription = loadingText
                            liveRegion = LiveRegionMode.Polite
                        }
                    } else {
                        Modifier
                    },
                ),
            contentPadding = contentPadding,
        ) {
            item(key = "artist_header", contentType = "header") {
                ArtistHeader(
                    artist = artist,
                    subscriberCountText = subscriberCountText,
                    monthlyListenerCount = monthlyListenerCount,
                    isPlaceholder = isPlaceholder,
                    imageHeight = headerImageHeight,
                    isWide = isWide,
                    isMedium = isMedium,
                    actionSize = actionSize,
                    isSubscribed = isSubscribed,
                    subscribeSuccessCount = subscribeSuccessCount,
                    onMenuClick = onMenuClick.takeUnless { actionSize == ArtistActionSize.Compact },
                    onTitleBottom = { titleBottom.floatValue = it },
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onMixClick = onMixClick,
                    onSubscribeClick = onSubscribeClick,
                )
            }

            if (isPlaceholder) {
                item(key = "artist_loading_sections", contentType = "loading") {
                    ShimmerHost {
                        ArtistLoadingSections(isWide = isWide, isMedium = isMedium)
                    }
                }
            } else {
                renderArtistSections(
                    classification = classification,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    sections = sections,
                    likedSongIds = likedSongIds,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onSectionClick = onSectionClick,
                    onPlayAlbumClick = onPlayAlbumClick,
                    onLikeSong = onLikeSong,
                    isWide = isWide,
                    isMedium = isMedium,
                    about = {
                        item(key = "artist_about", contentType = "about") {
                            ArtistAboutCard(
                                artist = artist,
                                monthlyListenerCount = monthlyListenerCount,
                                description = description,
                                imageUrl = aboutImageUrl,
                                isWide = isWide,
                                descriptionLines = if (isWide || isMedium) 2 else 4,
                                onClick = onAboutClick,
                            )
                        }
                    },
                )
            }

            item(key = "artist_footer_space", contentType = "spacer") {
                Spacer(Modifier.height(24.dp))
            }
        }

        val barMenuClick = onMenuClick.takeIf { actionSize == ArtistActionSize.Compact }
        val appBarBottom = remember { mutableFloatStateOf(0f) }
        val showAppBar by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || titleBottom.floatValue <= appBarBottom.floatValue }
        }
        LaunchedEffect(showAppBar) { onAppBarVisibleChange(showAppBar) }
        val progress by animateFloatAsState(
            targetValue = if (showAppBar) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "artistAppBarProgress",
        )
        val iconColor = lerp(Color.White, MaterialTheme.colorScheme.onSurface, progress)
        val buttonModifier = Modifier.drawBehind { drawCircle(Color.Black, alpha = 0.4f * (1f - progress)) }

        TopAppBar(
            title = {
                Text(
                    text = artist.title,
                    modifier = Modifier
                        .graphicsLayer { alpha = progress }
                        .then(if (isPlaceholder) Modifier.clearAndSetSemantics {} else Modifier),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = buttonModifier,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = iconColor,
                    )
                }
            },
            actions = {
                if (onRefreshClick != null) {
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = buttonModifier,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.artist_refresh),
                            tint = iconColor,
                        )
                    }
                }
                if (barMenuClick != null) {
                    IconButton(
                        onClick = barMenuClick,
                        modifier = buttonModifier,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.artist_actions),
                            tint = iconColor,
                        )
                    }
                }
            },
            modifier = Modifier
                .onGloballyPositioned { appBarBottom.floatValue = it.boundsInWindow().bottom }
                .drawBehind { drawRect(surface, alpha = progress) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets.statusBars,
        )
    }
}

@Composable
private fun ArtistPlaceholderBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Spacer(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
private fun ArtistPlaceholderText(
    style: TextStyle,
    modifier: Modifier = Modifier,
    fraction: Float = 0.7f,
) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    Text(
        text = "",
        style = style,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        if (layoutDirection == LayoutDirection.Rtl) size.width * (1f - fraction) else 0f,
                        size.height * 0.2f,
                    ),
                    size = Size(size.width * fraction, size.height * 0.6f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            },
    )
}

@Composable
private fun ArtistLoadingSections(
    isWide: Boolean,
    isMedium: Boolean,
) {
    val isSpacious = isWide || isMedium
    val horizontalPadding = if (isWide) 24.dp else 16.dp
    val imageSize = if (isSpacious) 180.dp else 135.dp
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1400.dp).fillMaxWidth()) {
            Column(Modifier.padding(top = 15.dp)) {
                ArtistSectionTitle(
                    title = stringResource(R.string.latest_release),
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    isWide = isSpacious,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ArtistPlaceholderBlock(Modifier.size(imageSize), RoundedCornerShape(12.dp))
                        Column(Modifier.height(imageSize).weight(1f)) {
                            ArtistPlaceholderText(
                                if (isSpacious) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            )
                            ArtistPlaceholderText(MaterialTheme.typography.bodyLarge, fraction = 0.5f)
                            Spacer(Modifier.weight(1f))
                            ArtistPlaceholderBlock(
                                Modifier
                                    .align(if (isWide) Alignment.Start else Alignment.End)
                                    .size(width = 88.dp, height = 40.dp),
                                CircleShape,
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(top = 15.dp)) {
                ArtistSectionTitle(
                    title = stringResource(R.string.top_tracks),
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    isWide = isSpacious,
                )
                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = horizontalPadding)) {
                    repeat(5) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistPlaceholderBlock(Modifier.size(if (isWide) 40.dp else 48.dp))
                            Spacer(Modifier.width(if (isWide) 16.dp else 12.dp))
                            Column(Modifier.weight(1f)) {
                                ArtistPlaceholderText(MaterialTheme.typography.bodyMedium)
                                ArtistPlaceholderText(MaterialTheme.typography.bodySmall, fraction = 0.45f)
                            }
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                ArtistPlaceholderBlock(Modifier.size(24.dp), CircleShape)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.renderArtistSections(
    classification: ArtistSectionClassification,
    currentSongId: String?,
    isPlaying: Boolean,
    sections: List<ArtistSection>,
    likedSongIds: Set<String>,
    onItemClick: (YTItem, ArtistSection?) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    onSectionClick: (ArtistSection) -> Unit,
    onPlayAlbumClick: (AlbumItem) -> Unit,
    onLikeSong: (SongItem) -> Unit,
    isWide: Boolean,
    isMedium: Boolean,
    about: LazyListScope.() -> Unit,
) {
    val aboutBefore = classification.librarySection
    sections.forEach { section ->
        if (section.title == aboutBefore) about()
        if (section.items.isNotEmpty()) {
            val isLatestRelease = section.title == LATEST_RELEASE_SECTION_KEY
            val isTopTracks = section.title == classification.topTracksSection
            val isLibrary = section.title == classification.librarySection
            val isVideo = section.title in classification.videoSections
            val canBrowse = section.moreEndpoint != null || section.title == LOCAL_LIBRARY_SECTION_KEY

            item(key = "section_${section.title}", contentType = "section") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(modifier = Modifier.widthIn(max = 1400.dp).fillMaxWidth().padding(top = 15.dp)) {
                        val sectionTitle = when {
                            isLatestRelease -> stringResource(R.string.latest_release)
                            isTopTracks -> stringResource(R.string.top_tracks)
                            section.title == LOCAL_LIBRARY_SECTION_KEY -> stringResource(R.string.from_your_library)
                            else -> section.title
                        }
                        ArtistSectionTitle(
                            title = sectionTitle,
                            modifier = Modifier.padding(horizontal = if (isWide) 24.dp else 16.dp),
                            onClick = if (canBrowse) {
                                { onSectionClick(section) }
                            } else {
                                null
                            },
                            isWide = isWide || isMedium,
                        )
                        Spacer(Modifier.height(10.dp))

                        ArtistSectionContent(
                            items = section.items,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isLatestRelease = isLatestRelease,
                            songRows = if (isTopTracks) 5 else 4,
                            isTopTracks = isTopTracks,
                            isLibrary = isLibrary,
                            isVideo = isVideo,
                            likedSongIds = likedSongIds,
                            onItemClick = { item -> onItemClick(item, section) },
                            onItemLongClick = onItemLongClick,
                            onPlayAlbumClick = onPlayAlbumClick,
                            onLikeSong = onLikeSong,
                            isWide = isWide,
                            isMedium = isMedium,
                        )
                    }
                }
            }
        }
    }
    if (aboutBefore == null) about()
}

@Composable
private fun ArtistHeader(
    artist: ArtistItem,
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    isPlaceholder: Boolean,
    imageHeight: Dp,
    isWide: Boolean,
    isMedium: Boolean,
    actionSize: ArtistActionSize,
    isSubscribed: Boolean,
    subscribeSuccessCount: Int,
    onMenuClick: (() -> Unit)?,
    onTitleBottom: (Float) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    onSubscribeClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.fillMaxWidth().height(imageHeight))
        Column(Modifier.widthIn(max = 1400.dp).fillMaxWidth()) {
            ArtistHeaderDetails(
                artist = artist,
                subscriberCountText = subscriberCountText,
                monthlyListenerCount = monthlyListenerCount,
                isPlaceholder = isPlaceholder,
                isWide = isWide,
                isMedium = isMedium,
                actionSize = actionSize,
                isSubscribed = isSubscribed,
                subscribeSuccessCount = subscribeSuccessCount,
                onPlayAllClick = onPlayAllClick,
                onShuffleClick = onShuffleClick,
                onMixClick = onMixClick,
                onSubscribeClick = onSubscribeClick,
                onMenuClick = onMenuClick,
                onTitleBottom = onTitleBottom,
                modifier = Modifier.padding(
                    horizontal = if (isWide) 32.dp else 20.dp,
                    vertical = if (isWide) 24.dp else 16.dp,
                ),
            )
        }
    }
}

@Composable
private fun ArtistHeaderImage(
    imageUrl: String?,
    square: Boolean,
    modifier: Modifier,
) {
    val resolvedImageUrl = remember(imageUrl) { imageUrl?.let(::highResolutionArtworkUrl) }
    val imageAlignment = if (square) Alignment.Center else BiasAlignment(0f, -0.65f)
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier) {
        ArtistBlurredArtworkBackground(
            url = resolvedImageUrl,
            modifier = Modifier.fillMaxSize(),
            blurRadius = 48.dp,
            verticalBias = -0.65f,
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 1400.dp)
                .fillMaxHeight()
                .then(if (square) Modifier else Modifier.bannerEdgeMask()),
        ) {
            AsyncImage(
                model = resolvedImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = imageAlignment,
            )
        }
        if (!square) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.6f),
                            0.15f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (square) {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.18f to Color.Transparent,
                            0.78f to Color.Transparent,
                            0.92f to surface.copy(alpha = 0.7f),
                            1f to surface,
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.3f to Color.Transparent,
                            0.6f to surface.copy(alpha = 0.5f),
                            0.85f to surface.copy(alpha = 0.88f),
                            1f to surface,
                        )
                    },
                ),
        )
    }
}

private fun Modifier.bannerEdgeMask(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            val edgeMask = Brush.horizontalGradient(
                0f to Color.Black,
                0.12f to Color.Transparent,
                0.88f to Color.Transparent,
                1f to Color.Black,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = edgeMask, blendMode = BlendMode.DstOut)
            }
        }

@Composable
private fun ArtistHeaderDetails(
    artist: ArtistItem,
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    isPlaceholder: Boolean,
    isWide: Boolean,
    isMedium: Boolean,
    actionSize: ArtistActionSize,
    isSubscribed: Boolean,
    subscribeSuccessCount: Int,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onMenuClick: (() -> Unit)?,
    onTitleBottom: (Float) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        val titleStyle = MaterialTheme.typography.headlineLargeEmphasized.copy(
            fontSize = when {
                isWide -> 44.sp
                isMedium -> 32.sp
                else -> 26.sp
            },
        )
        val titleModifier = Modifier.onGloballyPositioned { onTitleBottom(it.boundsInWindow().bottom) }
        if (isPlaceholder && artist.title.isBlank()) {
            ShimmerHost {
                ArtistPlaceholderText(titleStyle, titleModifier, 0.6f)
            }
        } else {
            Text(
                text = artist.title,
                modifier = titleModifier.then(if (isPlaceholder) Modifier.clearAndSetSemantics {} else Modifier),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isPlaceholder) {
            ShimmerHost {
                Column(Modifier.clearAndSetSemantics {}) {
                    ArtistPlaceholderText(MaterialTheme.typography.bodyLarge, fraction = 0.4f)
                    ArtistPlaceholderText(MaterialTheme.typography.bodyLarge, fraction = 0.55f)
                }
            }
        } else {
            val subscriberTemplate = stringResource(R.string.artist_subscriber_count, "%1\$s")
            val formattedSubscribers = remember(subscriberCountText, subscriberTemplate) {
                formatArtistSubscriberCount(subscriberCountText) { count ->
                    subscriberTemplate.replace("%1\$s", count)
                }
            }
            formattedSubscribers?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val monthlyAudienceLabel = stringResource(R.string.monthly_audience_short)
            val monthlyAudience = remember(monthlyListenerCount, monthlyAudienceLabel) {
                monthlyAudienceText(monthlyListenerCount, monthlyAudienceLabel)
            }
            if (monthlyAudience != null) {
                Text(
                    text = monthlyAudience,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ArtistActionRow(
            isPlaceholder = isPlaceholder,
            size = actionSize,
            isSubscribed = isSubscribed,
            subscribeSuccessCount = subscribeSuccessCount,
            showSubscribe = true,
            onPlayAllClick = onPlayAllClick,
            onShuffleClick = onShuffleClick,
            onMixClick = onMixClick,
            onSubscribeClick = onSubscribeClick,
            onMenuClick = onMenuClick,
        )
    }
}

private enum class ArtistActionSize { Compact, Medium, Expanded }

@Composable
private fun ArtistActionRow(
    isPlaceholder: Boolean,
    size: ArtistActionSize,
    isSubscribed: Boolean,
    subscribeSuccessCount: Int,
    showSubscribe: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onMenuClick: (() -> Unit)?,
) {
    val compact = size == ArtistActionSize.Compact
    val rowHeight = 48.dp
    val subscribeLabel = stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe)
    val radioLabel = stringResource(R.string.radio)
    val shuffleLabel = stringResource(R.string.shuffle)
    val playAllLabel = stringResource(R.string.artist_play_all)
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tonalColors = FilledTonalToggleButtonDefaults.colors()
    val subscribeColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.primary else tonalColors.containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artistSubscribeBg",
    )
    val subscribeContentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.onPrimary else tonalColors.contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artistSubscribeFg",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val widths = with(density) {
                artistActionWidths(
                    availableWidth = constraints.maxWidth,
                    subscribeLabelWidth = textMeasurer.measure(subscribeLabel, textStyle).size.width,
                    radioLabelWidth = textMeasurer.measure(radioLabel, textStyle).size.width,
                    showSubscribe = showSubscribe,
                )
            }
            if (isPlaceholder) {
                ShimmerHost {
                    Row(
                        Modifier.clearAndSetSemantics {},
                        horizontalArrangement = Arrangement.spacedBy(widths.spacing),
                    ) {
                        if (showSubscribe) {
                            ArtistPlaceholderBlock(Modifier.width(widths.subscribeWidth).height(rowHeight), CircleShape)
                            Spacer(Modifier.width(widths.separation))
                        }
                        ArtistPlaceholderBlock(Modifier.width(widths.radioWidth).height(rowHeight), MaterialTheme.shapes.small)
                        ArtistPlaceholderBlock(Modifier.width(widths.shuffleWidth).height(rowHeight), MaterialTheme.shapes.small)
                        ArtistPlaceholderBlock(Modifier.width(widths.playWidth).height(rowHeight), MaterialTheme.shapes.small)
                    }
                }
            } else {
                ButtonGroup(
                    overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    expandedRatio = 1f,
                    horizontalArrangement = Arrangement.spacedBy(widths.spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSubscribe) {
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .animateWidth(interactionSource)
                                        .width(widths.subscribeWidth)
                                        .height(rowHeight),
                                ) {
                                    FilledTonalToggleButton(
                                        checked = isSubscribed,
                                        onCheckedChange = { onSubscribeClick() },
                                        interactionSource = interactionSource,
                                        modifier = Modifier.fillMaxSize(),
                                        shapes = FilledTonalToggleButtonDefaults.shapesFor(rowHeight).copy(
                                            shape = MaterialTheme.shapes.small,
                                            checkedShape = MaterialTheme.shapes.medium,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = tonalColors.copy(
                                            containerColor = subscribeColor,
                                            contentColor = subscribeContentColor,
                                            checkedContainerColor = subscribeColor,
                                            checkedContentColor = subscribeContentColor,
                                        ),
                                    ) {
                                        Text(subscribeLabel, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    SubscribeConfetti(subscribeSuccessCount, Modifier.matchParentSize())
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(subscribeLabel) },
                                    onClick = {
                                        state.dismiss()
                                        onSubscribeClick()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = { Spacer(Modifier.width(widths.separation)) },
                            menuContent = {},
                        )
                    }
                    listOf(radioLabel, shuffleLabel).forEachIndexed { index, label ->
                        val onClick = if (index == 0) onMixClick else onShuffleClick
                        val showLabel = index == 0 && widths.labelRadio
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val shapes = if (index == 0) {
                                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                                } else {
                                    ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                                FilledTonalButton(
                                    onClick = onClick,
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .animateWidth(interactionSource)
                                        .width(if (index == 0) widths.radioWidth else widths.shuffleWidth)
                                        .height(rowHeight),
                                    shapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape),
                                    contentPadding = PaddingValues(horizontal = if (showLabel) 12.dp else 0.dp),
                                ) {
                                    Icon(
                                        imageVector = if (index == 0) Icons.Outlined.Sensors else Icons.Rounded.Shuffle,
                                        contentDescription = if (showLabel) null else label,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    if (showLabel) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        state.dismiss()
                                        onClick()
                                    },
                                )
                            },
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                            Button(
                                onClick = onPlayAllClick,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .animateWidth(interactionSource)
                                    .width(widths.playWidth)
                                    .height(rowHeight),
                                shapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = playAllLabel,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        },
                        menuContent = { state ->
                            DropdownMenuItem(
                                text = { Text(playAllLabel) },
                                onClick = {
                                    state.dismiss()
                                    onPlayAllClick()
                                },
                            )
                        },
                    )
                }
            }
        }
        if (!compact && onMenuClick != null) {
            FilledTonalIconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(rowHeight),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.artist_actions),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistSectionContent(
    items: List<YTItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    isLatestRelease: Boolean,
    songRows: Int,
    isTopTracks: Boolean,
    isLibrary: Boolean,
    isVideo: Boolean,
    likedSongIds: Set<String>,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    onPlayAlbumClick: (AlbumItem) -> Unit,
    onLikeSong: (SongItem) -> Unit,
    isWide: Boolean,
    isMedium: Boolean,
) {
    val isSpacious = isWide || isMedium
    val horizontalPadding = if (isWide) 24.dp else 16.dp
    val showReleaseType = remember(items) {
        items
            .filterIsInstance<AlbumItem>()
            .mapNotNull { it.type }
            .distinct()
            .size > 1
    }
    val isVideoShelf = remember(items, isVideo) { items.isVideoShelf(allowUntypedSongs = isVideo) }
    val isSongShelf = remember(items) { items.all { it is SongItem } }
    val release = (items.singleOrNull() as? AlbumItem)?.takeIf { isLatestRelease }

    if (release != null) {
        val imageSize = if (isSpacious) 180.dp else 135.dp
        Surface(
            modifier = if (isWide) {
                Modifier.padding(horizontal = 24.dp).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onItemClick(release) },
                        onLongClick = { onItemLongClick(release) },
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = release.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(imageSize)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.height(imageSize).weight(1f)) {
                    Text(
                        text = release.title,
                        style = if (isSpacious) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (release.explicit) ArtistExplicitBadge()
                        listOfNotNull(release.type, release.year?.toString())
                            .joinToString(" • ")
                            .takeIf(String::isNotBlank)
                            ?.let { meta ->
                                Text(
                                    text = meta,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isWide) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onPlayAlbumClick(release) },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.play))
                        }
                    }
                }
            }
        }
    } else if (isVideoShelf) {
        ArtistVideoShelf(
            items = items,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            isWide = isSpacious,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            currentSongId = currentSongId,
            isPlaying = isPlaying,
        )
    } else if (isLibrary) {
        val carouselState = rememberCarouselState { items.size }
        val carouselFling = CarouselDefaults.multiBrowseFlingBehavior(carouselState)
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            flingBehavior = carouselFling,
            preferredItemWidth = CarouselItemSize,
            itemSpacing = CarouselItemSpacing,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .clip(RoundedCornerShape(CarouselItemCornerRadius)),
        ) { index ->
            val item = items[index]
            ArtistCarouselCard(
                item = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                artworkModifier = Modifier.maskClip(carouselArtworkShape(item)),
                carouselItemDrawInfo = carouselItemDrawInfo,
                isCurrent = item.id == currentSongId,
                isPlaying = isPlaying && item.id == currentSongId,
                onAlbumPlay = (item as? AlbumItem)?.let { album -> { onPlayAlbumClick(album) } },
            )
        }
    } else if (!isVideo && isSongShelf) {
        if (isTopTracks) {
            Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
                ArtistSongRows(
                    songs = remember(items) { items.take(5) },
                    isWide = isWide,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    likedSongIds = likedSongIds,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onLikeSong = onLikeSong,
                )
            }
        } else {
            val pages = remember(items, songRows) { items.chunked(songRows) }
            val pagerState = rememberPagerState { pages.size }
            val flingBehavior = PagerDefaults.flingBehavior(pagerState)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.heightIn(min = 56.dp * songRows),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = if (isWide) horizontalPadding else 8.dp,
                flingBehavior = flingBehavior,
                verticalAlignment = Alignment.Top,
            ) { page ->
                ArtistSongRows(
                    songs = pages[page],
                    isWide = isWide,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    likedSongIds = likedSongIds,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onLikeSong = onLikeSong,
                )
            }
        }
    } else {
        val imageSize = if (isVideo) {
            if (isSpacious) 320.dp else 280.dp
        } else {
            CarouselItemSize
        }
        val carouselState = rememberCarouselState { items.size }
        val carouselFling = CarouselDefaults.multiBrowseFlingBehavior(carouselState)
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            flingBehavior = carouselFling,
            preferredItemWidth = imageSize,
            itemSpacing = if (isVideo) 12.dp else CarouselItemSpacing,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .clip(RoundedCornerShape(CarouselItemCornerRadius)),
        ) { index ->
            val item = items[index]
            val subtitleOverride = when (item) {
                is AlbumItem -> listOfNotNull(
                    item.type.takeIf { showReleaseType },
                    item.year?.toString(),
                    item.songCount?.takeIf { it > 1 }?.let { count ->
                        pluralStringResource(R.plurals.n_song, count, count)
                    },
                ).joinToString(" • ")
                is PlaylistItem -> {
                    val count = item.songCountText?.substringBefore(' ')?.toIntOrNull()
                    when {
                        count == null -> item.songCountText.orEmpty()
                        count > 1 -> pluralStringResource(R.plurals.n_song, count, count)
                        else -> ""
                    }
                }
                else -> null
            }
            ArtistCarouselCard(
                item = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                imageSize = imageSize,
                artworkModifier = Modifier.maskClip(carouselArtworkShape(item)),
                carouselItemDrawInfo = carouselItemDrawInfo,
                subtitleOverride = subtitleOverride,
                isCurrent = item is SongItem && item.id == currentSongId,
                isPlaying = isPlaying && item.id == currentSongId,
                onAlbumPlay = (item as? AlbumItem)?.let { album -> { onPlayAlbumClick(album) } },
            )
        }
    }
}

@Composable
private fun ArtistSongRows(
    songs: List<YTItem>,
    isWide: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<String>,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    onLikeSong: (SongItem) -> Unit,
) {
    Column {
        songs.forEachIndexed { index, item ->
            key(item.id, index) {
                val song = item as? SongItem
                if (isWide && song != null) {
                    ArtistWideSongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        isPlaying = isPlaying && song.id == currentSongId,
                        isLiked = song.id in likedSongIds,
                        onClick = { onItemClick(song) },
                        onLongClick = { onItemLongClick(song) },
                        onLikeClick = { onLikeSong(song) },
                        onMenuClick = { onItemLongClick(song) },
                    )
                } else {
                    ArtistSongRow(
                        item = item,
                        isCurrent = item.id == currentSongId,
                        isPlaying = isPlaying && item.id == currentSongId,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onShowMenu = { onItemLongClick(item) },
                    )
                }
            }
        }
    }
}

private fun artistCardModifier(isWide: Boolean): Modifier =
    if (isWide) {
        Modifier.padding(horizontal = 24.dp).widthIn(max = 720.dp).fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    }

@Composable
private fun ArtistAboutCard(
    artist: ArtistItem,
    monthlyListenerCount: String?,
    description: String?,
    imageUrl: String?,
    isWide: Boolean,
    descriptionLines: Int,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = 1400.dp).fillMaxWidth().padding(top = 15.dp)) {
            ArtistSectionTitle(
                title = stringResource(R.string.about),
                modifier = Modifier.padding(horizontal = if (isWide) 24.dp else 16.dp),
                isWide = isWide,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onClick,
                modifier = artistCardModifier(isWide),
                shape = RoundedCornerShape(28.dp),
                color = if (imageUrl.isNullOrBlank()) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.BottomStart) {
                    if (!imageUrl.isNullOrBlank()) {
                        ArtistAboutPhoto(
                            url = imageUrl,
                            gradient = { 1f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(28.dp)),
                            alignment = BiasAlignment(0f, -0.44f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ArtistAboutCardText(
                            artist = artist,
                            monthlyListenerCount = monthlyListenerCount,
                            description = description,
                            descriptionLines = descriptionLines,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 2.dp).size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAboutCardText(
    artist: ArtistItem,
    monthlyListenerCount: String?,
    description: String?,
    descriptionLines: Int,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = artist.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            fontSize = 30.sp,
            lineHeight = 33.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val audienceLabel = stringResource(R.string.monthly_audience_short)
        val monthlyAudience = remember(monthlyListenerCount, audienceLabel) {
            monthlyAudienceText(monthlyListenerCount, audienceLabel)
        }
        if (monthlyAudience != null) {
            Text(
                text = monthlyAudience,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = descriptionLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
