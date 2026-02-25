package com.anitail.music.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.anitail.innertube.YouTube
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalDownloadUtil
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.PlaylistSong
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.playback.DownloadUtil
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.DefaultDialog
import com.anitail.music.ui.component.DownloadFormatDialog
import com.anitail.music.ui.component.PlaylistListItem
import com.anitail.music.ui.component.TextFieldDialog
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }
    var showDownloadFormatDialog by rememberSaveable { mutableStateOf(false) }
    var isLoadingFormats by remember { mutableStateOf(false) }
    var availableFormats by remember { mutableStateOf<List<YTPlayerUtils.AudioFormatOption>>(emptyList()) }
    var pendingSongsForDownload by remember { mutableStateOf<List<Song>>(emptyList()) }

    if (showDownloadFormatDialog) {
        DownloadFormatDialog(
            isLoading = isLoadingFormats,
            formats = availableFormats,
            onFormatSelected = { format ->
                showDownloadFormatDialog = false
                downloadUtil.downloadSongsToMediaStore(pendingSongsForDownload, targetItag = format.itag)
                onDismiss()
            },
            onDismiss = { showDownloadFormatDialog = false }
        )
    }

    fun openBatchDownloadDialog(targetSongs: List<Song>) {
        val remoteSongs = targetSongs.filterNot { it.song.isLocal || it.id.startsWith("LOCAL_") }
        if (remoteSongs.isEmpty()) return

        pendingSongsForDownload = remoteSongs
        showDownloadFormatDialog = true
        isLoadingFormats = true
        availableFormats = emptyList()

        coroutineScope.launch {
            availableFormats = withContext(Dispatchers.IO) {
                YTPlayerUtils.getAllAvailableAudioFormats(remoteSongs.first().id).getOrDefault(emptyList())
            }
            isLoadingFormats = false
        }
    }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    val editable: Boolean = playlist.playlist.isEditable == true

    var mediaStoreDownloadState by remember {
        mutableStateOf<DownloadUtil.MediaStoreCollectionStatus>(DownloadUtil.MediaStoreCollectionStatus.NotDownloaded)
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) {
            mediaStoreDownloadState = DownloadUtil.MediaStoreCollectionStatus.NotDownloaded
            return@LaunchedEffect
        }
        downloadUtil.getMediaStoreCollectionStatus(songs).collect { nextStatus ->
            if (nextStatus != mediaStoreDownloadState) {
                mediaStoreDownloadState = nextStatus
            }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_playlist)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue =
            TextFieldValue(
                playlist.playlist.name,
                TextRange(playlist.playlist.name.length),
            ),
            onDone = { name ->
                onDismiss()
                database.query {
                    update(
                        playlist.playlist.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now()
                        )
                    )
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                }
            },
        )
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.playlist.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        downloadUtil.removeDownloads(songs.map { it.id })
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        database.transaction {
                            // First toggle the like using the same logic as the like button
                            if (playlist.playlist.bookmarkedAt != null) {
                                // Using the same toggleLike() method that's used in the like button
                                update(playlist.playlist.toggleLike())
                            }
                            // Then delete the playlist
                            delete(playlist.playlist)
                        }

                        coroutineScope.launch(Dispatchers.IO) {
                            playlist.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    PlaylistListItem(
        playlist = playlist,
        trailingContent = {
            if (playlist.playlist.isEditable != true) {
                IconButton(
                    onClick = {
                        database.query {
                            dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        },
    )

    HorizontalDivider()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        playlist.playlist.browseId?.let { browseId ->
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.start_radio)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        coroutineScope.launch(Dispatchers.IO) {
                            YouTube.playlist(browseId).getOrNull()?.playlist?.let { playlistItem ->
                                playlistItem.radioEndpoint?.let { radioEndpoint ->
                                    withContext(Dispatchers.Main) {
                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                    }
                                }
                            }
                        }
                        onDismiss()
                    }
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.play)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.toMediaItem() },
                        ),
                    )
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.play_next)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_play),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    coroutineScope.launch {
                        playerConnection.playNext(songs.map { it.toMediaItem() })
                    }
                    onDismiss()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    onDismiss()
                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.shuffle)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    onDismiss()
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.shuffled().map { it.toMediaItem() },
                        ),
                    )
                }
            )
        }
        if (editable && autoPlaylist != true) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.edit)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.edit),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        showEditDialog = true
                    }
                )
            }
        }
        if (downloadPlaylist != true) {
            item {
                when (mediaStoreDownloadState) {
                    is DownloadUtil.MediaStoreCollectionStatus.Completed -> {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.downloaded_to_device),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.removeDownloads(songs.map { it.id })
                                onDismiss()
                            }
                        )
                    }

                    is DownloadUtil.MediaStoreCollectionStatus.Downloading -> {
                        val progress =
                            (mediaStoreDownloadState as DownloadUtil.MediaStoreCollectionStatus.Downloading).progress
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.downloading_to_device))
                            },
                            supportingContent = {
                                Text(text = "${(progress * 100).toInt()}%")
                            },
                            leadingContent = {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.cancelMediaStoreDownloads(songs.map { it.id })
                                onDismiss()
                            }
                        )
                    }

                    is DownloadUtil.MediaStoreCollectionStatus.Failed -> {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.download_failed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(R.string.retry_download))
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.retryMediaStoreDownloads(songs.map { it.id })
                                onDismiss()
                            }
                        )
                    }

                    DownloadUtil.MediaStoreCollectionStatus.NotDownloaded -> {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                openBatchDownloadDialog(songs)
                            }
                        )
                    }
                }
            }
        }
        if (autoPlaylist != true) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.delete)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        showDeletePlaylistDialog = true
                    }
                )
            }
        }
        playlist.playlist.shareLink?.let { shareLink ->
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.share)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareLink)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        onDismiss()
                    }
                )
            }
        }
    }
}

