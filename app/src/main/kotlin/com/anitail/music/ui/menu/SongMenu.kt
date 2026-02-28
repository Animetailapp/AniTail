package com.anitail.music.ui.menu

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.innertube.YouTube
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalDownloadUtil
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.LocalSyncUtils
import com.anitail.music.R
import com.anitail.music.constants.ListItemHeight
import com.anitail.music.constants.ListThumbnailSize
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.Event
import com.anitail.music.db.entities.PlaylistSong
import com.anitail.music.db.entities.Song
import com.anitail.music.db.entities.SongArtistMap
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.ui.component.DownloadFormatDialog
import com.anitail.music.ui.component.ListDialog
import com.anitail.music.ui.component.LocalBottomSheetPageState
import com.anitail.music.ui.component.SongListItem
import com.anitail.music.ui.component.TextFieldDialog
import com.anitail.music.ui.utils.ShowMediaInfo
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.viewmodels.CachePlaylistViewModel
import com.anitail.music.viewmodels.LastFmViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil.getDownload(originalSong.id)
        .collectAsState(initial = null)
    val effectiveDownloadState = download?.state
        ?: if (!song.song.isLocal && !song.song.mediaStoreUri.isNullOrEmpty()) {
            Download.STATE_COMPLETED
        } else {
            null
        }
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }
    val isLocalSong = song.song.isLocal

    val cacheViewModel = viewModel<CachePlaylistViewModel>()
    val lastFmViewModel: LastFmViewModel = hiltViewModel()
    val lastFmUiState by lastFmViewModel.uiState.collectAsStateWithLifecycle()
    var showDownloadFormatDialog by rememberSaveable { mutableStateOf(false) }
    var isLoadingFormats by remember { mutableStateOf(false) }
    var availableFormats by remember { mutableStateOf<List<YTPlayerUtils.AudioFormatOption>>(emptyList()) }

    if (showDownloadFormatDialog) {
        DownloadFormatDialog(
            isLoading = isLoadingFormats,
            formats = availableFormats,
            onFormatSelected = { format ->
                showDownloadFormatDialog = false
                downloadUtil.downloadToMediaStore(song, targetItag = format.itag)
                Toast.makeText(context, R.string.downloading, Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { showDownloadFormatDialog = false },
        )
    }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    val orderedArtists by produceState(initialValue = emptyList<ArtistEntity>(), song) {
        withContext(Dispatchers.IO) {
            val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
            val sorted = artistMaps.mapNotNull { map ->
                song.artists.firstOrNull { it.id == map.artistId }
            }
            value = sorted
        }
    }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> = Saver(
        save = { it.text },
        restore = { text -> TextFieldValue(text, TextRange(text.length)) }
    )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(orderedArtists, stateSaver = TextFieldValueSaver) {
        mutableStateOf(
            TextFieldValue(
                orderedArtists.joinToString(", ") { it.name }
            )
        )
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields = listOf(
                stringResource(R.string.song_title) to titleField,
                stringResource(R.string.artist_name) to artistField
            ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) titleField = newValue
                else artistField = newValue
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    val newArtistNames = newArtist
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val oldArtistMaps = withContext(Dispatchers.IO) {
                        database.songArtistMap(song.id).sortedBy { it.position }
                    }

                    val oldNames = oldArtistMaps.mapIndexed { index, map ->
                        index to song.artists.firstOrNull { it.id == map.artistId }?.name.orEmpty()
                    }

                    database.transaction {
                        update(song.song.copy(title = newTitle, artistName = newArtist))

                        val namesChanged = oldNames.size != newArtistNames.size ||
                            oldNames.any { it.second != newArtistNames.getOrNull(it.first) }

                        if (namesChanged) {
                            openHelper.writableDatabase.execSQL(
                                "DELETE FROM song_artist_map WHERE songId = ?", arrayOf(song.id)
                            )

                            newArtistNames.forEachIndexed { index, name ->
                                val existingArtist = artistByName(name)
                                val artistId = existingArtist?.id ?: ArtistEntity.generateArtistId()

                                if (existingArtist == null) {
                                    insert(ArtistEntity(id = artistId, name = name))
                                }

                                insert(SongArtistMap(songId = song.id, artistId = artistId, position = index))
                            }
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            listOf(song.id)
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
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.tvClickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(listOf(song)) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = song.artists,
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
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
                            modifier = Modifier
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    SongListItem(
        song = song,
        badges = {},
        trailingContent = {
            val isFavorite =
                if (song.song.isEpisode) {
                    song.song.inLibrary != null
                } else {
                    song.song.liked
                }
            IconButton(
                onClick = {
                    val s = song.song.toggleLike()
                    database.query {
                        update(s)
                     }

                    if (!s.isEpisode) {
                        syncUtils.likeSong(s)
                        
                        // Last.fm integration
                        if (lastFmUiState.isLoggedIn && lastFmUiState.isLoveTracksEnabled) {
                            if (s.liked) {
                                lastFmViewModel.loveTrack(song)
                            } else {
                                lastFmViewModel.unloveTrack(song)
                            }
                        }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null,
                )
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current

    // Row for "Edit", "Add to playlist", and "Share" buttons with grid-like background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        // Edit button
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .tvClickable {
                    showEditDialog = true
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.edit),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }

        // Add to playlist button
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .tvClickable {
                    showChoosePlaylistDialog = true
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.playlist_add),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }

        if (!isLocalSong) {
            // Share button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .tvClickable {
                        onDismiss()
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
    }

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
                headlineContent = { Text(text = stringResource(R.string.start_radio)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    onDismiss()
                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                    onDismiss()
                    playerConnection.playNext(song.toMediaItem())
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
                    playerConnection.addToQueue(song.toMediaItem())
                }
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(
                            if (song.song.inLibrary == null) R.string.add_to_library
                            else R.string.remove_from_library
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (song.song.inLibrary == null) R.drawable.library_add
                            else R.drawable.library_add_check
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    database.query {
                        update(song.song.toggleLibrary())
                    }
                }
            )
        }
        if (event != null) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.remove_from_history)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        onDismiss()
                        database.query {
                            delete(event)
                        }
                    }
                )
            }
        }
        if (playlistSong != null) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.remove_from_playlist)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        database.transaction {
                            coroutineScope.launch {
                                playlistBrowseId?.let { playlistId ->
                                    if (playlistSong.map.setVideoId != null) {
                                        YouTube.removeFromPlaylist(
                                            playlistId, playlistSong.map.songId, playlistSong.map.setVideoId
                                        )
                                    }
                                }
                            }
                            move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE)
                            delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                        }
                        onDismiss()
                    }
                )
            }
        }
        if (isFromCache) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.remove_from_cache)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        onDismiss()
                        cacheViewModel.removeSongFromCache(song.id)
                    }
                )
            }
        }
        if (!isLocalSong) {
            item {
                when (effectiveDownloadState) {
                    Download.STATE_COMPLETED -> {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_download),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.removeDownload(song.id)
                            }
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.swap_download)) },
                            supportingContent = { Text(text = stringResource(R.string.swap_download_desc)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.removeDownload(song.id)
                                showDownloadFormatDialog = true
                                isLoadingFormats = true
                                availableFormats = emptyList()
                                coroutineScope.launch {
                                    val formats = withContext(Dispatchers.IO) {
                                        YTPlayerUtils.getAllAvailableAudioFormats(song.id)
                                            .getOrElse {
                                                Timber.tag("SongMenu").e(it, "Failed to load download formats")
                                                emptyList()
                                            }
                                    }
                                    availableFormats = formats
                                    isLoadingFormats = false
                                }
                            }
                        )
                    }

                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.downloading)) },
                            leadingContent = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                downloadUtil.removeDownload(song.id)
                            }
                        )
                    }

                    else -> {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.tvClickable {
                                showDownloadFormatDialog = true
                                isLoadingFormats = true
                                availableFormats = emptyList()
                                coroutineScope.launch {
                                    val formats = withContext(Dispatchers.IO) {
                                        YTPlayerUtils.getAllAvailableAudioFormats(song.id)
                                            .getOrElse {
                                                Timber.tag("SongMenu").e(it, "Failed to load download formats")
                                                emptyList()
                                            }
                                    }
                                    availableFormats = formats
                                    isLoadingFormats = false
                                }
                            }
                        )
                    }
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
                    if (song.artists.size == 1) {
                        navController.navigate("artist/${song.artists[0].id}")
                        onDismiss()
                    } else {
                        showSelectArtistDialog = true
                    }
                }
            )
        }
        if (song.song.albumId != null) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.view_album)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.tvClickable {
                        onDismiss()
                        navController.navigate("album/${song.song.albumId}")
                    }
                )
            }
        }
        if (!isLocalSong) {
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
                            YouTube.queue(listOf(song.id)).onSuccess {
                                val newSong = it.firstOrNull()
                                if (newSong != null) {
                                    database.transaction {
                                        update(song, newSong.toMediaMetadata())
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.details)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.tvClickable {
                    onDismiss()
                    bottomSheetPageState.show {
                        ShowMediaInfo(song.id)
                    }
                }
            )
        }
    }
}
