package com.anitail.music.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anitail.innertube.models.YTItem
import com.anitail.music.R
import com.anitail.music.constants.GridThumbnailHeight
import com.anitail.music.constants.ListItemHeight
import com.anitail.music.constants.ListThumbnailSize
import com.anitail.music.constants.ThumbnailCornerRadius
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Song
import com.anitail.music.models.SimilarRecommendation
import com.anitail.music.ui.component.NavigationTitle
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.utils.tvCombinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsHorizontalGridSection(
    songs: List<Song>,
    rows: Int,
    lazyGridState: LazyGridState,
    flingBehavior: FlingBehavior,
    itemWidth: Dp,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyHorizontalGrid(
        state = lazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = flingBehavior,
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight * rows),
    ) {
        items(
            items = songs,
            key = { it.id },
        ) { currentSong ->
            SongListItem(
                song = currentSong,
                showInLibraryIcon = true,
                isActive = currentSong.id == mediaMetadataId,
                isPlaying = isPlaying,
                isSwipeable = false,
                trailingContent = {
                    IconButton(onClick = { onSongMenuClick(currentSong) }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier
                    .width(itemWidth)
                    .tvCombinedClickable(
                        onClick = { onSongClick(currentSong) },
                        onLongClick = { onSongLongClick(currentSong) },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.SongsGridBlock(
    sectionKey: String,
    @StringRes titleRes: Int,
    songs: List<Song>,
    rows: Int,
    lazyGridState: LazyGridState,
    flingBehavior: FlingBehavior,
    itemWidth: Dp,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit,
) {
    if (songs.isEmpty()) return

    item(key = "${sectionKey}_title") {
        NavigationTitle(
            title = stringResource(titleRes),
        )
    }

    item(key = "${sectionKey}_content") {
        SongsHorizontalGridSection(
            songs = songs,
            rows = rows,
            lazyGridState = lazyGridState,
            flingBehavior = flingBehavior,
            itemWidth = itemWidth,
            mediaMetadataId = mediaMetadataId,
            isPlaying = isPlaying,
            onSongClick = onSongClick,
            onSongLongClick = onSongLongClick,
            onSongMenuClick = onSongMenuClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.KeepListeningBlock(
    keepListening: List<LocalItem>?,
    keepListeningLazyGridState: LazyGridState,
    localGridItem: @Composable (LocalItem) -> Unit,
) {
    if (keepListening.isNullOrEmpty()) return

    item(key = "keep_listening_title") {
        NavigationTitle(
            title = stringResource(R.string.keep_listening),
        )
    }

    item(key = "keep_listening_content") {
        val rows = if (keepListening.size > 6) 2 else 1
        LazyHorizontalGrid(
            state = keepListeningLazyGridState,
            rows = GridCells.Fixed(rows),
            modifier = Modifier
                .fillMaxWidth()
                .height((GridThumbnailHeight + with(LocalDensity.current) {
                    MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                }) * rows),
        ) {
            items(
                items = keepListening,
                key = { localItemStableKey(it) },
            ) {
                localGridItem(it)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.AccountPlaylistsBlock(
    accountPlaylists: List<com.anitail.innertube.models.PlaylistItem>?,
    accountName: String,
    accountImageUrl: String?,
    onAccountClick: () -> Unit,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    if (accountPlaylists.isNullOrEmpty()) return

    item(key = "account_playlists_title") {
        NavigationTitle(
            label = stringResource(R.string.your_ytb_playlists),
            title = accountName,
            thumbnail = {
                if (accountImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(accountImageUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(false)
                            .build(),
                        placeholder = painterResource(id = R.drawable.person),
                        error = painterResource(id = R.drawable.person),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.person),
                        contentDescription = null,
                        modifier = Modifier.size(ListThumbnailSize),
                    )
                }
            },
            onClick = onAccountClick,
        )
    }

    item(key = "account_playlists_content") {
        YtItemsRowSection(
            ytItems = accountPlaylists,
            itemContent = ytGridItem,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.SimilarRecommendationsBlock(
    similarRecommendations: List<SimilarRecommendation>?,
    onTitleClick: (LocalItem) -> Unit,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    similarRecommendations?.forEachIndexed { index, recommendation ->
        val recommendationKey = "${recommendation.title.id}_$index"

        item(key = "similar_title_$recommendationKey") {
            NavigationTitle(
                label = stringResource(R.string.similar_to),
                title = recommendation.title.title,
                thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
                                ThumbnailCornerRadius,
                            )
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape),
                        )
                    }
                },
                onClick = { onTitleClick(recommendation.title) },
            )
        }

        item(key = "similar_items_$recommendationKey") {
            YtItemsRowSection(
                ytItems = recommendation.items,
                itemContent = ytGridItem,
            )
        }
    }
}

@Composable
internal fun YtItemsRowSection(
    ytItems: List<YTItem>,
    itemContent: @Composable (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = ytItems,
            key = { ytItemStableKey(it) },
        ) { item ->
            itemContent(item)
        }
    }
}
