package com.anitail.desktop.ui.screen

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerHorizontalPadding
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.shared.model.LibraryItem

@Composable
fun PlayerControls(
    item: LibraryItem,
    playerState: PlayerState,
    textColor: Color,
    mutedTextColor: Color,
    modifier: Modifier = Modifier,
) {
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val durationMs = playerState.duration
    val sliderValue = sliderPosition ?: playerState.progress
    val displayPosition = if (sliderPosition != null) {
        (durationMs * sliderValue).toLong()
    } else {
        playerState.position
    }

    Column(modifier = modifier) {
        playerState.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(horizontal = PlayerHorizontalPadding)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.artist,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = mutedTextColor,
                modifier = Modifier.basicMarquee(),
            )
        }

        Spacer(Modifier.height(12.dp))

        Slider(
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                sliderPosition?.let { playerState.seekToProgress(it) }
                sliderPosition = null
            },
            colors = SliderDefaults.colors(
                activeTrackColor = textColor,
                inactiveTrackColor = textColor.copy(alpha = 0.3f),
                thumbColor = textColor,
            ),
            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding + 4.dp),
        ) {
            Text(
                text = formatTime(displayPosition),
                style = MaterialTheme.typography.labelMedium,
                color = mutedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = mutedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxW = maxWidth
            val playButtonHeight = maxW / 6f
            val playButtonWidth = playButtonHeight * 1.1f
            val sideButtonHeight = playButtonHeight * 0.7f
            val sideButtonWidth = sideButtonHeight * 1.3f

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { playerState.toggleRepeat() },
                    modifier = Modifier.size(48.dp),
                ) {
                    val repeatIcon = if (playerState.repeatMode == com.anitail.desktop.player.RepeatMode.ONE) {
                        IconAssets.repeatOne()
                    } else {
                        IconAssets.repeat()
                    }
                    val repeatTint = if (playerState.repeatMode == com.anitail.desktop.player.RepeatMode.OFF) {
                        mutedTextColor.copy(alpha = 0.4f)
                    } else {
                        textColor
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledTonalIconButton(
                    onClick = { playerState.skipToPrevious() },
                    enabled = playerState.canSkipPrevious,
                    modifier = Modifier
                        .size(width = sideButtonWidth, height = sideButtonHeight)
                        .clip(RoundedCornerShape(32.dp)),
                ) {
                    Icon(
                        imageVector = IconAssets.previous(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledIconButton(
                    onClick = { playerState.togglePlayPause() },
                    modifier = Modifier
                        .size(width = playButtonWidth, height = playButtonHeight)
                        .clip(RoundedCornerShape(32.dp)),
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = textColor,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) IconAssets.pause() else IconAssets.play(),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledTonalIconButton(
                    onClick = { playerState.skipToNext() },
                    enabled = playerState.canSkipNext,
                    modifier = Modifier
                        .size(width = sideButtonWidth, height = sideButtonHeight)
                        .clip(RoundedCornerShape(32.dp)),
                ) {
                    Icon(
                        imageVector = IconAssets.next(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { playerState.toggleShuffle() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.shuffle(),
                        contentDescription = "Shuffle",
                        tint = if (playerState.shuffleEnabled) textColor else mutedTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
