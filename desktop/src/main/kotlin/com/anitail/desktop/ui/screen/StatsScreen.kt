package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import com.anitail.desktop.db.relations.songHasArtist
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.ChoiceChipsRow
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.screen.library.GridThumbnailHeight
import com.anitail.desktop.ui.screen.library.SongSortType
import com.anitail.desktop.ui.screen.library.sortSongs
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val SectionSpacing = 12.dp
private val StatsThumbnailCornerRadius = 6.dp

private sealed class StatsPlaylistRequest {
    data class Single(val target: BrowseSongTarget) : StatsPlaylistRequest()
    data class Collection(val songs: List<SongEntity>) : StatsPlaylistRequest()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val songs by database.songs.collectAsState(initial = emptyList())
    val events by database.events.collectAsState(initial = emptyList())
    val artists by database.artists.collectAsState(initial = emptyList())
    val albums by database.albums.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val artistsById = remember(artists) { artists.associateBy { it.id } }
    val albumsById = remember(albums) { albums.associateBy { it.id } }
    val songsByAlbumId = remember(songs) {
        songs.filter { !it.albumId.isNullOrBlank() }.groupBy { it.albumId.orEmpty() }
    }
    val artistNamesByAlbum = remember(songsByAlbumId) {
        songsByAlbumId.mapValues { it.value.firstOrNull()?.artistName.orEmpty() }
    }

    var selectedOption by remember { mutableStateOf(OptionStats.CONTINUOUS) }
    var indexChips by remember { mutableStateOf(0) }
    var playlistRequest by remember { mutableStateOf<StatsPlaylistRequest?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }

    val firstEvent = remember(events) { events.minByOrNull { it.timestamp } }
    val currentDate = LocalDateTime.now()

    val weeklyDates = remember(currentDate, firstEvent) {
        if (firstEvent == null) {
            emptyList()
        } else {
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(firstEvent.timestamp.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")

                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)

                    val startMonth = date.month
                    val endMonth = endDate.month
                    val startYear = date.year
                    val endYear = endDate.year

                    val text = when {
                        startYear != currentDate.year -> "$startDateFormatted, $startYear - $endDateFormatted, $endYear"
                        startMonth != endMonth -> "$startDateFormatted - $endDateFormatted"
                        else -> "${date.dayOfMonth} - $endDateFormatted"
                    }
                    index to text
                }.toList()
        }
    }

    val monthlyDates = remember(currentDate, firstEvent) {
        if (firstEvent == null) {
            emptyList()
        } else {
            generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) {
                it.minusMonths(1)
            }
                .takeWhile { it.isAfter(firstEvent.timestamp.withDayOfMonth(1)) }
                .mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val formattedDate = formatter.format(date)
                    val text = if (date.year != currentDate.year) {
                        "$formattedDate ${date.year}"
                    } else {
                        formattedDate
                    }
                    index to text
                }.toList()
        }
    }

    val yearlyDates = remember(currentDate, firstEvent) {
        if (firstEvent == null) {
            emptyList()
        } else {
            generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) {
                it.minusYears(1)
            }
                .takeWhile { it.isAfter(firstEvent.timestamp) }
                .mapIndexed { index, date -> index to "${date.year}" }
                .toList()
        }
    }

    val fromMs = remember(selectedOption, indexChips, currentDate) {
        statToPeriod(selectedOption, indexChips, currentDate)
    }
    val toMs = remember(selectedOption, indexChips, currentDate) {
        if (selectedOption == OptionStats.CONTINUOUS || indexChips == 0) {
            currentDate.toInstant(ZoneOffset.UTC).toEpochMilli()
        } else {
            statToPeriod(selectedOption, indexChips - 1, currentDate)
        }
    }

    val songStats = remember(events, songsById, fromMs, toMs) {
        computeSongStats(events, songsById, fromMs, toMs)
    }
    val artistStats = remember(events, songsById, songArtistMaps, artistsById, fromMs, toMs) {
        computeArtistStats(events, songsById, songArtistMaps, artistsById, fromMs, toMs)
            .filter { it.artist.isYouTubeArtist }
    }
    val albumStats = remember(events, songsById, albumsById, fromMs, toMs) {
        computeAlbumStats(events, songsById, albumsById, fromMs, toMs)
    }

    val mostPlayedSongs = remember(songStats) { songStats.map { it.song } }
    val mostPlayedSongItems = remember(mostPlayedSongs) { mostPlayedSongs.map { it.toLibraryItem() } }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching {
            val uri = if (url.startsWith("http", ignoreCase = true)) URI(url) else File(url).toURI()
            Desktop.getDesktop().browse(uri)
        }
    }

    suspend fun ensureSongInDatabase(target: BrowseSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }
    playlistRequest?.let { request ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name, createdAt = LocalDateTime.now())
                scope.launch {
                    database.insertPlaylist(playlist)
                    when (request) {
                        is StatsPlaylistRequest.Single -> {
                            ensureSongInDatabase(request.target)
                            database.addSongToPlaylist(playlist.id, request.target.item.id)
                        }
                        is StatsPlaylistRequest.Collection -> {
                            request.songs.forEach { song ->
                                database.addSongToPlaylist(playlist.id, song.id)
                            }
                        }
                    }
                }
                playlistRequest = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    when (request) {
                        is StatsPlaylistRequest.Single -> {
                            ensureSongInDatabase(request.target)
                            database.addSongToPlaylist(playlist.id, request.target.item.id)
                        }
                        is StatsPlaylistRequest.Collection -> {
                            request.songs.forEach { song ->
                                database.addSongToPlaylist(playlist.id, song.id)
                            }
                        }
                    }
                }
                playlistRequest = null
            },
            onDismiss = { playlistRequest = null },
        )
    }

    pendingArtists?.let { artistsList ->
        ArtistPickerDialog(
            visible = true,
            artists = artistsList,
            onSelect = { artist ->
                val artistId = artist.id ?: return@ArtistPickerDialog
                onOpenArtist(artistId, artist.name)
                pendingArtists = null
            },
            onDismiss = { pendingArtists = null },
        )
    }

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Spacer(modifier = Modifier.height(SectionSpacing)) }

            item {
                ChoiceChipsRow(
                    chips = when (selectedOption) {
                        OptionStats.WEEKS -> weeklyDates
                        OptionStats.MONTHS -> monthlyDates
                        OptionStats.YEARS -> yearlyDates
                        OptionStats.CONTINUOUS -> listOf(
                            StatPeriod.WEEK_1.ordinal to pluralStringResource("n_week", 1, 1),
                            StatPeriod.MONTH_1.ordinal to pluralStringResource("n_month", 1, 1),
                            StatPeriod.MONTH_3.ordinal to pluralStringResource("n_month", 3, 3),
                            StatPeriod.MONTH_6.ordinal to pluralStringResource("n_month", 6, 6),
                            StatPeriod.YEAR_1.ordinal to pluralStringResource("n_year", 1, 1),
                            StatPeriod.ALL.ordinal to stringResource("filter_all"),
                        )
                    },
                    options = listOf(
                        OptionStats.CONTINUOUS to stringResource("continuous"),
                        OptionStats.WEEKS to stringResource("weeks"),
                        OptionStats.MONTHS to stringResource("months"),
                        OptionStats.YEARS to stringResource("years"),
                    ),
                    selectedOption = selectedOption,
                    onSelectionChange = {
                        selectedOption = it
                        indexChips = 0
                    },
                    currentValue = indexChips,
                    onValueUpdate = { indexChips = it },
                )
            }

            item(key = "mostPlayedSongs") {
                NavigationTitle(
                    title = "${songStats.size} ${stringResource("songs")}",
                )

                LazyRow {
                    itemsIndexed(
                        items = songStats,
                        key = { _, entry -> entry.song.id },
                    ) { index, entry ->
                        val song = entry.song
                        val menuExpanded = remember(song.id) { mutableStateOf(false) }
                        val libraryItem = song.toLibraryItem()
                        val menuActions = remember(
                            song.id,
                            songArtistMaps,
                            downloadStates,
                            downloadedSongs,
                        ) {
                            buildBrowseSongMenuActions(
                                strings = strings,
                                libraryItem = libraryItem,
                                songItem = null,
                                songsById = songsById,
                                songArtistMaps = songArtistMaps,
                                downloadStates = downloadStates,
                                downloadedSongs = downloadedSongs,
                                database = database,
                                downloadService = downloadService,
                                playerState = playerState,
                                coroutineScope = scope,
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                                onRequestPlaylist = { playlistRequest = StatsPlaylistRequest.Single(it) },
                                onRequestArtists = { pendingArtists = it },
                                onShowDetails = { detailsItem = it },
                                copyToClipboard = ::copyToClipboard,
                            )
                        }
                        val subtitle = joinByBullet(
                            pluralStringResource("n_time", entry.songCountListened, entry.songCountListened),
                            makeTimeString(entry.timeListened),
                        ).orEmpty()

                        Box {
                            StatsGridItem(
                                title = "${index + 1}. ${song.title}",
                                subtitle = subtitle,
                                thumbnailUrl = song.thumbnailUrl,
                                shape = RoundedCornerShape(StatsThumbnailCornerRadius),
                                isActive = song.id == playerState.currentItem?.id,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (song.id == playerState.currentItem?.id) {
                                            playerState.togglePlayPause()
                                        } else {
                                            playerState.playQueue(mostPlayedSongItems, startIndex = index)
                                        }
                                    },
                                    onLongClick = { menuExpanded.value = true },
                                ),
                            )

                            ItemContextMenu(
                                expanded = menuExpanded.value,
                                onDismiss = { menuExpanded.value = false },
                                item = libraryItem,
                                actions = menuActions,
                                headerContent = {
                                    SongMenuHeader(
                                        title = song.title,
                                        subtitle = subtitle,
                                        thumbnailUrl = song.thumbnailUrl,
                                        isLiked = song.liked,
                                        onToggleLike = { scope.launch { database.toggleSongLike(song.id) } },
                                        onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                        onAddToPlaylist = { playlistRequest = StatsPlaylistRequest.Single(BrowseSongTarget(libraryItem, null)) },
                                        onShare = { copyToClipboard(libraryItem.playbackUrl) },
                                        onDismiss = { menuExpanded.value = false },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item(key = "mostPlayedArtists") {
                NavigationTitle(
                    title = "${artistStats.size} ${stringResource("artists")}",
                )

                LazyRow {
                    itemsIndexed(
                        items = artistStats,
                        key = { _, entry -> entry.artist.id },
                    ) { index, entry ->
                        val artist = entry.artist
                        val menuExpanded = remember(artist.id) { mutableStateOf(false) }
                        val menuActions = remember(artist.id, artist.bookmarkedAt) {
                            buildBrowseArtistMenuActions(
                                strings = strings,
                                isSubscribed = artist.bookmarkedAt != null,
                                onToggleSubscribe = { scope.launch { database.toggleArtistBookmark(artist.id) } },
                            )
                        }
                        val subtitle = joinByBullet(
                            pluralStringResource("n_time", entry.songCount, entry.songCount),
                            makeTimeString(entry.timeListened),
                        ).orEmpty()

                        Box {
                            StatsGridItem(
                                title = "${index + 1}. ${artist.name}",
                                subtitle = subtitle,
                                thumbnailUrl = artist.thumbnailUrl,
                                shape = CircleShape,
                                isActive = false,
                                isPlaying = false,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onOpenArtist(artist.id, artist.name) },
                                    onLongClick = { menuExpanded.value = true },
                                ),
                            )

                            ItemContextMenu(
                                expanded = menuExpanded.value,
                                onDismiss = { menuExpanded.value = false },
                                item = artist.toLibraryItem(),
                                actions = menuActions,
                                headerContent = {
                                    StatsArtistMenuHeader(
                                        artist = artist,
                                        songCount = entry.songCount,
                                        onPlay = {
                                            val queue = buildArtistQueue(artist, songs, songArtistMaps)
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = false
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShuffle = {
                                            val queue = buildArtistQueue(artist, songs, songArtistMaps).shuffled()
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = true
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShare = { copyToClipboard("https://music.youtube.com/channel/${artist.id}") },
                                        onDismiss = { menuExpanded.value = false },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = "${albumStats.size} ${stringResource("albums")}",
                )

                if (albumStats.isNotEmpty()) {
                    LazyRow {
                        itemsIndexed(
                            items = albumStats,
                            key = { _, entry -> entry.album.id },
                        ) { index, entry ->
                            val album = entry.album
                            val menuExpanded = remember(album.id) { mutableStateOf(false) }
                            val albumSongs = songsByAlbumId[album.id].orEmpty()
                            val menuActions = remember(
                                album.id,
                                albumSongs,
                                songArtistMaps,
                                downloadStates,
                                downloadedSongs,
                            ) {
                                buildStatsAlbumMenuActions(
                                    strings = strings,
                                    album = album,
                                    albumSongs = albumSongs,
                                    songArtistMaps = songArtistMaps,
                                    downloadStates = downloadStates,
                                    downloadedSongs = downloadedSongs,
                                    playerState = playerState,
                                    downloadService = downloadService,
                                    onRequestAddToPlaylist = { playlistRequest = StatsPlaylistRequest.Collection(albumSongs) },
                                    onOpenArtist = onOpenArtist,
                                    onShare = { copyToClipboard(album.toLibraryItem().playbackUrl) },
                                    scope = scope,
                                )
                            }
                            val subtitle = joinByBullet(
                                pluralStringResource("n_time", entry.songCountListened, entry.songCountListened),
                                makeTimeString(entry.timeListened),
                            ).orEmpty()

                            Box {
                                StatsGridItem(
                                    title = "${index + 1}. ${album.title}",
                                    subtitle = subtitle,
                                    thumbnailUrl = album.thumbnailUrl,
                                    shape = RoundedCornerShape(StatsThumbnailCornerRadius),
                                    isActive = false,
                                    isPlaying = false,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onOpenAlbum(album.id, album.title) },
                                        onLongClick = { menuExpanded.value = true },
                                    ),
                                )

                                ItemContextMenu(
                                    expanded = menuExpanded.value,
                                    onDismiss = { menuExpanded.value = false },
                                    item = album.toLibraryItem(),
                                    actions = menuActions,
                                    headerContent = {
                                        CollectionMenuHeader(
                                            title = album.title,
                                            subtitle = joinByBullet(
                                                artistNamesByAlbum[album.id],
                                                album.year?.toString(),
                                            ).orEmpty(),
                                            thumbnailUrl = album.thumbnailUrl,
                                            showLike = true,
                                            isLiked = album.bookmarkedAt != null,
                                            onToggleLike = { scope.launch { database.toggleAlbumBookmark(album.id) } },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = listState,
                onClick = {
                    val shuffled = mostPlayedSongItems.shuffled()
                    playerState.shuffleEnabled = true
                    playerState.playQueue(shuffled, 0)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )
        }

        TopAppBar(
            title = { Text(stringResource("stats")) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = IconAssets.arrowBack(), contentDescription = stringResource("back"))
                }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun StatsGridItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    shape: androidx.compose.ui.graphics.Shape,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(GridThumbnailHeight)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GridThumbnailHeight)
                .clip(shape),
        ) {
            RemoteImage(
                url = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
            )
            PlayingIndicatorBox(
                isActive = isActive,
                isPlaying = isPlaying,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatsArtistMenuHeader(
    artist: com.anitail.desktop.db.entities.ArtistEntity,
    songCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            RemoteImage(
                url = artist.thumbnailUrl,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp, bottom = 4.dp),
        ) {
            if (artist.bookmarkedAt != null) {
                Icon(
                    imageVector = IconAssets.favorite(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp),
                )
            }
            Text(
                text = pluralStringResource("n_song", songCount, songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val canPlay = songCount > 0
        val canShare = artist.isYouTubeArtist
        if (canPlay || canShare) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canPlay) {
                    StatsMenuQuickActionButton(
                        label = stringResource("play"),
                        icon = IconAssets.play(),
                        onClick = {
                            onPlay()
                            onDismiss()
                        },
                    )
                    StatsMenuQuickActionButton(
                        label = stringResource("shuffle"),
                        icon = IconAssets.shuffle(),
                        onClick = {
                            onShuffle()
                            onDismiss()
                        },
                    )
                }
                if (canShare) {
                    StatsMenuQuickActionButton(
                        label = stringResource("share"),
                        icon = IconAssets.share(),
                        onClick = {
                            onShare()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatsMenuQuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun buildArtistQueue(
    artist: com.anitail.desktop.db.entities.ArtistEntity,
    songs: List<SongEntity>,
    songArtistMaps: List<SongArtistMap>,
): List<LibraryItem> {
    val artistSongs = songs.filter { song ->
        when {
            songHasArtist(song.id, artist.id, songArtistMaps) -> true
            !song.artistName.isNullOrBlank() -> song.artistName.equals(artist.name, ignoreCase = true)
            else -> false
        }
    }
    val sorted = sortSongs(artistSongs, SongSortType.CREATE_DATE, descending = true)
    return sorted.map { it.toLibraryItem() }
}

private fun buildStatsAlbumMenuActions(
    strings: com.anitail.desktop.i18n.StringResolver,
    album: com.anitail.desktop.db.entities.AlbumEntity,
    albumSongs: List<SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    playerState: PlayerState,
    downloadService: DesktopDownloadService,
    onRequestAddToPlaylist: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onShare: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): List<ContextMenuAction> {
    val downloadMenu = resolveCollectionDownloadMenuState(
        strings = strings,
        songIds = albumSongs.map { it.id },
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
        showWhenEmpty = true,
    ) ?: CollectionDownloadMenuState(label = strings.get("download"), action = CollectionDownloadAction.DOWNLOAD)
    val artistId = albumSongs
        .asSequence()
        .mapNotNull { song -> primaryArtistIdForSong(song.id, songArtistMaps) }
        .firstOrNull()
    val artistName = albumSongs.firstOrNull { !it.artistName.isNullOrBlank() }?.artistName

    return buildBrowseAlbumMenuActions(
        strings = strings,
        hasArtists = !artistId.isNullOrBlank(),
        downloadLabel = downloadMenu.label,
        downloadEnabled = true,
        onStartRadio = {
            val seed = albumSongs.firstOrNull() ?: return@buildBrowseAlbumMenuActions
            scope.launch {
                val result = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull() ?: return@launch
                val plan = buildRadioQueuePlan(seed.toLibraryItem(), result)
                playerState.playQueue(plan.items, plan.startIndex)
            }
        },
        onPlayNext = {
            albumSongs.asReversed().map { it.toLibraryItem() }.forEach { playerState.addToQueue(it, playNext = true) }
        },
        onAddToQueue = {
            albumSongs.map { it.toLibraryItem() }.forEach { playerState.addToQueue(it) }
        },
        onAddToPlaylist = onRequestAddToPlaylist,
        onDownload = {
            scope.launch {
                when (downloadMenu.action) {
                    CollectionDownloadAction.DOWNLOAD -> {
                        albumSongs.forEach { song ->
                            downloadService.downloadSong(
                                songId = song.id,
                                title = song.title,
                                artist = song.artistName.orEmpty(),
                                album = song.albumName,
                                thumbnailUrl = song.thumbnailUrl,
                                duration = song.duration,
                            )
                        }
                    }
                    CollectionDownloadAction.CANCEL -> {
                        albumSongs.forEach { song -> downloadService.cancelDownload(song.id) }
                    }
                    CollectionDownloadAction.REMOVE -> {
                        albumSongs.forEach { song -> downloadService.deleteDownload(song.id) }
                    }
                }
            }
        },
        onOpenArtist = {
            if (!artistId.isNullOrBlank()) {
                onOpenArtist(artistId, artistName)
            }
        },
        onShare = onShare,
    )
}

private fun joinByBullet(vararg parts: String?): String? {
    val items = parts.filterNot { it.isNullOrBlank() }
    return if (items.isEmpty()) null else items.joinToString(separator = " • ")
}

private fun makeTimeString(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0) return ""
    var sec = durationMs / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}
