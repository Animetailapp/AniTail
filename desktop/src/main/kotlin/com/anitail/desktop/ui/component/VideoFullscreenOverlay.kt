package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.lyrics.DesktopLyricsService
import com.anitail.desktop.lyrics.LyricsResult
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.screen.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoFullscreenOverlay(
    playerState: PlayerState,
    onExitFullscreen: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    songRomanizeLyrics: Boolean = true,
    onSetSongRomanizeLyrics: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!playerState.isFullscreenOverlay) return

    val focusRequester = remember { FocusRequester() }
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastActivityTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var shareCopied by remember { mutableStateOf(false) }

    LaunchedEffect(shareCopied) {
        if (shareCopied) {
            delay(1500L)
            shareCopied = false
        }
    }

    fun copyTrackUrl(videoId: String) {
        runCatching {
            val url = "https://music.youtube.com/watch?v=$videoId"
            val selection = StringSelection(url)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            shareCopied = true
        }
    }

    fun resetInactivity() {
        isControlsVisible = true
        lastActivityTime = System.currentTimeMillis()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        resetInactivity()
    }

    // Auto-hide controls after 2.5 seconds of inactivity (unless paused, dragging, or a panel/menu is open)
    LaunchedEffect(isControlsVisible, playerState.isPlaying, isDraggingSlider, showQueue, showLyrics, showMoreMenu) {
        if (!isControlsVisible || !playerState.isPlaying || isDraggingSlider || showQueue || showLyrics || showMoreMenu) {
            return@LaunchedEffect
        }
        while (true) {
            delay(250)
            if (System.currentTimeMillis() - lastActivityTime >= 2500L) {
                isControlsVisible = false
                break
            }
        }
    }

    val blankCursor = remember {
        try {
            val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val cursor = Toolkit.getDefaultToolkit().createCustomCursor(img, Point(0, 0), "blank_cursor")
            PointerIcon(cursor)
        } catch (_: Throwable) {
            PointerIcon.Default
        }
    }

    val pointerIcon = if (!isControlsVisible) blankCursor else PointerIcon.Default

    val item = playerState.currentItem
    val duration = playerState.duration

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerHoverIcon(pointerIcon)
            .onPointerEvent(PointerEventType.Move) { resetInactivity() }
            .onPointerEvent(PointerEventType.Press) { resetInactivity() }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                resetInactivity()
                when (event.key) {
                    Key.Escape -> {
                        when {
                            showQueue -> showQueue = false
                            showLyrics -> showLyrics = false
                            else -> onExitFullscreen()
                        }
                        true
                    }
                    Key.F -> {
                        onExitFullscreen()
                        true
                    }
                    Key.Spacebar -> {
                        playerState.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        val target = (playerState.position - 5000L).coerceAtLeast(0L)
                        playerState.seekTo(target)
                        true
                    }
                    Key.DirectionRight -> {
                        val target = (playerState.position + 5000L).coerceAtMost(playerState.duration)
                        playerState.seekTo(target)
                        true
                    }
                    Key.DirectionUp -> {
                        val next = (playerState.volume + 0.05).coerceIn(0.0, 1.0)
                        playerState.updateVolume(next)
                        true
                    }
                    Key.DirectionDown -> {
                        val next = (playerState.volume - 0.05).coerceIn(0.0, 1.0)
                        playerState.updateVolume(next)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Main Visual Content: Video or Album Art
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                if (playerState.isVideoMode && playerState.hasVideoStream) {
                    VideoPlayerSurface(
                        videoPlayer = playerState.videoPlayer,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                resetInactivity()
                                playerState.togglePlayPause()
                            },
                    )
                } else {
                    val thumb = item?.artworkUrl
                    if (!thumb.isNullOrBlank()) {
                        RemoteImage(
                            url = thumb,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxHeight(0.65f)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = ExpressiveIcons.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }

                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 3.5.dp,
                    )
                }

                // Live Subtitle / Caption overlay when CC is active (isolated from outer recomposition)
                if (showSubtitles && item != null) {
                    FullscreenSubtitleOverlay(
                        playerState = playerState,
                        videoId = item.id,
                        title = item.title,
                        artist = item.artist,
                        durationMs = duration.takeIf { it > 0L } ?: item.durationMs ?: 0L,
                        isControlsVisible = isControlsVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            if (showQueue) {
                QueueSideSheet(
                    visible = true,
                    playerState = playerState,
                    onClose = { showQueue = false },
                    modifier = Modifier.fillMaxHeight(),
                )
            } else if (showLyrics && item != null) {
                LyricsSideSheet(
                    visible = true,
                    title = item.title,
                    artist = item.artist,
                    videoId = item.id,
                    artworkUrl = item.artworkUrl,
                    durationSec = ((duration.takeIf { it > 0L } ?: item.durationMs ?: 0L) / 1000L).toInt(),
                    currentPositionMs = playerState.position,
                    isPlaying = playerState.isPlaying,
                    songRomanizeLyrics = songRomanizeLyrics,
                    onSetSongRomanizeLyrics = onSetSongRomanizeLyrics,
                    onSeek = { playerState.seekTo(it) },
                    onClose = { showLyrics = false },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }

        // Animated Overlays (Top Back Button + Metrolist-style Bottom Controls)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar (subtle gradient + Back button on top-left like Metrolist)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                            ),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        IconButton(
                            onClick = onExitFullscreen,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(
                                imageVector = IconAssets.arrowBack(),
                                contentDescription = stringResource("exit_fullscreen"),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                // Bottom Shadow Gradient & Metrolist-style Controls Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.92f),
                                ),
                            ),
                        )
                        .padding(top = 40.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                    ) {
                        // Row 1: Slider and timestamps (isolated composable to prevent recomposing entire overlay every 100ms)
                        FullscreenSeekbarRow(
                            playerState = playerState,
                            onDraggingChange = { dragging ->
                                isDraggingSlider = dragging
                                resetInactivity()
                            },
                            onUserInteract = { resetInactivity() },
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Row 2: 3-column Metrolist-style layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left: Artwork + Title/Artist + Connected [Share, Like, More] pill
                            Row(
                                modifier = Modifier.weight(1.35f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!item.artworkUrl.isNullOrBlank()) {
                                            RemoteImage(
                                                url = item.artworkUrl,
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = IconAssets.musicNote(),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .padding(end = 12.dp),
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.basicMarquee(),
                                        )
                                        Text(
                                            text = item.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.basicMarquee(),
                                        )
                                    }

                                    // Metrolist-style [Share, Like] pill + circular (More) button
                                    val pillContainer = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                    val pillContent = MaterialTheme.colorScheme.onSecondaryContainer
                                    val pillColors = ToggleButtonDefaults.colors(
                                        containerColor = pillContainer,
                                        contentColor = pillContent,
                                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    val likeColors = ToggleButtonDefaults.colors(
                                        containerColor = pillContainer,
                                        contentColor = pillContent,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    val leftShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                                        checkedShape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            bottomStart = 18.dp,
                                            topEnd = 6.dp,
                                            bottomEnd = 6.dp,
                                        ),
                                    )
                                    val rightShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                                        checkedShape = RoundedCornerShape(
                                            topStart = 6.dp,
                                            bottomStart = 6.dp,
                                            topEnd = 18.dp,
                                            bottomEnd = 18.dp,
                                        ),
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                    ) {
                                        ToggleButton(
                                            checked = shareCopied,
                                            onCheckedChange = {
                                                resetInactivity()
                                                copyTrackUrl(item.id)
                                            },
                                            shapes = leftShapes,
                                            colors = pillColors,
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 36.dp, height = 36.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (shareCopied) IconAssets.check() else IconAssets.share(),
                                                contentDescription = stringResource("share"),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }

                                        ToggleButton(
                                            checked = isLiked,
                                            onCheckedChange = {
                                                resetInactivity()
                                                onToggleLike()
                                            },
                                            shapes = rightShapes,
                                            colors = likeColors,
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 36.dp, height = 36.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                                contentDescription = stringResource("like"),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Box {
                                        ToggleButton(
                                            checked = showMoreMenu,
                                            onCheckedChange = {
                                                resetInactivity()
                                                showMoreMenu = !showMoreMenu
                                            },
                                            shapes = ToggleButtonShapes(
                                                shape = CircleShape,
                                                pressedShape = RoundedCornerShape(12.dp),
                                                checkedShape = CircleShape,
                                            ),
                                            colors = pillColors,
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = IconAssets.moreVert(),
                                                contentDescription = stringResource("more_options"),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }

                                        DropdownMenu(
                                                expanded = showMoreMenu,
                                                onDismissRequest = { showMoreMenu = false },
                                            ) {
                                                if (playerState.hasVideoStream) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(
                                                                    if (playerState.isVideoMode) "switch_to_audio" else "video_viewer",
                                                                ),
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = ExpressiveIcons.Movie,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showMoreMenu = false
                                                            playerState.toggleVideoMode()
                                                        },
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(stringResource("copy_link")) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = IconAssets.share(),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        copyTrackUrl(item.id)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource("exit_fullscreen")) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = ExpressiveIcons.FullscreenExit,
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onExitFullscreen()
                                                    },
                                                )
                                            }
                                    }
                                }
                            }

                            // Center: Prev / PlayPause / Next (kept identical to AniTail)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            resetInactivity()
                                            playerState.skipToPrevious()
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = ExpressiveIcons.SkipPrevious,
                                            contentDescription = "Previous",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }

                                    FilledIconButton(
                                        onClick = {
                                            resetInactivity()
                                            playerState.togglePlayPause()
                                        },
                                        modifier = Modifier.size(56.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.PlayArrow,
                                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            resetInactivity()
                                            playerState.skipToNext()
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = ExpressiveIcons.SkipNext,
                                            contentDescription = "Next",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }

                            // Right: Metrolist-style single connected pill (Queue, Lyrics, Shuffle, Repeat, Volume, Video, CC, Miniplayer, Fullscreen)
                            Row(
                                modifier = Modifier.weight(1.15f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlayerConnectedButtonGroup(
                                    playerState = playerState,
                                    showLyrics = showLyrics,
                                    onToggleLyrics = {
                                        resetInactivity()
                                        showLyrics = !showLyrics
                                        if (showLyrics) showQueue = false
                                    },
                                    showQueue = showQueue,
                                    onToggleQueue = {
                                        resetInactivity()
                                        showQueue = !showQueue
                                        if (showQueue) showLyrics = false
                                    },
                                    onOpenAudioSettings = {},
                                    onOpenSleepTimer = {},
                                    onToggleFullscreen = onExitFullscreen,
                                    isFullscreen = true,
                                    onVideoClick = {
                                        resetInactivity()
                                        playerState.toggleVideoMode()
                                    },
                                    isVideoActive = playerState.isVideoMode,
                                    onSubtitleClick = {
                                        resetInactivity()
                                        showSubtitles = !showSubtitles
                                    },
                                    isSubtitleActive = showSubtitles,
                                    onMiniplayerClick = {
                                        resetInactivity()
                                        onExitFullscreen()
                                        playerState.toggleMiniPlayer()
                                    },
                                    showCastButton = false,
                                    showMiniplayerButton = true,
                                    sectioned = false,
                                    onButtonColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenSeekbarRow(
    playerState: PlayerState,
    onDraggingChange: (Boolean) -> Unit,
    onUserInteract: () -> Unit,
) {
    val duration = playerState.duration
    val position = playerState.position
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var sliderProgress by remember(position, duration) { mutableFloatStateOf(progress) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(if (isDragging) (sliderProgress * duration).toLong() else position),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Slider(
            value = if (isDragging) sliderProgress else progress,
            onValueChange = {
                onUserInteract()
                if (!isDragging) {
                    isDragging = true
                    onDraggingChange(true)
                }
                sliderProgress = it
            },
            onValueChangeFinished = {
                val targetMs = (sliderProgress * duration).toLong()
                playerState.seekTo(targetMs)
                isDragging = false
                onDraggingChange(false)
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun FullscreenSubtitleOverlay(
    playerState: PlayerState,
    videoId: String,
    title: String,
    artist: String,
    durationMs: Long,
    isControlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var lyricsResult by remember(videoId) { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }

    LaunchedEffect(videoId, title, artist, durationMs) {
        isLoading = true
        lyricsResult = withContext(Dispatchers.IO) {
            val durationSec = (durationMs / 1000L).toInt().takeIf { it > 0 } ?: -1
            val primary = DesktopLyricsService.getLyrics(
                title = title,
                artist = artist,
                durationSec = durationSec,
                videoId = videoId,
            ).getOrNull()
            if (primary != null && primary.hasSyncedLyrics) {
                primary
            } else {
                val options = DesktopLyricsService.getAllLyricsOptions(
                    title = title,
                    artist = artist,
                    durationSec = durationSec,
                    videoId = videoId,
                )
                options.firstOrNull { it.hasSyncedLyrics } ?: primary
            }
        }
        isLoading = false
    }

    val position = playerState.position
    val captionText = when {
        isLoading -> null
        lyricsResult?.hasSyncedLyrics == true -> lyricsResult?.getCurrentLine(position)?.text
        else -> null
    }

    if (!captionText.isNullOrBlank()) {
        Box(
            modifier = modifier
                .padding(
                    start = 48.dp,
                    end = 48.dp,
                    bottom = if (isControlsVisible) 156.dp else 44.dp,
                )
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = captionText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
