package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.shared.model.LibraryItem

// Constante de padding horizontal del player (igual que Android)
private val PlayerHorizontalPadding = 32.dp

/**
 * PlayerScreen - Reproductor de música (diseño idéntico a Android Player.kt)
 */
@Composable
fun PlayerScreen(
    item: LibraryItem?,
    playerState: PlayerState,
) {
    // Colores del player
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val buttonContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val buttonContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (item == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = secondaryTextColor.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Selecciona una canción para reproducir",
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryTextColor,
                )
            }
        }
        return
    }

    // Fondo oscuro como en Android
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        // Contenedor con ancho máximo para simular layout móvil
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(450.dp)  // Ancho fijo para simular móvil
                .padding(horizontal = PlayerHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ========== NOW PLAYING HEADER ==========
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            ) {
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (playerState.queue.size > 1) 
                        "Queue · ${playerState.queue.size}" 
                    else 
                        "Playing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ========== THUMBNAIL (ocupa el espacio disponible) ==========
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    if (!item.artworkUrl.isNullOrEmpty()) {
                        RemoteImage(
                            url = item.artworkUrl,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                tint = secondaryTextColor.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // ========== CONTROLS SECTION ==========
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ----- Título/Artista + Botones -----
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = secondaryTextColor,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Botones like/playlist con forma especial
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(
                                topStart = 24.dp, bottomStart = 24.dp,
                                topEnd = 6.dp, bottomEnd = 6.dp
                            ),
                            color = buttonContainerColor,
                            onClick = { /* TODO: Like */ },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.FavoriteBorder,
                                    contentDescription = "Me gusta",
                                    modifier = Modifier.size(24.dp),
                                    tint = buttonContentColor,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(
                                topStart = 6.dp, bottomStart = 6.dp,
                                topEnd = 24.dp, bottomEnd = 24.dp
                            ),
                            color = buttonContainerColor,
                            onClick = { /* TODO: Playlist */ },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PlaylistAdd,
                                    contentDescription = "Añadir a playlist",
                                    modifier = Modifier.size(24.dp),
                                    tint = buttonContentColor,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ----- Slider de progreso -----
                var sliderPosition by remember { mutableStateOf<Long?>(null) }
                
                Slider(
                    value = (sliderPosition ?: playerState.position).toFloat(),
                    valueRange = 0f..playerState.duration.toFloat().coerceAtLeast(1f),
                    onValueChange = { sliderPosition = it.toLong() },
                    onValueChangeFinished = {
                        sliderPosition?.let { pos ->
                            playerState.seekTo(pos)
                        }
                        sliderPosition = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                // ----- Tiempos -----
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatDuration(sliderPosition ?: playerState.position),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor,
                    )
                    Text(
                        text = if (playerState.duration > 0) formatDuration(playerState.duration) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ----- Controles de reproducción -----
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Repeat
                    IconButton(
                        onClick = { playerState.toggleRepeat() },
                    ) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.ONE -> Icons.Filled.RepeatOne
                                else -> Icons.Filled.Repeat
                            },
                            contentDescription = "Repetir",
                            tint = if (playerState.repeatMode != RepeatMode.OFF)
                                MaterialTheme.colorScheme.primary
                            else
                                secondaryTextColor.copy(alpha = 0.5f),
                        )
                    }

                    // Previous
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(50),
                        color = buttonContainerColor,
                        onClick = { playerState.skipToPrevious() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Anterior",
                                modifier = Modifier.size(28.dp),
                                tint = buttonContentColor,
                            )
                        }
                    }

                    // Play/Pause
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(50),
                        color = buttonContainerColor,
                        onClick = { playerState.togglePlayPause() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = buttonContentColor,
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                                    modifier = Modifier.size(36.dp),
                                    tint = buttonContentColor,
                                )
                            }
                        }
                    }

                    // Next
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(50),
                        color = buttonContainerColor,
                        onClick = { playerState.skipToNext() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Siguiente",
                                modifier = Modifier.size(28.dp),
                                tint = buttonContentColor,
                            )
                        }
                    }

                    // Shuffle
                    IconButton(
                        onClick = { playerState.toggleShuffle() },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (playerState.shuffleEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                secondaryTextColor.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ----- Barra de volumen -----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = secondaryTextColor,
                    )
                    Slider(
                        value = playerState.volume.toFloat(),
                        onValueChange = { playerState.updateVolume(it.toDouble()) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = secondaryTextColor,
                    )
                }

                // Error message
                playerState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
