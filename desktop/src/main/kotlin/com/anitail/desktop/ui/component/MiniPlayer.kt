@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive persistent mini player bar for desktop, matching Metrolist layout:
 * - Left: Album Art + Title/Artist + Connected Pill [Share, Like, More]
 * - Center: Retained Expressive Transport [Previous, Morphing Play/Pause with wavy ring, Next]
 * - Right: Connected Pill [Queue, Lyrics, Shuffle, Repeat, Volume, More Options]
 */
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
    var isVolumeButtonHovered by remember { mutableStateOf(false) }
    var isVolumePopupHovered by remember { mutableStateOf(false) }
    var isVolumeKeyboardOpen by remember { mutableStateOf(false) }
    var showVolumePopup by remember { mutableStateOf(false) }

    LaunchedEffect(isVolumeButtonHovered, isVolumePopupHovered, isVolumeKeyboardOpen) {
        if (isVolumeButtonHovered || isVolumePopupHovered || isVolumeKeyboardOpen) {
            showVolumePopup = true
        } else {
            delay(180)
            showVolumePopup = false
        }
    }

    val volumeIcon = when {
        volume <= 0.01f -> ExpressiveIcons.VolumeOff
        volume < 0.5f -> ExpressiveIcons.VolumeDown
        else -> ExpressiveIcons.VolumeUp
    }

    val database = remember { DesktopDatabase.getInstance() }
    val scope = rememberCoroutineScope()
    val songEntity by remember(item?.id) {
        if (item?.id != null) database.song(item.id) else flowOf(null)
    }.collectAsState(initial = null)
    val isLiked = songEntity?.liked == true

    var showLeftMoreMenu by remember { mutableStateOf(false) }
    var showRightMoreMenu by remember { mutableStateOf(false) }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
            ) {
                // Zona Izquierda: Thumbnail + Textos + Píldora de acciones [Share, Like, More]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // Clickable & swipeable track metadata region
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
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
                            .padding(vertical = 4.dp, horizontal = 2.dp),
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
                            modifier = Modifier.weight(1f, fill = false),
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

                    if (item != null) {
                        Spacer(modifier = Modifier.width(6.dp))

                        // Píldora de acciones conectadas: [Compartir, Me gusta, Más opciones]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            val pillContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                            val pillContent = MaterialTheme.colorScheme.onSurfaceVariant
                            val pillColors = ToggleButtonDefaults.colors(
                                containerColor = pillContainer,
                                contentColor = pillContent,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val leftShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                                checkedShape = RoundedCornerShape(
                                    topStart = 14.dp,
                                    bottomStart = 14.dp,
                                    topEnd = 6.dp,
                                    bottomEnd = 6.dp,
                                ),
                            )
                            val midShapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
                                checkedShape = RoundedCornerShape(6.dp),
                            )
                            val rightShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                                checkedShape = RoundedCornerShape(
                                    topStart = 6.dp,
                                    bottomStart = 6.dp,
                                    topEnd = 14.dp,
                                    bottomEnd = 14.dp,
                                ),
                            )

                            // Compartir
                            var shareCopied by remember { mutableStateOf(false) }
                            LaunchedEffect(shareCopied) {
                                if (shareCopied) {
                                    delay(1500L)
                                    shareCopied = false
                                }
                            }
                            ToggleButton(
                                checked = shareCopied,
                                onCheckedChange = {
                                    val id = item.id
                                    runCatching {
                                        val selection = java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=$id")
                                        val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        clip.setContents(selection, selection)
                                        shareCopied = true
                                    }
                                },
                                shapes = leftShapes,
                                colors = pillColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 34.dp, height = 32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (shareCopied) IconAssets.check() else IconAssets.share(),
                                        contentDescription = stringResource("share"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Me gusta
                            ToggleButton(
                                checked = isLiked,
                                onCheckedChange = {
                                    val id = item.id
                                    scope.launch { database.toggleSongLike(id) }
                                },
                                shapes = midShapes,
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = pillContainer,
                                    contentColor = if (isLiked) Color(0xFFFF4D6D) else pillContent,
                                    checkedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    checkedContentColor = Color(0xFFFF4D6D),
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 34.dp, height = 32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                        contentDescription = stringResource(if (isLiked) "action_remove_like" else "action_like"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Más opciones de pista
                            Box {
                                ToggleButton(
                                    checked = showLeftMoreMenu,
                                    onCheckedChange = { showLeftMoreMenu = !showLeftMoreMenu },
                                    shapes = rightShapes,
                                    colors = pillColors,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(width = 34.dp, height = 32.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = IconAssets.moreVert(),
                                            contentDescription = stringResource("more_options"),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showLeftMoreMenu,
                                    onDismissRequest = { showLeftMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource("open_full_player")) },
                                        leadingIcon = { Icon(IconAssets.expandLess(), contentDescription = null) },
                                        onClick = {
                                            showLeftMoreMenu = false
                                            onOpenFullPlayer()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource("open_miniplayer")) },
                                        leadingIcon = { Icon(ExpressiveIcons.PictureInPicture, contentDescription = null) },
                                        onClick = {
                                            showLeftMoreMenu = false
                                            onToggleMiniPlayer?.invoke() ?: playerState.toggleMiniPlayer()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Zona Central: [Anterior, Play/Pausa con wavy ring, Siguiente] (retenido exactamente como pidió el usuario)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
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
                }

                // Zona Derecha: Píldora conectada de utilidades [Cola, Letras, Aleatorio, Repetición, Volumen, Más opciones]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f),
                ) {
                    val groupContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    val groupContent = MaterialTheme.colorScheme.onSurfaceVariant
                    val groupColors = ToggleButtonDefaults.colors(
                        containerColor = groupContainer,
                        contentColor = groupContent,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    val groupLeadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                        checkedShape = RoundedCornerShape(
                            topStart = 16.dp,
                            bottomStart = 16.dp,
                            topEnd = 6.dp,
                            bottomEnd = 6.dp,
                        ),
                    )
                    val groupMiddleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
                        checkedShape = RoundedCornerShape(6.dp),
                    )
                    val groupTrailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                        checkedShape = RoundedCornerShape(
                            topStart = 6.dp,
                            bottomStart = 6.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                        ),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        // 1. Cola
                        ToggleButton(
                            checked = isQueueSideSheetOpen,
                            onCheckedChange = { onToggleQueueSideSheet?.invoke() },
                            shapes = groupLeadingShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.QueueMusic,
                                    contentDescription = stringResource("queue"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 2. Letras
                        ToggleButton(
                            checked = isLyricsSideSheetOpen,
                            onCheckedChange = { onToggleLyricsSideSheet?.invoke() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.Lyrics,
                                    contentDescription = stringResource("lyrics"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 3. Aleatorio
                        ToggleButton(
                            checked = playerState.shuffleEnabled,
                            onCheckedChange = { playerState.toggleShuffle() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.Shuffle,
                                    contentDescription = stringResource("shuffle"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 4. Repetición
                        val repeatActive = playerState.repeatMode != RepeatMode.OFF
                        ToggleButton(
                            checked = repeatActive,
                            onCheckedChange = { playerState.toggleRepeat() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playerState.repeatMode == RepeatMode.ONE) {
                                        ExpressiveIcons.RepeatOne
                                    } else {
                                        ExpressiveIcons.Repeat
                                    },
                                    contentDescription = stringResource("repeat"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 5. Volumen con popup vertical en hover/scroll/click
                        Box(
                            modifier = Modifier
                                .onPointerEvent(PointerEventType.Enter) { isVolumeButtonHovered = true }
                                .onPointerEvent(PointerEventType.Exit) { isVolumeButtonHovered = false }
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (deltaY != 0f) {
                                        playerState.stepVolume(if (deltaY < 0f) 0.05 else -0.05)
                                    }
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.stepVolume(0.05)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.stepVolume(-0.05)
                                            true
                                        }
                                        Key.M -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.toggleMute()
                                            true
                                        }
                                        Key.Escape -> {
                                            if (showVolumePopup) {
                                                isVolumeKeyboardOpen = false
                                                showVolumePopup = false
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                },
                        ) {
                            ToggleButton(
                                checked = volume <= 0.01f,
                                onCheckedChange = { playerState.toggleMute() },
                                shapes = groupMiddleShapes,
                                colors = groupColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 36.dp, height = 34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = volumeIcon,
                                        contentDescription = stringResource("volume"),
                                        tint = if (volume <= 0.01f) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            VolumePopupOverlay(
                                visible = showVolumePopup,
                                volume = volume,
                                onVolumeChange = { playerState.updateVolume(it.toDouble()) },
                                onToggleMute = { playerState.toggleMute() },
                                onStepVolume = { playerState.stepVolume(it.toDouble()) },
                                onPopupHoverChanged = { isVolumePopupHovered = it },
                                onDismissRequest = {
                                    isVolumeKeyboardOpen = false
                                    showVolumePopup = false
                                },
                            )
                        }

                        // 6. Más opciones (Menú con Ecualizador, PiP, Cast, Vídeo, Expandir)
                        Box {
                            ToggleButton(
                                checked = showRightMoreMenu,
                                onCheckedChange = { showRightMoreMenu = !showRightMoreMenu },
                                shapes = groupTrailingShapes,
                                colors = groupColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 36.dp, height = 34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = IconAssets.moreVert(),
                                        contentDescription = stringResource("more_options"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showRightMoreMenu,
                                onDismissRequest = { showRightMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource("open_miniplayer")) },
                                    leadingIcon = { Icon(ExpressiveIcons.PictureInPicture, contentDescription = null) },
                                    onClick = {
                                        showRightMoreMenu = false
                                        onToggleMiniPlayer?.invoke() ?: playerState.toggleMiniPlayer()
                                    },
                                )
                                if (onOpenAudioSettings != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource("equalizer")) },
                                        leadingIcon = { Icon(ExpressiveIcons.Equalizer, contentDescription = null) },
                                        onClick = {
                                            showRightMoreMenu = false
                                            onOpenAudioSettings()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource("cast")) },
                                    leadingIcon = { Icon(ExpressiveIcons.Cast, contentDescription = null) },
                                    onClick = {
                                        showRightMoreMenu = false
                                        playerState.showCastDialog = true
                                    },
                                )
                                if (playerState.hasVideoStream) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource("video_viewer")) },
                                        leadingIcon = { Icon(ExpressiveIcons.Movie, contentDescription = null) },
                                        onClick = {
                                            showRightMoreMenu = false
                                            playerState.toggleVideoViewer()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource("open_full_player")) },
                                    leadingIcon = { Icon(IconAssets.expandLess(), contentDescription = null) },
                                    onClick = {
                                        showRightMoreMenu = false
                                        onOpenFullPlayer()
                                    },
                                )
                            }
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
