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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerQueueCollapsedHeight
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.BottomSheet
import com.anitail.desktop.ui.component.BottomSheetState
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
    pureBlack: Boolean,
    textColor: Color,
    mutedTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val borderColor = textColor.copy(alpha = 0.35f)
    val listBackground = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val chromeBackground = if (pureBlack) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    }
    val clipboard = LocalClipboardManager.current
    val preferences = remember { DesktopPreferences.getInstance() }
    val queueEditLocked by preferences.queueEditLocked.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }

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
                    contentDescription = "Compartir",
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
                    contentDescription = "Cola",
                    shape = RoundedCornerShape(5.dp),
                    borderColor = borderColor,
                    iconTint = textColor,
                    onClick = { state.expandSoft() },
                )

                QueueActionButton(
                    icon = IconAssets.lyrics(),
                    contentDescription = "Letras",
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
                            contentDescription = "Menu",
                            tint = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copiar enlace") },
                            onClick = {
                                copyLink(currentItem.playbackUrl)
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Abrir en navegador") },
                            onClick = {
                                openInBrowser(currentItem.playbackUrl)
                                showMenu = false
                            },
                        )
                    }
                }
            }
        },
    ) {
        val queue = playerState.queue
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
                val message = if (removedItems.size == 1) {
                    "Cancion eliminada"
                } else {
                    "${removedItems.size} canciones eliminadas"
                }
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Deshacer",
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
                            text = "La cola esta vacia",
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
                        ) {
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
                                QueueItemRow(
                                    item = item,
                                    isActive = index == currentIndex,
                                    isPlaying = playerState.isPlaying && index == currentIndex,
                                    isSelected = isSelected,
                                    showDragHandle = !queueEditLocked && !selectionMode,
                                    showItemMenu = !selectionMode,
                                    dragHandleModifier = if (!queueEditLocked && !selectionMode) Modifier.draggableHandle() else Modifier,
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

            QueueHeader(
                title = "Cola actual",
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
                val selectedItems = queue.filter { selectedIds.contains(it.id) }
                val selectedLinks = selectedItems.map { it.playbackUrl }.filter { it.isNotBlank() }
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
                    onRemove = {
                        val indices = selectedIds.mapNotNull { id ->
                            queue.indexOfFirst { it.id == id }.takeIf { it != -1 }
                        }
                        removeItemsWithUndo(indices)
                    },
                    onCopyLinks = {
                        if (selectedLinks.isNotEmpty()) {
                            copyLink(selectedLinks.joinToString("\n"))
                        }
                    },
                    onOpenInBrowser = {
                        selectedLinks.firstOrNull()?.let { openInBrowser(it) }
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
                    text = if (count == 1) "1 cancion" else "$count canciones",
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
    onRemove: () -> Unit,
    onCopyLinks: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

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
                contentDescription = "Cerrar seleccion",
                tint = mutedTextColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = "$selectedCount seleccionados",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (allSelected) IconAssets.deselect() else IconAssets.selectAll(),
                contentDescription = if (allSelected) "Deseleccionar todo" else "Seleccionar todo",
                tint = mutedTextColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = IconAssets.moreVert(),
                    contentDescription = "Menu seleccion",
                    tint = mutedTextColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Reproducir seleccion") },
                    onClick = {
                        onPlay()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    onClick = {
                        onRemove()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                )
                DropdownMenuItem(
                    text = { Text("Copiar enlace") },
                    onClick = {
                        onCopyLinks()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                )
                DropdownMenuItem(
                    text = { Text("Abrir en navegador") },
                    onClick = {
                        onOpenInBrowser()
                        showMenu = false
                    },
                    enabled = selectedCount > 0,
                )
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
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNext: () -> Unit,
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
                        contentDescription = "Menu",
                        tint = mutedTextColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = itemMenuExpanded,
                    onDismissRequest = { itemMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Reproducir siguiente") },
                        onClick = {
                            onPlayNext()
                            itemMenuExpanded = false
                        },
                        enabled = !isActive,
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar de la cola") },
                        onClick = {
                            onRemove()
                            itemMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar enlace") },
                        onClick = {
                            onCopyLink()
                            itemMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Abrir en navegador") },
                        onClick = {
                            onOpenInBrowser()
                            itemMenuExpanded = false
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
