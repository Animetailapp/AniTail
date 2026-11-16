@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class LocalMatchKind {
    SONG,
    PLAYLIST,
    ALBUM,
    ARTIST,
}

private sealed interface FloatingSearchEntry {
    data class HistoryEntry(val query: String) : FloatingSearchEntry

    data class OnlineSearchEntry(val query: String) : FloatingSearchEntry

    data class LocalItemEntry(
        val item: LibraryItem,
        val subtitle: String,
        val kind: LocalMatchKind,
        val rawId: String,
        val rawName: String,
    ) : FloatingSearchEntry
}

/**
 * Spotlight-style quick search overlay (`Ctrl+K`) that searches local songs, playlists,
 * albums, artists, and search history instantaneously as the user types, while pressing
 * Enter (or selecting the online search action) launches a full online search.
 */
@Composable
fun FloatingSearchOverlay(
    visible: Boolean,
    database: DesktopDatabase,
    onDismiss: () -> Unit,
    onSubmitSearch: (String) -> Unit,
    onPlayItem: (LibraryItem) -> Unit,
    onOpenArtist: ((String, String) -> Unit)? = null,
    onOpenAlbum: ((String, String) -> Unit)? = null,
    onOpenPlaylist: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = visible, label = "floatingSearch")
    if (!visible && !transition.currentState && !transition.isRunning) {
        return
    }

    val motion = rememberDesktopMotion()
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val trimmedQuery = query.trim()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var queryFocused by remember { mutableStateOf(false) }

    val allSongs by database.songs.collectAsState(initial = emptyList())
    val allPlaylists by database.allPlaylists().collectAsState(initial = emptyList())
    val allAlbums by database.albums.collectAsState(initial = emptyList())
    val allArtists by database.artists.collectAsState(initial = emptyList())
    val recentSearches by database.recentSearches(limit = 12).collectAsState(initial = emptyList())

    val isSpanish = LocalStrings.current.locale.language.equals("es", ignoreCase = true)
    val searchLabel = stringResource("search")
    val clearLabel = stringResource("clear")
    val closeLabel = stringResource("close")
    val songsLabel = stringResource("songs")
    val playlistsLabel = stringResource("playlists")
    val albumsLabel = stringResource("albums")
    val artistsLabel = stringResource("artists")
    val searchOnlineLabel = stringResource("search_online")
    val placeholderText = stringResource("search_yt_music").let { resolved ->
        if (resolved == "search_yt_music") {
            if (isSpanish) "Buscar canciones, álbumes, artistas..." else "Search songs, albums, artists..."
        } else {
            resolved
        }
    }

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .drop(1)
            .collect {
                selectedIndex = -1
            }
    }

    LaunchedEffect(visible) {
        if (visible) {
            selectedIndex = -1
            queryState.setTextAndPlaceCursorAtEnd("")
            delay(60.milliseconds)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val historyEntries = remember(trimmedQuery, recentSearches) {
        if (trimmedQuery.isEmpty()) {
            recentSearches.take(5).map { FloatingSearchEntry.HistoryEntry(it.query) }
        } else {
            recentSearches
                .filter { entry ->
                    entry.query.contains(trimmedQuery, ignoreCase = true) &&
                        !entry.query.equals(trimmedQuery, ignoreCase = true)
                }
                .take(3)
                .map { FloatingSearchEntry.HistoryEntry(it.query) }
        }
    }

    val songMatches = remember(trimmedQuery, allSongs, songsLabel) {
        if (trimmedQuery.isEmpty()) {
            allSongs
                .filter { it.inLibrary != null || it.liked }
                .sortedByDescending { it.totalPlayTime }
                .take(4)
                .map { song ->
                    FloatingSearchEntry.LocalItemEntry(
                        item = song.toLibraryItem(),
                        subtitle = listOfNotNull(
                            song.artistName?.takeIf { it.isNotBlank() },
                            songsLabel,
                        ).joinToString(" • "),
                        kind = LocalMatchKind.SONG,
                        rawId = song.id,
                        rawName = song.title,
                    )
                }
        } else {
            allSongs
                .asSequence()
                .filter { song ->
                    song.title.contains(trimmedQuery, ignoreCase = true) ||
                        song.artistName.orEmpty().contains(trimmedQuery, ignoreCase = true) ||
                        song.albumName.orEmpty().contains(trimmedQuery, ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<com.anitail.desktop.db.entities.SongEntity> {
                        it.title.startsWith(trimmedQuery, ignoreCase = true)
                    }.thenByDescending {
                        it.inLibrary != null || it.liked
                    }.thenByDescending {
                        it.totalPlayTime
                    },
                )
                .take(6)
                .map { song ->
                    FloatingSearchEntry.LocalItemEntry(
                        item = song.toLibraryItem(),
                        subtitle = listOfNotNull(
                            song.artistName?.takeIf { it.isNotBlank() },
                            song.albumName?.takeIf { it.isNotBlank() },
                            songsLabel,
                        ).joinToString(" • "),
                        kind = LocalMatchKind.SONG,
                        rawId = song.id,
                        rawName = song.title,
                    )
                }
                .toList()
        }
    }

    val playlistMatches = remember(trimmedQuery, allPlaylists, playlistsLabel) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            allPlaylists
                .asSequence()
                .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
                .sortedByDescending { it.name.startsWith(trimmedQuery, ignoreCase = true) }
                .take(4)
                .map { playlist ->
                    FloatingSearchEntry.LocalItemEntry(
                        item = playlist.toLibraryItem(),
                        subtitle = playlistsLabel,
                        kind = LocalMatchKind.PLAYLIST,
                        rawId = playlist.id,
                        rawName = playlist.name,
                    )
                }
                .toList()
        }
    }

    val albumMatches = remember(trimmedQuery, allAlbums, albumsLabel) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            allAlbums
                .asSequence()
                .filter { it.title.contains(trimmedQuery, ignoreCase = true) }
                .sortedByDescending { it.title.startsWith(trimmedQuery, ignoreCase = true) }
                .take(4)
                .map { album ->
                    FloatingSearchEntry.LocalItemEntry(
                        item = album.toLibraryItem(),
                        subtitle = listOfNotNull(
                            albumsLabel,
                            album.year?.toString(),
                        ).joinToString(" • "),
                        kind = LocalMatchKind.ALBUM,
                        rawId = album.id,
                        rawName = album.title,
                    )
                }
                .toList()
        }
    }

    val artistMatches = remember(trimmedQuery, allArtists, artistsLabel) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            allArtists
                .asSequence()
                .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
                .sortedByDescending { it.name.startsWith(trimmedQuery, ignoreCase = true) }
                .take(4)
                .map { artist ->
                    FloatingSearchEntry.LocalItemEntry(
                        item = artist.toLibraryItem(),
                        subtitle = artistsLabel,
                        kind = LocalMatchKind.ARTIST,
                        rawId = artist.id,
                        rawName = artist.name,
                    )
                }
                .toList()
        }
    }

    val entries = remember(
        trimmedQuery,
        historyEntries,
        songMatches,
        playlistMatches,
        albumMatches,
        artistMatches,
    ) {
        buildList {
            if (trimmedQuery.isNotEmpty()) {
                add(FloatingSearchEntry.OnlineSearchEntry(trimmedQuery))
            }
            addAll(songMatches)
            addAll(playlistMatches)
            addAll(albumMatches)
            addAll(artistMatches)
            addAll(historyEntries)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            val rowHeightPx = with(density) { 52.dp.roundToPx() }
            val targetPx = (selectedIndex * rowHeightPx).coerceAtMost(scrollState.maxValue)
            scrollState.animateScrollTo(targetPx)
        } else {
            scrollState.animateScrollTo(0)
        }
    }

    fun dismissOverlay() {
        focusManager.clearFocus()
        onDismiss()
    }

    fun activateEntry(entry: FloatingSearchEntry) {
        when (entry) {
            is FloatingSearchEntry.HistoryEntry -> {
                onSubmitSearch(entry.query)
                dismissOverlay()
            }

            is FloatingSearchEntry.OnlineSearchEntry -> {
                if (entry.query.isNotBlank()) {
                    onSubmitSearch(entry.query)
                    dismissOverlay()
                }
            }

            is FloatingSearchEntry.LocalItemEntry -> {
                when (entry.kind) {
                    LocalMatchKind.SONG -> onPlayItem(entry.item)
                    LocalMatchKind.ARTIST -> onOpenArtist?.invoke(entry.rawId, entry.rawName) ?: onPlayItem(entry.item)
                    LocalMatchKind.ALBUM -> onOpenAlbum?.invoke(entry.rawId, entry.rawName) ?: onPlayItem(entry.item)
                    LocalMatchKind.PLAYLIST -> onOpenPlaylist?.invoke(entry.rawId, entry.rawName) ?: onPlayItem(entry.item)
                }
                dismissOverlay()
            }
        }
    }

    fun performSubmit() {
        val highlighted = entries.getOrNull(selectedIndex)
        if (highlighted != null) {
            activateEntry(highlighted)
        } else if (trimmedQuery.isNotEmpty()) {
            onSubmitSearch(trimmedQuery)
            dismissOverlay()
        }
    }

    val scrimAlpha by transition.animateFloat(
        transitionSpec = { motion.fastEffects },
        label = "floatingSearchScrimAlpha",
    ) { isShown -> if (isShown) 0.52f else 0f }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = searchLabel
                isTraversalGroup = true
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && visible) {
                    when (event.key) {
                        Key.Escape -> {
                            dismissOverlay()
                            true
                        }

                        Key.DirectionDown -> {
                            if (entries.isNotEmpty()) {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(entries.lastIndex)
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            if (entries.isNotEmpty()) {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(-1)
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            val selected = entries.getOrNull(selectedIndex)
                            if (selected is FloatingSearchEntry.HistoryEntry && queryFocused) {
                                queryState.setTextAndPlaceCursorAtEnd(selected.query)
                                selectedIndex = -1
                                true
                            } else {
                                false
                            }
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            performSubmit()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = ::dismissOverlay,
                ),
        )

        transition.AnimatedVisibility(
            visible = { it },
            enter = motion.popupEnter,
            exit = motion.popupExit,
        ) {
            Surface(
                modifier = Modifier
                    .blockInput(!this.transition.acceptsInput(EnterExitState.Visible))
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .focusGroup()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surfaceContainerHigh,
                shadowElevation = 24.dp,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Column(
                    modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = IconAssets.search(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                state = queryState,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = searchLabel }
                                    .onFocusChanged { queryFocused = it.isFocused }
                                    .focusRequester(focusRequester),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                cursorBrush = SolidColor(colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                onKeyboardAction = KeyboardActionHandler { performSubmit() },
                                decorator = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = placeholderText,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { queryState.setTextAndPlaceCursorAtEnd("") },
                                modifier = Modifier.size(36.dp),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = IconAssets.close(),
                                    contentDescription = clearLabel,
                                    modifier = Modifier.size(18.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            ShortcutKeyCap(label = "Esc")
                        }
                    }

                    if (entries.isNotEmpty()) {
                        HorizontalDivider(
                            color = colorScheme.outlineVariant.copy(alpha = 0.22f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            entries.forEachIndexed { index, entry ->
                                val isSelected = index == selectedIndex
                                when (entry) {
                                    is FloatingSearchEntry.OnlineSearchEntry -> {
                                        OnlineSearchRow(
                                            query = entry.query,
                                            label = searchOnlineLabel,
                                            selected = isSelected,
                                            onClick = { activateEntry(entry) },
                                        )
                                    }

                                    is FloatingSearchEntry.LocalItemEntry -> {
                                        LocalSearchResultRow(
                                            entry = entry,
                                            selected = isSelected,
                                            onClick = { activateEntry(entry) },
                                        )
                                    }

                                    is FloatingSearchEntry.HistoryEntry -> {
                                        HistorySearchRow(
                                            query = entry.query,
                                            selected = isSelected,
                                            clearLabel = clearLabel,
                                            onClick = { activateEntry(entry) },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    database.deleteSearch(entry.query)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else if (trimmedQuery.isEmpty()) {
                        HorizontalDivider(
                            color = colorScheme.outlineVariant.copy(alpha = 0.22f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isSpanish) {
                                    "Escribe para buscar en tu biblioteca o pulsa Enter para buscar en línea"
                                } else {
                                    "Type to search your library or press Enter to search online"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.18f),
                    )
                    SearchOverlayFooter(isSpanish = isSpanish)
                }
            }
        }
    }
}

@Composable
private fun OnlineSearchRow(
    query: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = if (selected) {
        colorScheme.primaryContainer
    } else {
        colorScheme.primaryContainer.copy(alpha = 0.35f)
    }
    val contentColor = if (selected) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = IconAssets.search(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$label: \"$query\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ShortcutKeyCap(label = "Enter")
    }
}

@Composable
private fun LocalSearchResultRow(
    entry: FloatingSearchEntry.LocalItemEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = if (selected) colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    val titleColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val subtitleColor = if (selected) {
        colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    } else {
        colorScheme.onSurfaceVariant
    }

    val thumbShape: Shape = if (entry.kind == LocalMatchKind.ARTIST) {
        CircleShape
    } else {
        RoundedCornerShape(8.dp)
    }

    val fallbackIcon: ImageVector = when (entry.kind) {
        LocalMatchKind.SONG -> IconAssets.musicNote()
        LocalMatchKind.PLAYLIST -> IconAssets.playlistPlay()
        LocalMatchKind.ALBUM -> IconAssets.album()
        LocalMatchKind.ARTIST -> IconAssets.person()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!entry.item.artworkUrl.isNullOrBlank()) {
            RemoteImage(
                url = entry.item.artworkUrl,
                contentDescription = entry.item.title,
                modifier = Modifier.size(40.dp),
                shape = thumbShape,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(thumbShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (entry.kind == LocalMatchKind.SONG) {
                IconAssets.play()
            } else {
                IconAssets.chevronRight()
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = subtitleColor.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun HistorySearchRow(
    query: String,
    selected: Boolean,
    clearLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = if (selected) colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    val textColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val iconColor = if (selected) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = IconAssets.history(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconColor,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = IconAssets.close(),
                contentDescription = clearLabel,
                modifier = Modifier.size(14.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SearchOverlayFooter(isSpanish: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterHint(
            keys = "↑↓",
            label = if (isSpanish) "Navegar" else "Navigate",
            textColor = colorScheme.onSurfaceVariant,
        )
        FooterHint(
            keys = "Enter",
            label = if (isSpanish) "Abrir / Buscar" else "Open / Search",
            textColor = colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        FooterHint(
            keys = "Esc",
            label = if (isSpanish) "Cerrar" else "Close",
            textColor = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FooterHint(
    keys: String,
    label: String,
    textColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShortcutKeyCap(label = keys)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

@Composable
private fun ShortcutKeyCap(label: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
