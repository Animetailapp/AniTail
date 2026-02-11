package com.anitail.desktop.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.lyrics.DesktopLyricsService
import com.anitail.desktop.lyrics.LyricLine
import com.anitail.desktop.lyrics.LyricsResult
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.LyricsAnimationStylePreference
import com.anitail.desktop.storage.LyricsPositionPreference
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lyrics panel component for displaying synchronized lyrics.
 */
@Composable
fun LyricsPanel(
    title: String,
    artist: String,
    videoId: String? = null,
    durationSec: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    songRomanizeLyrics: Boolean,
    onSetSongRomanizeLyrics: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { DesktopPreferences.getInstance() }
    val clipboard = LocalClipboardManager.current
    val lyricsTextPosition by preferences.lyricsTextPosition.collectAsState()
    val lyricsClick by preferences.lyricsClick.collectAsState()
    val lyricsScroll by preferences.lyricsScroll.collectAsState()
    val lyricsFontSize by preferences.lyricsFontSize.collectAsState()
    val lyricsCustomFontPath by preferences.lyricsCustomFontPath.collectAsState()
    val lyricsSmoothScroll by preferences.lyricsSmoothScroll.collectAsState()
    val lyricsAnimationStyle by preferences.lyricsAnimationStyle.collectAsState()

    var lyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSyncOptionsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var isSyncingOptions by remember { mutableStateOf(false) }
    var syncOptions by remember { mutableStateOf<List<LyricsResult>>(emptyList()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var editableLyricsText by remember { mutableStateOf("") }
    var searchTitle by remember { mutableStateOf(title) }
    var searchArtist by remember { mutableStateOf(artist) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    var selectedLineIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sharePayload by remember { mutableStateOf<String?>(null) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lyricsLabel = stringResource("lyrics")
    val syncedLabel = stringResource("lyrics_synced")
    val plainLabel = stringResource("lyrics_plain")
    val lyricsNotFoundLabel = stringResource("lyrics_not_found")
    val lyricsEmptyLabel = stringResource("lyrics_start_playing")
    val moreOptionsLabel = stringResource("more_options")
    val shareLyricsLabel = stringResource("share_lyrics")
    val shareSelectedLabel = stringResource("share_selected")
    val shareAsTextLabel = stringResource("share_as_text")
    val shareAsImageLabel = stringResource("share_as_image")
    val lyricsResyncLabel = stringResource("lyrics_resync")
    val refetchLabel = stringResource("refetch")
    val editLabel = stringResource("edit")
    val searchLabel = stringResource("search")
    val searchLyricsLabel = stringResource("search_lyrics")
    val searchOnlineLabel = stringResource("search_online")
    val songTitleLabel = stringResource("song_title")
    val songArtistsLabel = stringResource("song_artists")
    val romanizeCurrentTrackLabel = stringResource("romanize_current_track")
    val saveLabel = stringResource("save")
    val cancelLabel = stringResource("cancel")
    val copiedLabel = stringResource("copied")
    val closeLabel = stringResource("close")

    LaunchedEffect(actionMessage) {
        if (actionMessage == null) return@LaunchedEffect
        delay(1800L)
        actionMessage = null
    }

    LaunchedEffect(isSelectionModeActive, selectedLineIndices) {
        if (isSelectionModeActive && selectedLineIndices.isEmpty()) {
            isSelectionModeActive = false
        }
    }

    val lyricsScrollLockConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && !isProgrammaticScroll) {
                    isAutoScrollEnabled = false
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!isProgrammaticScroll) {
                    isAutoScrollEnabled = false
                }
                return Velocity.Zero
            }
        }
    }

    val textAlign = when (lyricsTextPosition) {
        LyricsPositionPreference.LEFT -> TextAlign.Left
        LyricsPositionPreference.CENTER -> TextAlign.Center
        LyricsPositionPreference.RIGHT -> TextAlign.Right
    }

    val customFont = remember(lyricsCustomFontPath) {
        val path = lyricsCustomFontPath.trim()
        if (path.isEmpty()) {
            null
        } else {
            val fontFile = File(path)
            if (fontFile.exists() && fontFile.isFile) {
                // Compose Desktop in this target does not expose a File-based Font constructor.
                FontFamily.Default
            } else {
                null
            }
        }
    }

    fun buildSharePayload(result: LyricsResult): String {
        val lyricsBody = when {
            !result.plainLyrics.isNullOrBlank() -> result.plainLyrics
            result.parsedLines.isNotEmpty() -> result.parsedLines.joinToString("\n") { it.text }
            else -> ""
        }
        return "\"$lyricsBody\"\n\n$title - $artist"
    }

    fun extractRawLyrics(result: LyricsResult): String =
        result.syncedLyrics
            ?: result.plainLyrics
            ?: result.parsedLines.joinToString("\n") { line -> line.text }

    fun buildSharePayloadFromSelectedLines(lines: List<String>): String {
        val body = lines.joinToString("\n").trim()
        return "\"$body\"\n\n$title - $artist"
    }

    suspend fun applyLyricsOption(option: LyricsResult) {
        val savedResult = DesktopLyricsService.saveOverrideLyrics(
            title = title,
            artist = artist,
            durationSec = durationSec,
            rawLyrics = extractRawLyrics(option),
            videoId = videoId,
            provider = option.provider,
        )
        lyricsResult = savedResult
        actionMessage = option.provider
        isSelectionModeActive = false
        selectedLineIndices = emptySet()
    }

    fun openWebSearch() {
        if (!Desktop.isDesktopSupported()) return
        val query = "$searchArtist $searchTitle lyrics".trim()
        if (query.isBlank()) return
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        runCatching {
            Desktop.getDesktop().browse(URI("https://www.google.com/search?q=$encodedQuery"))
        }
    }

    suspend fun refreshLyrics(showLoadingState: Boolean) {
        if (title.isBlank()) {
            lyricsResult = null
            return
        }
        if (showLoadingState) {
            isLoading = true
        }
        error = null
        try {
            DesktopLyricsService.getLyrics(
                title = title,
                artist = artist,
                durationSec = durationSec,
                videoId = videoId,
            )
                .onSuccess { result ->
                    lyricsResult = result
                }
                .onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    error = e.message
                }
        } finally {
            if (showLoadingState) {
                isLoading = false
            }
        }
    }

    suspend fun refetchLyrics() {
        DesktopLyricsService.clearOverrideLyrics(
            title = title,
            artist = artist,
            videoId = videoId,
        )
        refreshLyrics(showLoadingState = true)
    }

    suspend fun syncLyricsOptions(
        queryTitle: String = title,
        queryArtist: String = artist,
        includeOverride: Boolean = true,
    ) {
        if (queryTitle.isBlank()) return
        isSyncingOptions = true
        error = null
        try {
            val options = DesktopLyricsService.getAllLyricsOptions(
                title = queryTitle,
                artist = queryArtist,
                durationSec = durationSec,
                videoId = videoId,
                includeOverride = includeOverride,
            )
                .distinctBy { candidate ->
                    buildString {
                        append(candidate.provider)
                        append("::")
                        append(candidate.syncedLyrics ?: candidate.plainLyrics.orEmpty())
                    }
                }

            if (options.isEmpty()) {
                actionMessage = lyricsNotFoundLabel
                return
            }

            syncOptions = options
            if (options.size == 1) {
                applyLyricsOption(options.first())
            } else {
                showSyncOptionsDialog = true
            }
        } finally {
            isSyncingOptions = false
        }
    }

    LaunchedEffect(title, artist, videoId, durationSec) {
        menuExpanded = false
        showEditDialog = false
        showSearchDialog = false
        showSyncOptionsDialog = false
        showShareDialog = false
        syncOptions = emptyList()
        actionMessage = null
        lyricsResult = null
        editableLyricsText = ""
        searchTitle = title
        searchArtist = artist
        isSelectionModeActive = false
        selectedLineIndices = emptySet()
        sharePayload = null
        isAutoScrollEnabled = true
        refreshLyrics(showLoadingState = true)
    }

    var renderPlaybackPositionMs by remember { mutableFloatStateOf((currentPositionMs + KaraokeLeadMs).toFloat()) }
    var reportedPlaybackPositionMs by remember { mutableFloatStateOf((currentPositionMs + KaraokeLeadMs).toFloat()) }
    var reportedAtFrameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentPositionMs) {
        reportedPlaybackPositionMs = (currentPositionMs + KaraokeLeadMs).toFloat()
        reportedAtFrameNanos = 0L
        if (!isPlaying) {
            renderPlaybackPositionMs = reportedPlaybackPositionMs
        }
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (reportedAtFrameNanos == 0L) {
                    reportedAtFrameNanos = now
                }

                val extrapolatedPosition = if (isPlaying) {
                    val elapsedSinceReportMs = ((now - reportedAtFrameNanos) / 1_000_000f).coerceAtLeast(0f)
                    reportedPlaybackPositionMs + elapsedSinceReportMs
                } else {
                    reportedPlaybackPositionMs
                }

                if (!isPlaying) {
                    renderPlaybackPositionMs = extrapolatedPosition
                } else {
                    val backwardDelta = renderPlaybackPositionMs - extrapolatedPosition
                    renderPlaybackPositionMs = when {
                        extrapolatedPosition >= renderPlaybackPositionMs -> extrapolatedPosition
                        backwardDelta <= PlaybackJitterToleranceMs -> renderPlaybackPositionMs
                        else -> extrapolatedPosition
                    }
                }
            }
        }
    }

    val currentLineIndex = lyricsResult?.getCurrentLineIndex(renderPlaybackPositionMs.toLong()) ?: -1
    LaunchedEffect(currentLineIndex, lyricsScroll, lyricsSmoothScroll, lyricsAnimationStyle, lyricsResult, isSelectionModeActive, isAutoScrollEnabled) {
        if (isSelectionModeActive) return@LaunchedEffect
        if (!isAutoScrollEnabled) return@LaunchedEffect
        if (!lyricsScroll) return@LaunchedEffect
        if (currentLineIndex < 0 || lyricsResult?.parsedLines?.isEmpty() != false) return@LaunchedEffect

        val shouldAnimate = lyricsSmoothScroll || lyricsAnimationStyle == LyricsAnimationStylePreference.APPLE
        isProgrammaticScroll = true
        try {
            if (shouldAnimate) {
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -200,
                )
            } else {
                listState.scrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -200,
                )
            }
        } finally {
            isProgrammaticScroll = false
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = IconAssets.lyrics(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lyricsLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))

                if (isSelectionModeActive) {
                    Text(
                        text = selectedLineIndices.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val lines = lyricsResult?.parsedLines ?: emptyList()
                            if (selectedLineIndices.isEmpty() || lines.isEmpty()) return@IconButton
                            val selectedText = selectedLineIndices
                                .sorted()
                                .mapNotNull { index -> lines.getOrNull(index)?.text }
                            if (selectedText.isEmpty()) return@IconButton
                            sharePayload = buildSharePayloadFromSelectedLines(selectedText)
                            showShareDialog = true
                            isSelectionModeActive = false
                            selectedLineIndices = emptySet()
                        },
                        enabled = selectedLineIndices.isNotEmpty(),
                    ) {
                        Icon(IconAssets.share(), contentDescription = shareSelectedLabel)
                    }
                    IconButton(
                        onClick = {
                            isSelectionModeActive = false
                            selectedLineIndices = emptySet()
                        },
                    ) {
                        Icon(IconAssets.close(), contentDescription = closeLabel)
                    }
                }

                if (lyricsResult != null) {
                    Surface(
                        color = if (lyricsResult?.hasSyncedLyrics == true) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (lyricsResult?.hasSyncedLyrics == true) syncedLabel else plainLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                val currentProvider = lyricsResult?.provider
                if (!currentProvider.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = currentProvider,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = !isLoading && !isSyncingOptions,
                    ) {
                        Icon(IconAssets.moreVert(), contentDescription = moreOptionsLabel)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                val status = if (songRomanizeLyrics) "ON" else "OFF"
                                Text("$romanizeCurrentTrackLabel: $status")
                            },
                            onClick = {
                                menuExpanded = false
                                onSetSongRomanizeLyrics(!songRomanizeLyrics)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(editLabel) },
                            enabled = lyricsResult != null,
                            onClick = {
                                menuExpanded = false
                                editableLyricsText = lyricsResult?.let(::extractRawLyrics).orEmpty()
                                showEditDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(searchLabel) },
                            onClick = {
                                menuExpanded = false
                                showSearchDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(refetchLabel) },
                            onClick = {
                                menuExpanded = false
                                scope.launch { refetchLyrics() }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(lyricsResyncLabel) },
                            onClick = {
                                menuExpanded = false
                                scope.launch { syncLyricsOptions() }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(shareLyricsLabel) },
                            enabled = lyricsResult != null,
                            onClick = {
                                menuExpanded = false
                                sharePayload = lyricsResult?.let(::buildSharePayload)
                                showShareDialog = true
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isSyncingOptions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lyricsResyncLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (actionMessage != null) {
                Text(
                    text = actionMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(lyricsScrollLockConnection),
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    error != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = IconAssets.musicNote(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lyricsNotFoundLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    lyricsResult != null -> {
                        val lines = lyricsResult?.parsedLines ?: emptyList()
                        if (lines.isNotEmpty()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(lines) { index, line ->
                                    val nextTimestamp = lines.getOrNull(index + 1)?.timestampMs
                                        ?: (line.timestampMs + 2500L)
                                    val lineDuration = (nextTimestamp - line.timestampMs).coerceAtLeast(1L)
                                    val karaokeProgress = if (currentLineIndex == index) {
                                        ((renderPlaybackPositionMs - line.timestampMs) / lineDuration.toFloat())
                                            .coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    LyricLineItem(
                                        line = line,
                                        lineIndex = index,
                                        currentLineIndex = currentLineIndex,
                                        isActive = index == currentLineIndex,
                                        playbackPositionMs = renderPlaybackPositionMs,
                                        canSeek = lyricsClick && lyricsResult?.hasSyncedLyrics == true,
                                        animationStyle = lyricsAnimationStyle,
                                        smoothScroll = lyricsSmoothScroll,
                                        textAlign = textAlign,
                                        fontSizeSp = lyricsFontSize,
                                        customFont = customFont,
                                        karaokeProgress = karaokeProgress,
                                        isSelectionMode = isSelectionModeActive,
                                        isSelected = selectedLineIndices.contains(index),
                                        onToggleSelection = {
                                            selectedLineIndices = if (selectedLineIndices.contains(index)) {
                                                selectedLineIndices - index
                                            } else {
                                                selectedLineIndices + index
                                            }
                                        },
                                        onLongPress = {
                                            if (!isSelectionModeActive) {
                                                isSelectionModeActive = true
                                                selectedLineIndices = selectedLineIndices + index
                                            }
                                        },
                                        onClick = { onSeek(line.timestampMs) },
                                    )
                                }
                            }
                        } else if (lyricsResult?.plainLyrics != null) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                item {
                                    Text(
                                        text = lyricsResult?.plainLyrics ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = lyricsFontSize.sp,
                                        fontFamily = customFont ?: FontFamily.Default,
                                        textAlign = textAlign,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = IconAssets.lyrics(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lyricsEmptyLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (!isAutoScrollEnabled && lyricsResult?.hasSyncedLyrics == true && currentLineIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    scope.launch {
                                        isAutoScrollEnabled = true
                                        isProgrammaticScroll = true
                                        try {
                                            listState.animateScrollToItem(
                                                index = currentLineIndex,
                                                scrollOffset = -200,
                                            )
                                        } finally {
                                            isProgrammaticScroll = false
                                        }
                                    }
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = IconAssets.cached(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lyricsResyncLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(editLabel) },
            text = {
                OutlinedTextField(
                    value = editableLyricsText,
                    onValueChange = { editableLyricsText = it },
                    label = { Text(lyricsLabel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        scope.launch {
                            if (editableLyricsText.isBlank()) {
                                refetchLyrics()
                                return@launch
                            }
                            val updatedResult = DesktopLyricsService.saveOverrideLyrics(
                                title = title,
                                artist = artist,
                                durationSec = durationSec,
                                rawLyrics = editableLyricsText,
                                videoId = videoId,
                                provider = editLabel,
                            )
                            lyricsResult = updatedResult
                            actionMessage = editLabel
                            isSelectionModeActive = false
                            selectedLineIndices = emptySet()
                        }
                    },
                ) {
                    Text(saveLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(cancelLabel)
                }
            },
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text(searchLyricsLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchTitle,
                        onValueChange = { searchTitle = it },
                        label = { Text(songTitleLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = searchArtist,
                        onValueChange = { searchArtist = it },
                        label = { Text(songArtistsLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { openWebSearch() },
                    ) {
                        Text(searchOnlineLabel)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSearchDialog = false
                        scope.launch {
                            syncLyricsOptions(
                                queryTitle = searchTitle,
                                queryArtist = searchArtist,
                                includeOverride = false,
                            )
                        }
                    },
                ) {
                    Text(searchLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text(cancelLabel)
                }
            },
        )
    }

    if (showSyncOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showSyncOptionsDialog = false },
            title = { Text(lyricsResyncLabel) },
            text = {
                if (syncOptions.isEmpty()) {
                    Text(lyricsNotFoundLabel)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(syncOptions) { index, option ->
                            val preview = option.parsedLines
                                .take(2)
                                .joinToString(" ") { it.text }
                                .ifBlank {
                                    option.plainLyrics
                                        ?.lineSequence()
                                        ?.firstOrNull()
                                        .orEmpty()
                                }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .clickable {
                                        showSyncOptionsDialog = false
                                        scope.launch {
                                            applyLyricsOption(option)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = option.provider,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (index != syncOptions.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncOptionsDialog = false }) {
                    Text(closeLabel)
                }
            },
        )
    }

    if (showShareDialog && !sharePayload.isNullOrBlank()) {
        val payload = sharePayload.orEmpty()
        AlertDialog(
            onDismissRequest = {
                showShareDialog = false
                sharePayload = null
            },
            title = { Text(shareLyricsLabel) },
            text = {
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(payload))
                        actionMessage = copiedLabel
                        showShareDialog = false
                        sharePayload = null
                    },
                ) {
                    Text(shareAsTextLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val exported = exportLyricsAsImage(
                            lyricsText = payload,
                            trackTitle = title,
                            trackArtist = artist,
                        )
                        exported.onSuccess { imagePath ->
                            clipboard.setText(AnnotatedString(imagePath.toAbsolutePath().toString()))
                            actionMessage = copiedLabel
                        }.onFailure { throwable ->
                            actionMessage = throwable.message ?: lyricsNotFoundLabel
                        }
                        showShareDialog = false
                        sharePayload = null
                    },
                ) {
                    Text(shareAsImageLabel)
                }
            },
        )
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    lineIndex: Int,
    currentLineIndex: Int,
    isActive: Boolean,
    playbackPositionMs: Float,
    canSeek: Boolean,
    animationStyle: LyricsAnimationStylePreference,
    smoothScroll: Boolean,
    textAlign: TextAlign,
    fontSizeSp: Float,
    customFont: FontFamily?,
    karaokeProgress: Float,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
) {
    val distance = if (currentLineIndex < 0) Int.MAX_VALUE else abs(lineIndex - currentLineIndex)
    val targetAlpha = when (animationStyle) {
        LyricsAnimationStylePreference.NONE -> 1f
        LyricsAnimationStylePreference.FADE -> when (distance) {
            0 -> 1f
            1 -> 0.7f
            else -> 0.35f
        }
        LyricsAnimationStylePreference.SLIDE -> when (distance) {
            0 -> 1f
            1 -> 0.68f
            else -> 0.3f
        }
        LyricsAnimationStylePreference.GLOW -> when (distance) {
            0 -> 1f
            1 -> 0.78f
            else -> 0.45f
        }
        LyricsAnimationStylePreference.KARAOKE -> when (distance) {
            0 -> 1f
            1 -> 0.72f
            else -> 0.4f
        }
        LyricsAnimationStylePreference.APPLE -> when (distance) {
            0 -> 1f
            1 -> 0.8f
            2 -> 0.55f
            else -> 0.3f
        }
    }

    val targetScale = when {
        !isActive -> 1f
        animationStyle == LyricsAnimationStylePreference.NONE -> 1f
        animationStyle == LyricsAnimationStylePreference.FADE -> 1.02f
        animationStyle == LyricsAnimationStylePreference.SLIDE -> 1.03f
        animationStyle == LyricsAnimationStylePreference.KARAOKE -> 1.05f
        animationStyle == LyricsAnimationStylePreference.GLOW -> 1.08f
        animationStyle == LyricsAnimationStylePreference.APPLE -> 1.1f
        else -> 1f
    }

    val targetTranslateX = when (animationStyle) {
        LyricsAnimationStylePreference.SLIDE -> when (distance) {
            0 -> 0f
            1 -> -14f
            2 -> -22f
            else -> -28f
        }
        LyricsAnimationStylePreference.APPLE -> when (distance) {
            0 -> 0f
            1 -> -6f
            else -> -10f
        }
        else -> 0f
    }

    val animationSpec = if (smoothScroll || animationStyle == LyricsAnimationStylePreference.APPLE) {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )
    } else {
        tween(durationMillis = 220)
    }

    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = animationSpec, label = LyricAlphaLabel)
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = animationSpec, label = LyricScaleLabel)
    val translateX by animateFloatAsState(
        targetValue = targetTranslateX,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = LyricOffsetLabel,
    )
    val animatedKaraokeProgress by animateFloatAsState(
        targetValue = if (isActive) karaokeProgress else 0f,
        animationSpec = tween(durationMillis = 16, easing = LinearEasing),
        label = LyricKaraokeProgressLabel,
    )

    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(isActive, animationStyle) {
        val shouldPulse =
            isActive && (animationStyle == LyricsAnimationStylePreference.GLOW || animationStyle == LyricsAnimationStylePreference.APPLE)
        if (!shouldPulse) {
            pulseAnim.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
            )
            pulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
            )
        }
    }
    val pulseProgress = pulseAnim.value
    val pulsedScale = if (isActive &&
        (animationStyle == LyricsAnimationStylePreference.GLOW || animationStyle == LyricsAnimationStylePreference.APPLE)
    ) {
        scale * (1f + (0.02f * pulseProgress))
    } else {
        scale
    }

    val textColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        },
        label = LyricColorLabel,
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else -> Color.Transparent
        },
        label = LyricBackgroundLabel,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .scale(pulsedScale)
            .offset { IntOffset(translateX.roundToInt(), 0) }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .combinedClickable(
                enabled = true,
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else if (canSeek) {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongPress()
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val lyricStyle = if (isActive && (animationStyle == LyricsAnimationStylePreference.GLOW || animationStyle == LyricsAnimationStylePreference.APPLE)) {
            MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + (0.2f * pulseProgress)),
                    blurRadius = 16f + (8f * pulseProgress),
                ),
            )
        } else {
            MaterialTheme.typography.bodyLarge
        }

        val wordTimestamps = line.wordTimestamps
        if (isActive && animationStyle == LyricsAnimationStylePreference.KARAOKE && !wordTimestamps.isNullOrEmpty()) {
            val inactiveWordColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            val wordByWordText = buildAnnotatedString {
                wordTimestamps.forEachIndexed { wordIndex, word ->
                    val hasStarted = playbackPositionMs >= word.startMs
                    val wordStyle = if (hasStarted) {
                        SpanStyle(color = MaterialTheme.colorScheme.primary)
                    } else {
                        SpanStyle(color = inactiveWordColor)
                    }
                    withStyle(wordStyle) {
                        append(word.text)
                    }
                    if (wordIndex != wordTimestamps.lastIndex) {
                        append(" ")
                    }
                }
            }
            Text(
                text = wordByWordText,
                fontSize = fontSizeSp.sp,
                textAlign = textAlign,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = customFont ?: FontFamily.Default,
                style = lyricStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (isActive && animationStyle == LyricsAnimationStylePreference.KARAOKE) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.text,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontSize = fontSizeSp.sp,
                    textAlign = textAlign,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont ?: FontFamily.Default,
                    style = lyricStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = line.text,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = fontSizeSp.sp,
                    textAlign = textAlign,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont ?: FontFamily.Default,
                    style = lyricStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            clipRect(right = size.width * animatedKaraokeProgress) {
                                this@drawWithContent.drawContent()
                            }
                        },
                )
            }
        } else {
            Text(
                text = line.text,
                color = textColor,
                fontSize = fontSizeSp.sp,
                textAlign = textAlign,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                fontFamily = customFont ?: FontFamily.Default,
                style = lyricStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun exportLyricsAsImage(
    lyricsText: String,
    trackTitle: String,
    trackArtist: String,
): Result<Path> = runCatching {
    val cleanedLyrics = lyricsText.trim().ifBlank { "-" }
    val wrappedLyrics = wrapText(cleanedLyrics, maxCharsPerLine = 48)
    val header = "$trackTitle - $trackArtist"
    val footer = "Anitail Lyrics"

    val width = 1200
    val lineHeight = 50
    val headerHeight = 120
    val footerHeight = 90
    val contentHeight = wrappedLyrics.size * lineHeight
    val height = (headerHeight + contentHeight + footerHeight).coerceAtLeast(700)

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    graphics.color = java.awt.Color(18, 22, 28)
    graphics.fillRect(0, 0, width, height)

    graphics.color = java.awt.Color(255, 255, 255)
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 44)
    graphics.drawString(header, 64, 78)

    graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 34)
    var y = headerHeight
    wrappedLyrics.forEach { line ->
        graphics.drawString(line, 64, y)
        y += lineHeight
    }

    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
    graphics.color = java.awt.Color(166, 188, 216)
    graphics.drawString(footer, 64, height - 36)
    graphics.dispose()

    val outputDir = Paths.get(System.getProperty("user.home") ?: ".", ".anitail", "share")
    if (!Files.exists(outputDir)) {
        Files.createDirectories(outputDir)
    }
    val fileName = "lyrics_${System.currentTimeMillis()}.png"
    val output = outputDir.resolve(fileName)
    ImageIO.write(image, "png", output.toFile())
    output
}

private fun wrapText(
    text: String,
    maxCharsPerLine: Int,
): List<String> {
    val result = mutableListOf<String>()
    text.lines().forEach { sourceLine ->
        val words = sourceLine.split(" ")
            .filter { word -> word.isNotBlank() }
        if (words.isEmpty()) {
            result += ""
            return@forEach
        }
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                current.append(' ').append(word)
            } else {
                result += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) {
            result += current.toString()
        }
    }
    return result
}

private const val LyricColorLabel = "lyric_color"
private const val LyricBackgroundLabel = "lyric_bg"
private const val LyricAlphaLabel = "lyric_alpha"
private const val LyricScaleLabel = "lyric_scale"
private const val LyricOffsetLabel = "lyric_offset"
private const val LyricKaraokeProgressLabel = "lyric_karaoke_progress"
private const val KaraokeLeadMs = 0L
private const val PlaybackJitterToleranceMs = 140f
