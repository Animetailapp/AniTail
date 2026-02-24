package com.anitail.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.library_music),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.n_song, allSongs.size, allSongs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (allSongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = folderName,
                                                items = allSongs.map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.play))
                            }

                            OutlinedButton(
                                onClick = {
                                    if (allSongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = folderName,
                                                items = allSongs.shuffled().map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.shuffle))
                            }
                        }
                    }
                }
            }

            if (subfolders.isNotEmpty()) {
                item(key = "subfolders_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.folders),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${subfolders.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(
                    items = subfolders,
                    key = { "subfolder_$it" },
                ) { subfolder ->
                    LocalSubfolderListItem(
                        subfolderPath = subfolder,
                        onClick = {
                            val encodedPath = java.net.URLEncoder.encode(subfolder, "UTF-8")
                            navController.navigate("local_folder/$encodedPath")
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "songs_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.songs),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(
                    items = songs,
                    key = { it.id },
                    contentType = { CONTENT_TYPE_SONG },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .combinedClickable(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = songs.map { it.toMediaItem() },
                                            startIndex = songs.indexOf(song),
                                        ),
                                    )
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            )
                            .animateItem(),
                    )
                }
            }

            if (songs.isEmpty() && subfolders.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.library_music,
                        text = stringResource(R.string.no_local_folders),
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
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LocalSubfolderListItem(
    subfolderPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.library_music_outlined),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subfolderPath.substringAfterLast('/').ifBlank { subfolderPath },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subfolderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
