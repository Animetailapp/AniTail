package com.anitail.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.anitail.music.R
import com.anitail.music.utils.YTPlayerUtils.AudioFormatOption

@Composable
fun DownloadFormatDialog(
    isLoading: Boolean,
    formats: List<AudioFormatOption>,
    onFormatSelected: (AudioFormatOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val recommendedItag = formats.firstOrNull { it.supportsMetadata }?.itag
        ?: formats.maxByOrNull { it.bitrate }?.itag

    val orderedFormats = remember(formats, recommendedItag) {
        formats.sortedWith(
            compareByDescending<AudioFormatOption> { it.itag == recommendedItag }
                .thenByDescending { it.bitrate }
        )
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.84f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.choose_download_quality),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (!isLoading && orderedFormats.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.download_quality_dialog_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading_formats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                orderedFormats.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.no_formats_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items = orderedFormats, key = { it.itag }) { format ->
                                FormatItem(
                                    format = format,
                                    isRecommended = format.itag == recommendedItag,
                                    onClick = { onFormatSelected(format) },
                                )
                            }
                        }

                        if (orderedFormats.any { !it.supportsMetadata }) {
                            Text(
                                text = stringResource(R.string.m4a_metadata_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun FormatItem(
    format: AudioFormatOption,
    isRecommended: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isRecommended) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        },
        tonalElevation = if (isRecommended) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${format.bitrateKbps} kbps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (isRecommended) {
                        FormatBadge(
                            text = stringResource(R.string.recommended_badge),
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    FormatBadge(
                        text = if (format.supportsMetadata) {
                            stringResource(R.string.metadata_badge)
                        } else {
                            stringResource(R.string.audio_only_badge)
                        },
                        backgroundColor = if (format.supportsMetadata) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (format.supportsMetadata) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Text(
                    text = formatCodecLabel(format),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = if (format.supportsMetadata) {
                        stringResource(R.string.metadata_supported_hint)
                    } else {
                        stringResource(R.string.metadata_unsupported_hint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (format.supportsMetadata) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FormatBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun formatCodecLabel(format: AudioFormatOption): String {
    val extension = when {
        format.mimeType.contains("webm", ignoreCase = true) -> ".webm"
        format.mimeType.contains("mp4", ignoreCase = true) -> ".m4a"
        format.mimeType.contains("mpeg", ignoreCase = true) -> ".mp3"
        else -> ".audio"
    }
    return "${format.codec.uppercase()} ($extension)"
}
