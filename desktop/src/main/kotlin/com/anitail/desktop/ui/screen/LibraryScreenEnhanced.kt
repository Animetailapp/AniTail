package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.NavigationTitle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.shared.model.LibraryItem

/**
 * Filtros para la biblioteca - replica Android LibraryFilter
 */
enum class LibraryFilterType(val label: String) {
    TODOS("Todo"),
    PLAYLISTS("Playlists"),
    CANCIONES("Canciones"),
    ALBUMES("Álbumes"),
    ARTISTAS("Artistas"),
    DESCARGAS("Descargas"),
}

/**
 * Datos de una playlist especial
 */
data class SpecialPlaylist(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val iconTint: Color,
    val songCount: Int,
)

/**
 * Pantalla de Biblioteca mejorada - replica Android LibraryScreen/LibraryMixScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenEnhanced(
    items: List<LibraryItem>,
    playerState: PlayerState,
    onPlayItem: (LibraryItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
) {
    var currentFilter by remember { mutableStateOf(LibraryFilterType.TODOS) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Playlists especiales (como Android)
    val specialPlaylists = remember {
        listOf(
            SpecialPlaylist(
                id = "liked",
                name = "Me gusta",
                icon = IconAssets.favorite(),
                iconTint = Color(0xFFE91E63),
                songCount = 0,
            ),
            SpecialPlaylist(
                id = "offline",
                name = "Sin conexión",
                icon = IconAssets.download(),
                iconTint = Color(0xFF4CAF50),
                songCount = 0,
            ),
            SpecialPlaylist(
                id = "top50",
                name = "Mi Top 50",
                icon = IconAssets.trendingUp(),
                iconTint = Color(0xFFFF9800),
                songCount = 0,
            ),
        )
    }

    // Categorizar items
    val playlists = remember(items) {
        items.filter { it.playbackUrl.contains("playlist?list=") || it.playbackUrl.contains("/playlist/") }
    }
    val artists = remember(items) {
        items.filter { it.playbackUrl.contains("/channel/") || it.playbackUrl.contains("/artist/") }
    }
    val songs = remember(items) {
        items.filterNot { it in playlists || it in artists }
    }
    val albums = remember(items) {
        items.filter { it.playbackUrl.contains("/album/") }
    }

    // Dialogo crear playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            title = { Text("Nueva Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName)
                            showCreatePlaylistDialog = false
                            newPlaylistName = ""
                        }
                    },
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NavigationTitle(title = "Biblioteca")
        ChipsRow(
            chips = listOf(
                LibraryFilterType.PLAYLISTS to "Playlists",
                LibraryFilterType.CANCIONES to "Canciones",
                LibraryFilterType.ALBUMES to "Álbumes",
                LibraryFilterType.ARTISTAS to "Artistas",
                LibraryFilterType.DESCARGAS to "Descargas",
            ),
            currentValue = currentFilter,
            onValueUpdate = { filter ->
                currentFilter = if (currentFilter == filter) LibraryFilterType.TODOS else filter
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Contenido según filtro
        when (currentFilter) {
            LibraryFilterType.TODOS -> LibraryMixContent(
                specialPlaylists = specialPlaylists,
                playlists = playlists,
                artists = artists,
                songs = songs,
                albums = albums,
                onPlayItem = onPlayItem,
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onPlaylistClick = onPlaylistClick,
                onSpecialPlaylistClick = { /* TODO: Navigate to special playlist */ },
                onCreatePlaylist = { showCreatePlaylistDialog = true },
            )
            
            LibraryFilterType.PLAYLISTS -> LibraryPlaylistsContent(
                specialPlaylists = specialPlaylists,
                playlists = playlists,
                onPlaylistClick = onPlaylistClick,
                onSpecialPlaylistClick = { /* TODO */ },
                onCreatePlaylist = { showCreatePlaylistDialog = true },
            )
            
            LibraryFilterType.CANCIONES -> LibrarySongsContent(
                songs = songs,
                onPlayItem = onPlayItem,
            )
            
            LibraryFilterType.ALBUMES -> LibraryAlbumsContent(
                albums = albums,
                onAlbumClick = onAlbumClick,
            )
            
            LibraryFilterType.ARTISTAS -> LibraryArtistsContent(
                artists = artists,
                onArtistClick = onArtistClick,
            )
            
            LibraryFilterType.DESCARGAS -> LibraryDownloadsContent()
        }
    }
}

/**
 * Vista mezclada (Todo) - como LibraryMixScreen de Android
 */
@Composable
private fun LibraryMixContent(
    specialPlaylists: List<SpecialPlaylist>,
    playlists: List<LibraryItem>,
    artists: List<LibraryItem>,
    songs: List<LibraryItem>,
    albums: List<LibraryItem>,
    onPlayItem: (LibraryItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onSpecialPlaylistClick: (SpecialPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Playlists especiales en grid
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onCreatePlaylist) {
                        Icon(IconAssets.add(), contentDescription = "Crear playlist")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // Grid de playlists especiales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    specialPlaylists.forEach { special ->
                        SpecialPlaylistCard(
                            playlist = special,
                            onClick = { onSpecialPlaylistClick(special) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Playlists del usuario
        if (playlists.isNotEmpty()) {
            items(playlists.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        LibraryGridItem(
                            item = item,
                            onClick = { 
                                val id = extractIdFromUrl(item.playbackUrl, "playlist")
                                onPlaylistClick(id, item.title)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Espaciadores para mantener grid uniforme
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Artistas
        if (artists.isNotEmpty()) {
            item {
                Text(
                    text = "Artistas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(artists.chunked(4)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        ArtistGridItem(
                            item = item,
                            onClick = {
                                val id = extractIdFromUrl(item.playbackUrl, "channel")
                                onArtistClick(id, item.title)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Álbumes
        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Álbumes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(albums.chunked(4)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        LibraryGridItem(
                            item = item,
                            onClick = {
                                val id = extractIdFromUrl(item.playbackUrl, "album")
                                onAlbumClick(id, item.title)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Canciones recientes
        if (songs.isNotEmpty()) {
            item {
                Text(
                    text = "Canciones añadidas recientemente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(songs.take(10)) { song ->
                SongListItem(
                    item = song,
                    onPlay = onPlayItem,
                )
            }
        }

        // Estado vacío
        if (playlists.isEmpty() && artists.isEmpty() && songs.isEmpty() && albums.isEmpty()) {
            item {
                EmptyLibraryState()
            }
        }
    }
}

/**
 * Contenido para pestaña Playlists
 */
@Composable
private fun LibraryPlaylistsContent(
    specialPlaylists: List<SpecialPlaylist>,
    playlists: List<LibraryItem>,
    onPlaylistClick: (String, String) -> Unit,
    onSpecialPlaylistClick: (SpecialPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Botón crear
        item {
            CreatePlaylistCard(onClick = onCreatePlaylist)
        }
        
        // Playlists especiales
        items(specialPlaylists) { special ->
            SpecialPlaylistCard(
                playlist = special,
                onClick = { onSpecialPlaylistClick(special) },
            )
        }
        
        // Playlists del usuario
        items(playlists) { item ->
            LibraryGridItem(
                item = item,
                onClick = {
                    val id = extractIdFromUrl(item.playbackUrl, "playlist")
                    onPlaylistClick(id, item.title)
                },
            )
        }
    }
}

/**
 * Contenido para pestaña Canciones
 */
@Composable
private fun LibrarySongsContent(
    songs: List<LibraryItem>,
    onPlayItem: (LibraryItem) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyStateMessage(
            icon = IconAssets.musicNote(),
            title = "No hay canciones",
            subtitle = "Las canciones que guardes aparecerán aquí",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(songs) { song ->
                SongListItem(item = song, onPlay = onPlayItem)
            }
        }
    }
}

/**
 * Contenido para pestaña Álbumes
 */
@Composable
private fun LibraryAlbumsContent(
    albums: List<LibraryItem>,
    onAlbumClick: (String, String) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyStateMessage(
            icon = IconAssets.album(),
            title = "No hay álbumes",
            subtitle = "Los álbumes que guardes aparecerán aquí",
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums) { album ->
                LibraryGridItem(
                    item = album,
                    onClick = {
                        val id = extractIdFromUrl(album.playbackUrl, "album")
                        onAlbumClick(id, album.title)
                    },
                )
            }
        }
    }
}

/**
 * Contenido para pestaña Artistas
 */
@Composable
private fun LibraryArtistsContent(
    artists: List<LibraryItem>,
    onArtistClick: (String, String) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyStateMessage(
            icon = IconAssets.person(),
            title = "No hay artistas",
            subtitle = "Los artistas que sigas aparecerán aquí",
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists) { artist ->
                ArtistGridItem(
                    item = artist,
                    onClick = {
                        val id = extractIdFromUrl(artist.playbackUrl, "channel")
                        onArtistClick(id, artist.title)
                    },
                )
            }
        }
    }
}

/**
 * Contenido para pestaña Descargas
 */
@Composable
private fun LibraryDownloadsContent() {
    EmptyStateMessage(
        icon = IconAssets.download(),
        title = "No hay descargas",
        subtitle = "Las descargas estarán disponibles próximamente en Desktop",
    )
}

// ========== COMPONENTES DE UI ==========

@Composable
private fun SpecialPlaylistCard(
    playlist: SpecialPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(playlist.iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = playlist.icon,
                    contentDescription = null,
                    tint = playlist.iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlist.songCount > 0) {
                    Text(
                        text = "${playlist.songCount} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(180.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = IconAssets.add(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Nueva Playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LibraryGridItem(
    item: LibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(180.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!item.artworkUrl.isNullOrEmpty()) {
                    RemoteImage(
                        url = item.artworkUrl.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.queueMusic(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.artist.isNotEmpty()) {
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistGridItem(
    item: LibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!item.artworkUrl.isNullOrEmpty()) {
                RemoteImage(
                    url = item.artworkUrl.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = IconAssets.person(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SongListItem(
    item: LibraryItem,
    onPlay: (LibraryItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(item) },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!item.artworkUrl.isNullOrEmpty()) {
                    RemoteImage(
                        url = item.artworkUrl.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.musicNote(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.artist.isNotEmpty()) {
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { onPlay(item) }) {
                Icon(
                    imageVector = IconAssets.play(),
                    contentDescription = "Reproducir",
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = IconAssets.libraryMusic(),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "Tu biblioteca está vacía",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Busca música y añádela a tu biblioteca",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ========== UTILIDADES ==========

/**
 * Extrae el ID de una URL de YouTube Music
 */
private fun extractIdFromUrl(url: String, type: String): String {
    return when (type) {
        "playlist" -> {
            val regex = Regex("list=([^&]+)")
            regex.find(url)?.groupValues?.getOrNull(1) ?: url
        }
        "channel", "artist" -> {
            val regex = Regex("/channel/([^/&?]+)|/artist/([^/&?]+)")
            regex.find(url)?.let { match ->
                match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
            } ?: url
        }
        "album" -> {
            val regex = Regex("/album/([^/&?]+)|browse/([^/&?]+)")
            regex.find(url)?.let { match ->
                match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
            } ?: url
        }
        else -> url
    }
}
