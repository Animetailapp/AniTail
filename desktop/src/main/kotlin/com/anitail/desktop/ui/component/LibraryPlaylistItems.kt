package com.anitail.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.GridThumbnailHeight
import com.anitail.desktop.ui.screen.library.LibraryPlaylist
import com.anitail.desktop.ui.screen.library.SmallGridThumbnailHeight

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable BoxScope.() -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    when (thumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            placeHolder()
        }
        1 -> RemoteImage(
            url = thumbnails[0],
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape),
        ) {
            val positions = listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd,
            )
            positions.forEachIndexed { index, alignment ->
                val url = thumbnails.getOrNull(index) ?: return@forEachIndexed
                RemoteImage(
                    url = url,
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2),
                )
            }
        }
    }
}

@Composable
fun LibraryPlaylistListItem(
    playlist: LibraryPlaylist,
    autoPlaylist: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    LibraryListItem(
        title = playlist.playlist.name,
        subtitle = if (autoPlaylist) null else pluralizeSongs(playlist.songCount),
        thumbnailContent = {
            PlaylistThumbnail(
                thumbnails = playlist.thumbnails,
                size = ListThumbnailSize,
                placeHolder = {
                    val icon = playlistPlaceholderIcon(playlist.playlist.name, autoPlaylist)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ListThumbnailSize / 2),
                    )
                },
                shape = RoundedCornerShape(ThumbnailCornerRadius),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
    )
}

@Composable
fun LibraryPlaylistGridItem(
    playlist: LibraryPlaylist,
    autoPlaylist: Boolean,
    gridItemSize: GridItemSize,
    modifier: Modifier = Modifier,
) {
    val size = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    Column(
        modifier = modifier
            .width(size)
            .padding(12.dp),
    ) {
        BoxWithPlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = size,
            autoPlaylist = autoPlaylist,
            name = playlist.playlist.name,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!autoPlaylist) {
            Text(
                text = pluralizeSongs(playlist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxWithPlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    autoPlaylist: Boolean,
    name: String,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
    ) {
        PlaylistThumbnail(
            thumbnails = thumbnails,
            size = size,
            placeHolder = {
                val icon = playlistPlaceholderIcon(name, autoPlaylist)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size / 2),
                )
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )
    }
}
