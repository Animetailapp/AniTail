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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toAlbumEntity
import com.anitail.desktop.db.mapper.toArtistEntity
import com.anitail.desktop.db.mapper.toPlaylistEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.shimmer.GridItemPlaceholder
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
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
    val strings = LocalStrings.current
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadStates by downloadService.downloadStates.collectAsState()
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    var browseResult by remember { mutableStateOf<BrowseResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var pendingAlbumPlaylist by remember { mutableStateOf<AlbumItem?>(null) }
    var pendingPlaylistPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    val albumMenuStates = remember { mutableStateMapOf<String, BrowseCollectionMenuState>() }
    val playlistMenuStates = remember { mutableStateMapOf<String, BrowseCollectionMenuState>() }

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
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }

    suspend fun loadAlbumSongs(album: AlbumItem): List<SongItem> {
        val albumPage = withContext(Dispatchers.IO) {
            YouTube.album(album.browseId).getOrNull()
        }
        if (albumPage != null && albumPage.songs.isNotEmpty()) {
            return albumPage.songs
        }
        return withContext(Dispatchers.IO) {
            YouTube.albumSongs(album.playlistId, album).getOrNull().orEmpty()
        }
    }

    suspend fun loadPlaylistSongs(playlist: PlaylistItem): List<SongItem> {
        return withContext(Dispatchers.IO) {
            YouTube.playlist(playlist.id).getOrNull()?.songs.orEmpty()
        }
    }

    fun ensureAlbumMenuState(album: AlbumItem) {
        if (albumMenuStates.containsKey(album.browseId)) return
        scope.launch {
            val songs = loadAlbumSongs(album)
            albumMenuStates[album.browseId] = BrowseCollectionMenuState(songs)
        }
    }

    fun ensurePlaylistMenuState(playlist: PlaylistItem) {
        if (playlistMenuStates.containsKey(playlist.id)) return
        scope.launch {
            val songs = loadPlaylistSongs(playlist)
            playlistMenuStates[playlist.id] = BrowseCollectionMenuState(songs)
        }
    }

    suspend fun resolveAlbumSongs(album: AlbumItem): List<SongItem> {
        val cached = albumMenuStates[album.browseId]?.songs.orEmpty()
        if (cached.isNotEmpty()) return cached
        val songs = loadAlbumSongs(album)
        albumMenuStates[album.browseId] = BrowseCollectionMenuState(songs)
        return songs
    }

    suspend fun resolvePlaylistSongs(playlist: PlaylistItem): List<SongItem> {
        val cached = playlistMenuStates[playlist.id]?.songs.orEmpty()
        if (cached.isNotEmpty()) return cached
        val songs = loadPlaylistSongs(playlist)
        playlistMenuStates[playlist.id] = BrowseCollectionMenuState(songs)
        return songs
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

    suspend fun addAlbumSongsToPlaylist(
        album: AlbumItem,
        playlistId: String,
    ) {
        val songs = resolveAlbumSongs(album)
        if (songs.isEmpty()) return
        songs.forEach { song ->
            val entity = song.toSongEntity(inLibrary = true)
            database.insertSong(entity, song.toSongArtistMaps())
            database.addSongToPlaylist(playlistId, song.id)
        }
    }

    suspend fun addPlaylistSongsToPlaylist(
        playlist: PlaylistItem,
        playlistId: String,
    ) {
        val songs = resolvePlaylistSongs(playlist)
        if (songs.isEmpty()) return
        songs.forEach { song ->
            val entity = song.toSongEntity(inLibrary = true)
            database.insertSong(entity, song.toSongArtistMaps())
            database.addSongToPlaylist(playlistId, song.id)
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
                            contentDescription = stringResource("back"),
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
                    var menuActions: List<ContextMenuAction> = emptyList()
                    var menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
                    var onMenuOpened: (() -> Unit)? = null

                    when (item) {
                        is SongItem -> {
                            val songEntity = songsById[item.id]
                            menuActions = buildBrowseSongMenuActions(
                                strings = strings,
                                libraryItem = libraryItem,
                                songItem = item,
                                songsById = songsById,
                                songArtistMaps = songArtistMaps,
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
                            menuHeader = { onDismiss ->
                                SongMenuHeader(
                                    title = item.title,
                                    subtitle = joinByBullet(
                                        item.artists.joinToString { it.name },
                                        item.duration?.let { formatTime(it * 1000L) },
                                    ).orEmpty(),
                                    thumbnailUrl = item.thumbnail,
                                    isLiked = songEntity?.liked == true,
                                    onToggleLike = {
                                        scope.launch {
                                                if (songEntity == null) {
                                                    database.insertSong(
                                                        item.toSongEntity(inLibrary = true).toggleLike(),
                                                        item.toSongArtistMaps(),
                                                    )
                                                } else {
                                                    database.toggleSongLike(item.id)
                                                }
                                        }
                                    },
                                    onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                    onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(libraryItem, item) },
                                    onShare = { copyToClipboard(item.shareLink) },
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                        is AlbumItem -> {
                            val albumEntity by database.album(item.browseId).collectAsState(initial = null)
                            val albumSongs = albumMenuStates[item.browseId]?.songs.orEmpty()
                            val downloadMenu = resolveCollectionDownloadMenuState(
                                songIds = albumSongs.map { it.id },
                                downloadStates = downloadStates,
                                downloadedSongs = downloadedSongs,
                                showWhenEmpty = true,
                            ) ?: CollectionDownloadMenuState(
                                label = strings.get("download"),
                                action = CollectionDownloadAction.DOWNLOAD,
                            )
                            val artistCandidates = item.artists.orEmpty()
                            menuActions = buildBrowseAlbumMenuActions(
                                strings = strings,
                                hasArtists = artistCandidates.isNotEmpty(),
                                downloadLabel = downloadMenu.label,
                                downloadEnabled = true,
                                onStartRadio = {
                                    scope.launch {
                                        val songs = resolveAlbumSongs(item)
                                        val firstSong = songs.firstOrNull() ?: return@launch
                                        val librarySeed = songItemToLibraryItem(firstSong)
                                        val result = YouTube.next(WatchEndpoint(videoId = firstSong.id)).getOrNull()
                                            ?: return@launch
                                        val plan = buildRadioQueuePlan(librarySeed, result)
                                        playerState.playQueue(plan.items, plan.startIndex)
                                    }
                                },
                                onPlayNext = {
                                    scope.launch {
                                        val songs = resolveAlbumSongs(item)
                                        val items = songs.map { songItemToLibraryItem(it) }
                                        items.asReversed().forEach { song -> playerState.addToQueue(song, playNext = true) }
                                    }
                                },
                                onAddToQueue = {
                                    scope.launch {
                                        val songs = resolveAlbumSongs(item)
                                        songs.map { songItemToLibraryItem(it) }.forEach { song ->
                                            playerState.addToQueue(song)
                                        }
                                    }
                                },
                                onAddToPlaylist = { pendingAlbumPlaylist = item },
                                onDownload = {
                                    scope.launch {
                                        val songs = resolveAlbumSongs(item)
                                        when (downloadMenu.action) {
                                            CollectionDownloadAction.DOWNLOAD -> {
                                                songs.forEach { song ->
                                                    downloadService.downloadSong(
                                                        songId = song.id,
                                                        title = song.title,
                                                        artist = song.artists.joinToString { it.name },
                                                        album = item.title,
                                                        thumbnailUrl = song.thumbnail,
                                                        duration = song.duration ?: 0,
                                                    )
                                                }
                                            }
                                            CollectionDownloadAction.CANCEL -> {
                                                songs.forEach { song -> downloadService.cancelDownload(song.id) }
                                            }
                                            CollectionDownloadAction.REMOVE -> {
                                                songs.forEach { song -> downloadService.deleteDownload(song.id) }
                                            }
                                        }
                                    }
                                },
                                onOpenArtist = openArtist@{
                                    when {
                                        artistCandidates.size == 1 -> {
                                            val artist = artistCandidates.first()
                                            val artistId = artist.id ?: return@openArtist
                                            onOpenArtist(artistId, artist.name)
                                        }
                                        artistCandidates.size > 1 -> pendingArtists = artistCandidates
                                    }
                                },
                                onShare = { copyToClipboard(item.shareLink) },
                            )
                            menuHeader = {
                                CollectionMenuHeader(
                                    title = item.title,
                                    subtitle = item.artists?.joinToString { it.name }.orEmpty(),
                                    thumbnailUrl = item.thumbnail,
                                    showLike = true,
                                    isLiked = albumEntity?.bookmarkedAt != null,
                                    onToggleLike = {
                                        scope.launch {
                                            if (albumEntity == null) {
                                                database.insertAlbum(item.toAlbumEntity())
                                            }
                                            database.toggleAlbumBookmark(item.browseId)
                                        }
                                    },
                                )
                            }
                            onMenuOpened = { ensureAlbumMenuState(item) }
                        }
                        is PlaylistItem -> {
                            val playlistEntity by database.playlist(item.id).collectAsState(initial = null)
                            val playlistSongs = playlistMenuStates[item.id]?.songs.orEmpty()
                            val downloadMenu = resolveCollectionDownloadMenuState(
                                songIds = playlistSongs.map { it.id },
                                downloadStates = downloadStates,
                                downloadedSongs = downloadedSongs,
                                showWhenEmpty = false,
                            )
                            val canLike = item.id != "LM" && !item.isEditable
                            menuActions = buildBrowsePlaylistMenuActions(
                                strings = strings,
                                canPlay = item.playEndpoint != null,
                                canShuffle = item.shuffleEndpoint != null,
                                canRadio = item.radioEndpoint != null,
                                showDownload = downloadMenu != null,
                                downloadLabel = downloadMenu?.label ?: strings.get("download"),
                                downloadEnabled = true,
                                onPlay = {
                                    scope.launch {
                                        val songs = resolvePlaylistSongs(item)
                                        if (songs.isEmpty()) return@launch
                                        val items = songs.map { songItemToLibraryItem(it) }
                                        playerState.playQueue(items, startIndex = 0)
                                    }
                                },
                                onShuffle = {
                                    scope.launch {
                                        val songs = resolvePlaylistSongs(item)
                                        if (songs.isEmpty()) return@launch
                                        val items = songs.map { songItemToLibraryItem(it) }.shuffled()
                                        playerState.playQueue(items, startIndex = 0)
                                    }
                                },
                                onStartRadio = {
                                    scope.launch {
                                        val endpoint = item.radioEndpoint ?: return@launch
                                        val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                        val seedSong = result.items.firstOrNull() ?: return@launch
                                        val plan = buildRadioQueuePlan(songItemToLibraryItem(seedSong), result)
                                        playerState.playQueue(plan.items, plan.startIndex)
                                    }
                                },
                                onPlayNext = {
                                    scope.launch {
                                        val songs = resolvePlaylistSongs(item)
                                        val items = songs.map { songItemToLibraryItem(it) }
                                        items.asReversed().forEach { song -> playerState.addToQueue(song, playNext = true) }
                                    }
                                },
                                onAddToQueue = {
                                    scope.launch {
                                        val songs = resolvePlaylistSongs(item)
                                        songs.map { songItemToLibraryItem(it) }.forEach { song ->
                                            playerState.addToQueue(song)
                                        }
                                    }
                                },
                                onAddToPlaylist = { pendingPlaylistPlaylist = item },
                                onDownload = {
                                    downloadMenu?.let { menu ->
                                        scope.launch {
                                            val songs = resolvePlaylistSongs(item)
                                            when (menu.action) {
                                                CollectionDownloadAction.DOWNLOAD -> {
                                                    songs.forEach { song ->
                                                        downloadService.downloadSong(
                                                            songId = song.id,
                                                            title = song.title,
                                                            artist = song.artists.joinToString { it.name },
                                                            album = null,
                                                            thumbnailUrl = song.thumbnail,
                                                            duration = song.duration ?: 0,
                                                        )
                                                    }
                                                }
                                                CollectionDownloadAction.CANCEL -> {
                                                    songs.forEach { song -> downloadService.cancelDownload(song.id) }
                                                }
                                                CollectionDownloadAction.REMOVE -> {
                                                    songs.forEach { song -> downloadService.deleteDownload(song.id) }
                                                }
                                            }
                                        }
                                    }
                                },
                                onShare = { copyToClipboard(item.shareLink) },
                            )
                            menuHeader = {
                                CollectionMenuHeader(
                                    title = item.title,
                                    subtitle = item.author?.name.orEmpty(),
                                    thumbnailUrl = item.thumbnail,
                                    showLike = canLike,
                                    isLiked = playlistEntity?.bookmarkedAt != null,
                                    onToggleLike = {
                                        if (!canLike) return@CollectionMenuHeader
                                        scope.launch {
                                            if (playlistEntity == null) {
                                                val entity = item.toPlaylistEntity().toggleLike()
                                                database.insertPlaylist(entity)
                                                val songs = resolvePlaylistSongs(item)
                                                songs.forEach { song ->
                                                    database.insertSong(
                                                        song.toSongEntity(inLibrary = true),
                                                        song.toSongArtistMaps(),
                                                    )
                                                    database.addSongToPlaylist(entity.id, song.id)
                                                }
                                            } else {
                                                database.togglePlaylistBookmark(item.id)
                                            }
                                        }
                                    },
                                )
                            }
                            onMenuOpened = { ensurePlaylistMenuState(item) }
                        }
                        is ArtistItem -> {
                            val artistEntity by database.artist(item.id).collectAsState(initial = null)
                            menuActions = buildBrowseArtistMenuActions(
                                strings = strings,
                                isSubscribed = artistEntity?.bookmarkedAt != null,
                                onToggleSubscribe = {
                                    scope.launch {
                                        if (artistEntity == null) {
                                            database.insertArtist(item.toArtistEntity().toggleLike())
                                        } else {
                                            database.toggleArtistBookmark(item.id)
                                        }
                                    }
                                },
                            )
                            menuHeader = { onDismiss ->
                                ArtistMenuHeader(
                                    title = item.title,
                                    thumbnailUrl = item.thumbnail,
                                    showRadio = item.radioEndpoint != null,
                                    showShuffle = item.shuffleEndpoint != null,
                                    onStartRadio = {
                                        val endpoint = item.radioEndpoint ?: return@ArtistMenuHeader
                                        scope.launch {
                                            val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                            val seedSong = result.items.firstOrNull() ?: return@launch
                                            val plan = buildRadioQueuePlan(songItemToLibraryItem(seedSong), result)
                                            playerState.playQueue(plan.items, plan.startIndex)
                                        }
                                    },
                                    onShuffle = {
                                        val endpoint = item.shuffleEndpoint ?: return@ArtistMenuHeader
                                        scope.launch {
                                            val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                            val items = result.items.map { songItemToLibraryItem(it) }
                                            if (items.isNotEmpty()) {
                                                playerState.playQueue(items, startIndex = 0)
                                            }
                                        }
                                    },
                                    onShare = { copyToClipboard(item.shareLink) },
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                    }

                    BrowseGridItem(
                        item = item,
                        isActive = isActive,
                        isPlaying = isActive && playerState.isPlaying,
                        libraryItem = libraryItem,
                        menuActions = menuActions,
                        songEntity = if (item is SongItem) songsById[item.id] else null,
                        downloadState = if (item is SongItem) downloadStates[item.id] else null,
                        isDownloaded = if (item is SongItem) downloadedSongs.any { it.songId == item.id } else false,
                        menuHeader = menuHeader,
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
                        onMenuOpened = onMenuOpened,
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

    pendingAlbumPlaylist?.let { album ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                scope.launch {
                    database.insertPlaylist(playlist)
                    addAlbumSongsToPlaylist(album, playlist.id)
                }
                pendingAlbumPlaylist = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    addAlbumSongsToPlaylist(album, playlist.id)
                }
                pendingAlbumPlaylist = null
            },
            onDismiss = { pendingAlbumPlaylist = null },
        )
    }

    pendingPlaylistPlaylist?.let { playlistItem ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                scope.launch {
                    database.insertPlaylist(playlist)
                    addPlaylistSongsToPlaylist(playlistItem, playlist.id)
                }
                pendingPlaylistPlaylist = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    addPlaylistSongsToPlaylist(playlistItem, playlist.id)
                }
                pendingPlaylistPlaylist = null
            },
            onDismiss = { pendingPlaylistPlaylist = null },
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

private fun joinByBullet(left: String?, right: String?): String? {
    return when {
        left.isNullOrBlank() && right.isNullOrBlank() -> null
        left.isNullOrBlank() -> right
        right.isNullOrBlank() -> left
        else -> "$left • $right"
    }
}

private data class BrowseCollectionMenuState(
    val songs: List<SongItem>,
)
