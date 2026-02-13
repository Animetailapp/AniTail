package com.anitail.desktop.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.GridThumbnailHeight
import com.anitail.desktop.ui.screen.library.SmallGridThumbnailHeight

@Composable
fun LibraryArtistListItem(
    artist: ArtistEntity,
    songCount: Int,
    showLikedBadge: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    LibraryListItem(
        title = artist.name,
        subtitle = pluralizeSongs(songCount),
        thumbnailContent = {
            RemoteImage(
                url = artist.thumbnailUrl,
                shape = CircleShape,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        badges = {
            if (showLikedBadge) {
                Icon(
                    imageVector = IconAssets.favorite(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun LibraryArtistGridItem(
    artist: ArtistEntity,
    songCount: Int,
    showLikedBadge: Boolean,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLikedBadge) {
                Icon(
                    imageVector = IconAssets.favorite(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp),
                )
            }
            Text(
                text = pluralizeSongs(songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
