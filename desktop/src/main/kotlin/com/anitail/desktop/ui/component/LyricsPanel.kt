package com.anitail.desktop.ui.component

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.lyrics.DesktopLyricsService
import com.anitail.desktop.lyrics.LyricLine
import com.anitail.desktop.lyrics.LyricsResult
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.launch

/**
 * Lyrics panel component for displaying synchronized lyrics.
 */
@Composable
fun LyricsPanel(
    title: String,
    artist: String,
    videoId: String? = null,
    durationSec: Int,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lyricsLabel = stringResource("lyrics")
    val syncedLabel = stringResource("lyrics_synced")
    val plainLabel = stringResource("lyrics_plain")
    val retryLabel = stringResource("retry")
    val lyricsNotFoundLabel = stringResource("lyrics_not_found")
    val lyricsEmptyLabel = stringResource("lyrics_start_playing")

    // Cargar letras cuando cambia la canción
    LaunchedEffect(title, artist, videoId, durationSec) {
        if (title.isBlank()) {
            lyricsResult = null
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        lyricsResult = null

        DesktopLyricsService.getLyrics(
            title = title,
            artist = artist,
            durationSec = durationSec,
            videoId = videoId,
        )
            .onSuccess { result ->
                lyricsResult = result
            }
            .onFailure { e ->
                error = e.message
            }

        isLoading = false
    }

    // Auto-scroll a la línea actual
    val currentLineIndex = lyricsResult?.getCurrentLineIndex(currentPositionMs) ?: -1
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && lyricsResult?.parsedLines?.isNotEmpty() == true) {
            // Centrar la línea actual
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -200 // offset para centrar aproximadamente
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = IconAssets.lyrics(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lyricsLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))

                // Indicador de tipo
                if (lyricsResult != null) {
                    Surface(
                        color = if (lyricsResult?.hasSyncedLyrics == true) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (lyricsResult?.hasSyncedLyrics == true) syncedLabel else plainLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Retry button
                if (error != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                DesktopLyricsService.getLyrics(
                                    title = title,
                                    artist = artist,
                                    durationSec = durationSec,
                                    videoId = videoId,
                                )
                                    .onSuccess { lyricsResult = it }
                                    .onFailure { error = it.message }
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(IconAssets.refresh(), contentDescription = retryLabel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    error != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = IconAssets.musicNote(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lyricsNotFoundLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    lyricsResult != null -> {
                        val lines = lyricsResult?.parsedLines ?: emptyList()
                        if (lines.isNotEmpty()) {
                            // Synced lyrics
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(lines) { index, line ->
                                    LyricLineItem(
                                        line = line,
                                        isActive = index == currentLineIndex,
                                        onClick = { onSeek(line.timestampMs) },
                                    )
                                }
                            }
                        } else if (lyricsResult?.plainLyrics != null) {
                            // Plain lyrics
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                item {
                                    Text(
                                        text = lyricsResult?.plainLyrics ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // No song playing
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = IconAssets.lyrics(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lyricsEmptyLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        label = LyricColorLabel
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        label = LyricBackgroundLabel
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = line.text,
            style = if (isActive) {
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = textColor,
        )
    }
}

private const val LyricColorLabel = "lyric_color"
private const val LyricBackgroundLabel = "lyric_bg"
