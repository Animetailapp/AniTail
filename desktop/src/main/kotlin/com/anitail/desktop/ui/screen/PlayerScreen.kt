package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerHorizontalPadding
import com.anitail.desktop.constants.PlayerQueueCollapsedHeight
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.LyricsPanel
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.collapsedAnchor
import com.anitail.desktop.ui.component.rememberBottomSheetState
import com.anitail.shared.model.LibraryItem

@Composable
fun PlayerScreen(
    item: LibraryItem?,
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        EmptyPlayerState(modifier = modifier)
        return
    }

    val preferences = remember { DesktopPreferences.getInstance() }
    val showLyrics by preferences.showLyrics.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()

    val backgroundColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    val textMutedColor = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val queueSheetState = rememberBottomSheetState(
            dismissedBound = PlayerQueueCollapsedHeight,
            collapsedBound = PlayerQueueCollapsedHeight,
            expandedBound = maxHeight,
            initialAnchor = collapsedAnchor,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = PlayerQueueCollapsedHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerNowPlayingHeader(
                title = "Reproduciendo",
                subtitle = item.artist.ifBlank { "Cola actual" },
                textColor = textColor,
                subtitleColor = textMutedColor,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics) {
                    LyricsPanel(
                        title = item.title,
                        artist = item.artist,
                        durationSec = ((item.durationMs ?: 0L) / 1000L).toInt(),
                        currentPositionMs = playerState.position,
                        onSeek = { playerState.seekTo(it) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = PlayerHorizontalPadding),
                    )
                } else {
                    RemoteImage(
                        url = item.artworkUrl ?: "",
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .aspectRatio(1f)
                            .shadow(16.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }

            PlayerControls(
                item = item,
                playerState = playerState,
                textColor = textColor,
                mutedTextColor = textMutedColor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        PlayerQueueSheet(
            state = queueSheetState,
            playerState = playerState,
            currentItem = item,
            showLyrics = showLyrics,
            onToggleLyrics = { preferences.setShowLyrics(it) },
            pureBlack = pureBlack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlayerNowPlayingHeader(
    title: String,
    subtitle: String,
    textColor: Color,
    subtitleColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
            .padding(top = 40.dp, bottom = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = IconAssets.musicNote(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay nada reproduciendose",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
