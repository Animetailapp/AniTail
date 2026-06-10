package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.shared.model.LibraryItem

private val BrowseGridSize = 160.dp
val BrowseGridMinWidth = 152.dp
private val BrowseThumbnailCornerRadius = 8.dp
private val BrowseOverlayButtonSize = 36.dp
private val BrowseOverlayIconSize = 20.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseGridItem(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    libraryItem: LibraryItem,
    menuActions: List<ContextMenuAction>,
    songEntity: SongEntity?,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
    onMenuOpened: (() -> Unit)? = null,
    onAlbumPlay: () -> Unit,
) {
    val menuExpanded = remember(item.id) { mutableStateOf(false) }
    val subtitle = when (item) {
        is SongItem -> joinByBullet(
            item.artists.joinToString { it.name },
            item.duration?.let { formatTime(it * 1000L) },
        )
        is AlbumItem -> joinByBullet(
            item.artists?.joinToString { it.name },
            item.year?.toString(),
        )
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        is ArtistItem -> null
        else -> null
    }

    Box {
        Column(
            modifier = Modifier
                .width(BrowseGridSize)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (menuActions.isNotEmpty()) {
                            onMenuOpened?.invoke()
                            menuExpanded.value = true
                        }
                    },
                )
                .padding(4.dp),
        ) {
            Box(modifier = Modifier.size(BrowseGridSize)) {
                val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(BrowseThumbnailCornerRadius)
                RemoteImage(
                    url = item.thumbnail,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f), shape),
                    )
                    PlayingIndicatorBox(
                        isActive = isActive,
                        isPlaying = isPlaying,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (item is SongItem && !isActive) {
                    BrowseOverlayPlayButton(modifier = Modifier.align(Alignment.Center))
                }
                if (item is AlbumItem && !isActive) {
                    BrowseAlbumPlayButton(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onClick = onAlbumPlay,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (item is SongItem) {
                BrowseBadgesRow(
                    songEntity = songEntity,
                    isExplicit = item.explicit,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded,
                )
            }
        }

        ItemContextMenu(
            expanded = menuExpanded.value,
            onDismiss = { menuExpanded.value = false },
            item = libraryItem,
            actions = menuActions,
            headerContent = menuHeader?.let { header ->
                { header { menuExpanded.value = false } }
            },
        )
    }
}

@Composable
private fun BrowseBadgesRow(
    songEntity: SongEntity?,
    isExplicit: Boolean,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
) {
    val showLiked = songEntity?.liked == true
    val showLibrary = songEntity?.inLibrary != null
    val showDownload = shouldShowDownloadIcon(downloadState, isDownloaded)
    if (!showLiked && !isExplicit && !showLibrary && !showDownload) return

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLiked) {
            Icon(
                imageVector = IconAssets.favorite(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (isExplicit) {
            Text(
                text = stringResource("explicit_badge"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showLibrary) {
            Icon(
                imageVector = IconAssets.libraryMusic(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (showDownload) {
            Icon(
                imageVector = IconAssets.download(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun BrowseOverlayPlayButton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(BrowseOverlayButtonSize)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = IconAssets.play(),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(BrowseOverlayIconSize),
        )
    }
}

@Composable
private fun BrowseAlbumPlayButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(BrowseOverlayButtonSize)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = IconAssets.play(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BrowseOverlayIconSize),
            )
        }
    }
}

private fun joinByBullet(left: String?, right: String?): String? {
    return when {
        left.isNullOrBlank() && right.isNullOrBlank() -> null
        left.isNullOrBlank() -> right
        right.isNullOrBlank() -> left
        else -> "$left • $right"
    }
}

fun YTItem.toBrowseLibraryItem(): LibraryItem {
    val artistName = when (this) {
        is SongItem -> artists.joinToString { it.name }
        is AlbumItem -> artists?.joinToString { it.name } ?: ""
        is PlaylistItem -> author?.name ?: ""
        is ArtistItem -> title
        else -> ""
    }
    return LibraryItem(
        id = id,
        title = title,
        artist = artistName,
        artworkUrl = thumbnail,
        playbackUrl = shareLink,
        durationMs = if (this is SongItem) duration?.times(1000L) else null,
    )
}
