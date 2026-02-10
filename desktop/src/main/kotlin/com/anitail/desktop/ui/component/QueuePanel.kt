package com.anitail.desktop.ui.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.shared.model.LibraryItem

/**
 * Queue panel component for displaying and managing the playback queue.
 */
@Composable
fun QueuePanel(
    playerState: PlayerState,
    onItemClick: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = playerState.queue
    val currentIndex = playerState.currentQueueIndex
    val queueTitle = stringResource("queue")
    val queueCount = pluralStringResource("n_song", queue.size, queue.size)
    val shuffleLabel = stringResource("shuffle")
    val repeatLabel = stringResource("repeat")
    val repeatAllLabel = stringResource("repeat_all")
    val repeatOneLabel = stringResource("repeat_one")
    val queueEmptyLabel = stringResource("queue_empty")
    val nowPlayingLabel = stringResource("now_playing")
    val upNextLabel = stringResource("up_next")
    val previouslyPlayedLabel = stringResource("previously_played")
    val removeFromQueueLabel = stringResource("remove_from_queue")

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
                    imageVector = IconAssets.queueMusic(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = queueTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))

                // Queue count badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = queueCount,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Queue controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Shuffle toggle
                FilterChip(
                    selected = playerState.shuffleEnabled,
                    onClick = { playerState.toggleShuffle() },
                    label = { Text(shuffleLabel) },
                    leadingIcon = {
                        Icon(
                            IconAssets.shuffle(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                // Repeat toggle
                FilterChip(
                    selected = playerState.repeatMode != com.anitail.desktop.player.RepeatMode.OFF,
                    onClick = { playerState.toggleRepeat() },
                    label = {
                        Text(
                            when (playerState.repeatMode) {
                                com.anitail.desktop.player.RepeatMode.OFF -> repeatLabel
                                com.anitail.desktop.player.RepeatMode.ALL -> repeatAllLabel
                                com.anitail.desktop.player.RepeatMode.ONE -> repeatOneLabel
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (playerState.repeatMode == com.anitail.desktop.player.RepeatMode.ONE) {
                                IconAssets.repeatOne()
                            } else {
                                IconAssets.repeat()
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Queue list
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = IconAssets.queueMusic(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = queueEmptyLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Now playing
                    if (currentIndex >= 0 && currentIndex < queue.size) {
                        item {
                            Text(
                                text = nowPlayingLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        item {
                            QueueItem(
                                item = queue[currentIndex],
                                index = currentIndex,
                                isPlaying = true,
                                onClick = { },
                                onRemove = { },
                                removeFromQueueLabel = removeFromQueueLabel,
                            )
                        }
                    }

                    // Up next
                    val upNextItems = queue.drop(currentIndex + 1)
                    if (upNextItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = upNextLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        itemsIndexed(upNextItems) { relativeIndex, item ->
                            val absoluteIndex = currentIndex + 1 + relativeIndex
                            QueueItem(
                                item = item,
                                index = absoluteIndex,
                                isPlaying = false,
                                onClick = { onItemClick(absoluteIndex) },
                                onRemove = { onRemoveItem(absoluteIndex) },
                                removeFromQueueLabel = removeFromQueueLabel,
                            )
                        }
                    }

                    // Previously played
                    if (currentIndex > 0) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = previouslyPlayedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        itemsIndexed(queue.take(currentIndex)) { index, item ->
                            QueueItem(
                                item = item,
                                index = index,
                                isPlaying = false,
                                isPrevious = true,
                                onClick = { onItemClick(index) },
                                onRemove = { onRemoveItem(index) },
                                removeFromQueueLabel = removeFromQueueLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    item: LibraryItem,
    index: Int,
    isPlaying: Boolean,
    isPrevious: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    removeFromQueueLabel: String,
) {
    val backgroundColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isPrevious -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    val contentAlpha = if (isPrevious) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(enabled = !isPlaying, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (!item.artworkUrl.isNullOrEmpty()) {
                RemoteImage(
                    url = item.artworkUrl!!,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = IconAssets.musicNote(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & artist
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Remove button (not for currently playing)
        if (!isPlaying) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = IconAssets.close(),
                    contentDescription = removeFromQueueLabel,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
    }
}
