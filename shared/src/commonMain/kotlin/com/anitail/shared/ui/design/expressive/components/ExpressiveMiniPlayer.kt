/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Expressive design: ported from Expressive's MiniPlayerContentInternal.
 */

package com.anitail.shared.ui.design.expressive.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.add_to_favorites
import com.anitail.shared.generated.resources.add_to_playlist
import com.anitail.shared.generated.resources.loading
import com.anitail.shared.generated.resources.mini_player_subscribe_artist
import com.anitail.shared.generated.resources.mini_player_unsubscribe_artist
import com.anitail.shared.generated.resources.next
import com.anitail.shared.generated.resources.pause
import com.anitail.shared.generated.resources.play
import com.anitail.shared.generated.resources.previous
import com.anitail.shared.generated.resources.remove_from_favorites
import com.anitail.shared.model.SongItem
import com.anitail.shared.model.previewThumbnailUrl
import com.anitail.shared.settings.ExpressiveMiniPlayerButton
import com.anitail.shared.settings.Settings
import com.anitail.shared.settings.displayName
import com.anitail.shared.settings.expressiveMiniPlayerButtons
import com.anitail.shared.ui.components.PlayerSheetSurface
import com.anitail.shared.ui.components.WavyCircularProgressIndicator
import com.anitail.shared.ui.components.currentSongArtworkShape
import com.anitail.shared.ui.components.images.AsyncImage
import com.anitail.shared.ui.components.rememberArtworkShapeRotation
import com.anitail.shared.ui.components.rememberTrackMarquee
import com.anitail.shared.ui.haptics.HapticEvent
import com.anitail.shared.ui.haptics.rememberAnitailHaptics
import com.anitail.shared.ui.icons.AnitailIcons
import com.anitail.shared.ui.theme.rememberReducedMotion
import com.anitail.shared.ui.tv.tvFocusHighlight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

val ExpressiveMiniPlayerHeight = 64.dp
private val ExpressiveMiniPlayerCornerRadius = 32.dp
private val ExpressiveMiniPlayerRingedArtworkSize = 38.dp
private val ExpressiveMiniPlayerProgressRingSize = 54.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMiniPlayer(
    song: SongItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    active: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    playbackEnabled: Boolean,
    swipeThresholdPx: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    progress: () -> Float,
    progressAvailable: Boolean,
    isLiked: Boolean,
    isArtistSubscribed: Boolean,
    onLike: (() -> Unit)?,
    onSubscribeToggle: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onDpadExpand: () -> Unit = onExpand,
    playbackActionLabel: String? = null,
    playbackActionIcon: ImageVector? = null,
) {
    val settings = Settings.shared
    val buttons = settings.expressiveMiniPlayerButtons
    val haptics = rememberAnitailHaptics()
    val scheme = MaterialTheme.colorScheme
    val containerColor = scheme.primaryContainer
    val onContainer = scheme.onPrimaryContainer
    val artworkShape = if (settings.expressiveMiniPlayerCookieArtwork) currentSongArtworkShape() else CircleShape
    val marquee = rememberTrackMarquee(song.id, active, 3_000L)
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val artworkMotionScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val artworkMotionEnabled =
        settings.expressiveMiniPlayerCookieArtwork && settings.expressiveMiniPlayerShapeMotion &&
            artworkMotionScale > 0f && !rememberReducedMotion()
    val artworkShapeRotation by rememberArtworkShapeRotation(
        spinning = active && isPlaying,
        motionEnabled = artworkMotionEnabled,
    )
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val playLabel = playbackActionLabel ?: stringResource(if (isPlaying) Res.string.pause else Res.string.play)
    var isFocused by remember { mutableStateOf(false) }

    PlayerSheetSurface(
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (isFocused && event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                        onDpadExpand()
                        true
                    } else {
                        false
                    }
                }.height(ExpressiveMiniPlayerHeight)
                .graphicsLayer {
                    translationX = dragOffsetX
                    alpha = if (isVisible) 1f else 0f
                }.then(
                    if (swipeEnabled) {
                        Modifier.pointerInput(canSkipNext, canSkipPrevious, layoutDirection, swipeThresholdPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragOffsetX = 0f },
                                onDragEnd = {
                                    val logical = if (layoutDirection == LayoutDirection.Rtl) -dragOffsetX else dragOffsetX
                                    if (logical < -swipeThresholdPx && canSkipNext) {
                                        onSkipNext()
                                    } else if (logical > swipeThresholdPx && canSkipPrevious) {
                                        onSkipPrevious()
                                    }
                                    val from = dragOffsetX
                                    scope.launch {
                                        Animatable(from).animateTo(
                                            targetValue = 0f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                        ) { dragOffsetX = value }
                                    }
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { change, amount ->
                                    dragOffsetX += amount
                                    change.consume()
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        onClick = onExpand,
        cornerRadius = ExpressiveMiniPlayerCornerRadius,
        expressive = true,
        color = containerColor,
        elevation = 3.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp)) {
            val controlsMaxWidth = maxWidth * 0.5f
            Row(
                modifier = Modifier.fillMaxWidth().height(ExpressiveMiniPlayerHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val showProgress = settings.showExpressiveMiniPlayerProgress && progressAvailable
                val artworkSize = if (showProgress) ExpressiveMiniPlayerRingedArtworkSize else 44.dp
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .graphicsLayer { rotationZ = artworkShapeRotation }
                                .clip(artworkShape)
                                .then(
                                    if (settings.expressiveMiniPlayerArtworkPlayback) {
                                        Modifier
                                            .tvFocusHighlight(artworkShape, scale = 1f)
                                            .clickable(
                                                enabled = playbackEnabled,
                                                role = Role.Button,
                                                onClickLabel = playLabel,
                                                onClick = onTogglePlay,
                                            ).semantics { contentDescription = playLabel }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = -artworkShapeRotation },
                        ) {
                            AsyncImage(
                                url = song.previewThumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            // Darkens the artwork only behind the white loading indicator.
                            if (showProgress && isLoading && active) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                )
                            }
                            if (isLoading && active) {
                                val loadingText = stringResource(Res.string.loading)
                                LoadingIndicator(
                                    modifier = Modifier.size(24.dp).semantics { contentDescription = loadingText },
                                    color = if (showProgress) Color.White else onContainer,
                                )
                            }
                        }
                    }
                    if (showProgress) {
                        WavyCircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.requiredSize(ExpressiveMiniPlayerProgressRingSize),
                            color = onContainer,
                            trackColor = onContainer.copy(alpha = 0.25f),
                            animate = active && isPlaying && !isLoading,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = song.title,
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                            ),
                        color = onContainer,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = marquee,
                    )
                    Text(
                        text = song.artists.joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                        color = onContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = marquee,
                    )
                }
                if (buttons.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.widthIn(max = controlsMaxWidth).height(48.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEach { button ->
                            when (button) {
                                ExpressiveMiniPlayerButton.PREVIOUS -> {
                                    ExpressiveMiniButton(
                                        icon = AnitailIcons.Rounded.SkipPrevious,
                                        description = stringResource(Res.string.previous),
                                        background = scheme.onPrimary,
                                        tint = scheme.primary,
                                        enabled = canSkipPrevious,
                                        onClick = onSkipPrevious,
                                    )
                                }

                                ExpressiveMiniPlayerButton.PLAY_PAUSE -> {
                                    ExpressiveMiniButton(
                                        icon =
                                            playbackActionIcon
                                                ?: if (isPlaying) AnitailIcons.Rounded.Pause else AnitailIcons.Rounded.PlayArrow,
                                        description = playLabel,
                                        background = scheme.primary,
                                        tint = scheme.onPrimary,
                                        enabled = playbackEnabled,
                                        onClick = onTogglePlay,
                                    )
                                }

                                ExpressiveMiniPlayerButton.NEXT -> {
                                    ExpressiveMiniButton(
                                        icon = AnitailIcons.Rounded.SkipNext,
                                        description = stringResource(Res.string.next),
                                        background = scheme.onPrimary,
                                        tint = scheme.primary,
                                        enabled = canSkipNext,
                                        onClick = onSkipNext,
                                    )
                                }

                                ExpressiveMiniPlayerButton.PLAYLIST -> {
                                    ExpressiveMiniButton(
                                        icon = AnitailIcons.AutoMirrored.Outlined.PlaylistAdd,
                                        description = stringResource(Res.string.add_to_playlist),
                                        background = scheme.onPrimary,
                                        tint = scheme.primary,
                                        enabled = onAddToPlaylist != null,
                                        onClick = {
                                            haptics.perform(HapticEvent.PRESS)
                                            onAddToPlaylist?.invoke()
                                        },
                                    )
                                }

                                ExpressiveMiniPlayerButton.SUBSCRIBE -> {
                                    ExpressiveMiniButton(
                                        icon =
                                            if (isArtistSubscribed) {
                                                AnitailIcons.Filled.PersonRemove
                                            } else {
                                                AnitailIcons.Filled.PersonAdd
                                            },
                                        description =
                                            stringResource(
                                                if (isArtistSubscribed) {
                                                    Res.string.mini_player_unsubscribe_artist
                                                } else {
                                                    Res.string.mini_player_subscribe_artist
                                                },
                                            ),
                                        background = scheme.onPrimary,
                                        tint = scheme.primary,
                                        enabled = onSubscribeToggle != null,
                                        onClick = {
                                            haptics.toggle(!isArtistSubscribed)
                                            onSubscribeToggle?.invoke()
                                        },
                                    )
                                }

                                ExpressiveMiniPlayerButton.LIKE -> {
                                    ExpressiveMiniButton(
                                        icon = if (isLiked) AnitailIcons.Filled.Favorite else AnitailIcons.Outlined.FavoriteBorder,
                                        description =
                                            stringResource(
                                                if (isLiked) Res.string.remove_from_favorites else Res.string.add_to_favorites,
                                            ),
                                        background = scheme.onPrimary,
                                        tint = if (isLiked) scheme.error else scheme.primary,
                                        enabled = onLike != null,
                                        onClick = {
                                            haptics.toggle(!isLiked)
                                            onLike?.invoke()
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
private fun ExpressiveMiniButton(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .tvFocusHighlight(CircleShape, scale = 1f)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp),
        )
    }
}
