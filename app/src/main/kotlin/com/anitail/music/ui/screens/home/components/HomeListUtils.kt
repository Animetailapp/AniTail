@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.anitail.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.HomePage
import com.anitail.music.R
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.Song
import com.anitail.music.ui.component.rememberExpressivePolygonShape
import com.anitail.music.ui.screens.artist.artistArtworkShape
import com.anitail.music.ui.screens.artist.rememberArtistReducedMotion
import com.anitail.music.ui.utils.resize
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.sizedArtworkUrl

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
        is EpisodeItem -> "episode:${item.id}"
        is AlbumItem -> "album:${item.id}"
        is ArtistItem -> "artist:${item.id}"
        is PlaylistItem -> "playlist:${item.id}"
        is PodcastItem -> "podcast:${item.id}"
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
    val reducedMotion = rememberArtistReducedMotion()
    val widthSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = widthSpec,
                label = "homePagerDotWidth_$index",
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "homePagerDotColor_$index",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
internal fun HomeFilterChipShelf(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onToggleChip: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clipToBounds()
            .focusGroup()
            .selectableGroup()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val isSelected = selectedChip == chip
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onToggleChip(chip) },
                shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.height(ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Text(
                    text = chip.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun HomeExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    thumbnailUrl: String? = null,
    artistSeed: String? = null,
    isAccountAvatar: Boolean = false,
    onClick: (() -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .tvClickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (isAccountAvatar || thumbnailUrl != null) {
            val thumbShape = when {
                isAccountAvatar -> rememberExpressivePolygonShape(MaterialShapes.Cookie9Sided)
                artistSeed != null -> artistArtworkShape(artistSeed)
                else -> RoundedCornerShape(10.dp)
            }
            Surface(
                modifier = Modifier.size(42.dp),
                shape = thumbShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = sizedArtworkUrl(thumbnailUrl, 256),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            if (!overline.isNullOrBlank()) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onPlayAllClick != null) {
            FilledTonalButton(
                onClick = onPlayAllClick,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.height(ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.play_all),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        if (onClick != null) {
            IconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    HomeExpressiveSectionHeader(
        title = title,
        modifier = modifier,
    )
}
