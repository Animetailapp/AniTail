package com.anitail.desktop.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.storage.SliderStyle
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ExpressiveIcons
import com.anitail.shared.model.LibraryItem

/**
 * Material 3 Expressive player controls featuring clickable metadata header,
 * connected favorite/playlist/more pills, expressive progress slider,
 * and morphing transport controls.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerControls(
    item: LibraryItem?,
    playerState: PlayerState,
    isLiked: Boolean,
    textColor: Color,
    mutedTextColor: Color,
    sliderStyle: SliderStyle,
    textButtonColor: Color,
    iconButtonColor: Color,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    artistsWithId: List<Pair<String, String?>> = emptyList(),
    albumInfo: Pair<String, String>? = null,
    onOpenArtist: ((String, String) -> Unit)? = null,
    onOpenAlbum: ((String, String) -> Unit)? = null,
    onShowMore: (() -> Unit)? = null,
) {
    val totalDurationMs = if (playerState.duration > 0L) {
        playerState.duration
    } else {
        item?.durationMs ?: 0L
    }
    var dragFraction by remember(item?.id) { mutableStateOf<Float?>(null) }
    val activeProgress = (dragFraction ?: playerState.progress).coerceIn(0f, 1f)
    val displayedPositionMs = if (dragFraction != null && totalDurationMs > 0L) {
        (activeProgress * totalDurationMs).toLong()
    } else {
        playerState.position
    }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "player_like_scale",
    )
    val likeColor by animateColorAsState(
        targetValue = if (isLiked) Color(0xFFFF4D6D) else textColor,
        label = "player_like_color",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Track title, clickable artist/album links, and connected utility pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item?.title ?: stringResource("nothing_playing"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )

                if (artistsWithId.isNotEmpty() || albumInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    ) {
                        val artistEntries = artistsWithId.ifEmpty {
                            item?.artist?.takeIf { it.isNotBlank() }?.let { listOf(it to null) }.orEmpty()
                        }
                        artistEntries.forEachIndexed { index, (artistName, artistId) ->
                            val clickable = !artistId.isNullOrBlank() && onOpenArtist != null
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (clickable) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onOpenArtist?.invoke(artistId!!, artistName) }
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                } else {
                                    Modifier
                                },
                            )
                            if (index < artistEntries.lastIndex) {
                                Text(
                                    text = ", ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = mutedTextColor,
                                )
                            }
                        }

                        if (albumInfo != null && albumInfo.second.isNotBlank()) {
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedTextColor.copy(alpha = 0.7f),
                            )
                            Text(
                                text = albumInfo.second,
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = onOpenAlbum != null) {
                                        onOpenAlbum?.invoke(albumInfo.first, albumInfo.second)
                                    }
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = item?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            // Connected Like + Add to Playlist + optional More pill actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onToggleLike,
                    enabled = item != null,
                    shapes = IconButtonShapes(
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            bottomStart = 20.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp,
                        ),
                        pressedShape = RoundedCornerShape(10.dp),
                    ),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = textColor.copy(alpha = 0.12f),
                        contentColor = likeColor,
                    ),
                    modifier = Modifier.size(width = 44.dp, height = 40.dp),
                ) {
                    Icon(
                        imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                        contentDescription = stringResource(
                            if (isLiked) "action_remove_like" else "action_like",
                        ),
                        modifier = Modifier
                            .size(20.dp)
                            .scale(likeScale),
                    )
                }

                val trailingCorner = if (onShowMore != null) 8.dp else 20.dp
                FilledTonalIconButton(
                    onClick = onAddToPlaylist,
                    enabled = item != null,
                    shapes = IconButtonShapes(
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = trailingCorner,
                            bottomEnd = trailingCorner,
                        ),
                        pressedShape = RoundedCornerShape(10.dp),
                    ),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = textColor.copy(alpha = 0.12f),
                        contentColor = textColor,
                    ),
                    modifier = Modifier.size(width = 44.dp, height = 40.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.playlistAdd(),
                        contentDescription = stringResource("add_to_playlist"),
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (onShowMore != null) {
                    FilledTonalIconButton(
                        onClick = onShowMore,
                        enabled = item != null,
                        shapes = IconButtonShapes(
                            shape = RoundedCornerShape(
                                topStart = 8.dp,
                                bottomStart = 8.dp,
                                topEnd = 20.dp,
                                bottomEnd = 20.dp,
                            ),
                            pressedShape = RoundedCornerShape(10.dp),
                        ),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textColor.copy(alpha = 0.12f),
                            contentColor = textColor,
                        ),
                        modifier = Modifier.size(width = 44.dp, height = 40.dp),
                    ) {
                        Icon(
                            imageVector = IconAssets.moreVert(),
                            contentDescription = stringResource("more_options"),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Expressive timeline slider + formatted timestamps
        Column(modifier = Modifier.fillMaxWidth()) {
            PlayerProgressSlider(
                value = activeProgress,
                onValueChange = { fraction -> dragFraction = fraction },
                onValueChangeFinished = {
                    val target = dragFraction
                    dragFraction = null
                    if (target != null && totalDurationMs > 0L) {
                        playerState.seekToProgress(target)
                    }
                },
                style = sliderStyle,
                activeColor = iconButtonColor,
                inactiveColor = mutedTextColor,
                isPlaying = playerState.isPlaying,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = formatTime(displayedPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedTextColor,
                )
                Text(
                    text = formatTime(totalDurationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedTextColor,
                )
            }
        }

        // Responsive Material 3 Expressive transport bar
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sizes = calculatePlayerControlSizes(maxWidth)
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()

            val playCorner by animateDpAsState(
                targetValue = if (playerState.isPlaying) 18.dp else sizes.playButtonHeight / 2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "player_play_corner",
            )
            val playWidth by animateDpAsState(
                targetValue = if (isPlayPressed) sizes.playButtonWidth + 8.dp else sizes.playButtonWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "player_play_width",
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Previous + Morphing Play/Pause + Next cluster
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { playerState.skipToPrevious() },
                        enabled = playerState.canSkipPrevious,
                        shapes = IconButtonShapes(
                            shape = RoundedCornerShape(sizes.sideButtonHeight / 2f),
                            pressedShape = RoundedCornerShape(12.dp),
                        ),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textColor.copy(alpha = 0.14f),
                            contentColor = textColor,
                            disabledContainerColor = textColor.copy(alpha = 0.06f),
                            disabledContentColor = textColor.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.size(
                            width = sizes.sideButtonWidth,
                            height = sizes.sideButtonHeight,
                        ),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipPrevious,
                            contentDescription = stringResource("previous"),
                            modifier = Modifier.size(sizes.sideIconSize),
                        )
                    }

                    FilledIconButton(
                        onClick = { playerState.togglePlayPause() },
                        enabled = item != null,
                        interactionSource = playInteractionSource,
                        shapes = IconButtonShapes(
                            shape = RoundedCornerShape(playCorner),
                            pressedShape = RoundedCornerShape(14.dp),
                        ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = iconButtonColor,
                            contentColor = textButtonColor,
                            disabledContainerColor = iconButtonColor.copy(alpha = 0.35f),
                            disabledContentColor = textButtonColor.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.size(
                            width = playWidth,
                            height = sizes.playButtonHeight,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                playerState.isBuffering -> {
                                    LoadingIndicator(
                                        modifier = Modifier.size(sizes.playIconSize * 0.72f),
                                        color = textButtonColor,
                                    )
                                }
                                playerState.isPlaying -> {
                                    Icon(
                                        imageVector = ExpressiveIcons.Pause,
                                        contentDescription = stringResource("pause"),
                                        modifier = Modifier.size(sizes.playIconSize),
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = ExpressiveIcons.PlayArrow,
                                        contentDescription = stringResource("play"),
                                        modifier = Modifier.size(sizes.playIconSize),
                                    )
                                }
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { playerState.skipToNext() },
                        enabled = playerState.canSkipNext,
                        shapes = IconButtonShapes(
                            shape = RoundedCornerShape(sizes.sideButtonHeight / 2f),
                            pressedShape = RoundedCornerShape(12.dp),
                        ),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textColor.copy(alpha = 0.14f),
                            contentColor = textColor,
                            disabledContainerColor = textColor.copy(alpha = 0.06f),
                            disabledContentColor = textColor.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.size(
                            width = sizes.sideButtonWidth,
                            height = sizes.sideButtonHeight,
                        ),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipNext,
                            contentDescription = stringResource("next"),
                            modifier = Modifier.size(sizes.sideIconSize),
                        )
                    }
                }
            }
        }
    }
}
