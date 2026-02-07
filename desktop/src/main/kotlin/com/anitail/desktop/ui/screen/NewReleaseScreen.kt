package com.anitail.desktop.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.shimmer.GridItemPlaceholder
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NewReleaseScreen displays new album and single releases.
 * Mirrors the Android NewReleaseScreen functionality but adapted for flat list API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    var albums by remember { mutableStateOf<List<AlbumItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (albums == null) {
            scope.launch {
                isLoading = true
                withContext(Dispatchers.IO) {
                    YouTube.newReleaseAlbums().onSuccess { list ->
                        albums = list
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
                    contentDescription = "Volver",
                )
            }
            Text(
                text = "Nuevos lanzamientos",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (isLoading || albums == null) {
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = albums!!,
                    key = { it.id ?: it.title },
                ) { album ->
                    NewReleaseAlbumItem(
                        album = album,
                        onClick = {
                            onAlbumClick(album.browseId, album.title)
                        },
                        onArtistClick = { artistId, artistName ->
                            onArtistClick(artistId, artistName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NewReleaseAlbumItem(
    album: AlbumItem,
    onClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            // Album artwork
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            ) {
                RemoteImage(
                    url = album.thumbnail,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Album info
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Artists - clickable
                album.artists?.forEach { artist ->
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            artist.id?.let { onArtistClick(it, artist.name) }
                        },
                    )
                }

                // Year if available
                album.year?.let { year ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Album type badge
                if (album.explicit) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
