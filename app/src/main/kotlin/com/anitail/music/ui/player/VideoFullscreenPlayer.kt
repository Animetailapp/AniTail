package com.anitail.music.ui.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.db.entities.LyricsEntity
import com.anitail.music.extensions.metadata
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.extensions.toggleRepeatMode
import com.anitail.music.lyrics.LyricsUtils
import com.anitail.music.models.MediaMetadata
import com.anitail.music.ui.component.Lyrics
import com.anitail.music.ui.component.MediaMetadataListItem
import com.anitail.music.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun VideoFullscreenPlayer(
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val videoSize by playerConnection.videoSize.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val isLiked =
        if (librarySong?.song?.isEpisode == true) {
            librarySong?.song?.inLibrary != null
        } else {
            librarySong?.song?.liked == true
        }
    val lyricsEntity by database.lyrics(mediaMetadata.id).collectAsState(initial = null)

    var position by remember { mutableLongStateOf(playerConnection.player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember {
        mutableLongStateOf(
            playerConnection.player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L,
        )
    }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var isLyricsVisible by rememberSaveable { mutableStateOf(false) }
    var isQueueVisible by rememberSaveable { mutableStateOf(false) }
    var isSubtitlesEnabled by rememberSaveable { mutableStateOf(false) }
    var subtitleOffset by remember { mutableStateOf(IntOffset.Zero) }
    var interactionTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(playbackState, isPlaying) {
        while (isActive) {
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = playerConnection.player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L
            delay(if (isPlaying) 200L else 500L)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interactionTrigger, isLyricsVisible, isQueueVisible) {
        if (controlsVisible && isPlaying && !isLyricsVisible && !isQueueVisible) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity?.requestedOrientation = previousOrientation
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        when {
            isLyricsVisible || isQueueVisible -> {
                isLyricsVisible = false
                isQueueVisible = false
            }
            else -> onDismiss()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (!mediaMetadata.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                        .alpha(0.32f),
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            controlsVisible = !controlsVisible
                            interactionTrigger++
                        },
            ) {
                MusicVideoSurface(
                    player = playerConnection.player,
                    artworkUrl = mediaMetadata.thumbnailUrl,
                    cornerRadius = 0.dp,
                    fillContainer = false,
                    useSurfaceView = true,
                    showControls = false,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isSubtitlesEnabled) {
                    VideoSubtitleOverlay(
                        lyricsEntity = lyricsEntity,
                        positionMs = sliderPosition ?: position,
                        offset = subtitleOffset,
                        onOffsetChange = { subtitleOffset = it },
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    FullscreenControlsOverlay(
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playbackState = playbackState,
                        position = sliderPosition ?: position,
                        duration = duration,
                        isLiked = isLiked,
                        isSubtitlesEnabled = isSubtitlesEnabled,
                        isLyricsVisible = isLyricsVisible,
                        isQueueVisible = isQueueVisible,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        onUserInteraction = { interactionTrigger++ },
                        onDismiss = onDismiss,
                        onEnterPip =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                {
                                    context.findActivity()?.let { activity ->
                                        PictureInPictureHelper.enterPictureInPicture(
                                            activity = activity,
                                            isPlaying = isPlaying,
                                            videoSize = videoSize,
                                            hasVideo = true,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        onSeekPrevious = { playerConnection.seekToPrevious() },
                        onPlayPause = {
                            if (playbackState == Player.STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                        onSeekNext = { playerConnection.seekToNext() },
                        onSliderChange = { sliderPosition = it },
                        onSliderChangeFinished = {
                            sliderPosition?.let { target ->
                                playerConnection.player.seekTo(target)
                                position = target
                            }
                            sliderPosition = null
                        },
                        onToggleLike = { playerConnection.toggleLike() },
                        onToggleSubtitles = { isSubtitlesEnabled = !isSubtitlesEnabled },
                        onToggleLyrics = {
                            val next = !isLyricsVisible
                            isLyricsVisible = next
                            if (next) isQueueVisible = false
                        },
                        onToggleQueue = {
                            val next = !isQueueVisible
                            isQueueVisible = next
                            if (next) isLyricsVisible = false
                        },
                        onToggleShuffle = {
                            playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                        },
                        onToggleRepeat = { playerConnection.player.toggleRepeatMode() },
                        onOpenMenu = onOpenMenu,
                    )
                }
            }

            AnimatedVisibility(
                visible = isLyricsVisible || isQueueVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    contentColor = Color.White,
                    modifier =
                        Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(WindowInsets.systemBars.asPaddingValues())
                                .padding(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (isLyricsVisible) R.string.lyrics else R.string.queue,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            IconButton(
                                onClick = {
                                    isLyricsVisible = false
                                    isQueueVisible = false
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White,
                                )
                            }
                        }

                        if (isLyricsVisible) {
                            Lyrics(
                                sliderPositionProvider = { sliderPosition ?: position },
                                showLyrics = true,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            )
                        } else if (isQueueVisible) {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                itemsIndexed(
                                    items = queueWindows,
                                    key = { index, window -> "${window.mediaItem.mediaId}_$index" },
                                ) { _, window ->
                                    val metadata = window.mediaItem.metadata ?: return@itemsIndexed
                                    val isCurrent = window.firstPeriodIndex == currentWindowIndex
                                    MediaMetadataListItem(
                                        mediaMetadata = metadata,
                                        isActive = isCurrent,
                                        isPlaying = isPlaying,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                    playerConnection.player.playWhenReady = true
                                                },
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
private fun FullscreenControlsOverlay(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    playbackState: Int,
    position: Long,
    duration: Long,
    isLiked: Boolean,
    isSubtitlesEnabled: Boolean,
    isLyricsVisible: Boolean,
    isQueueVisible: Boolean,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onUserInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onEnterPip: (() -> Unit)?,
    onSeekPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekNext: () -> Unit,
    onSliderChange: (Long) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.65f),
                        0.3f to Color.Black.copy(alpha = 0.15f),
                        0.7f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                )
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                val artistText = mediaMetadata.artistName ?: mediaMetadata.artists.joinToString { it.name }
                if (artistText.isNotBlank()) {
                    Text(
                        text = artistText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            if (onEnterPip != null) {
                IconButton(
                    onClick = {
                        onUserInteraction()
                        onEnterPip()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = stringResource(R.string.enter_pip),
                        tint = Color.White,
                    )
                }
            }

            IconButton(
                onClick = {
                    onUserInteraction()
                    onDismiss()
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = stringResource(R.string.exit_fullscreen),
                    tint = Color.White,
                )
            }
        }

        // Center Transport Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                enabled = canSkipPrevious,
                onClick = {
                    onUserInteraction()
                    onSeekPrevious()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.previous),
                    tint = if (canSkipPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp),
                )
            }

            FilledIconButton(
                onClick = {
                    onUserInteraction()
                    onPlayPause()
                },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor = Color.White,
                    ),
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            when {
                                playbackState == Player.STATE_ENDED -> R.drawable.replay
                                isPlaying -> R.drawable.pause
                                else -> R.drawable.play
                            },
                        ),
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(
                enabled = canSkipNext,
                onClick = {
                    onUserInteraction()
                    onSeekNext()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // Bottom Seekbar & Actions
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = makeTimeString(position),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Slider(
                    value = position.toFloat(),
                    valueRange = 0f..(if (duration > 0L) duration.toFloat() else 0f),
                    onValueChange = {
                        onUserInteraction()
                        onSliderChange(it.toLong())
                    },
                    onValueChangeFinished = {
                        onUserInteraction()
                        onSliderChangeFinished()
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                )
                Text(
                    text = if (duration > 0L) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleLike()
                        },
                    ) {
                        Icon(
                            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                            contentDescription = null,
                            tint = if (isLiked) MaterialTheme.colorScheme.error else Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleSubtitles()
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (isSubtitlesEnabled) {
                                    Icons.Rounded.ClosedCaption
                                } else {
                                    Icons.Rounded.ClosedCaptionDisabled
                                },
                            contentDescription = stringResource(R.string.subtitles),
                            tint = if (isSubtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleLyrics()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = stringResource(R.string.lyrics),
                            tint = if (isLyricsVisible) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleQueue()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.queue),
                            tint = if (isQueueVisible) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleShuffle()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f),
                        )
                    }

                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onToggleRepeat()
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                                        R.drawable.repeat_one
                                    } else {
                                        R.drawable.repeat
                                    },
                                ),
                            contentDescription = stringResource(R.string.repeat_mode_all),
                            tint =
                                if (repeatMode != Player.REPEAT_MODE_OFF) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.65f)
                                },
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onOpenMenu()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSubtitleOverlay(
    lyricsEntity: LyricsEntity?,
    positionMs: Long,
    offset: IntOffset,
    onOffsetChange: (IntOffset) -> Unit,
) {
    val rawLyrics = lyricsEntity?.lyrics?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND && it.isNotBlank() } ?: return
    val entries = remember(rawLyrics) { LyricsUtils.parseLyrics(rawLyrics) }
    if (entries.isEmpty()) return

    val currentIndex = LyricsUtils.findCurrentLineIndex(entries, positionMs)
    val currentEntry = entries.getOrNull(currentIndex)?.takeIf { it.text.isNotBlank() }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var subtitleSize by remember { mutableStateOf(IntSize.Zero) }
    val boundedOffset = clampSubtitleOffset(offset, viewportSize, subtitleSize)
    val latestOffset by rememberUpdatedState(boundedOffset)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp, start = 32.dp, end = 32.dp)
                .onSizeChanged { viewportSize = it },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = currentEntry != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier =
                Modifier
                    .offset { boundedOffset }
                    .onSizeChanged { subtitleSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { latestOnOffsetChange(IntOffset.Zero) })
                    }
                    .pointerInput(viewportSize, subtitleSize) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            latestOnOffsetChange(
                                clampSubtitleOffset(
                                    IntOffset(
                                        (latestOffset.x + dragAmount.x).roundToInt(),
                                        (latestOffset.y + dragAmount.y).roundToInt(),
                                    ),
                                    viewportSize,
                                    subtitleSize,
                                ),
                            )
                        }
                    },
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
                modifier =
                    Modifier
                        .wrapContentSize(Alignment.Center)
                        .animateContentSize(tween(180))
                        .widthIn(min = 100.dp, max = 560.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = currentEntry?.text.orEmpty(),
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(150))
                        },
                        label = "videoSubtitleText",
                    ) { text ->
                        Text(
                            text = text,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun clampSubtitleOffset(
    offset: IntOffset,
    viewportSize: IntSize,
    subtitleSize: IntSize,
): IntOffset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || subtitleSize.width <= 0 || subtitleSize.height <= 0) {
        return offset
    }
    val maxX = ((viewportSize.width - subtitleSize.width) / 2).coerceAtLeast(0)
    val maxUp = (viewportSize.height - subtitleSize.height).coerceAtLeast(0)
    return IntOffset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxUp, 0),
    )
}

