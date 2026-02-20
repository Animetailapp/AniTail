package com.anitail.desktop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.screen.formatTime
import com.anitail.innertube.models.Artist
import com.anitail.shared.model.LibraryItem
import kotlin.math.roundToInt

@Composable
fun SleepTimerDialog(
    visible: Boolean,
    isActive: Boolean,
    timeLeftMs: Long,
    onStartTimer: (Long) -> Unit,
    onStartEndOfSong: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var minutes by remember { mutableStateOf(30f) }
    val minutesInt = minutes.roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("sleep_timer")) },
        text = {
            Column {
                if (isActive) {
                    Text(
                        text = stringResource("time_remaining", formatTime(timeLeftMs)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = pluralStringResource("minute", minutesInt, minutesInt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        onStartEndOfSong()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource("end_of_song"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStartTimer(minutes.roundToInt() * 60_000L)
                    onDismiss()
                },
            ) {
                Text(if (isActive) stringResource("update") else stringResource("start"))
            }
        },
        dismissButton = {
            Row {
                if (isActive) {
                    TextButton(
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource("cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource("close"))
                }
            }
        },
    )
}

@Composable
fun AudioSettingsDialog(
    visible: Boolean,
    volume: Double,
    onVolumeChange: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val volumePercent = (volume * 100).roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("equalizer")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource("volume_percent", volumePercent),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { onVolumeChange(it.toDouble()) },
                    valueRange = 0f..1f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("close"))
            }
        },
    )
}

@Composable
fun TempoPitchDialog(
    visible: Boolean,
    tempo: Float,
    pitchSemitone: Int,
    onTempoChange: (Float) -> Unit,
    onPitchChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("tempo_and_pitch")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val tempoFormatted = "%.2f".format(tempo)
                Text(
                    text = stringResource("tempo_value", tempoFormatted),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = tempo,
                    onValueChange = onTempoChange,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
                Spacer(modifier = Modifier.height(16.dp))
                val pitchLabel = if (pitchSemitone > 0) "+$pitchSemitone" else pitchSemitone.toString()
                Text(
                    text = stringResource("pitch_value", pitchLabel),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = pitchSemitone.toFloat(),
                    onValueChange = { onPitchChange(it.roundToInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("close"))
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(stringResource("reset"))
            }
        },
    )
}

@Composable
fun ArtistPickerDialog(
    visible: Boolean,
    artists: List<Artist>,
    onSelect: (Artist) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("select_artist")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                artists.forEach { artist ->
                    TextButton(
                        onClick = { onSelect(artist) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = artist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("close"))
            }
        },
    )
}

@Composable
fun MediaDetailsDialog(
    visible: Boolean,
    item: LibraryItem,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("details")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource("song_title"), item.title)
                DetailRow(stringResource("artist"), item.artist)
                DetailRow(stringResource("duration"), formatTime(item.durationMs ?: 0L))
                DetailRow(stringResource("id"), item.id)
                DetailRow(stringResource("url"), item.playbackUrl)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopyLink) {
                    Text(stringResource("copy_link"))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onOpenInBrowser) {
                    Text(stringResource("open_in_browser"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("close"))
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label + ":",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
