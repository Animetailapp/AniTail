package com.anitail.desktop.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LibrarySongListItem(
    song: SongEntity,
    showInLibraryIcon: Boolean,
    downloaded: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit,
    swipeEnabled: Boolean = false,
    onSwipePlayNext: (() -> Unit)? = null,
    onSwipeAddToQueue: (() -> Unit)? = null,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isActive -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val swipeThreshold = 180f
    val offsetX = remember(song.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable { onClick() }
    }

    Box(
        modifier = modifier
            .height(ListItemHeight)
            .fillMaxWidth()
            .pointerInput(swipeEnabled, onSwipePlayNext, onSwipeAddToQueue) {
                if (!swipeEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            val next = (offsetX.value + dragAmount).coerceIn(-swipeThreshold, swipeThreshold)
                            offsetX.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        val finalOffset = offsetX.value
                        when {
                            finalOffset >= swipeThreshold -> onSwipePlayNext?.invoke()
                            finalOffset <= -swipeThreshold -> onSwipeAddToQueue?.invoke()
                        }
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                        }
                    },
                )
            },
    ) {
        if (offsetX.value != 0f) {
            val swipeRight = offsetX.value > 0f
            val swipeBackground =
                if (swipeRight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            val swipeTint =
                if (swipeRight) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
            val swipeIcon =
                if (swipeRight) IconAssets.playlistPlay() else IconAssets.queueMusic()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(swipeBackground),
                contentAlignment = if (swipeRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = swipeIcon,
                    contentDescription = null,
                    tint = swipeTint,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(26.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(background)
                .then(clickModifier)
                .padding(horizontal = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    RemoteImage(
                        url = song.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.musicNote(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive && isPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!song.artistName.isNullOrBlank()) {
                        Text(
                            text = song.artistName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showInLibraryIcon && song.inLibrary != null) {
                        Icon(
                            imageVector = IconAssets.libraryAddCheck(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (downloaded) {
                        Icon(
                            imageVector = IconAssets.offline(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = IconAssets.moreVert(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
