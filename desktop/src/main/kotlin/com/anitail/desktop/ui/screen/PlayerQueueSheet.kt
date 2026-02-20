package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerQueueCollapsedHeight
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.BottomSheet
import com.anitail.desktop.ui.component.BottomSheetState
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.awt.Desktop
import java.net.URI

@Composable
fun PlayerQueueSheet(
    state: BottomSheetState,
    playerState: PlayerState,
    currentItem: LibraryItem,
    showLyrics: Boolean,
    onToggleLyrics: (Boolean) -> Unit,
    onStartRadio: () -> Unit,
    artistsWithId: List<Pair<String, String>>,
    albumInfo: Pair<String, String>?,
    onOpenArtist: (String, String) -> Unit,
    onShowArtistPicker: () -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onShowDetailsDialog: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    onShowAudioSettingsDialog: () -> Unit,
    onShowAdvancedDialog: () -> Unit,
    pureBlack: Boolean,
    textColor: Color,
    mutedTextColor: Color,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    modifier: Modifier = Modifier,
) {
    val borderColor = textColor.copy(alpha = 0.35f)
    val listBackground = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val chromeBackground = if (pureBlack) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    }
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val preferences = remember { DesktopPreferences.getInstance() }
    val queueEditLocked by preferences.queueEditLocked.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var playlistTargetItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var downloadTargetItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }

    val queue = playerState.queue

    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val editablePlaylists = remember(playlists) { playlists.filter { it.isEditable } }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val shareUrl = if (currentItem.id.isNotBlank()) {
        "https://music.youtube.com/watch?v=${currentItem.id}"
    } else {
        currentItem.playbackUrl
    }
    val currentDownloadState = computeSelectionDownloadState(
        selectedIds = listOf(currentItem.id),
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
    )

    fun copyLink(url: String) {
        clipboard.setText(AnnotatedString(url))
    }

    fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        }.onFailure { error ->
            println("Anitail WARN: no se pudo abrir el navegador: ${error.message}")
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun selectedItemsInQueue(queue: List<LibraryItem>): List<LibraryItem> {
        return if (selectionMode) {
            queue.filter { selectedIds.contains(it.id) }
        } else {
            emptyList()
        }
    }

    suspend fun ensureSongsInDatabase(items: List<LibraryItem>) {
        val existingIds = songsById.keys
        items.filter { it.id !in existingIds }.forEach { item ->
            database.insertSong(item.toSongEntity())
        }
    }

    fun addItemsToPlaylist(playlistId: String, items: List<LibraryItem>) {
        if (items.isEmpty()) return
        coroutineScope.launch {
            ensureSongsInDatabase(items)
            items.forEach { item ->
                database.addSongToPlaylist(playlistId, item.id)
            }
        }
    }

    fun toggleLikesForItems(items: List<LibraryItem>, like: Boolean) {
        if (items.isEmpty()) return
        val snapshot = songsById
        coroutineScope.launch {
            ensureSongsInDatabase(items)
            items.forEach { item ->
                val liked = snapshot[item.id]?.liked == true
                if (like && !liked) {
                    database.toggleSongLike(item.id)
                } else if (!like && liked) {
                    database.toggleSongLike(item.id)
                }
            }
        }
    }

    fun downloadItems(items: List<LibraryItem>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            val durationSec = ((item.durationMs ?: 0L) / 1000L).toInt()
            downloadService.downloadSong(
                songId = item.id,
                title = item.title,
                artist = item.artist,
                album = null,
                thumbnailUrl = item.artworkUrl,
                duration = durationSec,
            )
        }
    }

    fun removeDownloads(items: List<LibraryItem>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            downloadService.deleteDownload(item.id)
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            )
        },
        collapsedContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlayerQueueCollapsedHeight)
                    .background(chromeBackground)
                    .padding(horizontal = 30.dp),
            ) {
                QueueActionButton(
                    icon = IconAssets.share(),
                    contentDescription = stringResource("share"),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        bottomStart = 50.dp,
                        topEnd = 5.dp,
                        bottomEnd = 5.dp,
                    ),
                    borderColor = borderColor,
                    iconTint = textColor,
                    onClick = { copyLink(currentItem.playbackUrl) },
                )

                QueueActionButton(
                    icon = IconAssets.queueMusic(),
                    contentDescription = stringResource("queue"),
                    shape = RoundedCornerShape(5.dp),
                    borderColor = borderColor,
                    iconTint = textColor,
                    onClick = { state.expandSoft() },
                )

                QueueActionButton(
                    icon = IconAssets.lyrics(),
                    contentDescription = stringResource("lyrics"),
                    shape = RoundedCornerShape(
                        topStart = 5.dp,
                        bottomStart = 5.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp,
                    ),
                    borderColor = borderColor,
                    iconTint = textColor.copy(alpha = if (showLyrics) 1f else 0.5f),
                    onClick = { onToggleLyrics(!showLyrics) },
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(textColor),
                    ) {
                        Icon(
                            imageVector = IconAssets.moreVert(),
                            contentDescription = stringResource("more_options"),
                            tint = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                        ) {
                            PlayerMenuQuickAction(
                                icon = IconAssets.radio(),
                                label = stringResource("start_radio"),
                                onClick = {
                                    onStartRadio()
                                    showMenu = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PlayerMenuQuickAction(
                                icon = IconAssets.playlistAdd(),
                                label = stringResource("add_to_playlist"),
                                onClick = {
                                    playlistTargetItems = listOf(currentItem)
                                    showPlaylistDialog = true
                                    showMenu = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PlayerMenuQuickAction(
                                icon = IconAssets.link(),
                                label = stringResource("copy_link"),
                                onClick = {
                                    copyLink(shareUrl)
                                    showMenu = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Surface(
                            color = mutedTextColor.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp),
                        ) { }

                        if (artistsWithId.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource("view_artist")) },
                                onClick = {
                                    if (artistsWithId.size == 1) {
                                        val artist = artistsWithId.first()
                                        onOpenArtist(artist.first, artist.second)
                                    } else {
                                        onShowArtistPicker()
                                    }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconAssets.artist(),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        albumInfo?.let { album ->
                            DropdownMenuItem(
                                text = { Text(stringResource("view_album")) },
                                onClick = {
                                    onOpenAlbum(album.first, album.second)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconAssets.album(),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        when (currentDownloadState) {
                            SelectionDownloadState.NONE -> Unit
                            SelectionDownloadState.COMPLETED -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("remove_download")) },
                                    onClick = {
                                        removeDownloads(listOf(currentItem))
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = IconAssets.offline(),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                            SelectionDownloadState.DOWNLOADING -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("downloading")) },
                                    onClick = {
                                        removeDownloads(listOf(currentItem))
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    },
                                )
                            }
                            SelectionDownloadState.NOT_DOWNLOADED -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource("download")) },
                                    onClick = {
                                        downloadItems(listOf(currentItem))
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = IconAssets.download(),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource("share")) },
                            onClick = {
                                copyLink(shareUrl)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.share(),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("details")) },
                            onClick = {
                                onShowDetailsDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.info(),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                val timerLabel = if (playerState.isSleepTimerActive) {
                                    strings.get("sleep_timer") + " (" + formatTime(playerState.sleepTimerTimeLeftMs) + ")"
                                } else {
                                    strings.get("sleep_timer")
                                }
                                Text(text = timerLabel)
                            },
                            onClick = {
                                onShowSleepTimerDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.bedtime(),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("equalizer")) },
                            onClick = {
                                onShowAudioSettingsDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.equalizer(),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource("advanced")) },
                            onClick = {
                                onShowAdvancedDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.tune(),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) {
        val currentIndex = playerState.currentQueueIndex
        val allSelected = selectionMode && selectedIds.size == queue.size && queue.isNotEmpty()

        LaunchedEffect(queue, selectionMode) {
            if (!selectionMode) return@LaunchedEffect
            val availableIds = queue.map { it.id }.toSet()
            selectedIds.retainAll(availableIds)
            if (selectedIds.isEmpty()) {
                selectionMode = false
            }
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
            if (!queueEditLocked && !selectionMode && from.index in queue.indices && to.index in queue.indices) {
                playerState.moveQueueItem(from.index, to.index)
            }
        }

        fun removeItemsWithUndo(indices: List<Int>) {
            if (indices.isEmpty()) return
            val uniqueIndices = indices.distinct().sorted()
            val removedItems = uniqueIndices.mapNotNull { index ->
                queue.getOrNull(index)?.let { index to it }
            }
            uniqueIndices.sortedDescending().forEach { index ->
                playerState.removeFromQueue(index)
            }
            clearSelection()

            coroutineScope.launch {
                val message = strings.plural("queue_items_removed", removedItems.size, removedItems.size)
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = strings.get("undo"),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    removedItems.sortedBy { it.first }.forEach { (index, item) ->
                        playerState.insertIntoQueue(index, item)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(listBackground),
        ) {
            val topPadding = PlayerQueueCollapsedHeight + if (selectionMode) 48.dp else 0.dp
            val listPadding = PaddingValues(
                top = topPadding + 8.dp,
                bottom = PlayerQueueCollapsedHeight + 8.dp,
            )

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding, bottom = PlayerQueueCollapsedHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = IconAssets.queueMusic(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = mutedTextColor.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource("queue_empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedTextColor,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = listPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        ReorderableItem(
                            state = reorderableState,
                            key = item.id,
                        ) { _ ->
                            val dragHandleModifier =
                                if (!queueEditLocked && !selectionMode) {
                                    Modifier.draggableHandle()
                                } else {
                                    Modifier
                                }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.StartToEnd ||
                                        dismissValue == SwipeToDismissBoxValue.EndToStart
                                    ) {
                                        removeItemsWithUndo(listOf(index))
                                    }
                                    true
                                },
                            )

                            val content: @Composable () -> Unit = {
                                val isSelected = selectionMode && selectedIds.contains(item.id)
                                val isLiked = songsById[item.id]?.liked == true
                                val itemDownloadState = computeSelectionDownloadState(
                                    selectedIds = listOf(item.id),
                                    downloadStates = downloadStates,
                                    downloadedSongs = downloadedSongs,
                                )
                                QueueItemRow(
                                    item = item,
                                    isActive = index == currentIndex,
                                    isPlaying = playerState.isPlaying && index == currentIndex,
                                    isSelected = isSelected,
                                    showDragHandle = !queueEditLocked && !selectionMode,
                                    showItemMenu = !selectionMode,
                                    isLiked = isLiked,
                                    downloadState = itemDownloadState,
                                    dragHandleModifier = dragHandleModifier,
                                    onClick = {
                                        if (selectionMode) {
                                            if (isSelected) {
                                                selectedIds.remove(item.id)
                                                if (selectedIds.isEmpty()) {
                                                    selectionMode = false
                                                }
                                            } else {
                                                selectedIds.add(item.id)
                                            }
                                        } else {
                                            if (index == currentIndex) {
                                                playerState.togglePlayPause()
                                            } else {
                                                playerState.play(item)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds.clear()
                                            selectedIds.add(item.id)
                                        }
                                    },
                                    onPlayNext = {
                                        if (currentIndex in queue.indices && index != currentIndex) {
                                            playerState.moveQueueItem(index, (currentIndex + 1).coerceAtMost(queue.lastIndex))
                                        }
                                    },
                                    onAddToPlaylist = {
                                        playlistTargetItems = listOf(item)
                                        showPlaylistDialog = true
                                    },
                                    onToggleLike = {
                                        toggleLikesForItems(listOf(item), like = !isLiked)
                                    },
                                    onDownload = {
                                        downloadItems(listOf(item))
                                    },
                                    onRemoveDownload = {
                                        downloadTargetItems = listOf(item)
                                        showRemoveDownloadDialog = true
                                    },
                                    onRemove = { removeItemsWithUndo(listOf(index)) },
                                    onCopyLink = { copyLink(item.playbackUrl) },
                                    onOpenInBrowser = { openInBrowser(item.playbackUrl) },
                                    textColor = textColor,
                                    mutedTextColor = mutedTextColor,
                                    backgroundColor = listBackground,
                                )
                            }

                            val swipeEnabled = !queueEditLocked && !selectionMode

                            if (!swipeEnabled) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }

            val selectedItems = selectedItemsInQueue(queue)
            val allLiked = selectedItems.isNotEmpty() &&
                selectedItems.all { songsById[it.id]?.liked == true }
            val downloadState = computeSelectionDownloadState(
                selectedIds = selectedItems.map { it.id },
                downloadStates = downloadStates,
                downloadedSongs = downloadedSongs,
            )

            QueueHeader(
                title = stringResource("queue"),
                count = queue.size,
                totalDurationMs = queue.sumOf { it.durationMs ?: 0L },
                locked = queueEditLocked,
                showLock = !selectionMode,
                onToggleLock = { preferences.setQueueEditLocked(!queueEditLocked) },
                textColor = textColor,
                mutedTextColor = mutedTextColor,
                backgroundColor = chromeBackground,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (selectionMode) {
                QueueSelectionBar(
                    selectedCount = selectedIds.size,
                    allSelected = allSelected,
                    textColor = textColor,
                    mutedTextColor = mutedTextColor,
                    backgroundColor = chromeBackground,
                    onClose = { clearSelection() },
                    onToggleSelectAll = {
                        if (allSelected) {
                            selectedIds.clear()
                            selectionMode = false
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(queue.map { it.id })
                        }
                    },
                    onPlay = {
                        if (selectedItems.isNotEmpty()) {
                            playerState.playQueue(selectedItems, startIndex = 0)
                            clearSelection()
                        }
                    },
                    onShuffle = {
                        if (selectedItems.isNotEmpty()) {
                            playerState.playQueue(selectedItems.shuffled(), startIndex = 0)
                            clearSelection()
                        }
                    },
                    onAddToQueue = {
                        selectedItems.forEach { playerState.addToQueue(it) }
                        clearSelection()
                    },
                    onAddToPlaylist = {
                        if (selectedItems.isNotEmpty()) {
                            playlistTargetItems = selectedItems
                            showPlaylistDialog = true
                        }
                    },
                    allLiked = allLiked,
                    downloadState = downloadState,
                    onDownload = {
                        downloadItems(selectedItems)
                        clearSelection()
                    },
                    onRemoveDownload = {
                        downloadTargetItems = selectedItems
                        showRemoveDownloadDialog = true
                    },
                    onToggleLike = {
                        toggleLikesForItems(selectedItems, like = !allLiked)
                        clearSelection()
                    },
                    onRemoveFromQueue = {
                        val indices = selectedIds.mapNotNull { id ->
                            queue.indexOfFirst { it.id == id }.takeIf { it != -1 }
                        }
                        removeItemsWithUndo(indices)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = PlayerQueueCollapsedHeight),
                )
            }

            QueueBottomBar(
                playerState = playerState,
                textColor = textColor,
                mutedTextColor = mutedTextColor,
                backgroundColor = chromeBackground,
                onCollapse = { state.collapseSoft() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = PlayerQueueCollapsedHeight + 12.dp),
            )
        }
    }

    if (showPlaylistDialog) {
        val playlistItems =
            if (playlistTargetItems.isNotEmpty()) playlistTargetItems else selectedItemsInQueue(queue)
        PlaylistPickerDialog(
            visible = true,
            playlists = editablePlaylists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                coroutineScope.launch {
                    database.insertPlaylist(playlist)
                    addItemsToPlaylist(playlist.id, playlistItems)
                    if (selectionMode) clearSelection()
                }
                showPlaylistDialog = false
            },
            onSelectPlaylist = { playlist ->
                addItemsToPlaylist(playlist.id, playlistItems)
                if (selectionMode) clearSelection()
                showPlaylistDialog = false
            },
            onDismiss = { showPlaylistDialog = false },
        )
    }

    if (showRemoveDownloadDialog) {
        val removalItems =
            if (downloadTargetItems.isNotEmpty()) downloadTargetItems else selectedItemsInQueue(queue)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(stringResource("remove_downloads")) },
            text = { Text(stringResource("remove_downloads_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeDownloads(removalItems)
                        if (selectionMode) clearSelection()
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(stringResource("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }
}

@Composable
private fun QueueActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    shape: RoundedCornerShape,
    borderColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = iconTint,
        )
    }
}

@Composable
private fun PlayerMenuQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QueueHeader(
    title: String,
    count: Int,
    totalDurationMs: Long,
    locked: Boolean,
    showLock: Boolean,
    onToggleLock: () -> Unit,
    textColor: Color,
    mutedTextColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(backgroundColor)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerQueueCollapsedHeight)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (showLock) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (locked) IconAssets.lock() else IconAssets.lockOpen(),
                        contentDescription = null,
                        tint = mutedTextColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = pluralStringResource("n_song", count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedTextColor,
                )
                Text(
                    text = formatDurationMs(totalDurationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedTextColor,
                )
            }
        }

        if (count > 0) {
            Surface(
                color = mutedTextColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            ) { }
        }
    }
}

@Composable
private fun QueueSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    textColor: Color,
    mutedTextColor: Color,
    backgroundColor: Color,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    allLiked: Boolean,
    downloadState: SelectionDownloadState,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onToggleLike: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val likeLabel = if (allLiked) stringResource("action_remove_like") else stringResource("action_like")
    val likeIcon = if (allLiked) IconAssets.favorite() else IconAssets.favoriteBorder()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = IconAssets.close(),
                contentDescription = stringResource("close_selection"),
                tint = mutedTextColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = stringResource("elements_selected", selectedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (allSelected) IconAssets.deselect() else IconAssets.selectAll(),
                contentDescription = if (allSelected) stringResource("deselect_all") else stringResource("select_all"),
                tint = mutedTextColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = IconAssets.moreVert(),
                    contentDescription = stringResource("more_options"),
                    tint = mutedTextColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource("remove_from_queue")) },
                    onClick = {
                        onRemoveFromQueue()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = IconAssets.delete(),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("play")) },
                    onClick = {
                        onPlay()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = IconAssets.play(),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("shuffle")) },
                    onClick = {
                        onShuffle()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = IconAssets.shuffle(),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("add_to_queue")) },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = IconAssets.queueMusic(),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("add_to_playlist")) },
                    onClick = {
                        onAddToPlaylist()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = IconAssets.playlistAdd(),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(likeLabel) },
                    onClick = {
                        onToggleLike()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            imageVector = likeIcon,
                            contentDescription = null,
                        )
                    },
                )
                when (downloadState) {
                    SelectionDownloadState.NONE -> Unit
                    SelectionDownloadState.COMPLETED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource("remove_download")) },
                            onClick = {
                                onRemoveDownload()
                                showMenu = false
                            },
                            enabled = selectedCount > 0,
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.offline(),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    SelectionDownloadState.DOWNLOADING -> {
                        DropdownMenuItem(
                            text = { Text(stringResource("downloading")) },
                            onClick = {
                                onRemoveDownload()
                                showMenu = false
                            },
                            enabled = selectedCount > 0,
                            leadingIcon = {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            },
                        )
                    }
                    SelectionDownloadState.NOT_DOWNLOADED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource("download")) },
                            onClick = {
                                onDownload()
                                showMenu = false
                            },
                            enabled = selectedCount > 0,
                            leadingIcon = {
                                Icon(
                                    imageVector = IconAssets.download(),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun QueueItemRow(
    item: LibraryItem,
    isActive: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    showDragHandle: Boolean,
    showItemMenu: Boolean,
    isLiked: Boolean,
    downloadState: SelectionDownloadState,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onRemove: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    textColor: Color,
    mutedTextColor: Color,
    backgroundColor: Color,
) {
    var itemMenuExpanded by remember { mutableStateOf(false) }
    val rowBackground = when {
        isSelected -> textColor.copy(alpha = 0.14f)
        isActive -> textColor.copy(alpha = 0.08f)
        else -> backgroundColor
    }
    val contentAlpha = if (isActive) 1f else 0.9f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (!item.artworkUrl.isNullOrBlank()) {
                RemoteImage(
                    url = item.artworkUrl!!,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = IconAssets.musicNote(),
                        contentDescription = null,
                        tint = mutedTextColor.copy(alpha = contentAlpha),
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isPlaying) 0.45f else 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) IconAssets.pause() else IconAssets.play(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = IconAssets.check(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) textColor else textColor.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = mutedTextColor.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showItemMenu) {
            Box {
                IconButton(
                    onClick = { itemMenuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.moreVert(),
                        contentDescription = stringResource("more_options"),
                        tint = mutedTextColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = itemMenuExpanded,
                    onDismissRequest = { itemMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource("play_next")) },
                        onClick = {
                            onPlayNext()
                            itemMenuExpanded = false
                        },
                        enabled = !isActive,
                        leadingIcon = {
                            Icon(
                                imageVector = IconAssets.play(),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource("add_to_playlist")) },
                        onClick = {
                            onAddToPlaylist()
                            itemMenuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = IconAssets.playlistAdd(),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (isLiked) stringResource("action_remove_like") else stringResource("action_like"))
                        },
                        onClick = {
                            onToggleLike()
                            itemMenuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                contentDescription = null,
                            )
                        },
                    )
                    when (downloadState) {
                        SelectionDownloadState.NONE -> Unit
                        SelectionDownloadState.COMPLETED -> {
                            DropdownMenuItem(
                                text = { Text(stringResource("remove_download")) },
                                onClick = {
                                    onRemoveDownload()
                                    itemMenuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconAssets.offline(),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        SelectionDownloadState.DOWNLOADING -> {
                            DropdownMenuItem(
                                text = { Text(stringResource("downloading")) },
                                onClick = {
                                    onRemoveDownload()
                                    itemMenuExpanded = false
                                },
                                leadingIcon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                },
                            )
                        }
                        SelectionDownloadState.NOT_DOWNLOADED -> {
                            DropdownMenuItem(
                                text = { Text(stringResource("download")) },
                                onClick = {
                                    onDownload()
                                    itemMenuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconAssets.download(),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource("copy_link")) },
                        onClick = {
                            onCopyLink()
                            itemMenuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = IconAssets.link(),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource("open_in_browser")) },
                        onClick = {
                            onOpenInBrowser()
                            itemMenuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = IconAssets.arrowTopLeft(),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource("remove_from_queue")) },
                        onClick = {
                            onRemove()
                            itemMenuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = IconAssets.delete(),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        if (showDragHandle) {
            IconButton(
                onClick = { },
                modifier = dragHandleModifier.size(32.dp),
            ) {
                Icon(
                    imageVector = IconAssets.dragHandle(),
                    contentDescription = null,
                    tint = mutedTextColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueBottomBar(
    playerState: PlayerState,
    textColor: Color,
    mutedTextColor: Color,
    backgroundColor: Color,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerQueueCollapsedHeight)
            .background(backgroundColor)
            .clickable(onClick = onCollapse)
            .padding(horizontal = 12.dp),
    ) {
        IconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = { playerState.toggleShuffle() },
        ) {
            Icon(
                imageVector = IconAssets.shuffle(),
                contentDescription = null,
                tint = if (playerState.shuffleEnabled) textColor else mutedTextColor.copy(alpha = 0.5f),
            )
        }

        Icon(
            imageVector = IconAssets.expandMore(),
            contentDescription = null,
            tint = mutedTextColor,
            modifier = Modifier.align(Alignment.Center),
        )

        IconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { playerState.toggleRepeat() },
        ) {
            val repeatIcon = if (playerState.repeatMode == com.anitail.desktop.player.RepeatMode.ONE) {
                IconAssets.repeatOne()
            } else {
                IconAssets.repeat()
            }
            Icon(
                imageVector = repeatIcon,
                contentDescription = null,
                tint = if (playerState.repeatMode == com.anitail.desktop.player.RepeatMode.OFF) {
                    mutedTextColor.copy(alpha = 0.5f)
                } else {
                    textColor
                },
            )
        }
    }
}
