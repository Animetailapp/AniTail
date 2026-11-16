package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.ui.screen.formatTime
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ConnectedGroupAction(
    val label: String,
    val icon: ImageVector,
    val badgeText: String? = null,
    val checked: Boolean = false,
    val isVolume: Boolean = false,
    val onTrigger: () -> Unit,
)

/**
 * Material 3 Expressive connected button group for desktop player utilities:
 * hover/keyboard vertical Volume slider popup, Video mode, Lyrics, Queue,
 * Sleep Timer countdown, Audio Settings/EQ, and Fullscreen toggle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerConnectedButtonGroup(
    playerState: PlayerState,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    showQueue: Boolean,
    onToggleQueue: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onCastClick: (() -> Unit)? = { playerState.showCastDialog = true },
    isCasting: Boolean = playerState.isCasting,
    onVideoClick: (() -> Unit)? = { playerState.toggleVideoViewer() },
    isVideoActive: Boolean = playerState.showVideoViewer || playerState.isVideoMode,
    onMiniplayerClick: (() -> Unit)? = { playerState.toggleMiniPlayer() },
    showMiniplayer: Boolean = playerState.showMiniPlayer,
    onButtonColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
    checkedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
) {
    val volume = playerState.volume.toFloat().coerceIn(0f, 1f)
    var lastNonZeroVolume by remember {
        mutableFloatStateOf(if (volume > 0.01f) volume else 0.7f)
    }
    LaunchedEffect(volume) {
        if (volume > 0.01f) {
            lastNonZeroVolume = volume
        }
    }

    fun toggleMute() {
        playerState.toggleMute()
    }

    fun stepVolume(delta: Float) {
        playerState.stepVolume(delta.toDouble())
    }

    var isButtonHovered by remember { mutableStateOf(false) }
    var isPopupHovered by remember { mutableStateOf(false) }
    var isKeyboardOpen by remember { mutableStateOf(false) }
    var showVolumePopup by remember { mutableStateOf(false) }

    LaunchedEffect(isButtonHovered, isPopupHovered, isKeyboardOpen) {
        if (isButtonHovered || isPopupHovered || isKeyboardOpen) {
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
    val volumeText = "${stringResource("volume")} ${(volume * 100f).roundToInt()}%"

    val sleepBadge = if (playerState.isSleepTimerActive && playerState.sleepTimerTimeLeftMs > 0L) {
        formatTime(playerState.sleepTimerTimeLeftMs)
    } else {
        null
    }

    val actualFullscreenAction = onToggleFullscreen ?: { playerState.toggleFullscreenOverlay() }
    val actualFullscreenChecked = isFullscreen || playerState.isFullscreenOverlay

    val leftActions = buildList {
        add(
            ConnectedGroupAction(
                label = stringResource("queue"),
                icon = ExpressiveIcons.QueueMusic,
                checked = showQueue,
                onTrigger = onToggleQueue,
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource("lyrics"),
                icon = ExpressiveIcons.Lyrics,
                checked = showLyrics,
                onTrigger = onToggleLyrics,
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource("shuffle"),
                icon = ExpressiveIcons.Shuffle,
                checked = playerState.shuffleEnabled,
                onTrigger = { playerState.toggleShuffle() },
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource("repeat"),
                icon = if (playerState.repeatMode == RepeatMode.ONE) ExpressiveIcons.RepeatOne else ExpressiveIcons.Repeat,
                checked = playerState.repeatMode != RepeatMode.OFF,
                onTrigger = { playerState.toggleRepeat() },
            ),
        )
    }

    val rightActions = buildList {
        add(
            ConnectedGroupAction(
                label = volumeText,
                icon = volumeIcon,
                checked = volume <= 0.01f,
                isVolume = true,
                onTrigger = ::toggleMute,
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource("cast"),
                icon = ExpressiveIcons.Cast,
                checked = isCasting,
                onTrigger = { onCastClick?.invoke() ?: run { playerState.showCastDialog = true } },
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource("video_viewer"),
                icon = ExpressiveIcons.Movie,
                checked = isVideoActive,
                onTrigger = { onVideoClick?.invoke() ?: run { playerState.toggleVideoViewer() } },
            ),
        )
        add(
            ConnectedGroupAction(
                label = stringResource(if (actualFullscreenChecked) "exit_fullscreen" else "fullscreen"),
                icon = if (actualFullscreenChecked) ExpressiveIcons.FullscreenExit else ExpressiveIcons.Fullscreen,
                checked = actualFullscreenChecked,
                onTrigger = actualFullscreenAction,
            ),
        )
    }

    val buttonColors: ToggleButtonColors = ToggleButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = onButtonColor,
        checkedContainerColor = checkedContainerColor,
        checkedContentColor = checkedContentColor,
    )

    val leadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
        checkedShape = RoundedCornerShape(
            topStart = 16.dp,
            bottomStart = 16.dp,
            topEnd = 6.dp,
            bottomEnd = 6.dp,
        ),
    )
    val middleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
        checkedShape = RoundedCornerShape(6.dp),
    )
    val trailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
        checkedShape = RoundedCornerShape(
            topStart = 6.dp,
            bottomStart = 6.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
        ),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Grupo izquierdo: Cola, Letras, Aleatorio, Repeticion
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(
                    menuState = menuState,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = onButtonColor,
                    ),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            leftActions.forEachIndexed { index, action ->
                val shapes: ToggleButtonShapes = when (index) {
                    0 -> leadingShapes
                    leftActions.lastIndex -> trailingShapes
                    else -> middleShapes
                }
                customItem(
                    buttonGroupContent = {
                        ConnectedGroupToggle(
                            action = action,
                            shapes = shapes,
                            colors = buttonColors,
                        )
                    },
                    menuContent = { menuState ->
                        val shape = MenuDefaults.itemShape(index, leftActions.size).shape
                        DropdownMenuItem(
                            onClick = {
                                action.onTrigger()
                                menuState.dismiss()
                            },
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            shape = shape,
                        )
                    },
                )
            }
        }

        // Grupo derecho: Volumen, Transmitir, Video, Pantalla completa
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(
                    menuState = menuState,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = onButtonColor,
                    ),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            rightActions.forEachIndexed { index, action ->
                val shapes: ToggleButtonShapes = when (index) {
                    0 -> leadingShapes
                    rightActions.lastIndex -> trailingShapes
                    else -> middleShapes
                }
                customItem(
                    buttonGroupContent = {
                        if (action.isVolume) {
                            Box(
                                modifier = Modifier
                                    .onPointerEvent(PointerEventType.Enter) { isButtonHovered = true }
                                    .onPointerEvent(PointerEventType.Exit) { isButtonHovered = false }
                                    .onPointerEvent(PointerEventType.Scroll) { event ->
                                        val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (deltaY != 0f) {
                                            stepVolume(if (deltaY < 0f) 0.05f else -0.05f)
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> {
                                                isKeyboardOpen = true
                                                stepVolume(0.05f)
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                isKeyboardOpen = true
                                                stepVolume(-0.05f)
                                                true
                                            }
                                            Key.M -> {
                                                isKeyboardOpen = true
                                                toggleMute()
                                                true
                                            }
                                            Key.Escape -> {
                                                if (showVolumePopup) {
                                                    isKeyboardOpen = false
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
                                ConnectedGroupToggle(
                                    action = action,
                                    shapes = shapes,
                                    colors = buttonColors,
                                )

                                VolumePopupOverlay(
                                    visible = showVolumePopup,
                                    volume = volume,
                                    onVolumeChange = { playerState.updateVolume(it.toDouble()) },
                                    onToggleMute = ::toggleMute,
                                    onStepVolume = ::stepVolume,
                                    onPopupHoverChanged = { isPopupHovered = it },
                                    onDismissRequest = {
                                        isKeyboardOpen = false
                                        showVolumePopup = false
                                    },
                                )
                            }
                        } else {
                            ConnectedGroupToggle(
                                action = action,
                                shapes = shapes,
                                colors = buttonColors,
                            )
                        }
                    },
                    menuContent = { menuState ->
                        val shape = MenuDefaults.itemShape(index, rightActions.size).shape
                        DropdownMenuItem(
                            onClick = {
                                action.onTrigger()
                                menuState.dismiss()
                            },
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            shape = shape,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedGroupToggle(
    action: ConnectedGroupAction,
    shapes: ToggleButtonShapes,
    colors: ToggleButtonColors,
    modifier: Modifier = Modifier,
) {
    val hasBadge = !action.badgeText.isNullOrBlank()
    ToggleButton(
        checked = action.checked,
        onCheckedChange = { action.onTrigger() },
        shapes = shapes,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(width = 44.dp, height = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                modifier = Modifier.size(20.dp),
            )
            if (hasBadge) {
                Text(
                    text = action.badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun VolumePopupOverlay(
    visible: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onStepVolume: (Float) -> Unit,
    onPopupHoverChanged: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val desiredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val clampedX = desiredX.coerceIn(
                    minimumValue = 8,
                    maximumValue = (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8),
                )
                val aboveY = anchorBounds.top - popupContentSize.height - gapPx
                val belowY = anchorBounds.bottom + gapPx
                val resolvedY = if (aboveY >= 8) aboveY else belowY.coerceAtMost(
                    (windowSize.height - popupContentSize.height - 8).coerceAtLeast(8),
                )
                return IntOffset(clampedX, resolvedY)
            }
        }
    }

    val sliderState = rememberSliderState(
        value = volume,
        trackRange = 0f..1f,
    )
    LaunchedEffect(volume) {
        if (abs(sliderState.value - volume) > 0.005f) {
            sliderState.value = volume
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ),
            exit = fadeOut() + scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { onPopupHoverChanged(true) }
                    .onPointerEvent(PointerEventType.Exit) { onPopupHoverChanged(false) }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (deltaY != 0f) {
                            onStepVolume(if (deltaY < 0f) 0.05f else -0.05f)
                        }
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "${(volume * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val sliderColors = SliderDefaults.colors()
                    VerticalSlider(
                        state = sliderState,
                        onValueChange = { updated ->
                            sliderState.value = updated
                            onVolumeChange(updated)
                        },
                        topToBottom = false,
                        colors = sliderColors,
                        modifier = Modifier
                            .height(136.dp)
                            .width(40.dp),
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                trackCornerSize = 12.dp,
                                colors = sliderColors,
                                drawStopIndicator = null,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                volume <= 0.01f -> ExpressiveIcons.VolumeOff
                                volume < 0.5f -> ExpressiveIcons.VolumeDown
                                else -> ExpressiveIcons.VolumeUp
                            },
                            contentDescription = stringResource("volume"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

