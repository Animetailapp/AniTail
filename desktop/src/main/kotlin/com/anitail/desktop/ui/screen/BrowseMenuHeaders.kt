package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage

private val MenuThumbnailSize = 48.dp
private val MenuThumbnailCornerRadius = 6.dp
private val MenuQuickActionSpacing = 8.dp
private val MenuQuickActionRadius = 8.dp
private val MenuQuickActionIconSize = 24.dp

@Composable
fun SongMenuHeader(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuHeaderRow(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        trailingIcon = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
        trailingTint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        onTrailingClick = onToggleLike,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(MenuQuickActionSpacing),
    ) {
        MenuQuickActionButton(
            label = stringResource("play_next"),
            icon = IconAssets.playlistPlay(),
            onClick = {
                onPlayNext()
                onDismiss()
            },
        )
        MenuQuickActionButton(
            label = stringResource("add_to_playlist"),
            icon = IconAssets.playlistAdd(),
            onClick = {
                onAddToPlaylist()
                onDismiss()
            },
        )
        MenuQuickActionButton(
            label = stringResource("share"),
            icon = IconAssets.share(),
            onClick = {
                onShare()
                onDismiss()
            },
        )
    }
}

@Composable
fun ArtistMenuHeader(
    title: String,
    thumbnailUrl: String?,
    showRadio: Boolean,
    showShuffle: Boolean,
    onStartRadio: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuHeaderRow(
        title = title,
        subtitle = "",
        thumbnailUrl = thumbnailUrl,
        trailingIcon = null,
        trailingTint = MaterialTheme.colorScheme.onSurfaceVariant,
        onTrailingClick = {},
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(MenuQuickActionSpacing),
    ) {
        if (showRadio) {
            MenuQuickActionButton(
                label = stringResource("start_radio"),
                icon = IconAssets.radio(),
                onClick = {
                    onStartRadio()
                    onDismiss()
                },
            )
        }
        if (showShuffle) {
            MenuQuickActionButton(
                label = stringResource("shuffle"),
                icon = IconAssets.shuffle(),
                onClick = {
                    onShuffle()
                    onDismiss()
                },
            )
        }
        MenuQuickActionButton(
            label = stringResource("share"),
            icon = IconAssets.share(),
            onClick = {
                onShare()
                onDismiss()
            },
        )
    }
}

@Composable
fun CollectionMenuHeader(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    showLike: Boolean,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
) {
    MenuHeaderRow(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        trailingIcon = if (showLike) {
            if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder()
        } else null,
        trailingTint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        onTrailingClick = onToggleLike,
    )
}

@Composable
private fun MenuHeaderRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    trailingIcon: ImageVector?,
    trailingTint: androidx.compose.ui.graphics.Color,
    onTrailingClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RemoteImage(
            url = thumbnailUrl,
            modifier = Modifier
                .size(MenuThumbnailSize)
                .clip(RoundedCornerShape(MenuThumbnailCornerRadius)),
            shape = RoundedCornerShape(MenuThumbnailCornerRadius),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingIcon != null) {
            IconButton(onClick = onTrailingClick) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = trailingTint,
                )
            }
        }
    }
}

@Composable
private fun RowScope.MenuQuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(MenuQuickActionRadius))
            .clip(RoundedCornerShape(MenuQuickActionRadius))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(MenuQuickActionIconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 4.dp)
                .basicMarquee(),
        )
    }
}
