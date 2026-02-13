package com.anitail.desktop.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.GridThumbnailHeight
import com.anitail.desktop.ui.screen.library.SmallGridThumbnailHeight

@Composable
fun LibraryAlbumListItem(
    album: AlbumEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
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
