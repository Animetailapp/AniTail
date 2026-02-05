package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.RemoteImage

/**
 * MiniPlayer para Desktop - diseño inspirado en Android NewMiniPlayer.
 * Muestra controles de reproducción, progreso circular, y botones de acción.
 */
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem = playerState.currentItem ?: return

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onOpenFullPlayer() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            ) {
                // Botón Play/Pause con indicador de progreso circular
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp),
                ) {
                    // Progreso circular
                    CircularProgressIndicator(
                        progress = { playerState.progress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )

                    // Botón Play/Pause
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { playerState.togglePlayPause() },
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) IconAssets.pause() else IconAssets.play(),
                            contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Thumbnail pequeño
                RemoteImage(
                    url = currentItem.artworkUrl,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(6.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Info de la canción
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = currentItem.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "title",
                    ) { title ->
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    AnimatedContent(
                        targetState = currentItem.artist,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "artist",
                    ) { artist ->
                        Text(
                            text = artist,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botón Anterior
                IconButton(
                    onClick = { playerState.skipToPrevious() },
                    enabled = playerState.canSkipPrevious || playerState.position > 3000L,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.previous(),
                        contentDescription = "Anterior",
                        tint = if (playerState.canSkipPrevious || playerState.position > 3000L)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Botón Siguiente
                IconButton(
                    onClick = { playerState.skipToNext() },
                    enabled = playerState.canSkipNext,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.next(),
                        contentDescription = "Siguiente",
                        tint = if (playerState.canSkipNext)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Botón Shuffle
                IconButton(
                    onClick = { playerState.toggleShuffle() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.shuffle(),
                        contentDescription = "Aleatorio",
                        tint = if (playerState.shuffleEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Botón Repeat
                IconButton(
                    onClick = { playerState.toggleRepeat() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> IconAssets.repeatOne()
                            else -> IconAssets.repeat()
                        },
                        contentDescription = "Repetir",
                        tint = if (playerState.repeatMode != RepeatMode.OFF)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Formatea milisegundos a mm:ss
 */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}
