package com.anitail.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.anitail.music.LocalDownloadLibraryRepository
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.CONTENT_TYPE_HEADER
import com.anitail.music.constants.CONTENT_TYPE_SONG
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.ui.component.HideOnScrollFAB
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.menu.SelectionSongMenu
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.ui.utils.ItemWrapper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryDownloadedScreen(
    navController: NavController,
    onDeselect: () -> Unit,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val downloadRepository = LocalDownloadLibraryRepository.current
    val downloads by downloadRepository.observeDownloads().collectAsState()
    // Mostrar todos los downloads; para los sin song mapeado, usar metadata del archivo
    val playableSongs = downloads.mapNotNull { downloadedTrack ->
        downloadedTrack.song
    }

    val wrappedSongs = playableSongs.map { item -> ItemWrapper(item) }.toMutableList()
    var selection by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.filter_downloaded)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = ""
                            )
                        },
                    )
                }
            }

            if (playableSongs.isEmpty()) {
                item(
                    key = "empty",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.offline_player_empty_state_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(
                    key = "header",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        if (selection) {
                            val count = wrappedSongs.count { it.isSelected }
                            IconButton(
                                onClick = { selection = false },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                )
                            }
                            Text(
                                text = pluralStringResource(R.plurals.n_song, count, count),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (count == wrappedSongs.size) {
                                        wrappedSongs.forEach { it.isSelected = false }
                                    } else {
                                        wrappedSongs.forEach { it.isSelected = true }
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all),
                                    contentDescription = null,
                                )
                            }

                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SelectionSongMenu(
                                            songSelection = wrappedSongs.filter { it.isSelected }
                                                .map { it.item },
                                            onDismiss = menuState::dismiss,
                                            clearAction = { selection = false },
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    playableSongs.size,
                                    playableSongs.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = wrappedSongs,
                    key = { _, item -> item.item.song.id },
                    contentType = { _, _ -> CONTENT_TYPE_SONG },
                ) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        showInLibraryIcon = true,
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = songWrapper.item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        isSelected = songWrapper.isSelected && selection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!selection) {
                                        if (songWrapper.item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.filter_downloaded),
                                                    items = playableSongs.map { it.toMediaItem() },
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    } else {
                                        songWrapper.isSelected = !songWrapper.isSelected
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!selection) {
                                        selection = true
                                    }
                                    wrappedSongs.forEach {
                                        it.isSelected = false
                                    }
                                    songWrapper.isSelected = true
                                },
                            )
                            .animateItem(),
                    )
                }
            }
        }

        HideOnScrollFAB(
            visible = playableSongs.isNotEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.filter_downloaded),
                        items = playableSongs.shuffled().map { it.toMediaItem() },
                    ),
                )
            },
        )
    }
}
