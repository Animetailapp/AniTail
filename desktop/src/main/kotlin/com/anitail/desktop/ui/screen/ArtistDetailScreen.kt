package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import androidx.compose.ui.unit.sp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.ShimmerBox
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ArtistPage
import com.anitail.shared.model.LibraryItem

/**
 * Pantalla de detalle de artista para Desktop - Idéntica a Android.
 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onSongClick: (LibraryItem) -> Unit,
) {
    var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSubscribed by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val transparentAppBar by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex <= 1 }
    }

    LaunchedEffect(artistId) {
        isLoading = true
        error = null
        YouTube.artist(artistId).onSuccess { page ->
            artistPage = page
        }.onFailure { e ->
            error = e.message ?: "Error al cargar artista"
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (artistPage == null && isLoading) {
                item(key = "shimmer") {
                    ArtistShimmerPlaceholder()
                }
            } else {
                artistPage?.let { page ->
                    // Header: Imagen grande con fading edge
                    item(key = "header") {
                        Column {
                            // Imagen del artista con aspect ratio como Android
                            page.artist.thumbnail?.let { thumbnail ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.2f / 1f),
                                ) {
                                    RemoteImage(
                                        url = thumbnail,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .fadingEdge(bottom = 200.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }

                            // Nombre y controles
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
                            ) {
                                // Nombre del artista
                                Text(
                                    text = page.artist.title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 32.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Fila de botones
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Botón Subscribe
                                    OutlinedButton(
                                        onClick = { isSubscribed = !isSubscribed },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSubscribed)
                                                MaterialTheme.colorScheme.surface
                                            else
                                                Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(
                                            text = if (isSubscribed) "Suscrito" else "Suscribirse",
                                            fontSize = 14.sp,
                                            color = if (!isSubscribed)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Botón Radio
                                        page.artist.radioEndpoint?.let {
                                            OutlinedButton(
                                                onClick = {
                                                    // TODO: Implementar radio
                                                },
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                Icon(
                                                    IconAssets.radio(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Radio",
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        // Botón Shuffle circular
                                        page.artist.shuffleEndpoint?.let {
                                            IconButton(
                                                onClick = {
                                                    val allSongs = page.sections
                                                        .flatMap { it.items }
                                                        .filterIsInstance<SongItem>()
                                                        .shuffled()
                                                    if (allSongs.isNotEmpty()) {
                                                        playerState.shuffleEnabled = true
                                                        playerState.playQueue(
                                                            allSongs.map { songItemToLibraryItem(it) },
                                                            0
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            ) {
                                                Icon(
                                                    IconAssets.shuffle(),
                                                    contentDescription = "Shuffle",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Secciones dinámicas
                    page.sections.forEach { section ->
                        if (section.items.isNotEmpty()) {
                            // Título de sección con navegación
                            item(key = "section_title_${section.title}") {
                                NavigationTitle(
                                    title = section.title,
                                    onClick = section.moreEndpoint?.let { { /* Navigate to more */ } }
                                )
                            }

                            // Si son canciones con álbum, mostrar como lista
                            val firstItem = section.items.firstOrNull()
                            if (firstItem is SongItem && firstItem.album != null) {
                                items(
                                    items = section.items.filterIsInstance<SongItem>(),
                                    key = { "song_${it.id}" }
                                ) { song ->
                                    YouTubeListItem(
                                        item = song,
                                        isActive = playerState.currentItem?.id == song.id,
                                        isPlaying = playerState.isPlaying,
                                        onClick = { onSongClick(songItemToLibraryItem(song)) },
                                        onMoreClick = { /* TODO: Menu */ }
                                    )
                                }
                            } else {
                                // Mostrar en grid horizontal
                                item(key = "section_row_${section.title}") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        items(
                                            items = section.items,
                                            key = { it.id }
                                        ) { item ->
                                            YouTubeGridItem(
                                                item = item,
                                                isActive = when (item) {
                                                    is SongItem -> playerState.currentItem?.id == item.id
                                                    is AlbumItem -> false
                                                    else -> false
                                                },
                                                isPlaying = playerState.isPlaying,
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> onSongClick(songItemToLibraryItem(item))
                                                        is AlbumItem -> onAlbumClick(item.browseId, item.title)
                                                        is ArtistItem -> onArtistClick(item.id, item.title)
                                                        is PlaylistItem -> onPlaylistClick(item.id, item.title)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }

                error?.let { errorMsg ->
                    item {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        // TopAppBar flotante
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (transparentAppBar) Color.Transparent
                    else MaterialTheme.colorScheme.surface
                )
                .padding(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(IconAssets.arrowBack(), contentDescription = "Volver")
            }
            if (!transparentAppBar) {
                Text(
                    text = artistPage?.artist?.title ?: artistName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Extensión para fading edge como en Android
private fun Modifier.fadingEdge(bottom: androidx.compose.ui.unit.Dp) = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startY = size.height - bottom.toPx(),
            endY = size.height
        )
    )
}

@Composable
private fun ArtistShimmerPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Imagen placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f / 1f)
        ) {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Nombre
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(36.dp)
                    .padding(bottom = 16.dp)
            )

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .width(120.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(100.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    )
                    ShimmerBox(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    )
                }
            }
        }

        // Lista de canciones
        repeat(6) {
            ShimmerListItem()
        }
    }
}

@Composable
private fun YouTubeListItem(
    item: SongItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Thumbnail
        Box(modifier = Modifier.size(56.dp)) {
            RemoteImage(
                url = item.thumbnail,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
            )
            // Playing indicator
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Simple playing indicator
                    Text("▶", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                if (item.explicit) {
                    Text(
                        text = "E ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.artists.joinToString { it.name } +
                            (item.album?.let { " • ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                IconAssets.moreVert(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YouTubeGridItem(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val size = 160.dp
    Column(
        modifier = Modifier
            .width(size)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(modifier = Modifier.size(size)) {
            RemoteImage(
                url = item.thumbnail,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        when (item) {
                            is ArtistItem -> CircleShape
                            else -> RoundedCornerShape(8.dp)
                        }
                    ),
            )
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        when (item) {
            is AlbumItem -> {
                item.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            is ArtistItem -> {
                Text(
                    text = "Artista",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            is PlaylistItem -> {
                item.author?.name?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            else -> {}
        }
    }
}

