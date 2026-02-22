package com.anitail.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.Song
import com.anitail.music.ui.utils.resize

internal fun highResThumbnail(
    url: String?,
    width: Int,
    height: Int,
): String? {
    if (url == null) return null
    val resized = url.replace(Regex("=w\\d+-h\\d+"), "=w$width-h$height")
    return resized.resize(width, height)
}

internal fun ytItemStableKey(item: YTItem): String {
    return when (item) {
        is SongItem -> "song:${item.id}"
        is AlbumItem -> "album:${item.id}"
        is ArtistItem -> "artist:${item.id}"
        is PlaylistItem -> "playlist:${item.id}"
    }
}

internal fun localItemStableKey(item: LocalItem): String {
    return when (item) {
        is Song -> "song:${item.id}"
        is Album -> "album:${item.id}"
        is Artist -> "artist:${item.id}"
        is Playlist -> "playlist:${item.id}"
    }
}

@Composable
internal fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 11.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    ),
            )
        }
    }
}

@Composable
internal fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
