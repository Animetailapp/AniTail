package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerHorizontalPadding
import com.anitail.desktop.constants.PlayerQueueCollapsedHeight
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.AudioSettingsDialog
import com.anitail.desktop.ui.component.ExpressiveIcons
import com.anitail.desktop.ui.component.LyricsPanel
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlayerConnectedButtonGroup
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.SleepTimerDialog
import com.anitail.desktop.ui.component.TempoPitchDialog
import com.anitail.desktop.ui.component.VideoPlayerSurface
import com.anitail.desktop.ui.component.collapsedAnchor
import com.anitail.desktop.ui.component.rememberBottomSheetState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.NextResult
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private enum class DesktopRightPanelTab {
    LYRICS,
    QUEUE,
}

private val WidePlayerBreakpoint = 840.dp

internal fun fullscreenPlayerScale(
    availableWidth: Dp,
    availableHeight: Dp,
): Float = minOf(
    (availableWidth - 48.dp) / 650.dp,
    availableHeight / 1030.dp,
).coerceAtLeast(0f)

internal fun fullscreenArtworkSize(
    availableWidth: Int,
    availableHeight: Int,
    controlsHeight: Int,
    aspectRatio: Float,
    preferredHeight: Int,
): IntSize {
    if (availableWidth <= 0 || availableHeight <= 0) return IntSize.Zero
    val safeAspectRatio = aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
    val maxHeight = (availableHeight - controlsHeight).coerceAtLeast(0)
    if (maxHeight == 0) return IntSize.Zero
    val targetHeight = minOf(preferredHeight.coerceAtLeast(1), maxHeight)
    val width = minOf(availableWidth, (targetHeight * safeAspectRatio).roundToInt().coerceAtLeast(1))
    val height = (width / safeAspectRatio).roundToInt().coerceIn(1, targetHeight)
    return IntSize(width = width, height = height)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    item: LibraryItem?,
    playerState: PlayerState,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    onOpenArtist: (String, String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onCollapsePlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        EmptyPlayerState(onCollapsePlayer = onCollapsePlayer, modifier = modifier)
        return
    }

    val preferences = remember { DesktopPreferences.getInstance() }
    val showLyrics by preferences.showLyrics.collectAsState()
    val swipeThumbnail by preferences.swipeThumbnail.collectAsState()
    val swipeSensitivity by preferences.swipeSensitivity.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()

    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val editablePlaylists = remember(playlists) { playlists.filter { it.isEditable } }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showArtistPickerDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showQueueSidePanel by remember { mutableStateOf(false) }
    var rightPanelTab by remember { mutableStateOf(DesktopRightPanelTab.LYRICS) }

    LaunchedEffect(showLyrics) {
        if (showLyrics) {
            rightPanelTab = DesktopRightPanelTab.LYRICS
        } else if (!showQueueSidePanel) {
            rightPanelTab = DesktopRightPanelTab.QUEUE
        }
    }

    val thumbnailOffsetX = remember { Animatable(0f) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var totalDragDistance by remember { mutableStateOf(0f) }

    var nextResult by remember(item.id) { mutableStateOf<NextResult?>(null) }
    var songDetails by remember(item.id) { mutableStateOf<SongItem?>(null) }

    val isLiked = songsById[item.id]?.liked == true
    val currentTrackRomanizeLyrics = songsById[item.id]?.romanizeLyrics ?: true
    val downloadState = computeSelectionDownloadState(
        selectedIds = listOf(item.id),
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
    )

    fun resolveSongDetails(result: NextResult?): SongItem? {
        result ?: return null
        val byCurrentIndex = result.currentIndex?.let { index -> result.items.getOrNull(index) }
        return byCurrentIndex ?: result.items.firstOrNull { it.id == item.id } ?: result.items.firstOrNull()
    }

    LaunchedEffect(item.id) {
        nextResult = YouTube.next(WatchEndpoint(videoId = item.id)).getOrNull()
        songDetails = resolveSongDetails(nextResult)
    }

    val artistsWithId = remember(songDetails) {
        songDetails?.artists?.filter { it.id != null } ?: emptyList()
    }
    val artistPairs = remember(artistsWithId) {
        artistsWithId.map { artist -> artist.id.orEmpty() to artist.name }
    }
    val controlArtistPairs = remember(songDetails) {
        songDetails?.artists?.map { it.name to it.id }.orEmpty()
    }
    val album = songDetails?.album
    val albumInfo = album?.let { it.id to it.name }

    suspend fun ensureSongInDatabase(target: LibraryItem) {
        if (!songsById.containsKey(target.id)) {
            database.insertSong(target.toSongEntity())
        }
    }

    fun addToPlaylist(playlistId: String) {
        coroutineScope.launch {
            ensureSongInDatabase(item)
            database.addSongToPlaylist(playlistId, item.id)
        }
    }

    fun toggleLike(like: Boolean) {
        coroutineScope.launch {
            ensureSongInDatabase(item)
            val currentlyLiked = songsById[item.id]?.liked == true
            if (like != currentlyLiked) {
                database.toggleSongLike(item.id)
            }
        }
    }

    fun downloadItem() {
        val durationSec = ((item.durationMs ?: 0L) / 1000L).toInt()
        downloadService.downloadSong(
            songId = item.id,
            title = item.title,
            artist = item.artist,
            album = album?.name,
            thumbnailUrl = item.artworkUrl,
            duration = durationSec,
        )
    }

    fun removeDownload() {
        downloadService.deleteDownload(item.id)
    }

    fun copyLink(url: String) {
        if (url.isNotBlank()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        }
    }

    fun openInBrowser(url: String) {
        if (url.isBlank()) return
        if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(URI(url)) }
        }
    }

    val shareUrl = if (item.id.isNotBlank()) {
        "https://music.youtube.com/watch?v=${item.id}"
    } else {
        item.playbackUrl
    }

    suspend fun loadNextResult(): NextResult? {
        val cached = nextResult
        if (cached != null) return cached
        val fetched = YouTube.next(WatchEndpoint(videoId = item.id)).getOrNull()
        if (fetched != null) {
            nextResult = fetched
            if (songDetails == null) {
                songDetails = resolveSongDetails(fetched)
            }
        }
        return fetched
    }

    fun startRadio() {
        coroutineScope.launch {
            val result = loadNextResult() ?: return@launch
            val plan = buildRadioQueuePlan(item, result)
            playerState.playQueue(plan.items, plan.startIndex)
            if (songDetails == null) {
                songDetails = plan.currentSong
            }
        }
    }

    fun openArtist(artistId: String, artistName: String) {
        onOpenArtist(artistId, artistName)
        onCollapsePlayer()
    }

    fun openAlbum(albumId: String, albumName: String) {
        onOpenAlbum(albumId, albumName)
        onCollapsePlayer()
    }

    val backgroundColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val hiResArtworkUrl = remember(item.artworkUrl) { toHighResArtworkUrl(item.artworkUrl) }
    val swipeAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )
    }
    val autoSwipeThreshold = remember(swipeSensitivity) {
        600f / (1f + exp(-(-11.44748f * swipeSensitivity + 9.04945f)))
    }
    val artworkFallbackUrls = remember(item.artworkUrl, hiResArtworkUrl) {
        val original = item.artworkUrl
        if (original.isNullOrBlank() || original == hiResArtworkUrl) {
            emptyList()
        } else {
            listOf(original)
        }
    }

    val isDarkOverlayStyle = playerBackgroundStyle == PlayerBackgroundStyle.GRADIENT ||
        playerBackgroundStyle == PlayerBackgroundStyle.BLUR ||
        playerBackgroundStyle == PlayerBackgroundStyle.LIQUID

    val textBackgroundColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) Color.White else MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.GRADIENT,
        PlayerBackgroundStyle.BLUR,
        PlayerBackgroundStyle.LIQUID -> Color.White
    }
    val mutedTextColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) {
            Color.White.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        PlayerBackgroundStyle.GRADIENT,
        PlayerBackgroundStyle.BLUR,
        PlayerBackgroundStyle.LIQUID -> Color.White.copy(alpha = 0.74f)
    }
    val iconBackgroundColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) Color.White else MaterialTheme.colorScheme.primary
        PlayerBackgroundStyle.GRADIENT,
        PlayerBackgroundStyle.BLUR,
        PlayerBackgroundStyle.LIQUID -> Color.White
    }
    val onIconButtonColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) Color.Black else MaterialTheme.colorScheme.onPrimary
        PlayerBackgroundStyle.GRADIENT,
        PlayerBackgroundStyle.BLUR,
        PlayerBackgroundStyle.LIQUID -> Color(0xFF141416)
    }
    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT -> onIconButtonColor to iconBackgroundColor
        PlayerButtonsStyle.SECONDARY -> MaterialTheme.colorScheme.onSecondary to MaterialTheme.colorScheme.secondary
    }

    val sidePanelContainerColor = if (isDarkOverlayStyle || pureBlack) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
    }

    val artworkSwipeModifier = Modifier.pointerInput(
        swipeThumbnail,
        swipeSensitivity,
        layoutDirection,
        playerState.canSkipNext,
        playerState.canSkipPrevious,
    ) {
        if (!swipeThumbnail) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = {
                dragStartTime = System.currentTimeMillis()
                totalDragDistance = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                val adjustedDragAmount =
                    if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                val allowLeft = adjustedDragAmount < 0 && playerState.canSkipNext
                val allowRight =
                    adjustedDragAmount > 0 && (playerState.canSkipPrevious || playerState.position > 3000L)
                if (!allowLeft && !allowRight) return@detectHorizontalDragGestures
                totalDragDistance += abs(adjustedDragAmount)
                coroutineScope.launch {
                    thumbnailOffsetX.snapTo(thumbnailOffsetX.value + adjustedDragAmount)
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    thumbnailOffsetX.animateTo(0f, animationSpec = swipeAnimationSpec)
                }
            },
            onDragEnd = {
                val dragDuration = System.currentTimeMillis() - dragStartTime
                val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                val currentOffset = thumbnailOffsetX.value
                val minDistanceThreshold = 50f
                val velocityThreshold = (swipeSensitivity * -8.25f) + 8.5f
                val shouldChangeSong = (
                    abs(currentOffset) > minDistanceThreshold &&
                        velocity > velocityThreshold
                    ) || (abs(currentOffset) > autoSwipeThreshold)

                if (shouldChangeSong) {
                    val isRightSwipe = currentOffset > 0f
                    if (isRightSwipe && (playerState.canSkipPrevious || playerState.position > 3000L)) {
                        playerState.skipToPrevious()
                    } else if (!isRightSwipe && playerState.canSkipNext) {
                        playerState.skipToNext()
                    }
                }

                coroutineScope.launch {
                    thumbnailOffsetX.animateTo(0f, animationSpec = swipeAnimationSpec)
                }
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val isWide = maxWidth >= WidePlayerBreakpoint
        val screenMaxWidth = maxWidth
        var artworkAspectRatio by remember(item.id, hiResArtworkUrl) { mutableStateOf(1f) }
        val videoAspectRatio = playerState.videoPlayer.aspectRatio
            .takeIf { it.isFinite() && it > 0.1f }
            ?.coerceIn(0.56f, 2.35f)
            ?: (16f / 9f)
        val mediaAspectRatio = if (playerState.isVideoMode) videoAspectRatio else artworkAspectRatio

        PlayerBackgroundLayer(
            style = playerBackgroundStyle,
            artworkUrl = hiResArtworkUrl,
            artworkFallbackUrls = artworkFallbackUrls,
            pureBlack = pureBlack,
            isPlaying = playerState.isPlaying,
            modifier = Modifier.fillMaxSize(),
        )

        val queueSheetState = rememberBottomSheetState(
            dismissedBound = PlayerQueueCollapsedHeight,
            collapsedBound = PlayerQueueCollapsedHeight,
            expandedBound = maxHeight,
            initialAnchor = collapsedAnchor,
        )

        fun toggleWideLyrics() {
            if (!showLyrics) {
                preferences.setShowLyrics(true)
                rightPanelTab = DesktopRightPanelTab.LYRICS
            } else if (rightPanelTab != DesktopRightPanelTab.LYRICS) {
                rightPanelTab = DesktopRightPanelTab.LYRICS
            } else {
                preferences.setShowLyrics(false)
            }
        }

        fun toggleWideQueue() {
            if (!showQueueSidePanel) {
                showQueueSidePanel = true
                rightPanelTab = DesktopRightPanelTab.QUEUE
            } else if (rightPanelTab != DesktopRightPanelTab.QUEUE) {
                rightPanelTab = DesktopRightPanelTab.QUEUE
            } else {
                showQueueSidePanel = false
            }
        }

        val showRightColumn = isWide && (showLyrics || showQueueSidePanel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isWide) 16.dp else PlayerQueueCollapsedHeight),
        ) {
            // Translucent Expressive top bar
            PlayerTopBar(
                item = item,
                playerState = playerState,
                albumName = album?.name,
                textColor = textBackgroundColor,
                mutedTextColor = mutedTextColor,
                onCollapse = onCollapsePlayer,
                onShowMore = { showMoreMenu = true },
                moreMenuExpanded = showMoreMenu,
                onDismissMoreMenu = { showMoreMenu = false },
                dropdownContent = {
                    PlayerOverflowMenuItems(
                        isDownloaded = downloadState == SelectionDownloadState.COMPLETED,
                        isDownloading = downloadState == SelectionDownloadState.DOWNLOADING,
                        hasArtists = artistsWithId.isNotEmpty(),
                        hasAlbum = albumInfo != null,
                        onAddToPlaylist = {
                            showMoreMenu = false
                            showPlaylistDialog = true
                        },
                        onStartRadio = {
                            showMoreMenu = false
                            startRadio()
                        },
                        onDownloadToggle = {
                            showMoreMenu = false
                            if (downloadState == SelectionDownloadState.COMPLETED) {
                                removeDownload()
                            } else {
                                downloadItem()
                            }
                        },
                        onOpenArtist = {
                            showMoreMenu = false
                            if (artistsWithId.size == 1) {
                                val first = artistsWithId.first()
                                openArtist(first.id.orEmpty(), first.name)
                            } else {
                                showArtistPickerDialog = true
                            }
                        },
                        onOpenAlbum = {
                            showMoreMenu = false
                            albumInfo?.let { (id, name) -> openAlbum(id, name) }
                        },
                        onOpenSleepTimer = {
                            showMoreMenu = false
                            showSleepTimerDialog = true
                        },
                        onOpenAudioSettings = {
                            showMoreMenu = false
                            showAudioSettingsDialog = true
                        },
                        onOpenTempoPitch = {
                            showMoreMenu = false
                            showAdvancedDialog = true
                        },
                        onCopyLink = {
                            showMoreMenu = false
                            copyLink(shareUrl)
                        },
                        onOpenInBrowser = {
                            showMoreMenu = false
                            openInBrowser(shareUrl)
                        },
                        onShowDetails = {
                            showMoreMenu = false
                            showDetailsDialog = true
                        },
                    )
                },
            )

            if (isWide) {
                // Responsive 2-column Material 3 Expressive Desktop Layout
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Pane: Dynamically Scaled Artwork / Live Video + Expressive Controls
                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val density = LocalDensity.current
                        val scale = fullscreenPlayerScale(maxWidth, maxHeight)
                        val controlsMaxWidth = (620.dp * scale).coerceIn(380.dp, 760.dp)
                        val preferredMediaHeightDp = maxOf(
                            360.dp,
                            (if (playerState.isVideoMode) 640.dp else 580.dp) * scale,
                        )
                        val verticalGap = (18.dp * scale).coerceIn(10.dp, 22.dp)
                        val buttonsGap = (14.dp * scale).coerceIn(8.dp, 18.dp)
                        val gapPx = with(density) { verticalGap.roundToPx() }
                        val preferredHeightPx = with(density) { preferredMediaHeightDp.roundToPx() }

                        Layout(
                            modifier = Modifier.fillMaxSize(),
                            content = {
                                PlayerMediaStage(
                                    item = item,
                                    playerState = playerState,
                                    hiResArtworkUrl = hiResArtworkUrl,
                                    artworkFallbackUrls = artworkFallbackUrls,
                                    mediaAspectRatio = mediaAspectRatio,
                                    onArtworkLoaded = { w, h ->
                                        if (w > 0 && h > 0) {
                                            artworkAspectRatio = (w.toFloat() / h.toFloat()).coerceIn(0.56f, 2.35f)
                                        }
                                    },
                                    swipeOffsetX = thumbnailOffsetX.value,
                                    swipeModifier = artworkSwipeModifier,
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .widthIn(max = controlsMaxWidth)
                                        .fillMaxWidth(),
                                ) {
                                    PlayerControls(
                                        item = item,
                                        playerState = playerState,
                                        isLiked = isLiked,
                                        textColor = textBackgroundColor,
                                        mutedTextColor = mutedTextColor,
                                        sliderStyle = sliderStyle,
                                        textButtonColor = textButtonColor,
                                        iconButtonColor = iconButtonColor,
                                        onToggleLike = { toggleLike(!isLiked) },
                                        onAddToPlaylist = { showPlaylistDialog = true },
                                        artistsWithId = controlArtistPairs,
                                        albumInfo = albumInfo,
                                        onOpenArtist = { id, name -> openArtist(id, name) },
                                        onOpenAlbum = { id, name -> openAlbum(id, name) },
                                        onShowMore = { showMoreMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(modifier = Modifier.height(buttonsGap))

                                    PlayerConnectedButtonGroup(
                                        playerState = playerState,
                                        showLyrics = showLyrics && rightPanelTab == DesktopRightPanelTab.LYRICS,
                                        onToggleLyrics = ::toggleWideLyrics,
                                        showQueue = showQueueSidePanel && rightPanelTab == DesktopRightPanelTab.QUEUE,
                                        onToggleQueue = ::toggleWideQueue,
                                        onOpenAudioSettings = { showAudioSettingsDialog = true },
                                        onOpenSleepTimer = { showSleepTimerDialog = true },
                                        onToggleFullscreen = { playerState.toggleFullscreenOverlay() },
                                        isFullscreen = playerState.isFullscreenOverlay,
                                        onButtonColor = textBackgroundColor,
                                        containerColor = textBackgroundColor.copy(alpha = 0.12f),
                                        checkedContainerColor = iconButtonColor,
                                        checkedContentColor = textButtonColor,
                                    )
                                }
                            },
                        ) { measurables, constraints ->
                            val availableW = constraints.maxWidth
                            val availableH = constraints.maxHeight
                            val controlsMeasurable = measurables[1]
                            val mediaMeasurable = measurables[0]

                            val controlsPlaceable = controlsMeasurable.measure(
                                constraints.copy(
                                    minWidth = 0,
                                    minHeight = 0,
                                    maxWidth = availableW,
                                    maxHeight = availableH,
                                ),
                            )

                            val artworkSize = fullscreenArtworkSize(
                                availableWidth = availableW,
                                availableHeight = (availableH - gapPx).coerceAtLeast(0),
                                controlsHeight = controlsPlaceable.height,
                                aspectRatio = mediaAspectRatio,
                                preferredHeight = preferredHeightPx,
                            )

                            val mediaPlaceable = mediaMeasurable.measure(
                                Constraints.fixed(artworkSize.width, artworkSize.height),
                            )

                            val activeGap = if (artworkSize.height > 0 && controlsPlaceable.height > 0) gapPx else 0
                            val totalContentHeight = (mediaPlaceable.height + activeGap + controlsPlaceable.height)
                                .coerceAtMost(availableH)
                            val startY = ((availableH - totalContentHeight) / 2).coerceAtLeast(0)

                            layout(availableW, availableH) {
                                val mediaX = ((availableW - mediaPlaceable.width) / 2).coerceAtLeast(0)
                                val controlsX = ((availableW - controlsPlaceable.width) / 2).coerceAtLeast(0)
                                if (mediaPlaceable.height > 0) {
                                    mediaPlaceable.placeRelative(mediaX, startY)
                                }
                                controlsPlaceable.placeRelative(controlsX, startY + mediaPlaceable.height + activeGap)
                            }
                        }
                    }

                    // Right Column: Toggleable Docked Lyrics & Queue Panel
                    val rightPanelWidth = (screenMaxWidth * 0.48f).coerceIn(340.dp, 640.dp)
                    AnimatedVisibility(
                        visible = showRightColumn,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = sidePanelContainerColor,
                            border = BorderStroke(
                                width = 1.dp,
                                color = textBackgroundColor.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier
                                .width(rightPanelWidth)
                                .fillMaxHeight(),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Segmented Lyrics / Queue header bar
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = textBackgroundColor.copy(alpha = 0.1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            PanelTabChip(
                                                label = stringResource("lyrics"),
                                                icon = ExpressiveIcons.Lyrics,
                                                selected = rightPanelTab == DesktopRightPanelTab.LYRICS,
                                                activeBg = iconButtonColor,
                                                activeFg = textButtonColor,
                                                inactiveFg = textBackgroundColor,
                                                onClick = {
                                                    if (!showLyrics) {
                                                        preferences.setShowLyrics(true)
                                                    }
                                                    rightPanelTab = DesktopRightPanelTab.LYRICS
                                                },
                                            )
                                            PanelTabChip(
                                                label = stringResource("queue"),
                                                icon = ExpressiveIcons.QueueMusic,
                                                selected = rightPanelTab == DesktopRightPanelTab.QUEUE,
                                                activeBg = iconButtonColor,
                                                activeFg = textButtonColor,
                                                inactiveFg = textBackgroundColor,
                                                onClick = {
                                                    showQueueSidePanel = true
                                                    rightPanelTab = DesktopRightPanelTab.QUEUE
                                                },
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            preferences.setShowLyrics(false)
                                            showQueueSidePanel = false
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = IconAssets.close(),
                                            contentDescription = stringResource("close"),
                                            tint = mutedTextColor,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                HorizontalDivider(color = textBackgroundColor.copy(alpha = 0.1f))

                                AnimatedContent(
                                    targetState = rightPanelTab,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "player_right_panel_content",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) { tab ->
                                    when (tab) {
                                        DesktopRightPanelTab.LYRICS -> {
                                            LyricsPanel(
                                                title = item.title,
                                                artist = item.artist,
                                                videoId = item.id,
                                                artworkUrl = item.artworkUrl,
                                                durationSec = ((item.durationMs ?: 0L) / 1000L).toInt(),
                                                currentPositionMs = playerState.position,
                                                isPlaying = playerState.isPlaying,
                                                songRomanizeLyrics = currentTrackRomanizeLyrics,
                                                onSetSongRomanizeLyrics = { enabled ->
                                                    coroutineScope.launch {
                                                        val existing = songsById[item.id]
                                                        if (existing == null) {
                                                            database.insertSong(
                                                                item.toSongEntity().copy(romanizeLyrics = enabled),
                                                            )
                                                        } else {
                                                            database.updateSong(existing.copy(romanizeLyrics = enabled))
                                                        }
                                                    }
                                                },
                                                onSeek = { playerState.seekTo(it) },
                                                embedded = true,
                                                contentColor = textBackgroundColor,
                                                accentColor = if (playerBackgroundStyle == PlayerBackgroundStyle.DEFAULT && !pureBlack) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.White
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        DesktopRightPanelTab.QUEUE -> {
                                            DockedQueuePanel(
                                                playerState = playerState,
                                                textColor = textBackgroundColor,
                                                mutedTextColor = mutedTextColor,
                                                accentColor = iconButtonColor,
                                                onStartRadio = ::startRadio,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact / Narrow Single-Column Player Layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showLyrics) {
                            LyricsPanel(
                                title = item.title,
                                artist = item.artist,
                                videoId = item.id,
                                artworkUrl = item.artworkUrl,
                                durationSec = ((item.durationMs ?: 0L) / 1000L).toInt(),
                                currentPositionMs = playerState.position,
                                isPlaying = playerState.isPlaying,
                                songRomanizeLyrics = currentTrackRomanizeLyrics,
                                onSetSongRomanizeLyrics = { enabled ->
                                    coroutineScope.launch {
                                        val existing = songsById[item.id]
                                        if (existing == null) {
                                            database.insertSong(item.toSongEntity().copy(romanizeLyrics = enabled))
                                        } else {
                                            database.updateSong(existing.copy(romanizeLyrics = enabled))
                                        }
                                    }
                                },
                                onSeek = { playerState.seekTo(it) },
                                embedded = isDarkOverlayStyle || pureBlack,
                                contentColor = textBackgroundColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = PlayerHorizontalPadding),
                            )
                        } else {
                            PlayerMediaStage(
                                item = item,
                                playerState = playerState,
                                hiResArtworkUrl = hiResArtworkUrl,
                                artworkFallbackUrls = artworkFallbackUrls,
                                mediaAspectRatio = mediaAspectRatio,
                                onArtworkLoaded = { w, h ->
                                    if (w > 0 && h > 0) {
                                        artworkAspectRatio = (w.toFloat() / h.toFloat()).coerceIn(0.56f, 2.35f)
                                    }
                                },
                                swipeOffsetX = thumbnailOffsetX.value,
                                swipeModifier = artworkSwipeModifier,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = PlayerHorizontalPadding, vertical = 12.dp),
                            )
                        }
                    }

                    PlayerControls(
                        item = item,
                        playerState = playerState,
                        isLiked = isLiked,
                        textColor = textBackgroundColor,
                        mutedTextColor = mutedTextColor,
                        sliderStyle = sliderStyle,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        onToggleLike = { toggleLike(!isLiked) },
                        onAddToPlaylist = { showPlaylistDialog = true },
                        artistsWithId = controlArtistPairs,
                        albumInfo = albumInfo,
                        onOpenArtist = { id, name -> openArtist(id, name) },
                        onOpenAlbum = { id, name -> openAlbum(id, name) },
                        onShowMore = { showMoreMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayerHorizontalPadding),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PlayerConnectedButtonGroup(
                        playerState = playerState,
                        showLyrics = showLyrics,
                        onToggleLyrics = { preferences.setShowLyrics(!showLyrics) },
                        showQueue = queueSheetState.isExpanded,
                        onToggleQueue = {
                            if (queueSheetState.isExpanded) {
                                queueSheetState.collapseSoft()
                            } else {
                                queueSheetState.expandSoft()
                            }
                        },
                        onOpenAudioSettings = { showAudioSettingsDialog = true },
                        onOpenSleepTimer = { showSleepTimerDialog = true },
                        onToggleFullscreen = { playerState.toggleFullscreenOverlay() },
                        isFullscreen = playerState.isFullscreenOverlay,
                        onButtonColor = textBackgroundColor,
                        containerColor = textBackgroundColor.copy(alpha = 0.12f),
                        checkedContainerColor = iconButtonColor,
                        checkedContentColor = textButtonColor,
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (!isWide) {
            PlayerQueueSheet(
                state = queueSheetState,
                playerState = playerState,
                currentItem = item,
                showLyrics = showLyrics,
                onToggleLyrics = { preferences.setShowLyrics(it) },
                onStartRadio = { startRadio() },
                artistsWithId = artistPairs,
                albumInfo = albumInfo,
                onOpenArtist = { artistId, artistName -> openArtist(artistId, artistName) },
                onShowArtistPicker = { showArtistPickerDialog = true },
                onOpenAlbum = { albumId, albumName -> openAlbum(albumId, albumName) },
                onShowDetailsDialog = { showDetailsDialog = true },
                onShowSleepTimerDialog = { showSleepTimerDialog = true },
                onShowAudioSettingsDialog = { showAudioSettingsDialog = true },
                onShowAdvancedDialog = { showAdvancedDialog = true },
                pureBlack = pureBlack,
                textColor = textBackgroundColor,
                mutedTextColor = mutedTextColor,
                database = database,
                downloadService = downloadService,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showPlaylistDialog) {
        PlaylistPickerDialog(
            visible = true,
            playlists = editablePlaylists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                coroutineScope.launch {
                    database.insertPlaylist(playlist)
                    addToPlaylist(playlist.id)
                }
                showPlaylistDialog = false
            },
            onSelectPlaylist = { playlist ->
                addToPlaylist(playlist.id)
                showPlaylistDialog = false
            },
            onDismiss = { showPlaylistDialog = false },
        )
    }

    SleepTimerDialog(
        visible = showSleepTimerDialog,
        isActive = playerState.isSleepTimerActive,
        timeLeftMs = playerState.sleepTimerTimeLeftMs,
        onStartTimer = { durationMs ->
            playerState.startSleepTimer(durationMs)
        },
        onStartEndOfSong = {
            val remaining = (playerState.duration - playerState.position).coerceAtLeast(0L)
            playerState.startSleepTimer(remaining)
        },
        onCancelTimer = { playerState.cancelSleepTimer() },
        onDismiss = { showSleepTimerDialog = false },
    )

    AudioSettingsDialog(
        visible = showAudioSettingsDialog,
        volume = playerState.volume,
        onVolumeChange = { playerState.updateVolume(it) },
        onDismiss = { showAudioSettingsDialog = false },
    )

    TempoPitchDialog(
        visible = showAdvancedDialog,
        tempo = playerState.tempo,
        pitchSemitone = playerState.pitchSemitone,
        onTempoChange = { playerState.updateTempo(it) },
        onPitchChange = { playerState.updatePitchSemitone(it) },
        onReset = { playerState.resetTempoPitch() },
        onDismiss = { showAdvancedDialog = false },
    )

    ArtistPickerDialog(
        visible = showArtistPickerDialog,
        artists = artistsWithId,
        onSelect = { artist ->
            openArtist(artist.id.orEmpty(), artist.name)
            showArtistPickerDialog = false
        },
        onDismiss = { showArtistPickerDialog = false },
    )

    MediaDetailsDialog(
        visible = showDetailsDialog,
        item = item,
        onCopyLink = { copyLink(shareUrl) },
        onOpenInBrowser = { openInBrowser(shareUrl) },
        onDismiss = { showDetailsDialog = false },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerTopBar(
    item: LibraryItem,
    playerState: PlayerState,
    albumName: String?,
    textColor: Color,
    mutedTextColor: Color,
    onCollapse: () -> Unit,
    onShowMore: () -> Unit,
    moreMenuExpanded: Boolean,
    onDismissMoreMenu: () -> Unit,
    dropdownContent: @Composable () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding, vertical = 16.dp),
        content = {
            FilledTonalIconButton(
                onClick = onCollapse,
                shapes = IconButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(12.dp),
                ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = textColor.copy(alpha = 0.12f),
                    contentColor = textColor,
                ),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = ExpressiveIcons.KeyboardArrowDown,
                    contentDescription = stringResource("collapse"),
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource("now_playing").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = mutedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleText = albumName?.takeIf { it.isNotBlank() }
                    ?: item.artist.takeIf { it.isNotBlank() }
                    ?: stringResource("queue")
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playerState.hasVideoStream) {
                    Surface(
                        shape = CircleShape,
                        color = textColor.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            PanelTabChip(
                                label = stringResource("song"),
                                icon = IconAssets.musicNote(),
                                selected = !playerState.isVideoMode,
                                activeBg = textColor,
                                activeFg = if (textColor == Color.White) Color.Black else MaterialTheme.colorScheme.surface,
                                inactiveFg = textColor,
                                onClick = {
                                    if (playerState.isVideoMode) {
                                        playerState.toggleVideoMode()
                                    }
                                },
                            )
                            PanelTabChip(
                                label = "Video",
                                icon = ExpressiveIcons.Movie,
                                selected = playerState.isVideoMode,
                                activeBg = textColor,
                                activeFg = if (textColor == Color.White) Color.Black else MaterialTheme.colorScheme.surface,
                                inactiveFg = textColor,
                                onClick = {
                                    if (!playerState.isVideoMode) {
                                        playerState.toggleVideoMode()
                                    }
                                },
                            )
                        }
                    }
                }

                if (playerState.tempo != 1f || playerState.pitchSemitone != 0) {
                    Surface(
                        shape = CircleShape,
                        color = textColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "${"%.2f".format(playerState.tempo)}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                Box {
                    FilledTonalIconButton(
                        onClick = onShowMore,
                        shapes = IconButtonShapes(
                            shape = CircleShape,
                            pressedShape = RoundedCornerShape(12.dp),
                        ),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textColor.copy(alpha = 0.12f),
                            contentColor = textColor,
                        ),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = IconAssets.moreVert(),
                            contentDescription = stringResource("more_options"),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = onDismissMoreMenu,
                    ) {
                        dropdownContent()
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val totalWidth = constraints.maxWidth
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaceable = measurables[0].measure(looseConstraints)
        val endPlaceable = measurables[2].measure(looseConstraints)
        val availableCenterWidth = (totalWidth - startPlaceable.width - endPlaceable.width - gapPx * 2)
            .coerceAtLeast(0)
        val centerPlaceable = measurables[1].measure(
            looseConstraints.copy(maxWidth = availableCenterWidth),
        )
        val layoutHeight = maxOf(startPlaceable.height, centerPlaceable.height, endPlaceable.height)
        val minCenterX = startPlaceable.width + gapPx
        val maxCenterX = (totalWidth - endPlaceable.width - gapPx - centerPlaceable.width)
            .coerceAtLeast(minCenterX)
        val centerX = ((totalWidth - centerPlaceable.width) / 2).coerceIn(minCenterX, maxCenterX)

        layout(totalWidth, layoutHeight) {
            startPlaceable.placeRelative(0, (layoutHeight - startPlaceable.height) / 2)
            centerPlaceable.placeRelative(centerX, (layoutHeight - centerPlaceable.height) / 2)
            endPlaceable.placeRelative(
                totalWidth - endPlaceable.width,
                (layoutHeight - endPlaceable.height) / 2,
            )
        }
    }
}

@Composable
private fun PlayerMediaStage(
    item: LibraryItem,
    playerState: PlayerState,
    hiResArtworkUrl: String?,
    artworkFallbackUrls: List<String>,
    mediaAspectRatio: Float,
    onArtworkLoaded: (width: Int, height: Int) -> Unit,
    swipeOffsetX: Float,
    swipeModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val safeRatio = mediaAspectRatio.takeIf { it.isFinite() && it > 0.1f }?.coerceIn(0.56f, 2.35f) ?: 1f
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else safeRatio
        val stageSizeModifier = if (safeRatio >= containerRatio) {
            Modifier.fillMaxWidth().aspectRatio(safeRatio)
        } else {
            Modifier.fillMaxHeight().aspectRatio(safeRatio)
        }
        if (playerState.isVideoMode) {
            Box(
                modifier = stageSizeModifier
                    .shadow(20.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
            ) {
                VideoPlayerSurface(
                    videoPlayer = playerState.videoPlayer,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = stageSizeModifier.then(swipeModifier),
            ) {
                RemoteImage(
                    url = hiResArtworkUrl,
                    contentDescription = item.title,
                    fallbackUrls = artworkFallbackUrls,
                    contentScale = if (safeRatio > 1.08f || safeRatio < 0.92f) {
                        ContentScale.Fit
                    } else {
                        ContentScale.Crop
                    },
                    onImageLoaded = onArtworkLoaded,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
                        .shadow(22.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
        }
    }
}

@Composable
private fun PanelTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeBg: Color,
    activeFg: Color,
    inactiveFg: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) activeBg else Color.Transparent,
        contentColor = if (selected) activeFg else inactiveFg.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DockedQueuePanel(
    playerState: PlayerState,
    textColor: Color,
    mutedTextColor: Color,
    accentColor: Color,
    onStartRadio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = playerState.queue
    val currentIndex = playerState.currentQueueIndex
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex in queue.indices) {
            runCatching { listState.animateScrollToItem(currentIndex) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val totalDurationMs = queue.sumOf { it.durationMs ?: 0L }
            val metaText = if (totalDurationMs > 0L) {
                "${queue.size} • ${formatTime(totalDurationMs)}"
            } else {
                "${queue.size}"
            }
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelMedium,
                color = mutedTextColor,
            )

            Surface(
                shape = CircleShape,
                color = textColor.copy(alpha = 0.12f),
                contentColor = textColor,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onStartRadio),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.radio(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource("start_radio"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (queue.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = stringResource("nothing_playing"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedTextColor,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(queue, key = { index, qItem -> "${qItem.id}_$index" }) { index, queueItem ->
                    val isCurrent = index == currentIndex
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            accentColor.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                playerState.playQueue(queue, index)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            RemoteImage(
                                url = queueItem.artworkUrl,
                                contentDescription = queueItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = queueItem.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) accentColor else textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = queueItem.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            queueItem.durationMs?.takeIf { it > 0L }?.let { ms ->
                                Text(
                                    text = formatTime(ms),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedTextColor,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            playerState.moveQueueItem(index, index - 1)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = IconAssets.arrowUpward(),
                                        contentDescription = null,
                                        tint = if (index > 0) mutedTextColor else mutedTextColor.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < queue.lastIndex) {
                                            playerState.moveQueueItem(index, index + 1)
                                        }
                                    },
                                    enabled = index < queue.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = IconAssets.arrowDownward(),
                                        contentDescription = null,
                                        tint = if (index < queue.lastIndex) {
                                            mutedTextColor
                                        } else {
                                            mutedTextColor.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { playerState.removeFromQueue(index) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = IconAssets.close(),
                                        contentDescription = null,
                                        tint = mutedTextColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerOverflowMenuItems(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    hasArtists: Boolean,
    hasAlbum: Boolean,
    onAddToPlaylist: () -> Unit,
    onStartRadio: () -> Unit,
    onDownloadToggle: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenTempoPitch: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShowDetails: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource("add_to_playlist")) },
        leadingIcon = { Icon(IconAssets.playlistAdd(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onAddToPlaylist,
    )
    DropdownMenuItem(
        text = { Text(stringResource("start_radio")) },
        leadingIcon = { Icon(IconAssets.radio(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onStartRadio,
    )
    DropdownMenuItem(
        text = {
            Text(
                when {
                    isDownloaded -> stringResource("remove_download")
                    isDownloading -> stringResource("downloading")
                    else -> stringResource("download")
                },
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (isDownloaded) IconAssets.delete() else IconAssets.download(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onDownloadToggle,
    )
    if (hasArtists) {
        DropdownMenuItem(
            text = { Text(stringResource("view_artist")) },
            leadingIcon = { Icon(IconAssets.artist(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = onOpenArtist,
        )
    }
    if (hasAlbum) {
        DropdownMenuItem(
            text = { Text(stringResource("view_album")) },
            leadingIcon = { Icon(IconAssets.album(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = onOpenAlbum,
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource("sleep_timer")) },
        leadingIcon = { Icon(IconAssets.bedtime(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onOpenSleepTimer,
    )
    DropdownMenuItem(
        text = { Text(stringResource("equalizer")) },
        leadingIcon = { Icon(IconAssets.equalizer(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onOpenAudioSettings,
    )
    DropdownMenuItem(
        text = { Text(stringResource("advanced")) },
        leadingIcon = { Icon(IconAssets.tune(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onOpenTempoPitch,
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource("copy_link")) },
        leadingIcon = { Icon(IconAssets.link(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onCopyLink,
    )
    DropdownMenuItem(
        text = { Text(stringResource("share")) },
        leadingIcon = { Icon(IconAssets.openInNew(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onOpenInBrowser,
    )
    DropdownMenuItem(
        text = { Text(stringResource("details")) },
        leadingIcon = { Icon(IconAssets.info(), contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onShowDetails,
    )
}

@Composable
private fun EmptyPlayerState(
    onCollapsePlayer: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        IconButton(
            onClick = onCollapsePlayer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = ExpressiveIcons.KeyboardArrowDown,
                contentDescription = stringResource("collapse"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                imageVector = IconAssets.musicNote(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource("nothing_playing"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun toHighResArtworkUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    var result = url
    result = result.replace(Regex("w\\d+-h\\d+"), "w1200-h1200")
    result = result.replace(Regex("s\\d+"), "s1200")
    result = result.replace("=w60-h60", "=w1200-h1200")
    result = result.replace("=w120-h120", "=w1200-h1200")
    result = result.replace("hqdefault", "maxresdefault")
    result = result.replace("mqdefault", "maxresdefault")
    result = result.replace("sddefault", "maxresdefault")
    return result
}
