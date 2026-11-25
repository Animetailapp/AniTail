package com.anitail.desktop.ui.window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.anitail.desktop.i18n.AndroidStringLoader
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.AnitailTheme
import com.anitail.desktop.ui.component.ExpressiveIcons
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.screen.formatTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniplayerWindow(
    playerState: PlayerState,
    onCloseRequest: () -> Unit,
    onRestoreMainWindow: () -> Unit,
) {
    val preferences = remember { DesktopPreferences.getInstance() }
    val appLanguage by preferences.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AndroidStringLoader.load(appLanguage) }
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlackEnabled by preferences.pureBlack.collectAsState()

    val isFocusMode = playerState.miniplayerFocusMode
    val windowSize = if (isFocusMode) DpSize(320.dp, 320.dp) else DpSize(440.dp, 160.dp)
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.BottomEnd),
        size = windowSize,
    )

    LaunchedEffect(isFocusMode) {
        windowState.size = windowSize
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
    ) {
        Window(
            onCloseRequest = onCloseRequest,
            title = stringResource("miniplayer"),
            resizable = false,
            state = windowState,
            alwaysOnTop = true,
            undecorated = true,
            transparent = false,
        ) {
            AnitailTheme(
                darkMode = darkMode,
                pureBlack = pureBlackEnabled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    WindowDraggableArea {
                        if (isFocusMode) {
                            FocusModeMiniplayer(
                                playerState = playerState,
                                onCloseRequest = onCloseRequest,
                                onToggleFocusMode = { playerState.miniplayerFocusMode = false },
                                onRestoreMainWindow = onRestoreMainWindow,
                            )
                        } else {
                            StandardMiniplayer(
                                playerState = playerState,
                                onCloseRequest = onCloseRequest,
                                onToggleFocusMode = { playerState.miniplayerFocusMode = true },
                                onRestoreMainWindow = onRestoreMainWindow,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardMiniplayer(
    playerState: PlayerState,
    onCloseRequest: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onRestoreMainWindow: () -> Unit,
) {
    val item = playerState.currentItem

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail Artwork
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { onRestoreMainWindow() },
            contentAlignment = Alignment.Center,
        ) {
            val thumb = item?.artworkUrl
            if (!thumb.isNullOrBlank()) {
                RemoteImage(
                    url = thumb,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = ExpressiveIcons.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Track metadata and Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Title & Window Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = item?.title ?: stringResource("no_track_playing"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = item?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFocusMode,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.Fullscreen,
                            contentDescription = stringResource("enter_focus_mode"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onCloseRequest,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.Close,
                            contentDescription = stringResource("close_miniplayer"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Progress Slider
            MiniplayerProgressSection(playerState = playerState)

            // Playback Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { playerState.skipToPrevious() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                FilledIconButton(
                    onClick = { playerState.togglePlayPause() },
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                IconButton(
                    onClick = { playerState.skipToNext() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniplayerProgressSection(playerState: PlayerState) {
    val duration = playerState.duration
    val positionSecond by remember(playerState) {
        derivedStateOf { playerState.position / 1000L }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { playerState.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionSecond * 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FocusModeMiniplayer(
    playerState: PlayerState,
    onCloseRequest: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onRestoreMainWindow: () -> Unit,
) {
    val item = playerState.currentItem
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(hoverInteraction),
    ) {
        // Full artwork background
        val thumb = item?.artworkUrl
        if (!thumb.isNullOrBlank()) {
            RemoteImage(
                url = thumb,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ExpressiveIcons.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        // Top controls (always or on hover)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onRestoreMainWindow,
                modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = ExpressiveIcons.PictureInPicture,
                    contentDescription = "Restore",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Row {
                IconButton(
                    onClick = onToggleFocusMode,
                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.FullscreenExit,
                        contentDescription = stringResource("exit_focus_mode"),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCloseRequest,
                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.Close,
                        contentDescription = stringResource("close_miniplayer"),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Bottom info and controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item?.title ?: stringResource("no_track_playing"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = item?.artist ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { playerState.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { playerState.skipToPrevious() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledIconButton(
                    onClick = { playerState.togglePlayPause() },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { playerState.skipToNext() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ExpressiveIcons.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

