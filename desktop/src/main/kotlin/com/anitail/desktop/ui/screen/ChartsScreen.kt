package com.anitail.desktop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.shimmer.GridItemPlaceholder
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ChartsScreen displays music charts from YouTube Music.
 * Mirrors the Android ChartsScreen functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPlayTrack: (LibraryItem) -> Unit,
    onPlaylistClick: (String, String) -> Unit = { _, _ -> },
) {
    var chartsPage by remember { mutableStateOf<ChartsPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            scope.launch {
                isLoading = true
                withContext(Dispatchers.IO) {
                    YouTube.getChartsPage().onSuccess { page ->
                        chartsPage = page
                    }
                }
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = IconAssets.arrowBack(),
                    contentDescription = stringResource("back"),
                )
            }
            Text(
                text = stringResource("charts"),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (isLoading || chartsPage == null) {
            // Loading shimmer
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(200.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        repeat(4) {
                            GridItemPlaceholder()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                chartsPage?.sections?.filter { it.title != "Top music videos" }
                    ?.forEach { section ->
                        item {
                            NavigationTitle(
                                title = when (section.title) {
                                    "Trending" -> stringResource("trending")
                                    "Top songs" -> stringResource("top_songs")
                                    "Top artists" -> stringResource("top_artists")
                                    else -> section.title ?: stringResource("charts")
                                }
                            )
                        }

                        item {
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = section.items.filterIsInstance<SongItem>(),
                                    key = { it.id },
                                ) { song ->
                                    ChartSongItem(
                                        song = song,
                                        isPlaying = playerState.currentItem?.id == song.id && playerState.isPlaying,
                                        onClick = {
                                            val libraryItem = LibraryItem(
                                                id = song.id,
                                                title = song.title,
                                                artist = song.artists?.joinToString { it.name }
                                                    .orEmpty(),
                                                artworkUrl = song.thumbnail,
                                                playbackUrl = "https://music.youtube.com/watch?v=${song.id}",
                                            )
                                            onPlayTrack(libraryItem)
                                        },
                                        modifier = Modifier.width(300.dp),
                                    )
                                }
                            }
                        }
                    }

                // Top music videos section
                chartsPage?.sections?.find { it.title == "Top music videos" }?.let { section ->
                    item {
                        NavigationTitle(title = stringResource("top_music_videos"))
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = section.items.filterIsInstance<SongItem>(),
                                key = { it.id },
                            ) { video ->
                                ChartVideoItem(
                                    video = video,
                                    isPlaying = playerState.currentItem?.id == video.id && playerState.isPlaying,
                                    onClick = {
                                        val libraryItem = LibraryItem(
                                            id = video.id,
                                            title = video.title,
                                            artist = video.artists?.joinToString { it.name }
                                                .orEmpty(),
                                            artworkUrl = video.thumbnail,
                                            playbackUrl = "https://music.youtube.com/watch?v=${video.id}",
                                        )
                                        onPlayTrack(libraryItem)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSongItem(
    song: SongItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isPlaying) 8.dp else 0.dp,
        color = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(4.dp),
            ) {
                com.anitail.desktop.ui.component.RemoteImage(
                    url = song.thumbnail,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = song.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartVideoItem(
    video: SongItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isPlaying) 8.dp else 2.dp,
    ) {
        Column {
            // Video thumbnail (16:9)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            ) {
                com.anitail.desktop.ui.component.RemoteImage(
                    url = video.thumbnail,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Video info
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
