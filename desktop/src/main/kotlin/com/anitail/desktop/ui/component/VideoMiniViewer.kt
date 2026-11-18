package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.i18n.stringResource

@Composable
fun VideoMiniViewer(
    playerState: PlayerState,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = playerState.showVideoViewer &&
        playerState.hasVideoStream &&
        playerState.currentItem != null &&
        !playerState.isPlayerExpanded &&
        !playerState.isFullscreenOverlay

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        val hoverInteraction = remember { MutableInteractionSource() }
        val isHovered by hoverInteraction.collectIsHoveredAsState()
        val shape = RoundedCornerShape(14.dp)

        Surface(
            modifier = Modifier
                .width(360.dp)
                .hoverable(hoverInteraction),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .background(Color.Black)
                    .clickable { onOpenFullPlayer() },
                contentAlignment = Alignment.Center,
            ) {
                // Video Surface
                VideoPlayerSurface(
                    videoPlayer = playerState.videoPlayer,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                    )
                }

                // Controls overlay on hover
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        // Top bar inside PiP card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { playerState.toggleFullscreenOverlay() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                            ) {
                                Icon(
                                    imageVector = ExpressiveIcons.Fullscreen,
                                    contentDescription = stringResource("fullscreen"),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    playerState.showVideoViewer = false
                                },
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                            ) {
                                Icon(
                                    imageVector = ExpressiveIcons.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // Bottom Track Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = playerState.currentItem?.title ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

