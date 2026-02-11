package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.screen.library.SelectionDownloadState
import com.anitail.desktop.ui.screen.library.resolveSelectionDownloadState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.HistoryPage
import com.anitail.innertube.utils.parseCookieString
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ListItemSpacing = 4.dp
private val SectionSpacing = 12.dp

private class WrappedHistoryItem(val entry: HistoryEvent) {
    var isSelected by mutableStateOf(false)
}

private sealed class PlaylistRequest {
    data class Single(val target: BrowseSongTarget) : PlaylistRequest()
    data class Selection(val songIds: List<String>) : PlaylistRequest()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val songs by database.songs.collectAsState(initial = emptyList())
    val events by database.events.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.songId }.toSet() }

    var historySource by remember { mutableStateOf(HistorySource.LOCAL) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    var playlistRequest by remember { mutableStateOf<PlaylistRequest?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var historyPage by remember { mutableStateOf<HistoryPage?>(null) }
    var isRemoteLoading by remember { mutableStateOf(false) }

    val isLoggedIn = remember(YouTube.cookie) {
        val cookie = YouTube.cookie.orEmpty()
        cookie.isNotBlank() && "SAPISID" in parseCookieString(cookie)
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            historySource = HistorySource.LOCAL
        }
    }

    LaunchedEffect(historySource, isLoggedIn) {
        if (historySource != HistorySource.REMOTE || !isLoggedIn) return@LaunchedEffect
        isRemoteLoading = true
        historyPage = withContext(Dispatchers.IO) { YouTube.musicHistory().getOrNull() }
        isRemoteLoading = false
    }

    val groupedEvents = remember(events, songsById, query.text) {
        groupHistoryEvents(events, songsById, query = query.text)
    }

    val wrappedItemsMap = remember(groupedEvents) {
        groupedEvents.mapValues { (_, items) ->
            items.map { WrappedHistoryItem(it) }.toMutableStateList()
        }
    }

    val allWrappedItems = remember(wrappedItemsMap) {
        wrappedItemsMap.values.flatten()
    }

    val selectedItems = remember(allWrappedItems, selectionMode) {
        if (!selectionMode) emptyList() else allWrappedItems.filter { it.isSelected }
    }

    val selectionDownloadState = resolveSelectionDownloadState(
        songIds = selectedItems.map { it.entry.song.id },
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
    )

    val allLiked = selectedItems.isNotEmpty() && selectedItems.all { it.entry.song.liked }

    val filteredRemoteSections = remember(historyPage, query.text) {
        if (query.text.isBlank()) {
            historyPage?.sections
        } else {
            historyPage?.sections
                ?.map { section ->
                    section.copy(
                        songs = section.songs.filter { song ->
                            song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                        },
                    )
                }
                ?.filter { it.songs.isNotEmpty() }
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectionMenuExpanded = false
        wrappedItemsMap.values.flatten().forEach { it.isSelected = false }
    }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
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

    fun dateAgoToString(dateAgo: DateAgo): String {
        return when (dateAgo) {
            DateAgo.Today -> strings.get("today")
            DateAgo.Yesterday -> strings.get("yesterday")
            DateAgo.ThisWeek -> strings.get("this_week")
            DateAgo.LastWeek -> strings.get("last_week")
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
        }
    }

    if (playlistRequest != null) {
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                coroutineScope.launch {
                    database.insertPlaylist(
                        PlaylistEntity(
                            name = name,
                            createdAt = java.time.LocalDateTime.now(),
                        ),
                    )
                }
            },
            onSelectPlaylist = { playlist ->
                coroutineScope.launch {
                    when (val request = playlistRequest) {
                        is PlaylistRequest.Single -> {
                            ensureSongInDatabase(request.target)
                            database.addSongToPlaylist(playlist.id, request.target.item.id)
                        }
                        is PlaylistRequest.Selection -> {
                            request.songIds.forEach { songId ->
                                database.addSongToPlaylist(playlist.id, songId)
                            }
                        }
                        null -> Unit
                    }
                }
                playlistRequest = null
            },
            onDismiss = { playlistRequest = null },
        )
    }

    pendingArtists?.let { artists ->
        ArtistPickerDialog(
            visible = true,
            artists = artists,
            onSelect = { artist ->
                pendingArtists = null
                val artistId = artist.id ?: return@ArtistPickerDialog
                onOpenArtist(artistId, artist.name)
            },
            onDismiss = { pendingArtists = null },
        )
    }

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                when {
                    selectionMode -> {
                        val count = selectedItems.size
                        Text(pluralStringResource("n_song", count, count))
                    }
                    isSearching -> {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(text = stringResource("search")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    else -> Text(stringResource("history"))
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        when {
                            isSearching -> {
                                isSearching = false
                                query = TextFieldValue()
                            }
                            selectionMode -> clearSelection()
                            else -> onBack()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (selectionMode) IconAssets.close() else IconAssets.arrowBack(),
                        contentDescription = stringResource("back"),
                    )
                }
            },
            actions = {
                if (selectionMode) {
                    val allSelected = selectedItems.size == allWrappedItems.size && allWrappedItems.isNotEmpty()
                    IconButton(onClick = {
                        if (allSelected) {
                            allWrappedItems.forEach { it.isSelected = false }
                        } else {
                            allWrappedItems.forEach { it.isSelected = true }
                        }
                    }) {
                        Icon(
                            imageVector = if (allSelected) IconAssets.deselect() else IconAssets.selectAll(),
                            contentDescription = if (allSelected) stringResource("deselect_all") else stringResource("select_all"),
                        )
                    }
                    IconButton(onClick = { selectionMenuExpanded = true }) {
                        Icon(imageVector = IconAssets.moreVert(), contentDescription = stringResource("more_options"))
                    }
                    DropdownMenu(
                        expanded = selectionMenuExpanded,
                        onDismissRequest = { selectionMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource("play")) },
                            leadingIcon = { Icon(IconAssets.play(), contentDescription = null) },
                            onClick = {
                                val items = selectedItems.map { it.entry.song.toLibraryItem() }
                                if (items.isNotEmpty()) {
                                    playerState.playQueue(items, startIndex = 0)
                                }
                                clearSelection()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("shuffle")) },
                            leadingIcon = { Icon(IconAssets.shuffle(), contentDescription = null) },
                            onClick = {
                                val items = selectedItems.map { it.entry.song.toLibraryItem() }.shuffled()
                                if (items.isNotEmpty()) {
                                    playerState.playQueue(items, startIndex = 0)
                                }
                                clearSelection()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("add_to_queue")) },
                            leadingIcon = { Icon(IconAssets.queueMusic(), contentDescription = null) },
                            onClick = {
                                selectedItems.map { it.entry.song.toLibraryItem() }
                                    .forEach { item -> playerState.addToQueue(item) }
                                clearSelection()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("add_to_playlist")) },
                            leadingIcon = { Icon(IconAssets.playlistAdd(), contentDescription = null) },
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    playlistRequest = PlaylistRequest.Selection(selectedItems.map { it.entry.song.id })
                                }
                                selectionMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (allLiked) "dislike_all" else "like_all")) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (allLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                coroutineScope.launch {
                                    val targets = if (allLiked) {
                                        selectedItems.filter { it.entry.song.liked }
                                    } else {
                                        selectedItems.filter { !it.entry.song.liked }
                                    }
                                    targets.forEach { item -> database.toggleSongLike(item.entry.song.id) }
                                }
                                selectionMenuExpanded = false
                            },
                        )
                        when (selectionDownloadState) {
                            SelectionDownloadState.DOWNLOADED -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("remove_download")) },
                                    leadingIcon = { Icon(IconAssets.offline(), contentDescription = null) },
                                    onClick = {
                                        selectedItems.forEach { item ->
                                            downloadService.deleteDownload(item.entry.song.id)
                                        }
                                        selectionMenuExpanded = false
                                    },
                                )
                            }
                            SelectionDownloadState.DOWNLOADING -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("downloading_to_device")) },
                                    leadingIcon = { Icon(IconAssets.download(), contentDescription = null) },
                                    onClick = {
                                        selectedItems.forEach { item ->
                                            downloadService.cancelDownload(item.entry.song.id)
                                        }
                                        selectionMenuExpanded = false
                                    },
                                )
                            }
                            SelectionDownloadState.NOT_DOWNLOADED -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("download")) },
                                    leadingIcon = { Icon(IconAssets.download(), contentDescription = null) },
                                    onClick = {
                                        selectedItems.forEach { item ->
                                            downloadService.downloadSong(
                                                songId = item.entry.song.id,
                                                title = item.entry.song.title,
                                                artist = item.entry.song.artistName.orEmpty(),
                                                album = item.entry.song.albumName,
                                                thumbnailUrl = item.entry.song.thumbnailUrl,
                                                duration = item.entry.song.duration,
                                            )
                                        }
                                        selectionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else if (!isSearching) {
                    IconButton(onClick = { isSearching = true }) {
                        Icon(imageVector = IconAssets.search(), contentDescription = stringResource("search"))
                    }
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Spacer(modifier = Modifier.height(SectionSpacing))
                }

                item {
                    ChipsRow(
                        chips = if (isLoggedIn) {
                            listOf(
                                HistorySource.LOCAL to stringResource("local_history"),
                                HistorySource.REMOTE to stringResource("remote_history"),
                            )
                        } else {
                            listOf(HistorySource.LOCAL to stringResource("local_history"))
                        },
                        currentValue = historySource,
                        onValueUpdate = {
                            historySource = it
                            clearSelection()
                            if (it == HistorySource.REMOTE) {
                                isSearching = false
                                query = TextFieldValue()
                            }
                        },
                    )
                }

                if (historySource == HistorySource.REMOTE && isLoggedIn) {
                    if (isRemoteLoading) {
                        item {
                            Text(
                                text = stringResource("loading"),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else if (filteredRemoteSections.isNullOrEmpty()) {
                        item {
                            Text(
                                text = stringResource("history_empty"),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        filteredRemoteSections.forEach { section ->
                            stickyHeader {
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background),
                                )
                            }
                            items(section.songs, key = { "${section.title}_${it.id}" }) { song ->
                                val menuExpanded = remember(song.id) { mutableStateOf(false) }
                                val itemEntity = songsById[song.id] ?: song.toSongEntity(inLibrary = false)
                                val libraryItem = itemEntity.toLibraryItem()
                                val menuActions = remember(song.id, songArtistMaps, downloadStates, downloadedSongs) {
                                    buildBrowseSongMenuActions(
                                        strings = strings,
                                        libraryItem = libraryItem,
                                        songItem = song,
                                        songsById = songsById,
                                        songArtistMaps = songArtistMaps,
                                        downloadStates = downloadStates,
                                        downloadedSongs = downloadedSongs,
                                        database = database,
                                        downloadService = downloadService,
                                        playerState = playerState,
                                        coroutineScope = coroutineScope,
                                        onOpenArtist = onOpenArtist,
                                        onOpenAlbum = onOpenAlbum,
                                        onRequestPlaylist = { playlistRequest = PlaylistRequest.Single(it) },
                                        onRequestArtists = { pendingArtists = it },
                                        onShowDetails = { detailsItem = it },
                                        copyToClipboard = ::copyToClipboard,
                                    )
                                }
                                Box {
                                    LibrarySongListItem(
                                        song = itemEntity,
                                        showInLibraryIcon = true,
                                        downloaded = song.id in downloadedIds,
                                        isActive = song.id == playerState.currentItem?.id,
                                        isPlaying = playerState.isPlaying,
                                        isSelected = false,
                                        onClick = {
                                            if (song.id == playerState.currentItem?.id) {
                                                playerState.togglePlayPause()
                                            } else {
                                                coroutineScope.launch {
                                                    val result = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()
                                                        ?: return@launch
                                                    val plan = buildRadioQueuePlan(libraryItem, result)
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                            }
                                        },
                                        onMenuClick = { menuExpanded.value = true },
                                    )
                                    ItemContextMenu(
                                        expanded = menuExpanded.value,
                                        onDismiss = { menuExpanded.value = false },
                                        item = libraryItem,
                                        actions = menuActions,
                                        headerContent = {
                                            SongMenuHeader(
                                                title = song.title,
                                                subtitle = song.artists.joinToString { it.name },
                                                thumbnailUrl = song.thumbnail,
                                                isLiked = songsById[song.id]?.liked == true,
                                                onToggleLike = { coroutineScope.launch { database.toggleSongLike(song.id) } },
                                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                                onAddToPlaylist = { playlistRequest = PlaylistRequest.Single(BrowseSongTarget(libraryItem, song)) },
                                                onShare = { copyToClipboard(libraryItem.playbackUrl) },
                                                onDismiss = { menuExpanded.value = false },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (allWrappedItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource("history_empty"),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        groupedEvents.forEach { (dateAgo, _) ->
                            stickyHeader {
                                NavigationTitle(
                                    title = dateAgoToString(dateAgo),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface),
                                )
                            }

                            val currentItems = wrappedItemsMap[dateAgo].orEmpty()
                            itemsIndexed(
                                items = currentItems,
                                key = { index, item -> "${dateAgo}_${item.entry.event.id}_$index" },
                            ) { index, wrappedItem ->
                                val song = wrappedItem.entry.song
                                val libraryItem = song.toLibraryItem()
                                val menuExpanded = remember(song.id) { mutableStateOf(false) }
                                val menuActions = remember(song.id, songArtistMaps, downloadStates, downloadedSongs, selectionMode) {
                                    val base = buildBrowseSongMenuActions(
                                        strings = strings,
                                        libraryItem = libraryItem,
                                        songItem = null,
                                        songsById = songsById,
                                        songArtistMaps = songArtistMaps,
                                        downloadStates = downloadStates,
                                        downloadedSongs = downloadedSongs,
                                        database = database,
                                        downloadService = downloadService,
                                        playerState = playerState,
                                        coroutineScope = coroutineScope,
                                        onOpenArtist = onOpenArtist,
                                        onOpenAlbum = onOpenAlbum,
                                        onRequestPlaylist = { playlistRequest = PlaylistRequest.Single(it) },
                                        onRequestArtists = { pendingArtists = it },
                                        onShowDetails = { detailsItem = it },
                                        copyToClipboard = ::copyToClipboard,
                                    )
                                    val removeHistoryAction = ContextMenuAction(
                                        label = strings.get("remove_from_history"),
                                        icon = IconAssets.delete(),
                                        onClick = {
                                            coroutineScope.launch {
                                                database.deleteEvent(wrappedItem.entry.event.id)
                                            }
                                        },
                                    )
                                    val toggleLabels = listOf(
                                        strings.get("add_to_library"),
                                        strings.get("remove_from_library"),
                                    )
                                    val insertIndex = base.indexOfFirst { it.label in toggleLabels }
                                    if (insertIndex >= 0) {
                                        base.toMutableList().apply { add(insertIndex + 1, removeHistoryAction) }
                                    } else {
                                        base + removeHistoryAction
                                    }
                                }
                                Box {
                                    LibrarySongListItem(
                                        song = song,
                                        showInLibraryIcon = true,
                                        downloaded = song.id in downloadedIds,
                                        isActive = song.id == playerState.currentItem?.id,
                                        isPlaying = playerState.isPlaying,
                                        isSelected = wrappedItem.isSelected && selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                wrappedItem.isSelected = !wrappedItem.isSelected
                                            } else {
                                                if (song.id == playerState.currentItem?.id) {
                                                    playerState.togglePlayPause()
                                                } else {
                                                    val items = currentItems.map { it.entry.song.toLibraryItem() }
                                                    playerState.playQueue(items, startIndex = index)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                allWrappedItems.forEach { it.isSelected = false }
                                                wrappedItem.isSelected = true
                                            }
                                        },
                                        onMenuClick = { if (!selectionMode) menuExpanded.value = true },
                                    )
                                    ItemContextMenu(
                                        expanded = menuExpanded.value,
                                        onDismiss = { menuExpanded.value = false },
                                        item = libraryItem,
                                        actions = menuActions,
                                        headerContent = {
                                            SongMenuHeader(
                                                title = song.title,
                                                subtitle = song.artistName.orEmpty(),
                                                thumbnailUrl = song.thumbnailUrl,
                                                isLiked = song.liked,
                                                onToggleLike = { coroutineScope.launch { database.toggleSongLike(song.id) } },
                                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                                onAddToPlaylist = { playlistRequest = PlaylistRequest.Single(BrowseSongTarget(libraryItem, null)) },
                                                onShare = { copyToClipboard(libraryItem.playbackUrl) },
                                                onDismiss = { menuExpanded.value = false },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val fabVisible = if (historySource == HistorySource.REMOTE && isLoggedIn) {
                filteredRemoteSections?.any { it.songs.isNotEmpty() } == true
            } else {
                allWrappedItems.isNotEmpty()
            }
            HideOnScrollFAB(
                visible = fabVisible,
                lazyListState = listState,
                onClick = {
                    if (historySource == HistorySource.REMOTE && filteredRemoteSections != null) {
                        val songs = filteredRemoteSections.flatMap { it.songs }
                        if (songs.isNotEmpty()) {
                            val items = songs.map { it.toSongEntity().toLibraryItem() }.shuffled()
                            playerState.playQueue(items, startIndex = 0)
                        }
                    } else {
                        val items = allWrappedItems.map { it.entry.song.toLibraryItem() }.shuffled()
                        if (items.isNotEmpty()) {
                            playerState.playQueue(items, startIndex = 0)
                        }
                    }
                },
                modifier = Modifier
                    .padding(24.dp)
                    .align(androidx.compose.ui.Alignment.BottomEnd),
            )
        }
    }
}
