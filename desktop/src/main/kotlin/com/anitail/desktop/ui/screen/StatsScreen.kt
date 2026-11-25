package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
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
import com.anitail.desktop.ui.component.rememberExpressiveAvatarShape
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
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

private val SectionSpacing = 12.dp
private val StatsThumbnailCornerRadius = 6.dp

private sealed class StatsPlaylistRequest {
    data class Single(val target: BrowseSongTarget) : StatsPlaylistRequest()
    data class Collection(val songs: List<SongEntity>) : StatsPlaylistRequest()
}

private enum class StatsViewMode {
    CAPSULE,
    EXPLORER,
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
    val currentDate = remember(events.size) { LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) }

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
    val periodInsights = remember(events, songsById, songArtistMaps, fromMs, toMs) {
        computePeriodInsights(
            events = events,
            songsById = songsById,
            songArtistMaps = songArtistMaps,
            fromTimeMs = fromMs,
            toTimeMs = toMs,
        )
    }
    val capsuleOverview = remember(events, songsById, songArtistMaps, artistsById) {
        buildSoundCapsuleOverview(
            events = events,
            songsById = songsById,
            songArtistMaps = songArtistMaps,
            artistsById = artistsById,
        )
    }
    var viewMode by remember { mutableStateOf(StatsViewMode.EXPLORER) }
    var capsuleSubView by remember { mutableStateOf<CapsuleSubView>(CapsuleSubView.Overview) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = if (viewMode == StatsViewMode.CAPSULE) {
                        stringResource("sound_capsule_title")
                    } else {
                        stringResource("stats")
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (viewMode == StatsViewMode.CAPSULE && capsuleSubView !is CapsuleSubView.Overview) {
                            capsuleSubView = CapsuleSubView.Overview
                        } else {
                            onBack()
                        }
                    },
                ) {
                    Icon(imageVector = IconAssets.arrowBack(), contentDescription = stringResource("back"))
                }
            },
            actions = {
                StatsModeToggle(
                    selectedMode = viewMode,
                    onSelectMode = { mode ->
                        viewMode = mode
                        if (mode == StatsViewMode.CAPSULE) {
                            capsuleSubView = CapsuleSubView.Overview
                        }
                    },
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )

        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                (scaleIn(
                    initialScale = 0.97f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                ) + fadeIn(animationSpec = tween(220)))
                    .togetherWith(
                        scaleOut(
                            targetScale = 0.97f,
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                        ) + fadeOut(animationSpec = tween(160)),
                    )
            },
            label = "StatsViewModeTransition",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { mode ->
            when (mode) {
                StatsViewMode.CAPSULE -> {
                    SoundCapsulePane(
                        overview = capsuleOverview,
                        songsById = songsById,
                        playerState = playerState,
                        onOpenArtist = onOpenArtist,
                        onSearchArtist = { name -> onOpenArtist(name, name) },
                        onCopySummary = ::copyToClipboard,
                        subView = capsuleSubView,
                        onNavigateSubView = { capsuleSubView = it },
                    )
                }

                StatsViewMode.EXPLORER -> {
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

            item(key = "periodSummary") {
                AnimatedContent(
                    targetState = selectedOption to indexChips,
                    transitionSpec = {
                        (scaleIn(
                            initialScale = 0.98f,
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                        ) + fadeIn(animationSpec = tween(220)))
                            .togetherWith(
                                scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                                ) + fadeOut(animationSpec = tween(140)),
                            )
                    },
                    label = "StatsPeriodSummaryTransition",
                ) { _ ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        StatsPeriodSummaryRow(
                            insights = periodInsights,
                            topArtist = artistStats.firstOrNull(),
                            topSong = songStats.firstOrNull(),
                            onOpenArtist = { artist -> onOpenArtist(artist.id, artist.name) },
                            onPlayTopSong = { song ->
                                val item = song.toLibraryItem()
                                if (playerState.currentItem?.id == item.id) {
                                    playerState.togglePlayPause()
                                } else {
                                    playerState.playQueue(mostPlayedSongItems, startIndex = 0)
                                }
                            },
                        )
                        if (periodInsights.totalPlays > 0) {
                            StatsActivityAndHabitsRow(insights = periodInsights)
                        }
                    }
                }
            }

            item(key = "mostPlayedSongs") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavigationTitle(
                            title = "${songStats.size} ${stringResource("songs")}",
                        )
                    }
                    if (mostPlayedSongs.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 16.dp),
                        ) {
                            StatsHeaderActionPill(
                                icon = IconAssets.play(),
                                label = stringResource("play"),
                                onClick = { playerState.playQueue(mostPlayedSongItems, startIndex = 0) },
                            )
                            StatsHeaderActionPill(
                                icon = IconAssets.playlistAdd(),
                                label = stringResource("add_to_playlist"),
                                onClick = { playlistRequest = StatsPlaylistRequest.Collection(mostPlayedSongs) },
                            )
                        }
                    }
                }

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
                                shape = rememberExpressiveAvatarShape(artist.id),
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
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsModeToggle(
    selectedMode: StatsViewMode,
    onSelectMode: (StatsViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(999.dp),
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(3.dp),
        ) {
            StatsModePill(
                label = stringResource("stats"),
                selected = selectedMode == StatsViewMode.EXPLORER,
                onClick = { onSelectMode(StatsViewMode.EXPLORER) },
            )
            StatsModePill(
                label = stringResource("sound_capsule_title"),
                selected = selectedMode == StatsViewMode.CAPSULE,
                onClick = { onSelectMode(StatsViewMode.CAPSULE) },
            )
        }
    }
}

@Composable
private fun StatsPeriodSummaryRow(
    insights: PeriodInsights,
    topArtist: ArtistStatsEntry?,
    topSong: SongStatsEntry?,
    onOpenArtist: (com.anitail.desktop.db.entities.ArtistEntity) -> Unit,
    onPlayTopSong: (SongEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource("time_listened"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = makeTimeString(insights.totalTimeMs).ifBlank { "0s" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${insights.activeDays}d • ${insights.longestStreakDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource("songs"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = pluralStringResource("n_time", insights.totalPlays, insights.totalPlays),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${insights.uniqueSongs} ${stringResource("songs").lowercase()} • ${insights.uniqueArtists} ${stringResource("artists").lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (topArtist != null) {
            val shape = rememberExpressiveAvatarShape(topArtist.artist.id)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenArtist(topArtist.artist) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    RemoteImage(
                        url = topArtist.artist.thumbnailUrl,
                        shape = shape,
                        modifier = Modifier.size(48.dp).clip(shape),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource("sound_capsule_top_artist"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        )
                        Text(
                            text = topArtist.artist.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource("n_time", topArtist.songCount, topArtist.songCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (topSong != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onPlayTopSong(topSong.song) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    RemoteImage(
                        url = topSong.song.thumbnailUrl,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource("sound_capsule_top_song"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = topSong.song.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource("n_time", topSong.songCountListened, topSong.songCountListened),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsActivityAndHabitsRow(
    insights: PeriodInsights,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 780.dp
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatsActivityChartCard(
                    buckets = insights.activityBuckets,
                    modifier = Modifier.weight(1.35f),
                )
                StatsListeningHabitsCard(
                    insights = insights,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsActivityChartCard(
                    buckets = insights.activityBuckets,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatsListeningHabitsCard(
                    insights = insights,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatsActivityChartCard(
    buckets: List<PeriodActivityBucket>,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val maxMinutes = remember(buckets) {
        buckets.maxOfOrNull { it.playTimeMs / 60_000f }?.coerceAtLeast(1f) ?: 1f
    }
    var hoveredIndex by remember(buckets) { mutableStateOf<Int?>(null) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            RoundedCornerShape(18.dp),
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource("time_listened"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val activeBucket = hoveredIndex?.let { buckets.getOrNull(it) }
                if (activeBucket != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = primaryColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = "${activeBucket.label} • ${makeTimeString(activeBucket.playTimeMs).ifBlank { "0s" }} (${activeBucket.playCount})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (buckets.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                ) {
                    Text(
                        text = stringResource("sound_capsule_no_data"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .pointerInput(buckets.size) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Move,
                                        PointerEventType.Press,
                                        -> {
                                            val x = event.changes.firstOrNull()?.position?.x ?: continue
                                            val count = buckets.size.coerceAtLeast(1)
                                            val idx = ((x / size.width.coerceAtLeast(1)) * count)
                                                .toInt()
                                                .coerceIn(0, count - 1)
                                            hoveredIndex = idx
                                        }

                                        PointerEventType.Exit -> hoveredIndex = null
                                        else -> Unit
                                    }
                                }
                            }
                        },
                ) {
                    val drawHeight = size.height - 8f
                    drawLine(dividerColor, Offset(0f, drawHeight), Offset(size.width, drawHeight), strokeWidth = 1.5f)
                    drawLine(dividerColor, Offset(0f, drawHeight / 2f), Offset(size.width, drawHeight / 2f), strokeWidth = 1f)

                    val count = buckets.size.coerceAtLeast(1)
                    val stepX = size.width / count
                    val barWidth = (stepX * 0.56f).coerceIn(4f, 28f)
                    buckets.forEachIndexed { index, bucket ->
                        val minutes = bucket.playTimeMs / 60_000f
                        val barHeight = if (minutes > 0f) {
                            ((minutes / maxMinutes) * drawHeight).coerceAtLeast(6f)
                        } else {
                            4f
                        }
                        val left = index * stepX + (stepX - barWidth) / 2f
                        val isHovered = hoveredIndex == index
                        drawRoundRect(
                            color = if (isHovered) secondaryColor else primaryColor,
                            topLeft = Offset(left, drawHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val labels = when {
                        buckets.size <= 4 -> buckets.map { it.label }
                        else -> listOf(
                            buckets.first().label,
                            buckets[buckets.size / 2].label,
                            buckets.last().label,
                        )
                    }
                    labels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsListeningHabitsCard(
    insights: PeriodInsights,
    modifier: Modifier = Modifier,
) {
    val maxPeriodMs = remember(insights.periodBreakdown) {
        insights.periodBreakdown.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    }
    val dominantPeriod = remember(insights.periodBreakdown) {
        insights.periodBreakdown.maxByOrNull { it.value }?.key ?: ListeningPeriod.NIGHT
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            RoundedCornerShape(18.dp),
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = periodLabelText(dominantPeriod),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                insights.peakHour?.let { hour ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = String.format("%02d:00", hour),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            ListeningPeriod.entries.forEach { period ->
                val ms = insights.periodBreakdown[period] ?: 0L
                val targetProgress = (ms.toFloat() / maxPeriodMs.toFloat()).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
                    label = "stats_habit_${period.name}",
                )
                val isDominant = period == dominantPeriod
                val mins = (ms / 60_000L).toInt()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = periodLabelText(period),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDominant) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource("sound_capsule_minutes_short", mins),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isDominant) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun periodLabelText(period: ListeningPeriod): String =
    when (period) {
        ListeningPeriod.MORNING -> stringResource("sound_capsule_period_morning")
        ListeningPeriod.AFTERNOON -> stringResource("sound_capsule_period_afternoon")
        ListeningPeriod.EVENING -> stringResource("sound_capsule_period_evening")
        ListeningPeriod.NIGHT -> stringResource("sound_capsule_period_night")
    }

@Composable
private fun StatsHeaderActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatsModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
