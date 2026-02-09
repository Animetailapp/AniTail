package com.anitail.desktop.ui.component

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets

internal val ListItemHeight = 64.dp
internal val ListThumbnailSize = 48.dp
internal val ThumbnailCornerRadius = 6.dp

internal fun pluralizeSongs(count: Int): String {
    return if (count == 1) "1 canción" else "$count canciones"
}

internal fun playlistPlaceholderIcon(name: String, autoPlaylist: Boolean): ImageVector {
    return when (name) {
        "Me gusta" -> IconAssets.favoriteBorder()
        "Sin conexión" -> IconAssets.offline()
        "En caché" -> IconAssets.cached()
        else -> if (autoPlaylist) IconAssets.trendingUp() else IconAssets.queueMusic()
    }
}
