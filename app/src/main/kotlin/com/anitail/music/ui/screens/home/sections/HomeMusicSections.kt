@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.pages.HomePage
import com.anitail.music.R
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.models.SimilarRecommendation
import com.anitail.music.ui.component.AlbumPlayButton
import com.anitail.music.ui.component.SwipeToSongBox
import com.anitail.music.ui.screens.artist.ArtistExplicitBadge
import com.anitail.music.ui.screens.artist.ArtistSongThumbnailOverlay
import com.anitail.music.ui.screens.artist.ArtistVideoShelf
import com.anitail.music.ui.screens.artist.animateCurrentSongColors
import com.anitail.music.ui.screens.artist.animatedCurrentSongArtworkShape
import com.anitail.music.ui.screens.artist.artistArtworkShape
import com.anitail.music.ui.screens.artist.carouselCaption
import com.anitail.music.ui.screens.artist.currentSongArtworkShape
import com.anitail.music.ui.screens.artist.rememberCurrentSongArtworkRotation
import com.anitail.music.ui.utils.tvCombinedClickable
import com.anitail.music.utils.joinByBullet
import com.anitail.music.utils.makeTimeString
import com.anitail.music.utils.sizedArtworkUrl

private val HomeCarouselCardWidth = 152.dp
private val HomeCarouselCornerRadius = 12.dp

@Composable
internal fun HomeExpressiveLocalSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = animateCurrentSongColors(isCurrent)
    val contentColor = colors.content
    val subtitleColor = colors.subtitle
    val shape = animatedCurrentSongArtworkShape(isCurrent, RoundedCornerShape(8.dp))
    val artworkRotationState = rememberCurrentSongArtworkRotation(isCurrent, isPlaying)

    val subtitleText = joinByBullet(
        song.song.artistName ?: song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
        makeTimeString(song.song.duration * 1000L),
    )

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clip(if (isCurrent) CircleShape else RoundedCornerShape(12.dp))
                .background(colors.container)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (isCurrent) {
                            Modifier.graphicsLayer { rotationZ = artworkRotationState.value }
                        } else {
                            Modifier
                        },
                    )
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isCurrent) {
                                Modifier.graphicsLayer { rotationZ = -artworkRotationState.value }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumbUrl = song.thumbnailUrl
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = sizedArtworkUrl(thumbUrl, 544),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (isCurrent) {
                        ArtistSongThumbnailOverlay(
                            isActive = true,
                            isPlaying = isPlaying,
                            shape = shape,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val isFavorited = if (song.song.isEpisode) song.song.inLibrary != null else song.song.liked
                val hasBadges = isFavorited || song.song.inLibrary != null || song.song.explicit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isFavorited) {
                        Icon(
                            painter = painterResource(R.drawable.favorite),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (song.song.inLibrary != null) {
                        Icon(
                            painter = painterResource(R.drawable.library_add_check),
                            contentDescription = null,
                            tint = subtitleColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (song.song.explicit) {
                        ArtistExplicitBadge()
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (hasBadges) Modifier.weight(1f, fill = false) else Modifier,
                    )
                }
            }

            IconButton(
                onClick = onMenuClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = subtitleColor,
                )
            }
        }
    }

    if (swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            rowContent()
        }
    } else {
        rowContent()
    }
}

@Composable
private fun HomeSongsPagerSection(
    songs: List<Song>,
    rows: Int,
    screenWidthDp: Int,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    swipeEnabled: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val safeRows = rows.coerceAtLeast(1)
    val columns = remember(songs, safeRows) { songs.chunked(safeRows) }
    val pagerState = rememberPagerState { columns.size }
    val firstSongId = songs.firstOrNull()?.id
    LaunchedEffect(firstSongId) {
        if (firstSongId != null && pagerState.currentPage > 0) {
            pagerState.scrollToPage(0)
        }
    }
    val pageWidth = computeHomePagerWidthDp(screenWidthDp).dp

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(pageWidth),
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) { pageIndex ->
        val columnSongs = columns[pageIndex]
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            columnSongs.forEach { song ->
                val isCurrent = song.id == mediaMetadataId
                HomeExpressiveLocalSongRow(
                    song = song,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    swipeEnabled = swipeEnabled,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) },
                    onMenuClick = { onSongMenuClick(song) },
                )
            }
        }
    }
}

internal fun LazyListScope.SongsGridBlock(
    sectionKey: String,
    @StringRes titleRes: Int,
    songs: List<Song>,
    rows: Int,
    screenWidthDp: Int,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    swipeEnabled: Boolean,
    onPlayAllClick: (() -> Unit)? = null,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMenuClick: (Song) -> Unit,
) {
    if (songs.isEmpty()) return

    item(key = "${sectionKey}_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            title = stringResource(titleRes),
            onPlayAllClick = onPlayAllClick,
        )
    }

    item(key = "${sectionKey}_content", contentType = "home_song_pager") {
        HomeSongsPagerSection(
            songs = songs,
            rows = rows,
            screenWidthDp = screenWidthDp,
            mediaMetadataId = mediaMetadataId,
            isPlaying = isPlaying,
            swipeEnabled = swipeEnabled,
            onSongClick = onSongClick,
            onSongLongClick = onSongLongClick,
            onSongMenuClick = onSongMenuClick,
        )
    }
}

@Composable
private fun HomeLocalCarouselCard(
    item: LocalItem,
    artworkModifier: Modifier,
    maskShape: androidx.compose.ui.graphics.Shape,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    carouselItemDrawInfo: CarouselItemDrawInfo,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArtist = item is Artist
    val isCurrent = when (item) {
        is Song -> item.id == currentSongId
        is Album -> item.id == currentAlbumId
        else -> false
    }

    val title = when (item) {
        is Song -> item.song.title
        is Album -> item.album.title
        is Artist -> item.artist.name
        else -> ""
    }
    val subtitle = when (item) {
        is Song -> item.song.artistName ?: item.artists.joinToString { it.name }
        is Album -> item.artists.joinToString { it.name }
        is Artist -> pluralStringResource(R.plurals.n_song, item.songCount, item.songCount)
        else -> ""
    }
    val thumbnailUrl = item.thumbnailUrl

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HomeCarouselCornerRadius))
            .tvCombinedClickable(
                onClick = {
                    when (item) {
                        is Song -> onSongClick(item)
                        is Album -> onAlbumClick(item)
                        is Artist -> onArtistClick(item)
                        else -> Unit
                    }
                },
                onLongClick = {
                    when (item) {
                        is Song -> onSongLongClick(item)
                        is Album -> onAlbumLongClick(item)
                        is Artist -> onArtistLongClick(item)
                        else -> Unit
                    }
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(artworkModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = sizedArtworkUrl(thumbnailUrl, 544),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = if (isArtist) Icons.Outlined.AccountCircle else Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(HomeCarouselCardWidth / 2),
                    )
                }
                if (item is Song || item is Album) {
                    ArtistSongThumbnailOverlay(
                        isActive = isCurrent,
                        isPlaying = isPlaying,
                        shape = maskShape,
                    )
                }
                if (item is Album && !isCurrent) {
                    AlbumPlayButton(
                        visible = true,
                        onClick = { onAlbumPlay(item) },
                    )
                }
            }
        }

        Column(Modifier.carouselCaption(carouselItemDrawInfo)) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeKeepListeningCarousel(
    items: List<LocalItem>,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val carouselState = rememberCarouselState { items.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        flingBehavior = flingBehavior,
        preferredItemWidth = HomeCarouselCardWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { index ->
        val localItem = items[index]
        val isCurrent = when (localItem) {
            is Song -> localItem.id == currentSongId
            is Album -> localItem.id == currentAlbumId
            else -> false
        }
        val maskShape = when (localItem) {
            is Artist -> artistArtworkShape(localItem.id)
            is Song -> if (isCurrent) currentSongArtworkShape() else RoundedCornerShape(HomeCarouselCornerRadius)
            else -> RoundedCornerShape(HomeCarouselCornerRadius)
        }
        HomeLocalCarouselCard(
            item = localItem,
            artworkModifier = Modifier.maskClip(maskShape),
            maskShape = maskShape,
            currentSongId = currentSongId,
            currentAlbumId = currentAlbumId,
            isPlaying = isPlaying && isCurrent,
            carouselItemDrawInfo = carouselItemDrawInfo,
            onSongClick = onSongClick,
            onSongLongClick = onSongLongClick,
            onAlbumClick = onAlbumClick,
            onAlbumLongClick = onAlbumLongClick,
            onAlbumPlay = onAlbumPlay,
            onArtistClick = onArtistClick,
            onArtistLongClick = onArtistLongClick,
        )
    }
}

internal fun LazyListScope.KeepListeningBlock(
    keepListening: List<LocalItem>?,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onArtistLongClick: (Artist) -> Unit,
) {
    if (keepListening.isNullOrEmpty()) return

    item(key = "keep_listening_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            title = stringResource(R.string.keep_listening),
        )
    }

    item(key = "keep_listening_content", contentType = "home_keep_listening") {
        val displayItems = remember(keepListening) {
            keepListening.filter { it is Song || it is Album || it is Artist }
        }
        HomeKeepListeningCarousel(
            items = displayItems,
            currentSongId = currentSongId,
            currentAlbumId = currentAlbumId,
            isPlaying = isPlaying,
            onSongClick = onSongClick,
            onSongLongClick = onSongLongClick,
            onAlbumClick = onAlbumClick,
            onAlbumLongClick = onAlbumLongClick,
            onAlbumPlay = onAlbumPlay,
            onArtistClick = onArtistClick,
            onArtistLongClick = onArtistLongClick,
        )
    }
}

internal fun LazyListScope.AccountPlaylistsBlock(
    accountPlaylists: List<PlaylistItem>?,
    accountName: String,
    accountImageUrl: String?,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    actions: ExploreItemActions,
    onAccountClick: () -> Unit,
) {
    if (accountPlaylists.isNullOrEmpty()) return

    item(key = "account_playlists_title", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            overline = stringResource(R.string.your_ytb_playlists),
            title = accountName,
            thumbnailUrl = accountImageUrl,
            isAccountAvatar = true,
            onClick = onAccountClick,
        )
    }

    item(key = "account_playlists_content", contentType = "home_multi_browse") {
        ExploreMultiBrowseShelf(
            items = accountPlaylists,
            currentSongId = currentSongId,
            currentAlbumId = currentAlbumId,
            isPlaying = isPlaying,
            onItemClick = actions.onItemClick,
            onItemMenu = actions.onItemMenu,
            onAlbumPlay = actions.onAlbumPlay,
        )
    }
}

internal fun LazyListScope.SimilarRecommendationsBlock(
    similarRecommendations: List<SimilarRecommendation>?,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    actions: ExploreItemActions,
    onTitleClick: (LocalItem) -> Unit,
) {
    similarRecommendations?.forEachIndexed { index, recommendation ->
        if (recommendation.items.isEmpty()) return@forEachIndexed
        val recommendationKey = "${recommendation.title.id}_$index"
        val artistSeed = (recommendation.title as? Artist)?.id

        item(key = "similar_title_$recommendationKey", contentType = "home_section_header") {
            HomeExpressiveSectionHeader(
                overline = stringResource(R.string.similar_to),
                title = recommendation.title.title,
                thumbnailUrl = recommendation.title.thumbnailUrl,
                artistSeed = artistSeed,
                onClick = { onTitleClick(recommendation.title) },
            )
        }

        item(key = "similar_items_$recommendationKey", contentType = "home_multi_browse") {
            ExploreMultiBrowseShelf(
                items = recommendation.items,
                currentSongId = currentSongId,
                currentAlbumId = currentAlbumId,
                isPlaying = isPlaying,
                onItemClick = actions.onItemClick,
                onItemMenu = actions.onItemMenu,
                onAlbumPlay = actions.onAlbumPlay,
            )
        }
    }
}

internal fun LazyListScope.HomeDynamicSectionBlock(
    section: HomePage.Section,
    index: Int,
    currentSongId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    actions: ExploreItemActions,
    onHeaderClick: (() -> Unit)?,
    onPlayAllClick: (() -> Unit)?,
) {
    val shelfKind = classifyHomeDynamicSection(section.items) ?: return
    val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
    val artistSeed = if (section.endpoint?.isArtistEndpoint == true) {
        section.endpoint?.browseId ?: section.title
    } else {
        null
    }

    item(key = "home_section_title_$sectionKey", contentType = "home_section_header") {
        HomeExpressiveSectionHeader(
            overline = section.label,
            title = section.title,
            thumbnailUrl = section.thumbnail,
            artistSeed = artistSeed,
            onClick = onHeaderClick,
            onPlayAllClick = onPlayAllClick,
        )
    }

    item(key = "home_section_items_$sectionKey", contentType = "home_dynamic_shelf") {
        when (shelfKind) {
            HomeDynamicShelfKind.VIDEO_CAROUSEL -> {
                ArtistVideoShelf(
                    items = section.items,
                    onItemClick = actions.onItemClick,
                    onItemLongClick = actions.onItemMenu,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                )
            }
            HomeDynamicShelfKind.MULTI_BROWSE_CAROUSEL -> {
                ExploreMultiBrowseShelf(
                    items = section.items,
                    currentSongId = currentSongId,
                    currentAlbumId = currentAlbumId,
                    isPlaying = isPlaying,
                    onItemClick = actions.onItemClick,
                    onItemMenu = actions.onItemMenu,
                    onAlbumPlay = actions.onAlbumPlay,
                )
            }
        }
    }
}
