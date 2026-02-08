package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.constants.PlayerHorizontalPadding
import com.anitail.desktop.constants.PlayerQueueCollapsedHeight
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
import com.anitail.desktop.storage.SliderStyle
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.AudioSettingsDialog
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.LyricsPanel
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.SleepTimerDialog
import com.anitail.desktop.ui.component.TempoPitchDialog
import com.anitail.desktop.ui.component.collapsedAnchor
import com.anitail.desktop.ui.component.rememberBottomSheetState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.NextResult
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun PlayerScreen(
    item: LibraryItem?,
    playerState: PlayerState,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    onOpenArtist: (String, String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onCollapsePlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        EmptyPlayerState(modifier = modifier)
        return
    }

    val preferences = remember { DesktopPreferences.getInstance() }
    val showLyrics by preferences.showLyrics.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()

    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val editablePlaylists = remember(playlists) { playlists.filter { it.isEditable } }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showArtistPickerDialog by remember { mutableStateOf(false) }

    var nextResult by remember(item.id) { mutableStateOf<NextResult?>(null) }
    var songDetails by remember(item.id) { mutableStateOf<SongItem?>(null) }

    val isLiked = songsById[item.id]?.liked == true
    val downloadState = computeSelectionDownloadState(
        selectedIds = listOf(item.id),
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
    )

    fun resolveSongDetails(result: NextResult?): SongItem? {
        result ?: return null
        val byCurrentIndex = result.currentIndex?.let { index -> result.items.getOrNull(index) }
        return byCurrentIndex ?: result.items.firstOrNull { it.id == item.id } ?: result.items.firstOrNull()
    }

    LaunchedEffect(item.id) {
        nextResult = YouTube.next(WatchEndpoint(videoId = item.id)).getOrNull()
        songDetails = resolveSongDetails(nextResult)
    }

    val artistsWithId = remember(songDetails) {
        songDetails?.artists?.filter { it.id != null } ?: emptyList()
    }
    val album = songDetails?.album
    val artistPairs = remember(artistsWithId) {
        artistsWithId.map { artist -> artist.id.orEmpty() to artist.name }
    }
    val albumInfo = album?.let { it.id to it.name }

    suspend fun ensureSongInDatabase(target: LibraryItem) {
        if (!songsById.containsKey(target.id)) {
            database.insertSong(target.toSongEntity())
        }
    }

    fun addToPlaylist(playlistId: String) {
        coroutineScope.launch {
            ensureSongInDatabase(item)
            database.addSongToPlaylist(playlistId, item.id)
        }
    }

    fun toggleLike(like: Boolean) {
        coroutineScope.launch {
            ensureSongInDatabase(item)
            val currentlyLiked = songsById[item.id]?.liked == true
            if (like != currentlyLiked) {
                database.toggleSongLike(item.id)
            }
        }
    }

    fun downloadItem() {
        val durationSec = ((item.durationMs ?: 0L) / 1000L).toInt()
        downloadService.downloadSong(
            songId = item.id,
            title = item.title,
            artist = item.artist,
            album = null,
            thumbnailUrl = item.artworkUrl,
            duration = durationSec,
        )
    }

    fun removeDownload() {
        downloadService.deleteDownload(item.id)
    }

    fun copyLink(url: String) {
        if (url.isNotBlank()) {
            clipboard.setText(AnnotatedString(url))
        }
    }

    fun openInBrowser(url: String) {
        if (url.isBlank()) return
        if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(URI(url)) }
        }
    }

    val shareUrl = if (item.id.isNotBlank()) {
        "https://music.youtube.com/watch?v=${item.id}"
    } else {
        item.playbackUrl
    }

    suspend fun loadNextResult(): NextResult? {
        val cached = nextResult
        if (cached != null) return cached
        val fetched = YouTube.next(WatchEndpoint(videoId = item.id)).getOrNull()
        if (fetched != null) {
            nextResult = fetched
            if (songDetails == null) {
                songDetails = resolveSongDetails(fetched)
            }
        }
        return fetched
    }

    fun startRadio() {
        coroutineScope.launch {
            val result = loadNextResult() ?: return@launch
            val plan = buildRadioQueuePlan(item, result)
            playerState.playQueue(plan.items, plan.startIndex)
            if (songDetails == null) {
                songDetails = plan.currentSong
            }
        }
    }

    fun openArtist(artistId: String, artistName: String) {
        onOpenArtist(artistId, artistName)
        onCollapsePlayer()
    }

    fun openAlbum(albumId: String, albumName: String) {
        onOpenAlbum(albumId, albumName)
        onCollapsePlayer()
    }

    val backgroundColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val textBackgroundColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) Color.White else MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.BLUR -> Color.White
    }
    val mutedTextColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> if (pureBlack) {
            Color.White.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.BLUR -> Color.White.copy(alpha = 0.7f)
    }
    val iconBackgroundColor = when (playerBackgroundStyle) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
        PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.BLUR -> Color.Black
    }
    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT -> textBackgroundColor to iconBackgroundColor
        PlayerButtonsStyle.SECONDARY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        PlayerBackgroundLayer(
            artworkUrl = item.artworkUrl,
            style = playerBackgroundStyle,
            pureBlack = pureBlack,
            showLyrics = showLyrics,
            modifier = Modifier.fillMaxSize(),
        )

        val queueSheetState = rememberBottomSheetState(
            dismissedBound = PlayerQueueCollapsedHeight,
            collapsedBound = PlayerQueueCollapsedHeight,
            expandedBound = maxHeight,
            initialAnchor = collapsedAnchor,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = PlayerQueueCollapsedHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerNowPlayingHeader(
                title = "Reproduciendo",
                subtitle = item.artist.ifBlank { "Cola actual" },
                textColor = textBackgroundColor,
                subtitleColor = mutedTextColor,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics) {
                    LyricsPanel(
                        title = item.title,
                        artist = item.artist,
                        durationSec = ((item.durationMs ?: 0L) / 1000L).toInt(),
                        currentPositionMs = playerState.position,
                        onSeek = { playerState.seekTo(it) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = PlayerHorizontalPadding),
                    )
                } else {
                    RemoteImage(
                        url = item.artworkUrl ?: "",
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .aspectRatio(1f)
                            .shadow(16.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }

            PlayerControls(
                item = item,
                playerState = playerState,
                isLiked = isLiked,
                textColor = textBackgroundColor,
                mutedTextColor = mutedTextColor,
                sliderStyle = sliderStyle,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                onToggleLike = { toggleLike(!isLiked) },
                onAddToPlaylist = { showPlaylistDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        PlayerQueueSheet(
            state = queueSheetState,
            playerState = playerState,
            currentItem = item,
            showLyrics = showLyrics,
            onToggleLyrics = { preferences.setShowLyrics(it) },
            onStartRadio = { startRadio() },
            artistsWithId = artistPairs,
            albumInfo = albumInfo,
            onOpenArtist = { artistId, artistName -> openArtist(artistId, artistName) },
            onShowArtistPicker = { showArtistPickerDialog = true },
            onOpenAlbum = { albumId, albumName -> openAlbum(albumId, albumName) },
            onShowDetailsDialog = { showDetailsDialog = true },
            onShowSleepTimerDialog = { showSleepTimerDialog = true },
            onShowAudioSettingsDialog = { showAudioSettingsDialog = true },
            onShowAdvancedDialog = { showAdvancedDialog = true },
            pureBlack = pureBlack,
            textColor = textBackgroundColor,
            mutedTextColor = mutedTextColor,
            database = database,
            downloadService = downloadService,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showPlaylistDialog) {
        PlaylistPickerDialog(
            visible = true,
            playlists = editablePlaylists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                coroutineScope.launch {
                    database.insertPlaylist(playlist)
                    addToPlaylist(playlist.id)
                }
                showPlaylistDialog = false
            },
            onSelectPlaylist = { playlist ->
                addToPlaylist(playlist.id)
                showPlaylistDialog = false
            },
            onDismiss = { showPlaylistDialog = false },
        )
    }

    SleepTimerDialog(
        visible = showSleepTimerDialog,
        isActive = playerState.isSleepTimerActive,
        timeLeftMs = playerState.sleepTimerTimeLeftMs,
        onStartTimer = { durationMs ->
            playerState.startSleepTimer(durationMs)
        },
        onStartEndOfSong = {
            val remaining = (playerState.duration - playerState.position).coerceAtLeast(0L)
            playerState.startSleepTimer(remaining)
        },
        onCancelTimer = { playerState.cancelSleepTimer() },
        onDismiss = { showSleepTimerDialog = false },
    )

    AudioSettingsDialog(
        visible = showAudioSettingsDialog,
        volume = playerState.volume,
        onVolumeChange = { playerState.updateVolume(it) },
        onDismiss = { showAudioSettingsDialog = false },
    )

    TempoPitchDialog(
        visible = showAdvancedDialog,
        tempo = playerState.tempo,
        pitchSemitone = playerState.pitchSemitone,
        onTempoChange = { playerState.updateTempo(it) },
        onPitchChange = { playerState.updatePitchSemitone(it) },
        onReset = { playerState.resetTempoPitch() },
        onDismiss = { showAdvancedDialog = false },
    )

    ArtistPickerDialog(
        visible = showArtistPickerDialog,
        artists = artistsWithId,
        onSelect = { artist ->
            openArtist(artist.id.orEmpty(), artist.name)
            showArtistPickerDialog = false
        },
        onDismiss = { showArtistPickerDialog = false },
    )

    MediaDetailsDialog(
        visible = showDetailsDialog,
        item = item,
        onCopyLink = { copyLink(shareUrl) },
        onOpenInBrowser = { openInBrowser(shareUrl) },
        onDismiss = { showDetailsDialog = false },
    )
}

@Composable
private fun PlayerNowPlayingHeader(
    title: String,
    subtitle: String,
    textColor: Color,
    subtitleColor: Color,
    trailingContent: @Composable () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
            .padding(top = 40.dp, bottom = 16.dp),
    ) {
        Spacer(modifier = Modifier.size(40.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
        Box(modifier = Modifier.size(40.dp)) {
            trailingContent()
        }
    }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = IconAssets.musicNote(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay nada reproduciendose",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
