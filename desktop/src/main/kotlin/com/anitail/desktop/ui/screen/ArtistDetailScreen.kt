package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.ShimmerBox
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ArtistPage
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * Pantalla de detalle de artista para Desktop - Idéntica a Android.
 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    playerState: PlayerState,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playlists: List<PlaylistEntity>,
    songsById: Map<String, SongEntity>,
    onBack: () -> Unit,
    onBrowse: (String, String?) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onSongClick: (LibraryItem) -> Unit,
) {
    var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val artistEntity by database.artist(artistId).collectAsState(initial = null)
    val isSubscribed = artistEntity?.bookmarkedAt != null
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadStates by downloadService.downloadStates.collectAsState()
    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }

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

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    suspend fun ensureSongInDatabase(target: BrowseSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        database.insertSong(entity)
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
                                        onClick = {
                                            scope.launch {
                                                if (artistEntity == null) {
                                                    database.insertArtist(
                                                        ArtistEntity(
                                                            id = artistId,
                                                            name = artistPage?.artist?.title ?: artistName,
                                                            thumbnailUrl = artistPage?.artist?.thumbnail,
                                                            channelId = null,
                                                            bookmarkedAt = LocalDateTime.now(),
                                                        ),
                                                    )
                                                } else {
                                                    database.toggleArtistBookmark(artistId)
                                                }
                                            }
                                        },
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
                                                    val endpoint = page.artist.radioEndpoint ?: return@OutlinedButton
                                                    scope.launch {
                                                        val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                                        val seedSong = result.items.firstOrNull() ?: return@launch
                                                        val plan = buildRadioQueuePlan(songItemToLibraryItem(seedSong), result)
                                                        playerState.playQueue(plan.items, plan.startIndex)
                                                    }
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
                                                    val endpoint = page.artist.shuffleEndpoint ?: return@IconButton
                                                    scope.launch {
                                                        val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                                        val items = result.items.map { songItemToLibraryItem(it) }
                                                        if (items.isNotEmpty()) {
                                                            playerState.shuffleEnabled = true
                                                            playerState.playQueue(items, startIndex = 0)
                                                        }
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
                                    onClick = section.moreEndpoint?.let { endpoint ->
                                        { onBrowse(endpoint.browseId, endpoint.params) }
                                    }
                                )
                            }

                            // Si son canciones con álbum, mostrar como lista
                            val firstItem = section.items.firstOrNull()
                            if (firstItem is SongItem && firstItem.album != null) {
                                items(
                                    items = section.items.filterIsInstance<SongItem>(),
                                    key = { "song_${it.id}" }
                                ) { song ->
                                    val libraryItem = songItemToLibraryItem(song)
                                    val songEntity = songsById[song.id]
                                    val menuActions = buildBrowseSongMenuActions(
                                        libraryItem = libraryItem,
                                        songItem = song,
                                        songsById = songsById,
                                        downloadStates = downloadStates,
                                        downloadedSongs = downloadedSongs,
                                        database = database,
                                        downloadService = downloadService,
                                        playerState = playerState,
                                        coroutineScope = scope,
                                        onOpenArtist = { id, name -> onArtistClick(id, name ?: "") },
                                        onOpenAlbum = { id, name -> onAlbumClick(id, name ?: "") },
                                        onRequestPlaylist = { pendingPlaylistItem = it },
                                        onRequestArtists = { pendingArtists = it },
                                        onShowDetails = { detailsItem = it },
                                        copyToClipboard = ::copyToClipboard,
                                    )
                                    YouTubeListItem(
                                        item = song,
                                        libraryItem = libraryItem,
                                        isActive = playerState.currentItem?.id == song.id,
                                        isPlaying = playerState.isPlaying,
                                        menuActions = menuActions,
                                        menuHeader = { onDismiss ->
                                            SongMenuHeader(
                                                title = song.title,
                                                subtitle = joinByBullet(
                                                    song.artists.joinToString { it.name },
                                                    song.duration?.let { formatTime(it * 1000L) },
                                                ).orEmpty(),
                                                thumbnailUrl = song.thumbnail,
                                                isLiked = songEntity?.liked == true,
                                                onToggleLike = {
                                                    scope.launch {
                                                        if (songEntity == null) {
                                                            database.insertSong(song.toSongEntity(inLibrary = true).toggleLike())
                                                        } else {
                                                            database.toggleSongLike(song.id)
                                                        }
                                                    }
                                                },
                                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                                onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(libraryItem, song) },
                                                onShare = { copyToClipboard(song.shareLink) },
                                                onDismiss = onDismiss,
                                            )
                                        },
                                        onClick = { onSongClick(libraryItem) },
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

    pendingPlaylistItem?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                scope.launch {
                    database.insertPlaylist(playlist)
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onDismiss = { pendingPlaylistItem = null },
        )
    }

    ArtistPickerDialog(
        visible = pendingArtists.isNotEmpty(),
        artists = pendingArtists,
        onSelect = selectArtist@{ artist ->
            val artistId = artist.id ?: return@selectArtist
            onArtistClick(artistId, artist.name)
            pendingArtists = emptyList()
        },
        onDismiss = { pendingArtists = emptyList() },
    )

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
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

private fun joinByBullet(left: String?, right: String?): String? {
    return when {
        left.isNullOrBlank() && right.isNullOrBlank() -> null
        left.isNullOrBlank() -> right
        right.isNullOrBlank() -> left
        else -> "$left • $right"
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeListItem(
    item: SongItem,
    libraryItem: LibraryItem,
    isActive: Boolean,
    isPlaying: Boolean,
    menuActions: List<ContextMenuAction>,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val menuExpanded = remember(item.id) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (menuActions.isNotEmpty()) {
                        menuExpanded.value = true
                    }
                },
            )
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

        Box {
            IconButton(
                onClick = {
                    if (menuActions.isNotEmpty()) {
                        menuExpanded.value = true
                    }
                },
            ) {
                Icon(
                    IconAssets.moreVert(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (menuActions.isNotEmpty()) {
                ItemContextMenu(
                    expanded = menuExpanded.value,
                    onDismiss = { menuExpanded.value = false },
                    item = libraryItem,
                    actions = menuActions,
                    headerContent = menuHeader?.let { header ->
                        { header { menuExpanded.value = false } }
                    },
                )
            }
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

