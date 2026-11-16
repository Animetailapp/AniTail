package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.screen.formatTime
import com.anitail.desktop.i18n.stringResource
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoFullscreenOverlay(
    playerState: PlayerState,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!playerState.isFullscreenOverlay) return

    val focusRequester = remember { FocusRequester() }
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastActivityTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    fun resetInactivity() {
        isControlsVisible = true
        lastActivityTime = System.currentTimeMillis()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        resetInactivity()
    }

    // Auto-hide controls after 2.5 seconds of inactivity
    LaunchedEffect(isControlsVisible, playerState.isPlaying, isDraggingSlider) {
        if (!isControlsVisible || !playerState.isPlaying || isDraggingSlider) return@LaunchedEffect
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
    val position = playerState.position
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var sliderProgress by remember(position, duration) {
        mutableFloatStateOf(progress)
    }

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
                    Key.Escape, Key.F -> {
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
        // Main Visual Content: Video or Album Art
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
            // Audio Artwork Display
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

        // Animated Overlays (Top and Bottom Controls)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Shadow Gradient & Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                            ),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = item?.title ?: "",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                            Text(
                                text = item?.artist ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        FilledIconButton(
                            onClick = onExitFullscreen,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = ExpressiveIcons.FullscreenExit,
                                contentDescription = stringResource("exit_fullscreen"),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Bottom Shadow Gradient & Controls Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                    ) {
                        // Slider and timestamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTime(position),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Slider(
                                value = if (isDraggingSlider) sliderProgress else progress,
                                onValueChange = {
                                    isDraggingSlider = true
                                    sliderProgress = it
                                },
                                onValueChangeFinished = {
                                    val targetMs = (sliderProgress * duration).toLong()
                                    playerState.seekTo(targetMs)
                                    isDraggingSlider = false
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
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left: Video / Audio toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (playerState.hasVideoStream) {
                                    FilledIconButton(
                                        onClick = { playerState.toggleVideoMode() },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (playerState.isVideoMode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White.copy(alpha = 0.15f)
                                            },
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = ExpressiveIcons.Movie,
                                            contentDescription = "Video",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            // Center: Prev / PlayPause / Next
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                IconButton(
                                    onClick = { playerState.skipToPrevious() },
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
                                    onClick = { playerState.togglePlayPause() },
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
                                    onClick = { playerState.skipToNext() },
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

                            // Right: Volume indicator & Fullscreen exit
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val volumePercent = (playerState.volume * 100f).roundToInt()
                                Icon(
                                    imageVector = when {
                                        playerState.volume <= 0.01 -> ExpressiveIcons.VolumeOff
                                        playerState.volume < 0.5 -> ExpressiveIcons.VolumeDown
                                        else -> ExpressiveIcons.VolumeUp
                                    },
                                    contentDescription = "Volume",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$volumePercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

