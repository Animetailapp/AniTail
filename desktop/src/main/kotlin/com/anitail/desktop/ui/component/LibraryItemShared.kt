package com.anitail.desktop.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets

internal val ListItemHeight = 64.dp
internal val ListThumbnailSize = 48.dp
internal val ThumbnailCornerRadius = 6.dp

@Composable
internal fun pluralizeSongs(count: Int): String {
    return pluralStringResource("n_song", count, count)
}

@Composable
internal fun playlistPlaceholderIcon(name: String, autoPlaylist: Boolean): ImageVector {
    val likedName = stringResource("liked_songs")
    val downloadedName = stringResource("downloaded_songs")
    val cachedName = stringResource("cached_playlist")
    return when (name) {
        likedName -> IconAssets.favoriteBorder()
        downloadedName -> IconAssets.offline()
        cachedName -> IconAssets.cached()
        else -> if (autoPlaylist) IconAssets.trendingUp() else IconAssets.queueMusic()
    }
}
