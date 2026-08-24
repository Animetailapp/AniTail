package com.anitail.desktop.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.recognition.DesktopSongRecognitionService
import com.anitail.desktop.ui.IconAssets
import com.anitail.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.launch

/**
 * Diálogo interactivo de reconocimiento de música en Desktop con animación Material 3.
 */
@Composable
fun SongRecognitionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPlayTrack: (title: String, artist: String) -> Unit,
) {
    if (!visible) return

    val status by DesktopSongRecognitionService.recognitionStatus.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible && status is RecognitionStatus.Ready) {
            DesktopSongRecognitionService.recognize()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    AlertDialog(
        onDismissRequest = {
            DesktopSongRecognitionService.reset()
            onDismiss()
        },
        title = {
            Text(
                text = "Reconocimiento de Música",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val currentStatus = status) {
                    is RecognitionStatus.Ready, is RecognitionStatus.Listening -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = IconAssets.equalizer(),
                                contentDescription = "Escuchando",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Escuchando música cercana...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Asegúrate de que la música sea audible en el micrófono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    is RecognitionStatus.Processing -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Identificando canción...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is RecognitionStatus.Success -> {
                        val track = currentStatus.result
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onPlayTrack(track.title, track.artist)
                                    DesktopSongRecognitionService.reset()
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!track.coverArtUrl.isNullOrBlank()) {
                                    RemoteImage(
                                        url = track.coverArtUrl,
                                        contentDescription = track.title,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onPlayTrack(track.title, track.artist)
                                        DesktopSongRecognitionService.reset()
                                        onDismiss()
                                    }
                                ) {
                                    Icon(
                                        imageVector = IconAssets.play(),
                                        contentDescription = "Reproducir",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    is RecognitionStatus.NoMatch -> {
                        Icon(
                            imageVector = IconAssets.close(),
                            contentDescription = "Sin coincidencias",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron coincidencias.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Prueba a acercar el micrófono a la fuente de audio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is RecognitionStatus.Error -> {
                        Icon(
                            imageVector = IconAssets.close(),
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentStatus.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val currentStatus = status) {
                is RecognitionStatus.Success -> {
                    Button(
                        onClick = {
                            onPlayTrack(currentStatus.result.title, currentStatus.result.artist)
                            DesktopSongRecognitionService.reset()
                            onDismiss()
                        }
                    ) {
                        Text("Buscar y Reproducir")
                    }
                }

                is RecognitionStatus.NoMatch, is RecognitionStatus.Error -> {
                    Button(
                        onClick = {
                            scope.launch {
                                DesktopSongRecognitionService.recognize()
                            }
                        }
                    ) {
                        Text("Reintentar")
                    }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    DesktopSongRecognitionService.reset()
                    onDismiss()
                }
            ) {
                Text("Cerrar")
            }
        }
    )
}
