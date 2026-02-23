/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import android.widget.Toast
import com.anitail.music.LocalListenTogetherManager
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.AlbumSortDescendingKey
import com.anitail.music.constants.AlbumSortType
import com.anitail.music.constants.AlbumSortTypeKey
import com.anitail.music.constants.AlbumViewTypeKey
import com.anitail.music.constants.CONTENT_TYPE_ALBUM
import com.anitail.music.constants.CONTENT_TYPE_HEADER
import com.anitail.music.constants.CONTENT_TYPE_SONG
import com.anitail.music.constants.GridItemSize
import com.anitail.music.constants.GridItemsSizeKey
import com.anitail.music.constants.GridThumbnailHeight
import com.anitail.music.constants.LibraryViewType
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.ui.component.AlbumGridItem
import com.anitail.music.ui.component.AlbumListItem
import com.anitail.music.ui.component.EmptyPlaceholder
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.component.SortHeader
import com.anitail.music.ui.menu.AlbumMenu
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import com.anitail.music.viewmodels.LocalArtistViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalArtistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val songs by viewModel.songs.collectAsState()

    Timber.d("LocalArtistScreen: COMPOSE - artist=${artist?.artist?.name}, albumCount=${albums.size}, songCount=${songs.size}")

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.NAME)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, false)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val sortedAlbums = albums.let { albumList ->
        when (sortType) {
            AlbumSortType.NAME -> albumList.sortedBy { it.album.title.lowercase() }
            AlbumSortType.YEAR -> albumList.sortedBy { it.album.year ?: 0 }
            AlbumSortType.SONG_COUNT -> albumList.sortedBy { it.album.songCount }
            else -> albumList
        }.let { if (sortDescending) it.reversed() else it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            artist?.let { a ->
                                AsyncImage(
                                    model = a.artist.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = a.artist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                if (songs.isEmpty()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.n_song,
                                            songs.size,
                                            songs.size
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (listenTogetherManager?.isInRoom == true) {
                                            Toast.makeText(context, R.string.local_playback_blocked_listen_together, Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        Timber.d("LocalArtistScreen: [LIST] Play all clicked - ${songs.size} songs")
                                        if (songs.isNotEmpty()) {
                                            val mediaItems = songs.map { it.toMediaItem() }
                                            Timber.d("LocalArtistScreen: [LIST] Playing queue with ${mediaItems.size} items")
                                            mediaItems.forEachIndexed { idx, item ->
                                                Timber.d("LocalArtistScreen: [LIST] MediaItem[$idx] id=${item.mediaId}, uri=${item.localConfiguration?.uri}")
                                            }
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist?.artist?.name ?: "Local Artist",
                                                    items = mediaItems
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
                                        if (listenTogetherManager?.isInRoom == true) {
                                            Toast.makeText(context, R.string.local_playback_blocked_listen_together, Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        Timber.d("LocalArtistScreen: [LIST] Shuffle clicked - ${songs.size} songs")
                                        if (songs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist?.artist?.name ?: "Local Artist",
                                                    items = songs.shuffled().map { it.toMediaItem() }
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

                    if (albums.isNotEmpty()) {
                        item(key = "albums_header", contentType = CONTENT_TYPE_HEADER) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.albums),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            AlbumSortType.NAME -> R.string.sort_by_name
                                            AlbumSortType.ARTIST -> R.string.sort_by_artist
                                            AlbumSortType.YEAR -> R.string.sort_by_year
                                            AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                                            AlbumSortType.LENGTH -> R.string.sort_by_length
                                            AlbumSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                )
                                IconButton(
                                    onClick = { viewType = viewType.toggle() },
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when (viewType) {
                                                LibraryViewType.LIST -> R.drawable.list
                                                LibraryViewType.GRID -> R.drawable.grid_view
                                            }
                                        ),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }

                        items(
                            items = sortedAlbums,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM }
                        ) { album ->
                            AlbumListItem(
                                album = album,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            Timber.d("LocalArtistScreen: [LIST] Album clicked - id=${album.id}, title=${album.album.title}")
                                            navController.navigate("local_album/${album.id}")
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
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

                    if (songs.isNotEmpty()) {
                        item(key = "songs_header", contentType = CONTENT_TYPE_HEADER) {
                            Text(
                                text = stringResource(R.string.songs),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
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
                                            if (listenTogetherManager?.isInRoom == true) {
                                                Toast.makeText(context, R.string.local_playback_blocked_listen_together, Toast.LENGTH_SHORT).show()
                                                return@combinedClickable
                                            }
                                            Timber.d("LocalArtistScreen: [LIST] Song clicked - id=${song.id}, title=${song.song.title}")
                                            val mediaItem = song.toMediaItem()
                                            Timber.d("LocalArtistScreen: [LIST] Song MediaItem uri=${mediaItem.localConfiguration?.uri}")
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist?.artist?.name ?: "Local Artist",
                                                    items = songs.map { it.toMediaItem() },
                                                    startIndex = songs.indexOf(song)
                                                )
                                            )
                                        },
                                        onLongClick = {
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

                    if (albums.isEmpty() && songs.isEmpty()) {
                        item(key = "empty") {
                            EmptyPlaceholder(
                                icon = R.drawable.music_note,
                                text = stringResource(R.string.no_results_found)
                            )
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            artist?.let { a ->
                                AsyncImage(
                                    model = a.artist.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = a.artist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                if (songs.isEmpty()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.n_song,
                                            songs.size,
                                            songs.size
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (listenTogetherManager?.isInRoom == true) {
                                            Toast.makeText(context, R.string.local_playback_blocked_listen_together, Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        Timber.d("LocalArtistScreen: [GRID] Play all clicked - ${songs.size} songs")
                                        if (songs.isNotEmpty()) {
                                            val mediaItems = songs.map { it.toMediaItem() }
                                            Timber.d("LocalArtistScreen: [GRID] Playing queue with ${mediaItems.size} items")
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist?.artist?.name ?: "Local Artist",
                                                    items = mediaItems
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
                                        if (listenTogetherManager?.isInRoom == true) {
                                            Toast.makeText(context, R.string.local_playback_blocked_listen_together, Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        Timber.d("LocalArtistScreen: [GRID] Shuffle clicked - ${songs.size} songs")
                                        if (songs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist?.artist?.name ?: "Local Artist",
                                                    items = songs.shuffled().map { it.toMediaItem() }
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

                    if (albums.isNotEmpty()) {
                        item(
                            key = "albums_header",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = CONTENT_TYPE_HEADER
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.albums),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            AlbumSortType.NAME -> R.string.sort_by_name
                                            AlbumSortType.ARTIST -> R.string.sort_by_artist
                                            AlbumSortType.YEAR -> R.string.sort_by_year
                                            AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                                            AlbumSortType.LENGTH -> R.string.sort_by_length
                                            AlbumSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                )
                                IconButton(
                                    onClick = { viewType = viewType.toggle() },
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when (viewType) {
                                                LibraryViewType.LIST -> R.drawable.list
                                                LibraryViewType.GRID -> R.drawable.grid_view
                                            }
                                        ),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }

                        items(
                            items = sortedAlbums,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM }
                        ) { album ->
                            AlbumGridItem(
                                album = album,
                                fillMaxWidth = true,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            Timber.d("LocalArtistScreen: [GRID] Album clicked - id=${album.id}, title=${album.album.title}")
                                            navController.navigate("local_album/${album.id}")
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
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
                }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = artist?.artist?.name ?: "",
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
