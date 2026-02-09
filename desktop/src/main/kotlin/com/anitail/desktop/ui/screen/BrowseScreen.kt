package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.shimmer.GridItemPlaceholder
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.BrowseResult
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    browseId: String?,
    browseParams: String?,
    hideExplicit: Boolean,
    playerState: PlayerState,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playlists: List<PlaylistEntity>,
    songsById: Map<String, SongEntity>,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadStates by downloadService.downloadStates.collectAsState()
    var browseResult by remember { mutableStateOf<BrowseResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }

    val filteredItems = remember(browseResult, hideExplicit) {
        browseResult?.let { flattenBrowseItems(it, hideExplicit) }.orEmpty()
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

    fun playAlbum(album: AlbumItem) {
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                YouTube.album(album.browseId).getOrNull()
            } ?: return@launch
            val queue = page.songs.map { song -> song.toSongEntity().toLibraryItem() }
            if (queue.isNotEmpty()) {
                playerState.playQueue(queue, startIndex = 0)
            }
        }
    }

    LaunchedEffect(browseId, browseParams, hideExplicit) {
        if (browseId.isNullOrBlank()) {
            browseResult = null
            return@LaunchedEffect
        }
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            YouTube.browse(browseId, browseParams).getOrNull()
        }
        browseResult = result
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = browseResult?.title.orEmpty()) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = IconAssets.arrowBack(),
                        contentDescription = "Volver",
                    )
                }
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = BrowseGridMinWidth),
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isLoading || browseResult == null) {
                items(8) {
                    ShimmerHost {
                        GridItemPlaceholder()
                    }
                }
            } else if (filteredItems.isEmpty()) {
                items(8) {
                    ShimmerHost {
                        GridItemPlaceholder()
                    }
                }
            } else {
                items(
                    items = filteredItems,
                    key = { it.id },
                ) { item ->
                    val isActive = playerState.currentItem?.id == item.id
                    val libraryItem = item.toBrowseLibraryItem()
                    val menuActions = when (item) {
                        is SongItem -> buildBrowseSongMenuActions(
                            libraryItem = libraryItem,
                            songItem = item,
                            songsById = songsById,
                            downloadStates = downloadStates,
                            downloadedSongs = downloadedSongs,
                            database = database,
                            downloadService = downloadService,
                            playerState = playerState,
                            coroutineScope = scope,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                            onRequestPlaylist = { pendingPlaylistItem = it },
                            onRequestArtists = { pendingArtists = it },
                            onShowDetails = { detailsItem = it },
                            copyToClipboard = ::copyToClipboard,
                        )
                        else -> buildBrowseCollectionMenuActions(
                            item = item,
                            onOpen = {
                                when (item) {
                                    is AlbumItem -> onOpenAlbum(item.browseId, item.title)
                                    is PlaylistItem -> onOpenPlaylist(item.id, item.title)
                                    is ArtistItem -> onOpenArtist(item.id, item.title)
                                    else -> Unit
                                }
                            },
                            copyToClipboard = ::copyToClipboard,
                        )
                    }

                    BrowseGridItem(
                        item = item,
                        isActive = isActive,
                        isPlaying = isActive && playerState.isPlaying,
                        libraryItem = libraryItem,
                        menuActions = menuActions,
                        songEntity = if (item is SongItem) songsById[item.id] else null,
                        downloadState = downloadStates[item.id],
                        isDownloaded = downloadedSongs.any { it.songId == item.id },
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (isActive) {
                                        playerState.togglePlayPause()
                                    } else {
                                        playerState.play(libraryItem)
                                    }
                                }
                                is AlbumItem -> onOpenAlbum(item.browseId, item.title)
                                is PlaylistItem -> onOpenPlaylist(item.id, item.title)
                                is ArtistItem -> onOpenArtist(item.id, item.title)
                            }
                        },
                        onAlbumPlay = {
                            if (item is AlbumItem) {
                                playAlbum(item)
                            }
                        },
                    )
                }
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
            onOpenArtist(artistId, artist.name)
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
