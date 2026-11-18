@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anitail.desktop.YouTube
import com.anitail.desktop.constants.MiniPlayerHeight
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.indexPrimaryArtistIdBySongId
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.RepeatMode
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.screen.BrowseSongTarget
import com.anitail.desktop.ui.screen.CollectionSongDialogs
import com.anitail.desktop.ui.screen.PlayerMenuHeader
import com.anitail.desktop.ui.screen.SongMenuHeader
import com.anitail.desktop.ui.screen.buildBrowseSongMenuActions
import com.anitail.desktop.ui.screen.copyTextToClipboard
import com.anitail.desktop.ui.screen.formatBulletSubtitle
import com.anitail.desktop.ui.screen.formatTime
import com.anitail.desktop.ui.screen.library.isSpecialRemotePlaylist
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material 3 Expressive persistent mini player bar for desktop, matching Metrolist layout:
 * - Left: Album Art + Title/Artist + Connected Pill [Share, Like, More]
 * - Center: Retained Expressive Transport [Previous, Morphing Play/Pause with wavy ring, Next]
 * - Right: Connected Pill [Queue, Lyrics, Shuffle, Repeat, Volume, More Options]
 */
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleLyricsSideSheet: (() -> Unit)? = null,
    onToggleQueueSideSheet: (() -> Unit)? = null,
    isLyricsSideSheetOpen: Boolean = false,
    isQueueSideSheetOpen: Boolean = false,
    onOpenAudioSettings: (() -> Unit)? = null,
    onToggleMiniPlayer: (() -> Unit)? = null,
    database: DesktopDatabase = DesktopDatabase.getInstance(),
    downloadService: DesktopDownloadService = DesktopDownloadService(),
    onOpenArtist: ((String, String) -> Unit)? = null,
    onOpenAlbum: ((String, String) -> Unit)? = null,
) {
    val item = playerState.currentItem
    val preferences = remember { DesktopPreferences.getInstance() }
    val strings = LocalStrings.current
    val swipeToSkip by preferences.swipeThumbnail.collectAsState()
    val swipeSensitivity by preferences.swipeSensitivity.collectAsState()

    val volume = playerState.volume.toFloat().coerceIn(0f, 1f)
    var isVolumeButtonHovered by remember { mutableStateOf(false) }
    var isVolumePopupHovered by remember { mutableStateOf(false) }
    var isVolumeKeyboardOpen by remember { mutableStateOf(false) }
    var showVolumePopup by remember { mutableStateOf(false) }

    LaunchedEffect(isVolumeButtonHovered, isVolumePopupHovered, isVolumeKeyboardOpen) {
        if (isVolumeButtonHovered || isVolumePopupHovered || isVolumeKeyboardOpen) {
            showVolumePopup = true
        } else {
            delay(180)
            showVolumePopup = false
        }
    }

    val volumeIcon = when {
        volume <= 0.01f -> ExpressiveIcons.VolumeOff
        volume < 0.5f -> ExpressiveIcons.VolumeDown
        else -> ExpressiveIcons.VolumeUp
    }

    val scope = rememberCoroutineScope()
    val songEntity by remember(item?.id) {
        if (item?.id != null) database.song(item.id) else flowOf(null)
    }.collectAsState(initial = null)
    val isLiked = songEntity?.liked == true

    val songsById by database.songsById.collectAsState()
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val primaryArtistBySongId = remember(songArtistMaps) { indexPrimaryArtistIdBySongId(songArtistMaps) }
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val visiblePlaylists = remember(playlists) { playlists.filterNot { isSpecialRemotePlaylist(it) } }
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadedSongIds = remember(downloadedSongs) {
        downloadedSongs.mapTo(HashSet(downloadedSongs.size)) { it.songId }
    }

    var songDetails by remember(item?.id) { mutableStateOf<SongItem?>(null) }
    LaunchedEffect(item?.id) {
        val trackId = item?.id
        if (trackId.isNullOrBlank()) {
            songDetails = null
            return@LaunchedEffect
        }
        val result = YouTube.next(WatchEndpoint(videoId = trackId)).getOrNull()
        songDetails = result?.let { next ->
            next.currentIndex?.let { idx -> next.items.getOrNull(idx) }
                ?: next.items.firstOrNull { it.id == trackId }
                ?: next.items.firstOrNull()
        }
    }

    var showLeftMoreMenu by remember { mutableStateOf(false) }
    var showRightMoreMenu by remember { mutableStateOf(false) }
    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showTempoPitchDialog by remember { mutableStateOf(false) }

    val toggleCurrentLike: () -> Unit = {
        val track = item
        if (track != null) {
            scope.launch {
                val existing = songsById[track.id]
                if (existing == null) {
                    val details = songDetails
                    val entity = (details?.toSongEntity(inLibrary = true) ?: track.toSongEntity()).toggleLike()
                    if (details != null) {
                        database.insertSong(entity, details.toSongArtistMaps())
                    } else {
                        database.insertSong(entity)
                    }
                } else {
                    database.toggleSongLike(track.id)
                }
            }
        }
    }

    val trackShareUrl = remember(item?.id, item?.playbackUrl, songDetails?.shareLink) {
        songDetails?.shareLink
            ?: item?.let {
                if (it.id.isNotBlank()) "https://music.youtube.com/watch?v=${it.id}" else it.playbackUrl
            }.orEmpty()
    }
    val totalDurationMs = if (playerState.duration > 0L) playerState.duration else (item?.durationMs ?: 0L)
    val trackSubtitle = remember(item?.artist, songDetails, totalDurationMs) {
        formatBulletSubtitle(
            item?.artist?.ifBlank { songDetails?.artists?.joinToString { it.name } },
            totalDurationMs.takeIf { it > 0L }?.let { formatTime(it) },
        ).orEmpty()
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_player_drag_x",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
            ) {
                // Zona Izquierda: Thumbnail + Textos + Píldora de acciones [Share, Like, More]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // Clickable & swipeable track metadata region
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .offset { IntOffset(animatedDragX.roundToInt(), 0) }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = item != null, onClick = onOpenFullPlayer)
                            .pointerInput(swipeToSkip, swipeSensitivity, item?.id) {
                                if (!swipeToSkip || item == null) return@pointerInput
                                val threshold = (72.dp.toPx() / swipeSensitivity.coerceIn(0.3f, 2f))
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragOffsetX < -threshold && playerState.canSkipNext -> {
                                                playerState.skipToNext()
                                            }
                                            dragOffsetX > threshold && playerState.canSkipPrevious -> {
                                                playerState.skipToPrevious()
                                            }
                                        }
                                        dragOffsetX = 0f
                                    },
                                    onDragCancel = { dragOffsetX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-120f, 120f)
                                    },
                                )
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item?.artworkUrl != null) {
                                RemoteImage(
                                    url = item.artworkUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = IconAssets.musicNote(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        AnimatedContent(
                            targetState = item,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "mini_player_track_text",
                            modifier = Modifier.weight(1f, fill = false),
                        ) { track ->
                            if (track == null) {
                                Column {
                                    Text(
                                        text = stringResource("nothing_playing"),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(),
                                    )
                                    MiniPlayerSubtitle(
                                        artist = track.artist,
                                        fallbackDurationMs = track.durationMs ?: 0L,
                                        playerState = playerState,
                                    )
                                }
                            }
                        }
                    }

                    if (item != null) {
                        Spacer(modifier = Modifier.width(6.dp))

                        // Píldora de acciones conectadas: [Compartir, Me gusta, Más opciones]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            val pillContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                            val pillContent = MaterialTheme.colorScheme.onSurfaceVariant
                            val pillColors = ToggleButtonDefaults.colors(
                                containerColor = pillContainer,
                                contentColor = pillContent,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val leftShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                                checkedShape = RoundedCornerShape(
                                    topStart = 14.dp,
                                    bottomStart = 14.dp,
                                    topEnd = 6.dp,
                                    bottomEnd = 6.dp,
                                ),
                            )
                            val midShapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
                                checkedShape = RoundedCornerShape(6.dp),
                            )
                            val rightShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                                checkedShape = RoundedCornerShape(
                                    topStart = 6.dp,
                                    bottomStart = 6.dp,
                                    topEnd = 14.dp,
                                    bottomEnd = 14.dp,
                                ),
                            )

                            // Compartir
                            var shareCopied by remember { mutableStateOf(false) }
                            LaunchedEffect(shareCopied) {
                                if (shareCopied) {
                                    delay(1500L)
                                    shareCopied = false
                                }
                            }
                            ToggleButton(
                                checked = shareCopied,
                                onCheckedChange = {
                                    val id = item.id
                                    runCatching {
                                        val selection = java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=$id")
                                        val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        clip.setContents(selection, selection)
                                        shareCopied = true
                                    }
                                },
                                shapes = leftShapes,
                                colors = pillColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 34.dp, height = 32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (shareCopied) IconAssets.check() else IconAssets.share(),
                                        contentDescription = stringResource("share"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Me gusta
                            ToggleButton(
                                checked = isLiked,
                                onCheckedChange = { toggleCurrentLike() },
                                shapes = midShapes,
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = pillContainer,
                                    contentColor = if (isLiked) Color(0xFFFF4D6D) else pillContent,
                                    checkedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    checkedContentColor = Color(0xFFFF4D6D),
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 34.dp, height = 32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                        contentDescription = stringResource(if (isLiked) "action_remove_like" else "action_like"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Más opciones de pista
                            Box {
                                ToggleButton(
                                    checked = showLeftMoreMenu,
                                    onCheckedChange = { showLeftMoreMenu = !showLeftMoreMenu },
                                    shapes = rightShapes,
                                    colors = pillColors,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(width = 34.dp, height = 32.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = IconAssets.moreVert(),
                                            contentDescription = stringResource("more_options"),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                val leftMenuActions = remember(
                                    item.id,
                                    songDetails,
                                    songsById[item.id],
                                    primaryArtistBySongId[item.id],
                                    downloadStates[item.id],
                                    item.id in downloadedSongIds,
                                    strings,
                                ) {
                                    buildBrowseSongMenuActions(
                                        strings = strings,
                                        libraryItem = item,
                                        songItem = songDetails,
                                        songsById = songsById,
                                        primaryArtistBySongId = primaryArtistBySongId,
                                        downloadStates = downloadStates,
                                        downloadedSongIds = downloadedSongIds,
                                        database = database,
                                        downloadService = downloadService,
                                        playerState = playerState,
                                        coroutineScope = scope,
                                        onOpenArtist = { id, name -> onOpenArtist?.invoke(id, name.orEmpty()) },
                                        onOpenAlbum = { id, name -> onOpenAlbum?.invoke(id, name.orEmpty()) },
                                        onRequestPlaylist = { pendingPlaylistItem = it },
                                        onRequestArtists = { pendingArtists = it },
                                        onShowDetails = { detailsItem = it },
                                        copyToClipboard = ::copyTextToClipboard,
                                    )
                                }

                                ItemContextMenu(
                                    expanded = showLeftMoreMenu,
                                    onDismiss = { showLeftMoreMenu = false },
                                    item = item,
                                    actions = leftMenuActions,
                                    headerContent = {
                                        SongMenuHeader(
                                            title = item.title,
                                            subtitle = trackSubtitle,
                                            thumbnailUrl = item.artworkUrl,
                                            isLiked = isLiked,
                                            onToggleLike = toggleCurrentLike,
                                            onPlayNext = { playerState.addToQueue(item, playNext = true) },
                                            onAddToPlaylist = {
                                                pendingPlaylistItem = BrowseSongTarget(item, songDetails)
                                            },
                                            onShare = { copyTextToClipboard(trackShareUrl) },
                                            onDismiss = { showLeftMoreMenu = false },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Zona Central: [Anterior, Play/Pausa con wavy ring, Siguiente] (retenido exactamente como pidió el usuario)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    IconButton(
                        onClick = { playerState.skipToPrevious() },
                        enabled = playerState.canSkipPrevious,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipPrevious,
                            contentDescription = stringResource("previous"),
                            tint = if (playerState.canSkipPrevious) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    MiniPlayerPlayPauseButton(
                        playerState = playerState,
                        enabled = item != null,
                    )

                    IconButton(
                        onClick = { playerState.skipToNext() },
                        enabled = playerState.canSkipNext,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = ExpressiveIcons.SkipNext,
                            contentDescription = stringResource("next"),
                            tint = if (playerState.canSkipNext) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Zona Derecha: Píldora conectada de utilidades [Cola, Letras, Aleatorio, Repetición, Volumen, Más opciones]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f),
                ) {
                    val groupContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    val groupContent = MaterialTheme.colorScheme.onSurfaceVariant
                    val groupColors = ToggleButtonDefaults.colors(
                        containerColor = groupContainer,
                        contentColor = groupContent,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    val groupLeadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                        checkedShape = RoundedCornerShape(
                            topStart = 16.dp,
                            bottomStart = 16.dp,
                            topEnd = 6.dp,
                            bottomEnd = 6.dp,
                        ),
                    )
                    val groupMiddleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
                        checkedShape = RoundedCornerShape(6.dp),
                    )
                    val groupTrailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                        checkedShape = RoundedCornerShape(
                            topStart = 6.dp,
                            bottomStart = 6.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                        ),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        // 1. Cola
                        ToggleButton(
                            checked = isQueueSideSheetOpen,
                            onCheckedChange = { onToggleQueueSideSheet?.invoke() },
                            shapes = groupLeadingShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.QueueMusic,
                                    contentDescription = stringResource("queue"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 2. Letras
                        ToggleButton(
                            checked = isLyricsSideSheetOpen,
                            onCheckedChange = { onToggleLyricsSideSheet?.invoke() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.Lyrics,
                                    contentDescription = stringResource("lyrics"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 3. Aleatorio
                        ToggleButton(
                            checked = playerState.shuffleEnabled,
                            onCheckedChange = { playerState.toggleShuffle() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ExpressiveIcons.Shuffle,
                                    contentDescription = stringResource("shuffle"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 4. Repetición
                        val repeatActive = playerState.repeatMode != RepeatMode.OFF
                        ToggleButton(
                            checked = repeatActive,
                            onCheckedChange = { playerState.toggleRepeat() },
                            shapes = groupMiddleShapes,
                            colors = groupColors,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playerState.repeatMode == RepeatMode.ONE) {
                                        ExpressiveIcons.RepeatOne
                                    } else {
                                        ExpressiveIcons.Repeat
                                    },
                                    contentDescription = stringResource("repeat"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // 5. Volumen con popup vertical en hover/scroll/click
                        Box(
                            modifier = Modifier
                                .onPointerEvent(PointerEventType.Enter) { isVolumeButtonHovered = true }
                                .onPointerEvent(PointerEventType.Exit) { isVolumeButtonHovered = false }
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (deltaY != 0f) {
                                        playerState.stepVolume(if (deltaY < 0f) 0.05 else -0.05)
                                    }
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.stepVolume(0.05)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.stepVolume(-0.05)
                                            true
                                        }
                                        Key.M -> {
                                            isVolumeKeyboardOpen = true
                                            playerState.toggleMute()
                                            true
                                        }
                                        Key.Escape -> {
                                            if (showVolumePopup) {
                                                isVolumeKeyboardOpen = false
                                                showVolumePopup = false
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                },
                        ) {
                            ToggleButton(
                                checked = volume <= 0.01f,
                                onCheckedChange = { playerState.toggleMute() },
                                shapes = groupMiddleShapes,
                                colors = groupColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 36.dp, height = 34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = volumeIcon,
                                        contentDescription = stringResource("volume"),
                                        tint = if (volume <= 0.01f) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            VolumePopupOverlay(
                                visible = showVolumePopup,
                                volume = volume,
                                onVolumeChange = { playerState.updateVolume(it.toDouble()) },
                                onToggleMute = { playerState.toggleMute() },
                                onStepVolume = { playerState.stepVolume(it.toDouble()) },
                                onPopupHoverChanged = { isVolumePopupHovered = it },
                                onDismissRequest = {
                                    isVolumeKeyboardOpen = false
                                    showVolumePopup = false
                                },
                            )
                        }

                        // 6. Más opciones (Menú con Ecualizador, PiP, Cast, Vídeo, Expandir)
                        Box {
                            ToggleButton(
                                checked = showRightMoreMenu,
                                onCheckedChange = { showRightMoreMenu = !showRightMoreMenu },
                                shapes = groupTrailingShapes,
                                colors = groupColors,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(width = 36.dp, height = 34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = IconAssets.moreVert(),
                                        contentDescription = stringResource("more_options"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            if (item != null) {
                                val songEntitySnapshot = songsById[item.id]
                                val primaryArtistId = primaryArtistBySongId[item.id]
                                val isDownloaded = item.id in downloadedSongIds
                                val downloadStatus = downloadStates[item.id]?.status
                                val isDownloading =
                                    downloadStatus == DownloadStatus.DOWNLOADING ||
                                        downloadStatus == DownloadStatus.QUEUED
                                val rightMenuActions = remember(
                                    item.id,
                                    songDetails,
                                    songEntitySnapshot,
                                    primaryArtistId,
                                    isDownloaded,
                                    isDownloading,
                                    playerState.isSleepTimerActive,
                                    playerState.sleepTimerTimeLeftMs,
                                    playerState.hasVideoStream,
                                    onOpenAudioSettings,
                                    onOpenArtist,
                                    onOpenAlbum,
                                    strings,
                                ) {
                                    buildList {
                                        val artistCandidates = songDetails?.artists
                                            ?.mapNotNull { artist -> artist.id?.let { Artist(artist.name, it) } }
                                            .orEmpty()
                                        val resolvedArtistId = artistCandidates.firstOrNull()?.id ?: primaryArtistId
                                        if (onOpenArtist != null && !resolvedArtistId.isNullOrBlank()) {
                                            add(
                                                ContextMenuAction(
                                                    label = strings.get("view_artist"),
                                                    icon = IconAssets.artist(),
                                                    onClick = {
                                                        when {
                                                            artistCandidates.size == 1 -> {
                                                                val artist = artistCandidates.first()
                                                                artist.id?.let { onOpenArtist(it, artist.name) }
                                                            }
                                                            artistCandidates.size > 1 -> {
                                                                pendingArtists = artistCandidates
                                                            }
                                                            else -> {
                                                                onOpenArtist(resolvedArtistId, item.artist)
                                                            }
                                                        }
                                                    },
                                                )
                                            )
                                        }

                                        val resolvedAlbumId = songDetails?.album?.id ?: songEntitySnapshot?.albumId
                                        val resolvedAlbumName = songDetails?.album?.name ?: songEntitySnapshot?.albumName
                                        if (onOpenAlbum != null && !resolvedAlbumId.isNullOrBlank()) {
                                            add(
                                                ContextMenuAction(
                                                    label = strings.get("view_album"),
                                                    icon = IconAssets.album(),
                                                    onClick = {
                                                        onOpenAlbum(resolvedAlbumId, resolvedAlbumName.orEmpty())
                                                    },
                                                )
                                            )
                                        }

                                        add(
                                            when {
                                                isDownloaded -> ContextMenuAction(
                                                    label = strings.get("remove_download"),
                                                    icon = IconAssets.offline(),
                                                    onClick = { downloadService.deleteDownload(item.id) },
                                                )
                                                isDownloading -> ContextMenuAction(
                                                    label = strings.get("downloading"),
                                                    icon = IconAssets.download(),
                                                    onClick = { downloadService.cancelDownload(item.id) },
                                                )
                                                else -> ContextMenuAction(
                                                    label = strings.get("download"),
                                                    icon = IconAssets.download(),
                                                    onClick = {
                                                        val durationSec = ((item.durationMs ?: 0L) / 1000L).toInt()
                                                        downloadService.downloadSong(
                                                            songId = item.id,
                                                            title = item.title,
                                                            artist = item.artist,
                                                            album = resolvedAlbumName,
                                                            thumbnailUrl = item.artworkUrl,
                                                            duration = durationSec,
                                                        )
                                                    },
                                                )
                                            }
                                        )

                                        val timerLabel = if (playerState.isSleepTimerActive) {
                                            "${strings.get("sleep_timer")} (${formatTime(playerState.sleepTimerTimeLeftMs)})"
                                        } else {
                                            strings.get("sleep_timer")
                                        }
                                        add(
                                            ContextMenuAction(
                                                label = timerLabel,
                                                icon = IconAssets.bedtime(),
                                                onClick = { showSleepTimerDialog = true },
                                            )
                                        )

                                        if (onOpenAudioSettings != null) {
                                            add(
                                                ContextMenuAction(
                                                    label = strings.get("equalizer"),
                                                    icon = ExpressiveIcons.Equalizer,
                                                    onClick = { onOpenAudioSettings() },
                                                )
                                            )
                                        }

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("advanced"),
                                                icon = IconAssets.tune(),
                                                onClick = { showTempoPitchDialog = true },
                                            )
                                        )

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("cast"),
                                                icon = ExpressiveIcons.Cast,
                                                onClick = { playerState.showCastDialog = true },
                                            )
                                        )

                                        if (playerState.hasVideoStream) {
                                            add(
                                                ContextMenuAction(
                                                    label = strings.get("video_viewer"),
                                                    icon = ExpressiveIcons.Movie,
                                                    onClick = { playerState.toggleVideoViewer() },
                                                )
                                            )
                                        }

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("open_miniplayer"),
                                                icon = ExpressiveIcons.PictureInPicture,
                                                onClick = {
                                                    onToggleMiniPlayer?.invoke() ?: playerState.toggleMiniPlayer()
                                                },
                                            )
                                        )

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("expand"),
                                                icon = IconAssets.expandLess(),
                                                onClick = onOpenFullPlayer,
                                            )
                                        )

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("share"),
                                                icon = IconAssets.share(),
                                                onClick = { copyTextToClipboard(trackShareUrl) },
                                            )
                                        )

                                        add(
                                            ContextMenuAction(
                                                label = strings.get("details"),
                                                icon = IconAssets.info(),
                                                onClick = { detailsItem = item },
                                            )
                                        )
                                    }
                                }

                                ItemContextMenu(
                                    expanded = showRightMoreMenu,
                                    onDismiss = { showRightMoreMenu = false },
                                    item = item,
                                    actions = rightMenuActions,
                                    headerContent = {
                                        PlayerMenuHeader(
                                            title = item.title,
                                            subtitle = trackSubtitle,
                                            thumbnailUrl = item.artworkUrl,
                                            isLiked = isLiked,
                                            onToggleLike = toggleCurrentLike,
                                            onStartRadio = startRadio@{
                                                val videoId = songDetails?.id ?: item.id.ifBlank { null }
                                                    ?: return@startRadio
                                                scope.launch {
                                                    val result = YouTube.next(WatchEndpoint(videoId = videoId))
                                                        .getOrNull() ?: return@launch
                                                    val plan = buildRadioQueuePlan(item, result)
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                            },
                                            onAddToPlaylist = {
                                                pendingPlaylistItem = BrowseSongTarget(item, songDetails)
                                            },
                                            onCopyLink = { copyTextToClipboard(trackShareUrl) },
                                            onDismiss = { showRightMoreMenu = false },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Subtle bottom progress hairline inside the rounded surface
            val bottomActiveColor = MaterialTheme.colorScheme.primary
            val bottomTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .drawBehind {
                        val radius = size.height * 0.5f
                        val corner = CornerRadius(radius, radius)
                        drawRoundRect(
                            color = bottomTrackColor,
                            size = size,
                            cornerRadius = corner,
                        )
                        val fraction = playerState.progress.coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = bottomActiveColor,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = corner,
                            )
                        }
                    },
            )
        }
    }

    CollectionSongDialogs(
        pendingPlaylistItem = pendingPlaylistItem,
        pendingCollectionTargets = null,
        pendingArtists = pendingArtists,
        detailsItem = detailsItem,
        playlists = visiblePlaylists,
        songsById = songsById,
        database = database,
        coroutineScope = scope,
        onDismissPendingPlaylistItem = { pendingPlaylistItem = null },
        onDismissPendingCollectionTargets = {},
        onSelectArtist = { id, name -> onOpenArtist?.invoke(id, name) },
        onDismissArtists = { pendingArtists = emptyList() },
        onDismissDetails = { detailsItem = null },
    )

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            visible = true,
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
    }

    TempoPitchDialog(
        visible = showTempoPitchDialog,
        tempo = playerState.tempo,
        pitchSemitone = playerState.pitchSemitone,
        onTempoChange = { playerState.updateTempo(it) },
        onPitchChange = { playerState.updatePitchSemitone(it) },
        onReset = { playerState.resetTempoPitch() },
        onDismiss = { showTempoPitchDialog = false },
    )
}

@Composable
private fun MiniPlayerSubtitle(
    artist: String,
    fallbackDurationMs: Long,
    playerState: PlayerState,
) {
    val totalDurationMs = if (playerState.duration > 0L) playerState.duration else fallbackDurationMs
    val positionSecond by remember(playerState) {
        derivedStateOf { playerState.position / 1000L }
    }
    val timeSuffix = if (totalDurationMs > 0L) {
        " • ${formatDuration(positionSecond * 1000L)} / ${formatDuration(totalDurationMs)}"
    } else {
        ""
    }
    Text(
        text = "$artist$timeSuffix",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val TwoPiFloat = (2.0 * PI).toFloat()
private const val HalfPiFloat = (PI / 2.0).toFloat()
private val MiniPlayerRingDiameter = 54.dp
private val MiniPlayerRingStrokeWidth = 2.6.dp
private val MiniPlayerWaveAmplitude = 1.4.dp
private val MiniPlayerRingGapSize = 4.dp
private const val MiniPlayerWaveCount = 10

private enum class PlayPauseVisualState {
    Buffering,
    Playing,
    Paused,
}

internal data class PlayPauseRingGeometry(
    val centerlineRadiusDp: Float,
    val outerCrestRadiusDp: Float,
    val flatTrackInnerRadiusDp: Float,
    val waveTroughInnerRadiusDp: Float,
    val buttonMaxRadiusDp: Float,
    val waveTroughClearanceDp: Float,
    val flatTrackClearanceDp: Float,
)

internal fun calculatePlayPauseRingGeometry(
    ringDiameterDp: Float,
    strokeWidthDp: Float,
    maxWaveAmplitudeDp: Float,
    buttonSizeDp: Float,
    buttonCornerDp: Float,
): PlayPauseRingGeometry {
    val centerlineRadiusDp = ringDiameterDp / 2f - strokeWidthDp / 2f - maxWaveAmplitudeDp
    val outerCrestRadiusDp = centerlineRadiusDp + maxWaveAmplitudeDp + strokeWidthDp / 2f
    val flatTrackInnerRadiusDp = centerlineRadiusDp - strokeWidthDp / 2f
    val waveTroughInnerRadiusDp = centerlineRadiusDp - maxWaveAmplitudeDp - strokeWidthDp / 2f
    val halfSize = buttonSizeDp / 2f
    val effectiveCorner = buttonCornerDp.coerceIn(0f, halfSize)
    val cornerArcOffset = halfSize - effectiveCorner
    val buttonMaxRadiusDp = sqrt(2f) * cornerArcOffset + effectiveCorner
    return PlayPauseRingGeometry(
        centerlineRadiusDp = centerlineRadiusDp,
        outerCrestRadiusDp = outerCrestRadiusDp,
        flatTrackInnerRadiusDp = flatTrackInnerRadiusDp,
        waveTroughInnerRadiusDp = waveTroughInnerRadiusDp,
        buttonMaxRadiusDp = buttonMaxRadiusDp,
        waveTroughClearanceDp = waveTroughInnerRadiusDp - buttonMaxRadiusDp,
        flatTrackClearanceDp = flatTrackInnerRadiusDp - buttonMaxRadiusDp,
    )
}

@Composable
private fun MiniPlayerPlayPauseButton(
    playerState: PlayerState,
    enabled: Boolean,
) {
    val isPlaying = playerState.isPlaying
    val isBuffering = playerState.isBuffering
    val visualState = when {
        isBuffering -> PlayPauseVisualState.Buffering
        isPlaying -> PlayPauseVisualState.Playing
        else -> PlayPauseVisualState.Paused
    }
    val isSquircle = isPlaying && !isBuffering

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonSize by animateDpAsState(
        targetValue = if (isSquircle) 34.dp else 36.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mini_play_size",
    )
    val buttonCorner by animateDpAsState(
        targetValue = when {
            isPressed && isSquircle -> 9.dp
            isPressed -> 14.dp
            isSquircle -> 11.dp
            else -> 18.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mini_play_corner",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mini_play_scale",
    )

    val amplitudeAnim = remember {
        Animatable(if (isPlaying && !isBuffering && enabled) 1f else 0f)
    }
    LaunchedEffect(isPlaying, isBuffering, enabled) {
        amplitudeAnim.animateTo(
            targetValue = if (isPlaying && !isBuffering && enabled) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        )
    }

    val wavePhase = remember { mutableFloatStateOf(0f) }
    val indeterminateRotation = remember { mutableFloatStateOf(0f) }
    val indeterminateSweepPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, isBuffering, enabled) {
        if ((!isPlaying && !isBuffering) || !enabled) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    wavePhase.floatValue = (wavePhase.floatValue + dtSec * 2.8f) % TwoPiFloat
                    if (isBuffering) {
                        indeterminateRotation.floatValue =
                            (indeterminateRotation.floatValue + dtSec * 4.2f) % TwoPiFloat
                        indeterminateSweepPhase.floatValue =
                            (indeterminateSweepPhase.floatValue + dtSec * 2.4f) % TwoPiFloat
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val activePath = remember { Path() }

    Box(
        modifier = Modifier.size(MiniPlayerRingDiameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokePx = MiniPlayerRingStrokeWidth.toPx()
            val maxAmpPx = MiniPlayerWaveAmplitude.toPx()
            val gapPx = MiniPlayerRingGapSize.toPx()
            val radius = (min(size.width, size.height) / 2f - strokePx / 2f - maxAmpPx).coerceAtLeast(1f)
            val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

            if (isBuffering && enabled) {
                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = Offset(cx, cy),
                    style = strokeStyle,
                )
                val sweepRad = 0.85f + 2.35f * (0.5f + 0.5f * sin(indeterminateSweepPhase.floatValue))
                val startRad = -HalfPiFloat + indeterminateRotation.floatValue
                buildCircularWavyArcPath(
                    path = activePath,
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    startRad = startRad,
                    sweepRad = sweepRad,
                    amplitudePx = maxAmpPx * 0.7f,
                    waveCount = MiniPlayerWaveCount,
                    phase = wavePhase.floatValue,
                    isFullCircle = false,
                )
                drawPath(
                    path = activePath,
                    color = activeColor,
                    style = strokeStyle,
                )
            } else {
                val p = if (enabled) playerState.progress.coerceIn(0f, 1f) else 0f
                if (p <= 0.001f) {
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = strokeStyle,
                    )
                } else {
                    val startRad = -HalfPiFloat
                    val sweepRad = p * TwoPiFloat
                    val isFullCircle = p >= 0.995f
                    val gapRad = (gapPx + strokePx) / radius
                    val trackSweep = TwoPiFloat - sweepRad - 2f * gapRad

                    if (!isFullCircle && trackSweep > 0.04f) {
                        drawArc(
                            color = trackColor,
                            startAngle = radToDeg(startRad + sweepRad + gapRad),
                            sweepAngle = radToDeg(trackSweep),
                            useCenter = false,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = strokeStyle,
                        )
                    }

                    buildCircularWavyArcPath(
                        path = activePath,
                        cx = cx,
                        cy = cy,
                        radius = radius,
                        startRad = startRad,
                        sweepRad = sweepRad,
                        amplitudePx = maxAmpPx * amplitudeAnim.value,
                        waveCount = MiniPlayerWaveCount,
                        phase = wavePhase.floatValue,
                        isFullCircle = isFullCircle,
                    )
                    drawPath(
                        path = activePath,
                        color = activeColor,
                        style = strokeStyle,
                    )
                }
            }
        }

        FilledIconButton(
            onClick = { playerState.togglePlayPause() },
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(buttonCorner.coerceAtLeast(0.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier
                .size(buttonSize.coerceAtLeast(1.dp))
                .scale(buttonScale),
        ) {
            AnimatedContent(
                targetState = visualState,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.72f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.72f))
                },
                label = "mini_play_icon",
            ) { state ->
                when (state) {
                    PlayPauseVisualState.Buffering -> {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    PlayPauseVisualState.Playing -> {
                        Icon(
                            imageVector = ExpressiveIcons.Pause,
                            contentDescription = stringResource("pause"),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    PlayPauseVisualState.Paused -> {
                        Icon(
                            imageVector = ExpressiveIcons.PlayArrow,
                            contentDescription = stringResource("play"),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun buildCircularWavyArcPath(
    path: Path,
    cx: Float,
    cy: Float,
    radius: Float,
    startRad: Float,
    sweepRad: Float,
    amplitudePx: Float,
    waveCount: Int,
    phase: Float,
    isFullCircle: Boolean,
) {
    path.reset()
    val steps = (sweepRad * 48f).toInt().coerceIn(8, 220)
    val edgeRad = min(sweepRad * 0.45f, 0.16f)
    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val offset = t * sweepRad
        val theta = startRad + offset
        val edgeFactor = if (isFullCircle || edgeRad <= 0.0001f) {
            1f
        } else {
            smoothstep01(min(offset, sweepRad - offset) / edgeRad)
        }
        val r = radius + amplitudePx * edgeFactor * sin(waveCount * theta - phase)
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (isFullCircle) {
        path.close()
    }
}

private fun smoothstep01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun radToDeg(rad: Float): Float = (rad * 180.0 / PI).toFloat()
