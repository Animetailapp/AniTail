package com.anitail.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.CONTENT_TYPE_HEADER
import com.anitail.music.constants.CONTENT_TYPE_SONG
import com.anitail.music.constants.ThumbnailCornerRadius
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.ui.component.EmptyPlaceholder
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.utils.makeTimeString
import com.anitail.music.viewmodels.LocalAlbumViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalAlbumViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val album by viewModel.album.collectAsState()
    val songs by viewModel.songs.collectAsState()

    Timber.d("LocalAlbumScreen: COMPOSE - album=${album?.album?.title}, songCount=${songs.size}")

    val totalDuration = songs.sumOf { it.song.duration }
    Timber.d("LocalAlbumScreen: totalDuration=$totalDuration seconds")

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
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = album?.album?.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (album?.album?.thumbnailUrl.isNullOrBlank()) {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = album?.album?.title ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        album?.artists?.firstOrNull()?.let { artist ->
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            album?.album?.year?.let { year ->
                                LocalAlbumInfoChip(
                                    text = year.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            LocalAlbumInfoChip(
                                text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                                modifier = Modifier.weight(1f),
                            )
                            LocalAlbumInfoChip(
                                text = makeTimeString(totalDuration * 1000L),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    Timber.d("LocalAlbumScreen: Play button clicked - ${songs.size} songs")
                                    if (songs.isNotEmpty()) {
                                        val mediaItems = songs.map { it.toMediaItem() }
                                        Timber.d("LocalAlbumScreen: Playing queue with ${mediaItems.size} items")
                                        mediaItems.forEachIndexed { idx, item ->
                                            Timber.d("LocalAlbumScreen: MediaItem[$idx] id=${item.mediaId}, uri=${item.localConfiguration?.uri}")
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = album?.album?.title ?: "Local Album",
                                                items = mediaItems,
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
                                    Timber.d("LocalAlbumScreen: Shuffle button clicked - ${songs.size} songs")
                                    if (songs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = album?.album?.title ?: "Local Album",
                                                items = songs.shuffled().map { it.toMediaItem() },
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

            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            } else {
                item(key = "songs_header", contentType = CONTENT_TYPE_HEADER) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
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
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, song ->
                SongListItem(
                    song = song,
                    albumIndex = index + 1,
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
                                Timber.d("LocalAlbumScreen: Song clicked - index=$index, id=${song.id}, title=${song.song.title}")
                                val mediaItem = song.toMediaItem()
                                Timber.d("LocalAlbumScreen: Song MediaItem uri=${mediaItem.localConfiguration?.uri}")
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = album?.album?.title ?: "Local Album",
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            },
                            onLongClick = {
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
    }

    TopAppBar(
        title = {
            Text(
                text = album?.album?.title ?: "",
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
private fun LocalAlbumInfoChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
