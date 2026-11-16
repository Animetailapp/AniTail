package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.MiniPlayerHeight
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import kotlin.math.roundToInt

/**
 * Material 3 Expressive persistent mini player bar for desktop.
 * Keeps full compatibility with the root scaffold call site while supporting optional
 * desktop side-sheet toggles (Lyrics, Queue) and audio settings callbacks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleLyricsSideSheet: (() -> Unit)? = null,
    onToggleQueueSideSheet: (() -> Unit)? = null,
    isLyricsSideSheetOpen: Boolean = false,
    isQueueSideSheetOpen: Boolean = false,
    onOpenAudioSettings: (() -> Unit)? = null,
    onToggleMiniPlayer: (() -> Unit)? = null,
) {
    val item = playerState.currentItem
    val preferences = remember { DesktopPreferences.getInstance() }
    val swipeToSkip by preferences.swipeThumbnail.collectAsState()
    val swipeSensitivity by preferences.swipeSensitivity.collectAsState()

    val volume = playerState.volume.toFloat().coerceIn(0f, 1f)
    var lastNonZeroVolume by remember {
        mutableFloatStateOf(if (volume > 0.01f) volume else 0.7f)
    }
    LaunchedEffect(volume) {
        if (volume > 0.01f) {
            lastNonZeroVolume = volume
        }
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_player_drag_x",
    )

    val playButtonCorner by animateDpAsState(
        targetValue = if (playerState.isPlaying) 12.dp else 21.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mini_player_play_corner",
    )

    val totalDurationMs = if (playerState.duration > 0L) {
        playerState.duration
    } else {
        item?.durationMs ?: 0L
    }
    val progress = playerState.progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
            ) {
                // Clickable & swipeable track metadata region
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .offset { IntOffset(animatedDragX.roundToInt(), 0) }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = item != null, onClick = onOpenFullPlayer)
                        .pointerInput(swipeToSkip, swipeSensitivity, item?.id) {
                            if (!swipeToSkip || item == null) return@pointerInput
                            val threshold = (72.dp.toPx() / swipeSensitivity.coerceIn(0.3f, 2f))
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        dragOffsetX < -threshold && playerState.canSkipNext -> {
                                            playerState.skipToNext()
                                        }
                                        dragOffsetX > threshold && playerState.canSkipPrevious -> {
                                            playerState.skipToPrevious()
                                        }
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-120f, 120f)
                                },
                            )
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item?.artworkUrl != null) {
                            RemoteImage(
                                url = item.artworkUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = IconAssets.musicNote(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    AnimatedContent(
                        targetState = item,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "mini_player_track_text",
                        modifier = Modifier.weight(1f),
                    ) { track ->
                        if (track == null) {
                            Column {
                                Text(
                                    text = stringResource("nothing_playing"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(),
                                )
                                val timeSuffix = if (totalDurationMs > 0L) {
                                    " • ${formatDuration(playerState.position)} / ${formatDuration(totalDurationMs)}"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "${track.artist}$timeSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Expressive transport & side-sheet actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (onToggleLyricsSideSheet != null) {
                        SideSheetToggleButton(
                            checked = isLyricsSideSheetOpen,
                            onClick = onToggleLyricsSideSheet,
                            icon = ExpressiveIcons.Lyrics,
                            contentDescription = stringResource("lyrics"),
                        )
                    }
                    if (onToggleQueueSideSheet != null) {
                        SideSheetToggleButton(
                            checked = isQueueSideSheetOpen,
                            onClick = onToggleQueueSideSheet,
                            icon = ExpressiveIcons.QueueMusic,
                            contentDescription = stringResource("queue"),
                        )
                    }
                    if (onOpenAudioSettings != null) {
                        IconButton(
                            onClick = onOpenAudioSettings,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = ExpressiveIcons.Equalizer,
                                contentDescription = stringResource("equalizer"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (onToggleMiniPlayer != null) {
                        IconButton(
                            onClick = onToggleMiniPlayer,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = ExpressiveIcons.PictureInPicture,
                                contentDescription = stringResource("open_miniplayer"),
                                tint = if (playerState.showMiniPlayer) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = { playerState.skipToPrevious() },
                        enabled = playerState.canSkipPrevious,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipPrevious,
                            contentDescription = stringResource("previous"),
                            tint = if (playerState.canSkipPrevious) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Morphing M3 Expressive Play/Pause with circular wavy progress ring
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            stroke = Stroke(width = strokePx, cap = StrokeCap.Round),
                            trackStroke = Stroke(width = strokePx, cap = StrokeCap.Round),
                            gapSize = 2.dp,
                            amplitude = {
                                if (playerState.isPlaying && !playerState.isBuffering && item != null) {
                                    0.85f
                                } else {
                                    0f
                                }
                            },
                            wavelength = 10.dp,
                        )

                        FilledIconButton(
                            onClick = { playerState.togglePlayPause() },
                            enabled = item != null,
                            shape = RoundedCornerShape(playButtonCorner),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.size(38.dp),
                        ) {
                            when {
                                playerState.isBuffering -> {
                                    LoadingIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                playerState.isPlaying -> {
                                    Icon(
                                        imageVector = ExpressiveIcons.Pause,
                                        contentDescription = stringResource("pause"),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = ExpressiveIcons.PlayArrow,
                                        contentDescription = stringResource("play"),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { playerState.skipToNext() },
                        enabled = playerState.canSkipNext,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipNext,
                            contentDescription = stringResource("next"),
                            tint = if (playerState.canSkipNext) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            if (volume > 0.01f) {
                                playerState.updateVolume(0.0)
                            } else {
                                playerState.updateVolume(lastNonZeroVolume.toDouble())
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (deltaY != 0f) {
                                    val step = if (deltaY < 0f) 0.05f else -0.05f
                                    playerState.updateVolume((volume + step).coerceIn(0f, 1f).toDouble())
                                }
                            },
                    ) {
                        Icon(
                            imageVector = when {
                                volume <= 0.01f -> ExpressiveIcons.VolumeOff
                                volume < 0.5f -> ExpressiveIcons.VolumeDown
                                else -> ExpressiveIcons.VolumeUp
                            },
                            contentDescription = stringResource("volume"),
                            tint = if (volume <= 0.01f) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    if (item != null) {
                        IconButton(
                            onClick = onOpenFullPlayer,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = IconAssets.expandLess(),
                                contentDescription = stringResource("expand"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Subtle bottom progress hairline inside the rounded surface
            val bottomActiveColor = MaterialTheme.colorScheme.primary
            val bottomTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .background(bottomTrackColor, CircleShape),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(bottomActiveColor, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun SideSheetToggleButton(
    checked: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    if (checked) {
        FilledTonalIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
