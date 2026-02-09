package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import androidx.compose.ui.unit.sp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerBox
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.PlaylistPage
import com.anitail.shared.model.LibraryItem

/**
 * Pantalla de detalle de playlist para Desktop - Idéntica a Android.
 * Basada en OnlinePlaylistScreen.kt de la app Android.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    var playlistPage by remember { mutableStateOf<PlaylistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val topBarHeight = 56.dp
    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(playlistId) {
        isLoading = true
        error = null
        YouTube.playlist(playlistId).onSuccess { page ->
            playlistPage = page
        }.onFailure { e ->
            error = e.message ?: "Error al cargar playlist"
        }
        isLoading = false
    }

    // Filtrar canciones por búsqueda
    val filteredSongs = remember(playlistPage?.songs, searchQuery) {
        val songs = playlistPage?.songs ?: emptyList()
        if (searchQuery.isEmpty()) {
            songs.mapIndexed { index, song -> index to song }
        } else {
            songs.mapIndexed { index, song -> index to song }
                .filter { (_, song) ->
                    song.title.contains(searchQuery, ignoreCase = true) ||
                            song.artists.any { it.name.contains(searchQuery, ignoreCase = true) }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = topBarHeight),
            modifier = Modifier.fillMaxSize(),
        ) {
            playlistPage?.let { page ->
                // Header con info de playlist (si no está buscando)
                if (!isSearching) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Portada de playlist (AlbumThumbnailSize = 160.dp)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    RemoteImage(
                                        url = page.playlist.thumbnail,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Título de playlist
                                    Text(
                                        text = page.playlist.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 22.sp,
                                    )

                                    // Autor clickeable
                                    page.playlist.author?.let { artist ->
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                            color = if (artist.id != null)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.clickable(enabled = artist.id != null) {
                                                artist.id?.let { onArtistClick(it, artist.name) }
                                            },
                                        )
                                    }

                                    // Conteo de canciones
                                    page.playlist.songCountText?.let { songCountText ->
                                        Text(
                                            text = songCountText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                        )
                                    } ?: Text(
                                        text = "${page.songs.size} canciones",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                    )

                                    // Iconos de acción
                                    Row {
                                        // Favorito (solo si no es "LM" - Liked Music)
                                        if (page.playlist.id != "LM") {
                                            IconButton(
                                                onClick = { isLiked = !isLiked }
                                            ) {
                                                Icon(
                                                    imageVector = if (isLiked) IconAssets.favorite()
                                                    else IconAssets.favoriteBorder(),
                                                    contentDescription = null,
                                                    tint = if (isLiked)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        LocalContentColor.current
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { /* TODO: More menu */ }
                                        ) {
                                            Icon(
                                                IconAssets.moreVert(),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Botones Shuffle y Radio
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Shuffle
                                page.playlist.shuffleEndpoint?.let {
                                    Button(
                                        onClick = {
                                            val songs = page.songs.shuffled()
                                                .map { songItemToLibraryItem(it) }
                                            if (songs.isNotEmpty()) {
                                                playerState.shuffleEnabled = true
                                                playerState.playQueue(songs, 0)
                                            }
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            IconAssets.shuffle(),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text("Aleatorio")
                                    }
                                }

                                // Radio
                                page.playlist.radioEndpoint?.let {
                                    OutlinedButton(
                                        onClick = {
                                            // TODO: Implementar radio endpoint
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            IconAssets.radio(),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize),
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text("Radio")
                                    }
                                }
                            }
                        }
                    }
                }

                // Lista de canciones
                items(
                    items = filteredSongs,
                    key = { (index, song) -> "${index}_${song.id}" }
                ) { (originalIndex, song) ->
                    YouTubeListItem(
                        item = song,
                        isActive = playerState.currentItem?.id == song.id,
                        isPlaying = playerState.isPlaying,
                        onClick = {
                            val songs = page.songs.map { songItemToLibraryItem(it) }
                            playerState.playQueue(songs, originalIndex)
                        },
                        onArtistClick = onArtistClick,
                        onMoreClick = { /* TODO: Menu */ }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isLoading) {
                item { PlaylistShimmerPlaceholder() }
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

        // TopAppBar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        onBack()
                    }
                }
            ) {
                Icon(IconAssets.arrowBack(), contentDescription = "Volver")
            }

            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            } else if (showTopBarTitle) {
                Text(
                    text = playlistPage?.playlist?.title ?: playlistName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Botón buscar
            if (!isSearching) {
                IconButton(onClick = { isSearching = true }) {
                    Icon(IconAssets.search(), contentDescription = "Buscar")
                }
            }
        }
    }
}

@Composable
private fun YouTubeListItem(
    item: SongItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
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
            // Artistas clickeables
            Row {
                if (item.explicit) {
                    Text(
                        text = "E ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.artists.forEachIndexed { index, artist ->
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (artist.id != null)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(enabled = artist.id != null) {
                            artist.id?.let { onArtistClick(it, artist.name) }
                        },
                    )
                    if (index < item.artists.lastIndex) {
                        Text(
                            text = ", ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Duración
        item.duration?.let { duration ->
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun PlaylistShimmerPlaceholder() {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                ShimmerBox(modifier = Modifier.width(200.dp).height(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(150.dp).height(20.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(100.dp).height(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp)
            )
            ShimmerBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        repeat(10) {
            ShimmerListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
