package com.anitail.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.GridThumbnailHeight
import com.anitail.desktop.ui.screen.library.LibraryPlaylist
import com.anitail.desktop.ui.screen.library.SmallGridThumbnailHeight

private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val ThumbnailCornerRadius = 6.dp

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable BoxScope.() -> Unit,
    shape: Shape,
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
    trailingContent: @Composable RowScope.() -> Unit = {},
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

@Composable
fun LibraryAlbumListItem(
    album: AlbumEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    LibraryListItem(
        title = album.title,
        subtitle = album.year?.toString(),
        thumbnailContent = {
            RemoteImage(
                url = album.thumbnailUrl,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )
        },
        trailingContent = trailingContent,
        isActive = isActive,
        isPlaying = isPlaying,
        modifier = modifier,
    )
}

@Composable
fun LibraryAlbumGridItem(
    album: AlbumEntity,
    gridItemSize: GridItemSize,
    modifier: Modifier = Modifier,
) {
    val size = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    Column(
        modifier = modifier
            .width(size)
            .padding(12.dp),
    ) {
        RemoteImage(
            url = album.thumbnailUrl,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        album.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LibraryArtistListItem(
    artist: ArtistEntity,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    LibraryListItem(
        title = artist.name,
        subtitle = null,
        thumbnailContent = {
            RemoteImage(
                url = artist.thumbnailUrl,
                shape = CircleShape,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
    )
}

@Composable
fun LibraryArtistGridItem(
    artist: ArtistEntity,
    gridItemSize: GridItemSize,
    modifier: Modifier = Modifier,
) {
    val size = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    Column(
        modifier = modifier
            .width(size)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteImage(
            url = artist.thumbnailUrl,
            shape = CircleShape,
            modifier = Modifier.size(size),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
    onMenuClick: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isActive -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(ListItemHeight)
            .fillMaxWidth()
            .background(background)
            .clickable { onClick() }
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

@Composable
private fun LibraryListItem(
    title: String,
    subtitle: String?,
    thumbnailContent: @Composable BoxScope.() -> Unit,
    trailingContent: @Composable RowScope.() -> Unit,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(ListItemHeight)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            thumbnailContent()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive && isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailingContent()
        }
    }
}

private fun pluralizeSongs(count: Int): String {
    return if (count == 1) "1 canción" else "$count canciones"
}

private fun playlistPlaceholderIcon(name: String, autoPlaylist: Boolean): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name) {
        "Me gusta" -> IconAssets.favoriteBorder()
        "Sin conexión" -> IconAssets.offline()
        "En caché" -> IconAssets.cached()
        else -> if (autoPlaylist) IconAssets.trendingUp() else IconAssets.queueMusic()
    }
}
