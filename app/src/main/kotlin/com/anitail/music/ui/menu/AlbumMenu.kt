package com.anitail.music.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.innertube.YouTube
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalDownloadUtil
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.ListItemHeight
import com.anitail.music.constants.ListThumbnailSize
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Song
import com.anitail.music.playback.DownloadUtil
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.ui.component.AlbumListItem
import com.anitail.music.ui.component.DownloadFormatDialog
import com.anitail.music.ui.component.ListDialog
import com.anitail.music.ui.component.ListItem
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.YTPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AlbumMenu(
    originalAlbum: Album,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val libraryAlbum by database.album(originalAlbum.id).collectAsState(initial = originalAlbum)
    val album = libraryAlbum ?: originalAlbum
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    val coroutineScope = rememberCoroutineScope()
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
        database.albumSongs(album.id).collect {
            songs = it
        }
    }

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

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { playlistId ->
                    album.album.playlistId?.let { addPlaylistId ->
                        YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
                    }
                }
            }
            songs.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.already_in_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier =
                    Modifier
                        .tvClickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                SongListItem(song = song)
            }
        }
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = album.artists,
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .height(ListItemHeight)
                        .tvClickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    AlbumListItem(
        album = album,
        showLikedIcon = false,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        update(album.album.toggleLike())
                    }
                },
            ) {
                Icon(
                    painter = painterResource(if (album.album.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (album.album.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null,
                )
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
                    onDismiss()
                    playerConnection.playNext(songs.map { it.toMediaItem() })
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
                headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_add),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    showChoosePlaylistDialog = true
                }
            )
        }
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
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.artist),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    if (album.artists.size == 1) {
                        navController.navigate("artist/${album.artists[0].id}")
                        onDismiss()
                    } else {
                        showSelectArtistDialog = true
                    }
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.refetch)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                    )
                },
                modifier = Modifier.tvClickable {
                    refetchIconDegree -= 360
                    scope.launch(Dispatchers.IO) {
                        YouTube.album(album.id).onSuccess {
                            database.transaction {
                                update(album.album, it, album.artists)
                            }
                        }
                    }
                }
            )
        }
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
                    onDismiss()
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://music.youtube.com/browse/${album.album.id}"
                            )
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            )
        }
    }
}

