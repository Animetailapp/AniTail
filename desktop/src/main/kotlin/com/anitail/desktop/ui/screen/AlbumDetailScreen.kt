package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerBox
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.AlbumPage
import com.anitail.shared.model.LibraryItem

/**
 * Pantalla de detalle de álbum para Desktop - Idéntica a Android.
 * Basada en AlbumScreen.kt de la app Android.
 */
@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    var albumPage by remember { mutableStateOf<AlbumPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) {
        isLoading = true
        error = null
        YouTube.album(albumId).onSuccess { page ->
            albumPage = page
        }.onFailure { e ->
            error = e.message ?: "Error al cargar álbum"
        }
        isLoading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        // TopAppBar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = albumPage?.album?.title ?: albumName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        albumPage?.let { page ->
            // Header con portada e info - como Android AlbumScreen
            item {
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Portada del álbum (AlbumThumbnailSize = 160.dp en Android)
                        RemoteImage(
                            url = page.album.thumbnail,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // Título del álbum con AutoResizeText
                            Text(
                                text = page.album.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 22.sp,
                            )

                            // Artistas clickeables
                            page.album.artists?.let { artists ->
                                Row {
                                    artists.forEachIndexed { index, artist ->
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                artist.id?.let { onArtistClick(it, artist.name) }
                                            },
                                        )
                                        if (index != artists.lastIndex) {
                                            Text(
                                                text = ", ",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                }
                            }

                            // Año
                            page.album.year?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }

                            // Iconos de acción: Favorito, Descargar, Más
                            Row {
                                IconButton(
                                    onClick = { isLiked = !isLiked }
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite
                                        else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isLiked) MaterialTheme.colorScheme.error
                                        else LocalContentColor.current,
                                    )
                                }

                                IconButton(
                                    onClick = { /* TODO: Download */ }
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                    )
                                }

                                IconButton(
                                    onClick = { /* TODO: More menu */ }
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Botones Play y Shuffle
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val songs = page.songs.map { songItemToLibraryItem(it) }
                                if (songs.isNotEmpty()) {
                                    playerState.playQueue(songs, 0)
                                }
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Reproducir")
                        }

                        OutlinedButton(
                            onClick = {
                                val songs = page.songs.shuffled().map { songItemToLibraryItem(it) }
                                if (songs.isNotEmpty()) {
                                    playerState.shuffleEnabled = true
                                    playerState.playQueue(songs, 0)
                                }
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Aleatorio")
                        }
                    }
                }
            }

            // Lista de canciones con número de track
            itemsIndexed(
                items = page.songs,
                key = { _, song -> song.id }
            ) { index, song ->
                SongListItem(
                    song = song,
                    albumIndex = index + 1,
                    isActive = song.id == playerState.currentItem?.id,
                    isPlaying = playerState.isPlaying,
                    onClick = {
                        val songs = page.songs.map { songItemToLibraryItem(it) }
                        playerState.playQueue(songs, index)
                    },
                    onMoreClick = { /* TODO: Menu */ }
                )
            }

            // Otras versiones (si existen)
            if (page.otherVersions.isNotEmpty()) {
                item {
                    Text(
                        text = "Otras versiones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        items(
                            items = page.otherVersions,
                            key = { it.id }
                        ) { album ->
                            AlbumGridItem(
                                thumbnail = album.thumbnail,
                                title = album.title,
                                year = album.year,
                                onClick = { /* Navigate to album */ }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (isLoading) {
            item { AlbumShimmerPlaceholder() }
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

@Composable
private fun SongListItem(
    song: SongItem,
    albumIndex: Int,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Número de track
        Text(
            text = albumIndex.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )

        // Thumbnail pequeño
        RemoteImage(
            url = song.thumbnail,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Título y artista
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Duración
        song.duration?.let { duration ->
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Botón más opciones
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumGridItem(
    thumbnail: String,
    title: String,
    year: Int?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        RemoteImage(
            url = thumbnail,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        year?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumShimmerPlaceholder() {
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
                ShimmerBox(modifier = Modifier.width(80.dp).height(16.dp))
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

        repeat(8) {
            ShimmerListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun songItemToLibraryItem(song: SongItem): LibraryItem {
    return LibraryItem(
        id = song.id,
        title = song.title,
        artist = song.artists.joinToString { it.name },
        artworkUrl = song.thumbnail,
        playbackUrl = "https://music.youtube.com/watch?v=${song.id}",
        durationMs = song.duration?.times(1000)?.toLong(),
    )
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
