package com.anitail.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.CONTENT_TYPE_HEADER
import com.anitail.music.constants.CONTENT_TYPE_SONG
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.ui.component.EmptyPlaceholder
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.viewmodels.LocalFolderViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalFolderScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    folderPath: String,
    viewModel: LocalFolderViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val songs by viewModel.songs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val subfolders by viewModel.subfolders.collectAsState()

    val folderName = folderPath.substringAfterLast('/')

    Timber.d("LocalFolderScreen: COMPOSE - folderPath='$folderPath', directSongs=${songs.size}, allSongs=${allSongs.size}, subfolders=${subfolders.size}")
    Timber.d("LocalFolderScreen: Subfolders = $subfolders")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            // Header with play all / shuffle buttons
            item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.library_music),
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_song, allSongs.size, allSongs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (allSongs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = allSongs.map { it.toMediaItem() }
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = stringResource(R.string.play)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (allSongs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = allSongs.shuffled().map { it.toMediaItem() }
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = stringResource(R.string.shuffle)
                            )
                        }
                    }
                }
            }

            // Subfolders section
            if (subfolders.isNotEmpty()) {
                item(key = "subfolders_header") {
                    Text(
                        text = stringResource(R.string.folders),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = subfolders,
                    key = { "subfolder_$it" }
                ) { subfolder ->
                    ListItem(
                        headlineContent = { Text(subfolder.substringAfterLast('/')) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.library_music),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                val encodedPath = java.net.URLEncoder.encode(subfolder, "UTF-8")
                                navController.navigate("local_folder/$encodedPath")
                            }
                            .animateItem()
                    )
                }
            }

            // Songs section
            if (songs.isNotEmpty()) {
                item(key = "songs_header") {
                    Text(
                        text = stringResource(R.string.songs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = songs,
                    key = { it.id },
                    contentType = { CONTENT_TYPE_SONG }
                ) { song ->
                    SongListItem(
                        song = song,
                        showInLibraryIcon = true,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = songs.map { it.toMediaItem() },
                                            startIndex = songs.indexOf(song)
                                        )
                                    )
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                            .animateItem()
                    )
                }
            }

            // Empty state
            if (songs.isEmpty() && subfolders.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.library_music,
                        text = stringResource(R.string.no_local_folders)
                    )
                }
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = folderName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}


