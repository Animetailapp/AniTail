package com.anitail.desktop.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.lyrics.DesktopLyricsService
import com.anitail.desktop.lyrics.LyricLine
import com.anitail.desktop.lyrics.LyricsResult
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.LyricsAnimationStylePreference
import com.anitail.desktop.storage.LyricsPositionPreference
import com.anitail.desktop.ui.IconAssets
import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lyrics panel component for displaying synchronized lyrics.
 */
@Composable
fun LyricsPanel(
    title: String,
    artist: String,
    videoId: String? = null,
    artworkUrl: String? = null,
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
    val lyricsGlowEffect by preferences.lyricsGlowEffect.collectAsState()
    val lyricsRomanizeJapanese by preferences.lyricsRomanizeJapanese.collectAsState()
    val lyricsRomanizeKorean by preferences.lyricsRomanizeKorean.collectAsState()
    val lyricsRomanizeRussian by preferences.lyricsRomanizeRussian.collectAsState()
    val lyricsRomanizeUkrainian by preferences.lyricsRomanizeUkrainian.collectAsState()
    val lyricsRomanizeSerbian by preferences.lyricsRomanizeSerbian.collectAsState()
    val lyricsRomanizeBulgarian by preferences.lyricsRomanizeBulgarian.collectAsState()
    val lyricsRomanizeBelarusian by preferences.lyricsRomanizeBelarusian.collectAsState()
    val lyricsRomanizeKyrgyz by preferences.lyricsRomanizeKyrgyz.collectAsState()
    val lyricsRomanizeCyrillicByLine by preferences.lyricsRomanizeCyrillicByLine.collectAsState()

    var lyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchOptions by remember { mutableStateOf<List<LyricsResult>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var editableLyricsText by remember { mutableStateOf("") }
    var searchTitle by remember { mutableStateOf(title) }
    var searchArtist by remember { mutableStateOf(artist) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    var selectedLineIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var shareDialogData by remember { mutableStateOf<LyricsShareDialogData?>(null) }
    var previewBackgroundColor by remember { mutableStateOf(Color(0xFF242424)) }
    var previewTextColor by remember { mutableStateOf(Color.White) }
    var previewSecondaryTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.7f)) }
    val paletteColors = remember { mutableStateListOf<Color>() }
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
    val appNameLabel = stringResource("app_name")
    val customizeColorsLabel = stringResource("customize_colors")
    val backgroundColorLabel = stringResource("background_color")
    val textColorLabel = stringResource("text_color")
    val secondaryTextColorLabel = stringResource("secondary_text_color")
    val generatingImageLabel = stringResource("generating_image")
    val pleaseWaitLabel = stringResource("please_wait")
    val shareLabel = stringResource("share")

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

    LaunchedEffect(showColorPickerDialog, artworkUrl) {
        if (!showColorPickerDialog) return@LaunchedEffect
        val extracted = extractPaletteColorsFromArtwork(artworkUrl)
        paletteColors.clear()
        paletteColors.addAll(extracted)
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
    val romanizationOptions = remember(
        lyricsRomanizeJapanese,
        lyricsRomanizeKorean,
        lyricsRomanizeRussian,
        lyricsRomanizeUkrainian,
        lyricsRomanizeSerbian,
        lyricsRomanizeBulgarian,
        lyricsRomanizeBelarusian,
        lyricsRomanizeKyrgyz,
        lyricsRomanizeCyrillicByLine,
    ) {
        LyricsRomanizationOptions(
            japanese = lyricsRomanizeJapanese,
            korean = lyricsRomanizeKorean,
            russian = lyricsRomanizeRussian,
            ukrainian = lyricsRomanizeUkrainian,
            serbian = lyricsRomanizeSerbian,
            bulgarian = lyricsRomanizeBulgarian,
            belarusian = lyricsRomanizeBelarusian,
            kyrgyz = lyricsRomanizeKyrgyz,
            cyrillicByLine = lyricsRomanizeCyrillicByLine,
        )
    }
    val isRomanizationEnabled = songRomanizeLyrics && romanizationOptions.isAnyLanguageEnabled()
    val useGlowEffect = (lyricsGlowEffect && lyricsAnimationStyle != LyricsAnimationStylePreference.NONE) ||
        lyricsAnimationStyle == LyricsAnimationStylePreference.GLOW ||
        lyricsAnimationStyle == LyricsAnimationStylePreference.APPLE

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

    fun extractRawLyrics(result: LyricsResult): String =
        result.syncedLyrics
            ?: result.plainLyrics
            ?: result.parsedLines.joinToString("\n") { line -> line.text }

    fun extractLyricsBody(result: LyricsResult): String {
        val plain = result.plainLyrics.orEmpty().trim()
        if (plain.isNotBlank()) return plain

        val parsed = result.parsedLines
            .map { line -> line.text.trim() }
            .filter { line -> line.isNotBlank() }
            .joinToString("\n")
            .trim()
        if (parsed.isNotBlank()) return parsed

        val syncedFallback = result.syncedLyrics
            .orEmpty()
            .lineSequence()
            .map { raw ->
                raw
                    .replace(Regex("\\[[0-9]{2}:[0-9]{2}\\.[0-9]{2,3}]"), "")
                    .replace(Regex("<[0-9]{1,2}:[0-9]{2}\\.[0-9]{2,3}>\\s*"), "")
                    .trim()
            }
            .filter { line -> line.isNotBlank() }
            .joinToString("\n")
            .trim()

        return syncedFallback.ifBlank { "-" }
    }

    fun buildShareDialogData(result: LyricsResult): LyricsShareDialogData {
        return LyricsShareDialogData(
            lyricsText = extractLyricsBody(result).ifBlank { "-" },
            songTitle = title,
            artists = artist,
        )
    }

    fun buildShareDialogDataFromSelectedLines(lines: List<String>): LyricsShareDialogData {
        val body = lines.joinToString("\n").trim().ifBlank { "-" }
        return LyricsShareDialogData(
            lyricsText = body,
            songTitle = title,
            artists = artist,
        )
    }

    fun buildShareTextPayload(shareData: LyricsShareDialogData): String {
        val songLink = videoId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" }.orEmpty()
        return buildString {
            append("\"${shareData.lyricsText}\"")
            append("\n\n")
            append("${shareData.songTitle} - ${shareData.artists}")
            if (songLink.isNotBlank()) {
                append("\n")
                append(songLink)
            }
        }
    }

    fun previewForOption(option: LyricsResult): String {
        return option.parsedLines
            .take(2)
            .joinToString(" ") { it.text }
            .ifBlank {
                option.plainLyrics
                    ?.lineSequence()
                    ?.firstOrNull()
                    .orEmpty()
            }
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

    suspend fun searchLyricsOptions(
        queryTitle: String,
        queryArtist: String,
    ) {
        if (queryTitle.isBlank()) {
            searchOptions = emptyList()
            searchMessage = lyricsNotFoundLabel
            return
        }
        isSearchLoading = true
        searchMessage = null
        error = null
        try {
            val options = DesktopLyricsService.getAllLyricsOptions(
                title = queryTitle,
                artist = queryArtist,
                durationSec = durationSec,
                videoId = videoId,
                includeOverride = false,
            )
                .distinctBy { candidate ->
                    buildString {
                        append(candidate.provider)
                        append("::")
                        append(candidate.syncedLyrics ?: candidate.plainLyrics.orEmpty())
                    }
                }

            if (options.isEmpty()) {
                searchOptions = emptyList()
                searchMessage = lyricsNotFoundLabel
                return
            }

            searchOptions = options
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            searchOptions = emptyList()
            searchMessage = throwable.message ?: lyricsNotFoundLabel
        } finally {
            isSearchLoading = false
        }
    }

    LaunchedEffect(title, artist, videoId, durationSec) {
        menuExpanded = false
        showEditDialog = false
        showSearchDialog = false
        showShareDialog = false
        showColorPickerDialog = false
        showProgressDialog = false
        searchOptions = emptyList()
        searchMessage = null
        isSearchLoading = false
        actionMessage = null
        lyricsResult = null
        editableLyricsText = ""
        searchTitle = title
        searchArtist = artist
        isSelectionModeActive = false
        selectedLineIndices = emptySet()
        shareDialogData = null
        paletteColors.clear()
        isAutoScrollEnabled = true
        refreshLyrics(showLoadingState = true)
    }

    var renderPlaybackPositionMs by remember { mutableFloatStateOf((currentPositionMs + KaraokeLeadMs).toFloat()) }
    var reportedPlaybackPositionMs by remember { mutableFloatStateOf((currentPositionMs + KaraokeLeadMs).toFloat()) }
    var reportedAtFrameNanos by remember { mutableLongStateOf(0L) }
    var romanizedLineByIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

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
    LaunchedEffect(lyricsResult, isRomanizationEnabled, romanizationOptions) {
        val lines = lyricsResult?.parsedLines ?: emptyList()
        if (!isRomanizationEnabled || lines.isEmpty()) {
            romanizedLineByIndex = emptyMap()
            return@LaunchedEffect
        }
        romanizedLineByIndex = withContext(Dispatchers.Default) {
            val fullLyricsText = lines.joinToString(separator = "\n") { lyricLine -> lyricLine.text }
            val shouldRomanizeWholeCyrillic = shouldRomanizeCyrillicForLyrics(
                lyrics = fullLyricsText,
                options = romanizationOptions,
            )
            lines.mapIndexedNotNull { index, lyricLine ->
                val romanized = romanizeLyricLine(
                    line = lyricLine.text,
                    options = romanizationOptions,
                    shouldRomanizeWholeCyrillic = shouldRomanizeWholeCyrillic,
                )
                romanized
                    ?.takeIf { it.isNotBlank() && !it.equals(lyricLine.text, ignoreCase = false) }
                    ?.let { index to it }
            }.toMap()
        }
    }
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
                            shareDialogData = buildShareDialogDataFromSelectedLines(selectedText)
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
                        enabled = !isLoading,
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
                                searchOptions = emptyList()
                                searchMessage = null
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
                            text = { Text(shareLyricsLabel) },
                            enabled = lyricsResult != null,
                            onClick = {
                                menuExpanded = false
                                shareDialogData = lyricsResult?.let(::buildShareDialogData)
                                showShareDialog = shareDialogData != null
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (actionMessage != null) {
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
                                        romanizedText = romanizedLineByIndex[index],
                                        lineIndex = index,
                                        currentLineIndex = currentLineIndex,
                                        isActive = index == currentLineIndex,
                                        playbackPositionMs = renderPlaybackPositionMs,
                                        canSeek = lyricsClick && lyricsResult?.hasSyncedLyrics == true,
                                        animationStyle = lyricsAnimationStyle,
                                        useGlowEffect = useGlowEffect,
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

                    if (isSearchLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = searchLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!searchMessage.isNullOrBlank()) {
                        Text(
                            text = searchMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (searchOptions.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(searchOptions) { index, option ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .clickable {
                                            scope.launch {
                                                applyLyricsOption(option)
                                                showSearchDialog = false
                                                searchOptions = emptyList()
                                                searchMessage = null
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
                                            text = previewForOption(option),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (index != searchOptions.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            searchLyricsOptions(
                                queryTitle = searchTitle,
                                queryArtist = searchArtist,
                            )
                        }
                    },
                    enabled = !isSearchLoading && searchTitle.isNotBlank(),
                ) {
                    Text(searchLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSearchDialog = false
                        searchOptions = emptyList()
                        searchMessage = null
                    },
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }

    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            text = {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = generatingImageLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pleaseWaitLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showShareDialog && shareDialogData != null) {
        val shareData = shareDialogData!!
        AlertDialog(
            onDismissRequest = {
                showShareDialog = false
                shareDialogData = null
            },
            title = { Text(shareLyricsLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(buildShareTextPayload(shareData)))
                                actionMessage = copiedLabel
                                showShareDialog = false
                                shareDialogData = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = IconAssets.share(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = shareAsTextLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShareDialog = false
                                showColorPickerDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = IconAssets.share(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = shareAsImageLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShareDialog = false
                        shareDialogData = null
                    },
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }

    if (showColorPickerDialog && shareDialogData != null) {
        val shareData = shareDialogData!!
        val backgroundCandidates = (paletteColors + listOf(
            Color(0xFF242424),
            Color(0xFF121212),
            Color.White,
            Color.Black,
            Color(0xFFF5F5F5),
        )).distinctBy { it.toArgb() }.take(8)
        val textCandidates = (paletteColors + listOf(
            Color.White,
            Color.Black,
            Color(0xFF1DB954),
        )).distinctBy { it.toArgb() }.take(8)
        val secondaryCandidates = (paletteColors.map { it.copy(alpha = 0.7f) } + listOf(
            Color.White.copy(alpha = 0.7f),
            Color.Black.copy(alpha = 0.7f),
            Color(0xFF1DB954),
        )).distinctBy { it.toArgb() }.take(8)

        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            title = { Text(customizeColorsLabel) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .padding(4.dp),
                    ) {
                        LyricsImagePreviewCard(
                            lyricText = shareData.lyricsText,
                            songTitle = shareData.songTitle,
                            artistName = shareData.artists,
                            artworkUrl = artworkUrl,
                            backgroundColor = previewBackgroundColor,
                            textColor = previewTextColor,
                            secondaryTextColor = previewSecondaryTextColor,
                        )
                    }

                    Text(text = backgroundColorLabel, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        backgroundCandidates.forEach { color ->
                            ColorOptionSwatch(
                                color = color,
                                isSelected = previewBackgroundColor == color,
                                onClick = { previewBackgroundColor = color },
                            )
                        }
                    }

                    Text(text = textColorLabel, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textCandidates.forEach { color ->
                            ColorOptionSwatch(
                                color = color,
                                isSelected = previewTextColor == color,
                                onClick = { previewTextColor = color },
                            )
                        }
                    }

                    Text(text = secondaryTextColorLabel, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        secondaryCandidates.forEach { color ->
                            ColorOptionSwatch(
                                color = color,
                                isSelected = previewSecondaryTextColor == color,
                                onClick = { previewSecondaryTextColor = color },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showColorPickerDialog = false
                        showProgressDialog = true
                        scope.launch {
                            val exportResult = exportLyricsAsImageAndroidStyle(
                                lyricsText = shareData.lyricsText,
                                trackTitle = shareData.songTitle,
                                trackArtist = shareData.artists,
                                appName = appNameLabel,
                                coverArtUrl = artworkUrl,
                                width = ShareImageRenderSizePx,
                                height = ShareImageRenderSizePx,
                                backgroundColor = previewBackgroundColor,
                                textColor = previewTextColor,
                                secondaryTextColor = previewSecondaryTextColor,
                            )
                            exportResult.onSuccess { imagePath ->
                                clipboard.setText(AnnotatedString(imagePath.toAbsolutePath().toString()))
                                if (Desktop.isDesktopSupported()) {
                                    runCatching { Desktop.getDesktop().open(imagePath.toFile()) }
                                }
                                actionMessage = "${shareAsImageLabel}: ${imagePath.fileName}"
                                shareDialogData = null
                            }.onFailure { throwable ->
                                actionMessage = throwable.message ?: lyricsNotFoundLabel
                            }
                            showProgressDialog = false
                        }
                    },
                ) {
                    Text(shareLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showColorPickerDialog = false },
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    romanizedText: String?,
    lineIndex: Int,
    currentLineIndex: Int,
    isActive: Boolean,
    playbackPositionMs: Float,
    canSeek: Boolean,
    animationStyle: LyricsAnimationStylePreference,
    useGlowEffect: Boolean,
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
    LaunchedEffect(isActive, animationStyle, useGlowEffect) {
        val shouldPulse = isActive && useGlowEffect
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
    val pulsedScale = if (isActive && useGlowEffect) {
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
        val lyricStyle = if (isActive && useGlowEffect) {
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

        if (!romanizedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = romanizedText,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
                fontSize = (fontSizeSp * 0.72f).sp,
                textAlign = textAlign,
                fontWeight = FontWeight.Normal,
                fontFamily = customFont ?: FontFamily.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorOptionSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun rememberAdjustedFontSizeForPreview(
    text: String,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    initialFontSize: TextUnit = 26.sp,
    minFontSize: TextUnit = 14.sp,
    style: TextStyle = TextStyle.Default,
): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var calculatedFontSize by remember(text, maxWidth, maxHeight, style, density) {
        mutableStateOf(initialFontSize)
    }

    LaunchedEffect(text, maxWidth, maxHeight, style, density) {
        val targetWidthPx = with(density) { maxWidth.toPx() * 0.92f }
        val targetHeightPx = with(density) { maxHeight.toPx() * 0.92f }
        if (text.isBlank()) {
            calculatedFontSize = minFontSize
            return@LaunchedEffect
        }

        var minSize = minFontSize.value
        var maxSize = initialFontSize.value
        var bestFit = minSize
        var iterations = 0

        while (minSize <= maxSize && iterations < 20) {
            iterations++
            val midSize = (minSize + maxSize) / 2
            val midSizeSp = midSize.sp
            val result = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = midSizeSp),
            )
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                bestFit = midSize
                minSize = midSize + 0.5f
            } else {
                maxSize = midSize - 0.5f
            }
        }

        calculatedFontSize = if (bestFit < minFontSize.value) minFontSize else bestFit.sp
    }

    return calculatedFontSize
}

@Composable
private fun LyricsImagePreviewCard(
    lyricText: String,
    songTitle: String,
    artistName: String,
    artworkUrl: String?,
    backgroundColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource("app_name")
    Box(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RemoteImage(
                    url = artworkUrl,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = songTitle,
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artistName,
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val textStyle = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.005.em,
                )
                val initialSize = when {
                    lyricText.length < 50 -> 28.sp
                    lyricText.length < 100 -> 26.sp
                    lyricText.length < 200 -> 16.sp
                    lyricText.length < 300 -> 14.sp
                    else -> 29.sp
                }

                val dynamicFontSize = rememberAdjustedFontSizeForPreview(
                    text = lyricText,
                    maxWidth = maxWidth - 16.dp,
                    maxHeight = maxHeight - 8.dp,
                    initialFontSize = initialSize,
                    minFontSize = 22.sp,
                    style = textStyle,
                )

                Text(
                    text = lyricText,
                    style = textStyle.copy(
                        fontSize = dynamicFontSize,
                        lineHeight = dynamicFontSize.value.sp * 1.2f,
                    ),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = secondaryTextColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = IconAssets.icAni(),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = backgroundColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appName,
                    color = secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private suspend fun extractPaletteColorsFromArtwork(artworkUrl: String?): List<Color> = withContext(Dispatchers.IO) {
    val image = loadRemoteImageBuffered(artworkUrl) ?: return@withContext emptyList()
    val colorCounts = mutableMapOf<Int, Int>()
    val widthStep = max(1, image.width / 80)
    val heightStep = max(1, image.height / 80)

    for (y in 0 until image.height step heightStep) {
        for (x in 0 until image.width step widthStep) {
            val argb = image.getRGB(x, y)
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 160) continue
            val red = (argb ushr 16) and 0xFF
            val green = (argb ushr 8) and 0xFF
            val blue = argb and 0xFF
            val hsv = java.awt.Color.RGBtoHSB(red, green, blue, null)
            if (hsv[1] <= 0.2f) continue

            val quantizedRed = (red / 16) * 16
            val quantizedGreen = (green / 16) * 16
            val quantizedBlue = (blue / 16) * 16
            val key = (quantizedRed shl 16) or (quantizedGreen shl 8) or quantizedBlue
            colorCounts[key] = (colorCounts[key] ?: 0) + 1
        }
    }

    colorCounts.entries
        .sortedByDescending { it.value }
        .take(5)
        .map { entry -> Color(0xFF000000.toInt() or entry.key) }
}

private fun exportLyricsAsImageAndroidStyle(
    lyricsText: String,
    trackTitle: String,
    trackArtist: String,
    appName: String,
    coverArtUrl: String?,
    width: Int,
    height: Int,
    backgroundColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
): Result<Path> = runCatching {
    val image = renderLyricsShareImage(
        lyricsText = lyricsText,
        trackTitle = trackTitle,
        trackArtist = trackArtist,
        appName = appName,
        coverArtUrl = coverArtUrl,
        width = width,
        height = height,
        backgroundColor = backgroundColor,
        textColor = textColor,
        secondaryTextColor = secondaryTextColor,
    ).getOrThrow()

    val outputDir = Paths.get(System.getProperty("user.home") ?: ".", ".anitail", "share")
    if (!Files.exists(outputDir)) {
        Files.createDirectories(outputDir)
    }
    val fileName = "lyrics_${System.currentTimeMillis()}.png"
    val output = outputDir.resolve(fileName)
    ImageIO.write(image, "png", output.toFile())
    output
}

private fun renderLyricsShareImage(
    lyricsText: String,
    trackTitle: String,
    trackArtist: String,
    appName: String,
    coverArtUrl: String?,
    width: Int,
    height: Int,
    backgroundColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
): Result<BufferedImage> = runCatching {
    val cardSize = max(320, min(width, height))
    val scale = cardSize / 440f
    val image = BufferedImage(cardSize, cardSize, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    val bgColor = backgroundColor.toAwtColor()
    val mainTextColor = textColor.toAwtColor()
    val secondaryColor = secondaryTextColor.toAwtColor()

    val cornerRadius = 20f * scale
    graphics.color = bgColor
    graphics.fillRoundRect(0, 0, cardSize, cardSize, cornerRadius.roundToInt() * 2, cornerRadius.roundToInt() * 2)

    val coverArtBitmap = loadRemoteImageBuffered(coverArtUrl)
    val horizontalPadding = 24f * scale
    val verticalPadding = 24f * scale
    val imageCornerRadius = 10f * scale
    val coverArtSize = 54f * scale
    val headerGap = 16f * scale
    val headerTop = verticalPadding

    if (coverArtBitmap != null) {
        val drawX = horizontalPadding.roundToInt()
        val drawY = headerTop.roundToInt()
        val drawSize = coverArtSize.roundToInt()
        val oldClip = graphics.clip
        graphics.clip = java.awt.geom.RoundRectangle2D.Float(
            drawX.toFloat(),
            drawY.toFloat(),
            drawSize.toFloat(),
            drawSize.toFloat(),
            imageCornerRadius * 2f,
            imageCornerRadius * 2f,
        )
        graphics.drawImage(coverArtBitmap, drawX, drawY, drawSize, drawSize, null)
        graphics.clip = oldClip
    }

    val titleFont = Font(Font.SANS_SERIF, Font.BOLD, max(12, (17f * scale).roundToInt()))
    val artistFont = Font(Font.SANS_SERIF, Font.PLAIN, max(10, (14f * scale).roundToInt()))
    val contentWidth = cardSize - (2f * horizontalPadding)
    val textMaxWidth = max(80, (contentWidth - coverArtSize - headerGap).roundToInt())
    val textStartX = (horizontalPadding + coverArtSize + headerGap).roundToInt()

    graphics.font = titleFont
    val titleMetrics = graphics.fontMetrics
    val titleText = ellipsizeToWidth(trackTitle, titleMetrics, textMaxWidth)

    graphics.font = artistFont
    val artistMetrics = graphics.fontMetrics
    val artistText = ellipsizeToWidth(trackArtist, artistMetrics, textMaxWidth)

    val imageCenter = headerTop + coverArtSize / 2f
    val textBlockHeight = titleMetrics.height + artistMetrics.height + 8f
    val textBlockTop = imageCenter - textBlockHeight / 2f

    graphics.color = mainTextColor
    graphics.font = titleFont
    graphics.drawString(titleText, textStartX, (textBlockTop + titleMetrics.ascent).roundToInt())
    graphics.color = secondaryColor
    graphics.font = artistFont
    graphics.drawString(
        artistText,
        textStartX,
        (textBlockTop + titleMetrics.height + 8f + artistMetrics.ascent).roundToInt(),
    )

    val footerIconSize = 20f * scale
    val footerTextSize = max(10, (14f * scale).roundToInt())
    val footerHeight = max(footerIconSize, footerTextSize.toFloat()) + 2f
    val footerY = cardSize - verticalPadding - footerHeight

    val lyricsTop = (headerTop + coverArtSize + (8f * scale))
    val lyricsBottom = footerY - (8f * scale)
    val availableLyricsHeight = max(80f, lyricsBottom - lyricsTop)
    val lyricsMaxWidth = max(80f, contentWidth - (16f * scale))
    val lyricsStartX = ((cardSize - lyricsMaxWidth) / 2f)
    val targetWidth = lyricsMaxWidth * 0.92f
    val targetHeight = availableLyricsHeight * 0.92f

    val safeLyricsText = lyricsText.trim().ifBlank { "-" }
    val initialBaseSp = when {
        safeLyricsText.length < 50 -> 28f
        safeLyricsText.length < 100 -> 26f
        safeLyricsText.length < 200 -> 16f
        safeLyricsText.length < 300 -> 14f
        else -> 29f
    }
    val minBaseSp = 22f
    var minFontSizePx = minBaseSp * scale
    var maxFontSizePx = initialBaseSp * scale
    var bestFontSizePx = minFontSizePx
    var wrappedLines = emptyList<String>()
    var iterations = 0
    while (minFontSizePx <= maxFontSizePx && iterations < 20) {
        iterations++
        val testFontSizePx = (minFontSizePx + maxFontSizePx) / 2f
        val testFont = Font(Font.SANS_SERIF, Font.BOLD, max(8, testFontSizePx.roundToInt()))
        graphics.font = testFont
        val metrics = graphics.fontMetrics
        val lines = wrapTextToPixelWidth(safeLyricsText, metrics, targetWidth.roundToInt()).take(10)
        val lineHeight = max(1f, metrics.height * 1.2f)
        val totalHeight = lines.size * lineHeight
        val maxLineWidth = lines.maxOfOrNull { line -> metrics.stringWidth(line) } ?: 0
        val fits = lines.isNotEmpty() && totalHeight <= targetHeight && maxLineWidth <= targetWidth
        if (fits) {
            bestFontSizePx = testFontSizePx
            wrappedLines = lines
            minFontSizePx = testFontSizePx + 0.5f
        } else {
            maxFontSizePx = testFontSizePx - 0.5f
        }
    }

    if (wrappedLines.isEmpty()) {
        bestFontSizePx = minBaseSp * scale
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, max(8, bestFontSizePx.roundToInt()))
        val fallbackMetrics = graphics.fontMetrics
        val fallbackWrapped = wrapTextToPixelWidth(
            text = safeLyricsText,
            metrics = fallbackMetrics,
            maxWidth = targetWidth.roundToInt(),
        )
        wrappedLines = when {
            fallbackWrapped.isEmpty() -> listOf("-")
            fallbackWrapped.size <= 10 -> fallbackWrapped
            else -> {
                val clipped = fallbackWrapped.take(10).toMutableList()
                clipped[9] = ellipsizeToWidth(
                    text = clipped[9],
                    metrics = fallbackMetrics,
                    maxWidth = targetWidth.roundToInt(),
                )
                clipped
            }
        }
    }

    graphics.color = mainTextColor
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, max(8, bestFontSizePx.roundToInt()))
    val lyricsMetrics = graphics.fontMetrics
    val lineHeight = max(1f, lyricsMetrics.height * 1.2f)
    val lyricsBlockHeight = wrappedLines.size * lineHeight
    var lineY = lyricsTop + ((availableLyricsHeight - lyricsBlockHeight) / 2f) + lyricsMetrics.ascent
    wrappedLines.forEach { line ->
        val lineWidth = lyricsMetrics.stringWidth(line)
        val lineX = lyricsStartX + ((lyricsMaxWidth - lineWidth) / 2f)
        graphics.drawString(line, lineX.roundToInt(), lineY.roundToInt())
        lineY += lineHeight
    }

    drawAppLogo(
        graphics = graphics,
        cardSize = cardSize,
        padding = horizontalPadding,
        secondaryColor = secondaryColor,
        backgroundColor = bgColor,
        appName = appName,
    )
    graphics.dispose()
    image
}

private fun drawAppLogo(
    graphics: Graphics2D,
    cardSize: Int,
    padding: Float,
    secondaryColor: java.awt.Color,
    backgroundColor: java.awt.Color,
    appName: String,
) {
    val logoSize = max(18, (cardSize * 0.05f).roundToInt())
    val circleRadius = logoSize * 0.55f
    val circleX = padding + circleRadius
    val circleY = cardSize - padding - circleRadius
    val logoX = (circleX - logoSize / 2f).roundToInt()
    val logoY = (circleY - logoSize / 2f).roundToInt()

    graphics.color = secondaryColor
    graphics.fillOval(
        (circleX - circleRadius).roundToInt(),
        (circleY - circleRadius).roundToInt(),
        (circleRadius * 2f).roundToInt(),
        (circleRadius * 2f).roundToInt(),
    )

    loadBundledLogoImage()?.let { logo ->
        val glyphLogo = extractLogoGlyph(logo, backgroundColor)
        graphics.drawImage(glyphLogo, logoX, logoY, logoSize, logoSize, null)
    }

    val appNameFont = Font(Font.SANS_SERIF, Font.BOLD, max(12, (cardSize * 0.030f).roundToInt()))
    graphics.font = appNameFont
    graphics.color = secondaryColor
    val textX = (padding + circleRadius * 2f + 12f).roundToInt()
    val textY = (circleY + (graphics.fontMetrics.ascent * 0.35f)).roundToInt()
    graphics.drawString(appName, textX, textY)
}

private fun loadRemoteImageBuffered(url: String?): BufferedImage? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        URL(url).openStream().use { stream -> ImageIO.read(stream) }
    }.getOrNull() ?: runCatching {
        val bytes = URL(url).readBytes()
        val skiaImage = SkiaImage.makeFromEncoded(bytes)
        val pngData = skiaImage.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG, 100) ?: return@runCatching null
        ImageIO.read(ByteArrayInputStream(pngData.bytes))
    }.getOrNull()
}

private fun loadBundledLogoImage(): BufferedImage? {
    val classLoader = Thread.currentThread().contextClassLoader ?: return null
    val logoStream = classLoader.getResourceAsStream("drawable/ic_anitail.png") ?: return null
    return runCatching { logoStream.use { ImageIO.read(it) } }.getOrNull()
}

private fun extractLogoGlyph(source: BufferedImage, tintColor: java.awt.Color): BufferedImage {
    val glyph = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val argb = source.getRGB(x, y)
            val alpha = (argb ushr 24) and 0xFF
            if (alpha == 0) continue
            val red = (argb ushr 16) and 0xFF
            val green = (argb ushr 8) and 0xFF
            val blue = argb and 0xFF

            // Keep only the bright logo glyph from launcher artwork.
            val luminance = (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
            if (luminance < 185.0) continue

            val tintedArgb = (alpha shl 24) or
                (tintColor.red shl 16) or
                (tintColor.green shl 8) or
                tintColor.blue
            glyph.setRGB(x, y, tintedArgb)
        }
    }
    return glyph
}

private fun ellipsizeToWidth(
    text: String,
    metrics: java.awt.FontMetrics,
    maxWidth: Int,
): String {
    if (metrics.stringWidth(text) <= maxWidth) return text
    val ellipsis = "..."
    if (metrics.stringWidth(ellipsis) > maxWidth) return ellipsis
    var end = text.length
    while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
        end--
    }
    return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
}

private fun wrapTextToPixelWidth(
    text: String,
    metrics: java.awt.FontMetrics,
    maxWidth: Int,
): List<String> {
    val wrapped = mutableListOf<String>()
    text.lines().forEach { line ->
        val words = line.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) {
            wrapped += ""
            return@forEach
        }
        var currentLine = words.first()
        words.drop(1).forEach { word ->
            val candidate = "$currentLine $word"
            if (metrics.stringWidth(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                wrapped += currentLine
                currentLine = word
            }
        }
        wrapped += currentLine
    }
    return wrapped
}

private fun Color.toAwtColor(): java.awt.Color {
    val argb = toArgb()
    val alpha = (argb ushr 24) and 0xFF
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    return java.awt.Color(red, green, blue, alpha)
}

private data class LyricsShareDialogData(
    val lyricsText: String,
    val songTitle: String,
    val artists: String,
)

private data class LyricsRomanizationOptions(
    val japanese: Boolean,
    val korean: Boolean,
    val russian: Boolean,
    val ukrainian: Boolean,
    val serbian: Boolean,
    val bulgarian: Boolean,
    val belarusian: Boolean,
    val kyrgyz: Boolean,
    val cyrillicByLine: Boolean,
) {
    fun isAnyLanguageEnabled(): Boolean {
        return japanese || korean || russian || ukrainian || serbian || bulgarian || belarusian || kyrgyz
    }

    fun isAnyCyrillicEnabled(): Boolean {
        return russian || ukrainian || serbian || bulgarian || belarusian || kyrgyz
    }
}

private fun romanizeLyricLine(
    line: String,
    options: LyricsRomanizationOptions,
    shouldRomanizeWholeCyrillic: Boolean,
): String? {
    if (line.isBlank()) return null
    if (options.japanese && isJapaneseText(line) && !isChineseText(line)) {
        return romanizeJapaneseText(line)
    }
    if (options.korean && isKoreanText(line)) {
        return romanizeKoreanText(line)
    }
    if (!containsCyrillic(line)) return null
    if (!shouldRomanizeCyrillicLine(line, options, shouldRomanizeWholeCyrillic)) return null
    return romanizeCyrillicText(line)
}

private fun shouldRomanizeCyrillicForLyrics(
    lyrics: String,
    options: LyricsRomanizationOptions,
): Boolean {
    if (!options.isAnyCyrillicEnabled() || lyrics.isBlank() || options.cyrillicByLine) return false
    return (options.russian && isRussianText(lyrics)) ||
        (options.ukrainian && isUkrainianText(lyrics)) ||
        (options.serbian && isSerbianText(lyrics)) ||
        (options.bulgarian && isBulgarianText(lyrics)) ||
        (options.belarusian && isBelarusianText(lyrics)) ||
        (options.kyrgyz && isKyrgyzText(lyrics))
}

private fun shouldRomanizeCyrillicLine(
    line: String,
    options: LyricsRomanizationOptions,
    shouldRomanizeWholeCyrillic: Boolean,
): Boolean {
    if (!options.isAnyCyrillicEnabled()) return false
    if (!options.cyrillicByLine) return shouldRomanizeWholeCyrillic
    return (options.russian && isRussianText(line)) ||
        (options.ukrainian && isUkrainianText(line)) ||
        (options.serbian && isSerbianText(line)) ||
        (options.bulgarian && isBulgarianText(line)) ||
        (options.belarusian && isBelarusianText(line)) ||
        (options.kyrgyz && isKyrgyzText(line))
}

private fun containsCyrillic(text: String): Boolean {
    return text.any { char -> char.code in 0x0400..0x04FF }
}

private fun isJapaneseText(text: String): Boolean {
    return text.any { char ->
        (char in '\u3040'..'\u309F') ||
            (char in '\u30A0'..'\u30FF') ||
            (char in '\u4E00'..'\u9FFF')
    }
}

private fun isKoreanText(text: String): Boolean {
    return text.any { char -> char in '\uAC00'..'\uD7A3' }
}

private fun isChineseText(text: String): Boolean {
    if (text.isEmpty()) return false
    val cjkCharCount = text.count { char -> char in '\u4E00'..'\u9FFF' }
    val hiraganaKatakanaCount = text.count { char ->
        (char in '\u3040'..'\u309F') || (char in '\u30A0'..'\u30FF')
    }
    return cjkCharCount > 0 && (hiraganaKatakanaCount.toDouble() / text.length.toDouble()) < 0.1
}

private fun romanizeJapaneseText(text: String): String? {
    val tokens = runCatching { JapaneseTokenizer.tokenize(text) }.getOrElse { return null }
    if (tokens.isEmpty()) return null

    val romanized = tokens.mapIndexed { index, token ->
        val currentReading = token.reading
            ?.takeIf { it.isNotBlank() && it != "*" }
            ?: token.surface
        val nextReading = if (index + 1 < tokens.size) {
            tokens[index + 1].reading
                ?.takeIf { it.isNotBlank() && it != "*" }
                ?: tokens[index + 1].surface
        } else {
            null
        }
        katakanaToRomaji(currentReading, nextReading)
    }.joinToString(" ").trim()

    return romanized.ifBlank { null }
}

private fun katakanaToRomaji(
    katakana: String?,
    nextKatakana: String? = null,
): String {
    if (katakana.isNullOrEmpty()) return ""

    val romajiBuilder = StringBuilder(katakana.length)
    var index = 0
    while (index < katakana.length) {
        var consumed = false

        if (index + 1 < katakana.length) {
            val twoCharCandidate = katakana.substring(index, index + 2)
            val mappedTwoChars = KanaRomajiMap[twoCharCandidate]
            if (mappedTwoChars != null) {
                romajiBuilder.append(mappedTwoChars)
                index += 2
                consumed = true
            }
        }

        if (!consumed && katakana[index] == 'ッ') {
            val nextChar = nextKatakana?.firstOrNull()
            if (nextChar != null) {
                val nextRomajiFirstChar = KanaRomajiMap[nextChar.toString()]
                    ?.firstOrNull()
                    ?.toString()
                    ?: nextChar.toString()
                romajiBuilder.append(nextRomajiFirstChar.lowercase())
            }
            index += 1
            consumed = true
        }

        if (!consumed) {
            val mappedOneChar = KanaRomajiMap[katakana[index].toString()]
            romajiBuilder.append(mappedOneChar ?: katakana[index].toString())
            index += 1
        }
    }

    return romajiBuilder.toString().lowercase()
}

private fun romanizeKoreanText(text: String): String? {
    val romajaBuilder = StringBuilder()
    var previousFinalConsonant: String? = null

    text.forEach { char ->
        if (char in '\uAC00'..'\uD7A3') {
            val syllableIndex = char.code - 0xAC00
            val initialIndex = syllableIndex / (21 * 28)
            val vowelIndex = (syllableIndex % (21 * 28)) / 28
            val finalIndex = syllableIndex % 28

            val initialJamo = (0x1100 + initialIndex).toChar().toString()
            val vowelJamo = (0x1161 + vowelIndex).toChar().toString()
            val finalJamo = if (finalIndex == 0) null else (0x11A7 + finalIndex).toChar().toString()

            previousFinalConsonant?.let { previous ->
                val contextKey = previous + initialJamo
                val finalRomanized = HangulRomajaMap["jong"]?.get(contextKey)
                    ?: HangulRomajaMap["jong"]?.get(previous)
                    ?: previous
                romajaBuilder.append(finalRomanized)
            }

            val initialRomanized = HangulRomajaMap["cho"]?.get(initialJamo) ?: initialJamo
            val vowelRomanized = HangulRomajaMap["jung"]?.get(vowelJamo) ?: vowelJamo
            romajaBuilder.append(initialRomanized).append(vowelRomanized)
            previousFinalConsonant = finalJamo
        } else {
            previousFinalConsonant?.let { previous ->
                romajaBuilder.append(HangulRomajaMap["jong"]?.get(previous) ?: previous)
                previousFinalConsonant = null
            }
            romajaBuilder.append(char)
        }
    }

    previousFinalConsonant?.let { previous ->
        romajaBuilder.append(HangulRomajaMap["jong"]?.get(previous) ?: previous)
    }

    val romanized = romajaBuilder.toString().trim()
    return romanized.ifBlank { null }
}

private fun romanizeCyrillicText(text: String): String? {
    if (text.isEmpty()) return null
    if (!containsCyrillic(text)) return null
    return when {
        isRussianText(text) -> romanizeRussianInternal(text)
        isUkrainianText(text) -> romanizeUkrainianInternal(text)
        isSerbianText(text) -> romanizeSerbianInternal(text)
        isBulgarianText(text) -> romanizeBulgarianInternal(text)
        isBelarusianText(text) -> romanizeBelarusianInternal(text)
        isKyrgyzText(text) -> romanizeKyrgyzInternal(text)
        else -> null
    }
}

private fun romanizeRussianInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        var index = 0
        while (index < word.length) {
            var consumed = false
            if (index + 2 < word.length) {
                val candidate = word.substring(index, index + 3)
                val mappedCandidate = RussianRomajiMap[candidate]
                if (mappedCandidate != null) {
                    builder.append(mappedCandidate)
                    index += 3
                    consumed = true
                }
            }
            if (!consumed) {
                val currentChar = word[index].toString()
                if ((currentChar == "е" || currentChar == "Е") && index == 0) {
                    builder.append(if (currentChar == "е") "ye" else "Ye")
                } else {
                    builder.append(GeneralCyrillicRomajiMap[currentChar] ?: currentChar)
                }
                index += 1
            }
        }
    }
    return builder.toString()
}

private fun romanizeUkrainianInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        var index = 0
        while (index < word.length) {
            val currentChar = word[index].toString()
            var handled = false
            if (index > 0 && word[index - 1].isLetter() && !isCyrillicVowel(word[index - 1])) {
                if (currentChar == "Ю") {
                    builder.append("Iu")
                    handled = true
                } else if (currentChar == "ю") {
                    builder.append("iu")
                    handled = true
                } else if (currentChar == "Я") {
                    builder.append("Ia")
                    handled = true
                } else if (currentChar == "я") {
                    builder.append("ia")
                    handled = true
                }
            }
            if (!handled) {
                builder.append(UkrainianRomajiMap[currentChar] ?: GeneralCyrillicRomajiMap[currentChar] ?: currentChar)
            }
            index += 1
        }
    }
    return builder.toString()
}

private fun romanizeSerbianInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        word.forEach { char ->
            val charText = char.toString()
            builder.append(SerbianRomajiMap[charText] ?: GeneralCyrillicRomajiMap[charText] ?: charText)
        }
    }
    return builder.toString()
}

private fun romanizeBulgarianInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        word.forEach { char ->
            val charText = char.toString()
            builder.append(BulgarianRomajiMap[charText] ?: GeneralCyrillicRomajiMap[charText] ?: charText)
        }
    }
    return builder.toString()
}

private fun romanizeBelarusianInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        var index = 0
        while (index < word.length) {
            val currentChar = word[index].toString()
            if ((currentChar == "е" || currentChar == "Е") && index == 0) {
                builder.append(if (currentChar == "е") "ye" else "Ye")
            } else {
                builder.append(BelarusianRomajiMap[currentChar] ?: GeneralCyrillicRomajiMap[currentChar] ?: currentChar)
            }
            index += 1
        }
    }
    return builder.toString()
}

private fun romanizeKyrgyzInternal(text: String): String {
    val builder = StringBuilder(text.length)
    val words = text.split(WordSplitRegex).filter { it.isNotEmpty() }
    words.forEach { word ->
        if (word.matches(PunctuationRegex) || word.isBlank()) {
            builder.append(word)
            return@forEach
        }
        word.forEach { char ->
            val charText = char.toString()
            builder.append(KyrgyzRomajiMap[charText] ?: GeneralCyrillicRomajiMap[charText] ?: charText)
        }
    }
    return builder.toString()
}

private fun isRussianText(text: String): Boolean {
    return text.any { RussianCyrillicLetters.contains(it.toString()) } &&
        text.all { char -> RussianCyrillicLetters.contains(char.toString()) || !char.toString().matches(CyrillicRangeRegex) }
}

private fun isUkrainianText(text: String): Boolean {
    return text.any { UkrainianCyrillicLetters.contains(it.toString()) || UkrainianSpecificCyrillicLetters.contains(it.toString()) } &&
        text.all { char ->
            UkrainianCyrillicLetters.contains(char.toString()) ||
                UkrainianSpecificCyrillicLetters.contains(char.toString()) ||
                !char.toString().matches(CyrillicRangeRegex)
        }
}

private fun isSerbianText(text: String): Boolean {
    return text.any { SerbianCyrillicLetters.contains(it.toString()) || SerbianSpecificCyrillicLetters.contains(it.toString()) } &&
        text.all { char ->
            SerbianCyrillicLetters.contains(char.toString()) ||
                SerbianSpecificCyrillicLetters.contains(char.toString()) ||
                !char.toString().matches(CyrillicRangeRegex)
        }
}

private fun isBulgarianText(text: String): Boolean {
    return text.any { BulgarianCyrillicLetters.contains(it.toString()) } &&
        text.all { char -> BulgarianCyrillicLetters.contains(char.toString()) || !char.toString().matches(CyrillicRangeRegex) }
}

private fun isBelarusianText(text: String): Boolean {
    return text.any { BelarusianCyrillicLetters.contains(it.toString()) || BelarusianSpecificCyrillicLetters.contains(it.toString()) } &&
        text.all { char ->
            BelarusianCyrillicLetters.contains(char.toString()) ||
                BelarusianSpecificCyrillicLetters.contains(char.toString()) ||
                !char.toString().matches(CyrillicRangeRegex)
        }
}

private fun isKyrgyzText(text: String): Boolean {
    return text.any { KyrgyzCyrillicLetters.contains(it.toString()) || KyrgyzSpecificCyrillicLetters.contains(it.toString()) } &&
        text.all { char ->
            KyrgyzCyrillicLetters.contains(char.toString()) ||
                KyrgyzSpecificCyrillicLetters.contains(char.toString()) ||
                !char.toString().matches(CyrillicRangeRegex)
        }
}

private fun isCyrillicVowel(char: Char): Boolean {
    return "АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ".contains(char)
}

private val WordSplitRegex = Regex("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))")
private val PunctuationRegex = Regex("[.,!?;]")
private val CyrillicRangeRegex = Regex("[\\u0400-\\u04FF]")

private val RussianRomajiMap = mapOf(
    "ого" to "ovo",
    "Ого" to "Ovo",
    "его" to "evo",
    "Его" to "Evo",
)

private val UkrainianRomajiMap = mapOf(
    "Г" to "H", "г" to "h",
    "Ґ" to "G", "ґ" to "g",
    "Є" to "Ye", "є" to "ye",
    "І" to "I", "і" to "i",
    "Ї" to "Yi", "ї" to "yi",
)

private val SerbianRomajiMap = mapOf(
    "Ж" to "Ž", "Љ" to "Lj", "Њ" to "Nj", "Ц" to "C", "Ч" to "Č",
    "Џ" to "Dž", "Ш" to "Š", "Х" to "H",
    "ж" to "ž", "љ" to "lj", "њ" to "nj", "ц" to "c", "ч" to "č",
    "џ" to "dž", "ш" to "š", "х" to "h",
)

private val BulgarianRomajiMap = mapOf(
    "Ж" to "Zh", "Ц" to "Ts", "Ч" to "Ch", "Ш" to "Sh", "Щ" to "Sht",
    "Ъ" to "A", "Ь" to "Y", "Ю" to "Yu", "Я" to "Ya",
    "ж" to "zh", "ц" to "ts", "ч" to "ch", "ш" to "sh", "щ" to "sht",
    "ъ" to "a", "ь" to "y", "ю" to "yu", "я" to "ya",
)

private val BelarusianRomajiMap = mapOf(
    "Г" to "H", "г" to "h", "Ў" to "W", "ў" to "w",
)

private val KyrgyzRomajiMap = mapOf(
    "Ү" to "Ü", "ү" to "ü", "Ы" to "Y", "ы" to "y",
)

private val GeneralCyrillicRomajiMap = mapOf(
    "А" to "A", "Б" to "B", "В" to "V", "Г" to "G", "Ґ" to "G", "Д" to "D",
    "Ѓ" to "Ǵ", "Ђ" to "Đ", "Е" to "E", "Ё" to "Yo", "Є" to "Ye", "Ж" to "Zh",
    "З" to "Z", "Ѕ" to "Dz", "И" to "I", "І" to "I", "Ї" to "Yi", "Й" to "Y",
    "Ј" to "Y", "К" to "K", "Л" to "L", "Љ" to "Ly", "М" to "M", "Н" to "N",
    "Њ" to "Ny", "О" to "O", "П" to "P", "Р" to "R", "С" to "S", "Т" to "T",
    "Ћ" to "Ć", "У" to "U", "Ў" to "Ŭ", "Ф" to "F", "Х" to "Kh", "Ц" to "Ts",
    "Ч" to "Ch", "Џ" to "Dž", "Ш" to "Sh", "Щ" to "Shch", "Ъ" to "ʺ", "Ы" to "Y",
    "Ь" to "ʹ", "Э" to "E", "Ю" to "Yu", "Я" to "Ya",
    "Ѡ" to "O", "Ѣ" to "Ya", "Ѥ" to "Ye", "Ѧ" to "Ya", "Ѩ" to "Ya",
    "Ѫ" to "U", "Ѭ" to "Yu", "Ѯ" to "Ks", "Ѱ" to "Ps", "Ѳ" to "F",
    "Ѵ" to "I", "Ѷ" to "I", "Ғ" to "Gh", "Ҕ" to "G", "Җ" to "Zh",
    "Ҙ" to "Dz", "Қ" to "Q", "Ҝ" to "K", "Ҟ" to "K", "Ҡ" to "K",
    "Ң" to "Ng", "Ҥ" to "Ng", "Ҧ" to "P", "Ҩ" to "O", "Ҫ" to "S",
    "Ҭ" to "T", "Ү" to "U", "Ұ" to "U", "Ҳ" to "Kh", "Ҵ" to "Ts",
    "Ҷ" to "Ch", "Ҹ" to "Ch", "Һ" to "H", "Ҽ" to "Ch", "Ҿ" to "Ch",
    "Ќ" to "Ḱ", "Ө" to "Ö",
    "а" to "a", "б" to "b", "в" to "v", "г" to "g", "ґ" to "g", "д" to "d",
    "ѓ" to "ǵ", "ђ" to "đ", "е" to "e", "ё" to "yo", "є" to "ye", "ж" to "zh",
    "з" to "z", "ѕ" to "dz", "и" to "i", "і" to "i", "ї" to "yi", "й" to "y",
    "ј" to "y", "к" to "k", "л" to "l", "љ" to "ly", "м" to "m", "н" to "n",
    "њ" to "ny", "о" to "o", "п" to "p", "р" to "r", "с" to "s", "т" to "t",
    "ћ" to "ć", "у" to "u", "ў" to "ŭ", "ф" to "f", "х" to "kh", "ц" to "ts",
    "ч" to "ch", "џ" to "dž", "ш" to "sh", "щ" to "shch", "ъ" to "ʺ", "ы" to "y",
    "ь" to "ʹ", "э" to "e", "ю" to "yu", "я" to "ya",
    "ѡ" to "o", "ѣ" to "ya", "ѥ" to "ye", "ѧ" to "ya", "ѩ" to "ya",
    "ѫ" to "u", "ѭ" to "yu", "ѯ" to "ks", "ѱ" to "ps", "ѳ" to "f",
    "ѵ" to "i", "ѷ" to "i", "ғ" to "gh", "ҕ" to "g", "җ" to "zh",
    "ҙ" to "dz", "қ" to "q", "ҝ" to "k", "ҟ" to "k", "ҡ" to "k",
    "ң" to "ng", "ҥ" to "ng", "ҧ" to "p", "ҩ" to "o", "ҫ" to "s",
    "ҭ" to "t", "ү" to "u", "ұ" to "u", "ҳ" to "kh", "ҵ" to "ts",
    "ҷ" to "ch", "ҹ" to "ch", "һ" to "h", "ҽ" to "ch", "ҿ" to "ch",
    "ќ" to "ḱ", "ө" to "ö",
)

private val RussianCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н",
    "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь",
    "Э", "Ю", "Я",
    "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н",
    "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь",
    "э", "ю", "я",
)

private val UkrainianCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Ґ", "Д", "Е", "Є", "Ж", "З", "И", "І", "Ї", "Й",
    "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч",
    "Ш", "Щ", "Ь", "Ю", "Я",
    "а", "б", "в", "г", "ґ", "д", "е", "є", "ж", "з", "и", "і", "ї", "й",
    "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч",
    "ш", "щ", "ь", "ю", "я",
)

private val SerbianCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Д", "Ђ", "Е", "Ж", "З", "И", "Ј", "К", "Л", "Љ", "М",
    "Н", "Њ", "О", "П", "Р", "С", "Т", "Ћ", "У", "Ф", "Х", "Ц", "Ч", "Џ", "Ш",
    "а", "б", "в", "г", "д", "ђ", "е", "ж", "з", "и", "ј", "к", "л", "љ", "м",
    "н", "њ", "о", "п", "р", "с", "т", "ћ", "у", "ф", "х", "ц", "ч", "џ", "ш",
)

private val BulgarianCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й", "К", "Л", "М",
    "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ",
    "Ъ", "Ь", "Ю", "Я",
    "а", "б", "в", "г", "д", "е", "ж", "з", "и", "й", "к", "л", "м",
    "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ",
    "ъ", "ь", "ю", "я",
)

private val BelarusianCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "І", "Й", "К", "Л", "М", "Н",
    "О", "П", "Р", "С", "Т", "У", "Ў", "Ф", "Х", "Ц", "Ч", "Ш", "Ь", "Ю", "Я",
    "Ы", "Э",
    "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "і", "й", "к", "л", "м", "н",
    "о", "п", "р", "с", "т", "у", "ў", "ф", "х", "ц", "ч", "ш", "ь", "ю", "я",
    "ы", "э",
)

private val KyrgyzCyrillicLetters = setOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н",
    "Ң", "О", "Ө", "П", "Р", "С", "Т", "У", "Ү", "Ф", "Х", "Ц", "Ч", "Ш", "Щ",
    "Ъ", "Ы", "Ь", "Э", "Ю", "Я",
    "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н",
    "ң", "о", "ө", "п", "р", "с", "т", "у", "ү", "ф", "х", "ц", "ч", "ш", "щ",
    "ъ", "ы", "ь", "э", "ю", "я",
)

private val UkrainianSpecificCyrillicLetters = setOf("Ґ", "ґ", "Є", "є", "І", "і", "Ї", "ї")
private val SerbianSpecificCyrillicLetters = setOf("Ђ", "ђ", "Ј", "ј", "Љ", "љ", "Њ", "њ", "Ћ", "ћ", "Џ", "џ")
private val BelarusianSpecificCyrillicLetters = setOf("Ў", "ў", "І", "і")
private val KyrgyzSpecificCyrillicLetters = setOf("Ң", "ң", "Ө", "ө", "Ү", "ү")

private val JapaneseTokenizer by lazy { Tokenizer() }

private val HangulRomajaMap: Map<String, Map<String, String>> = mapOf(
    "cho" to mapOf(
        "ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d",
        "ᄄ" to "tt", "ᄅ" to "r", "ᄆ" to "m", "ᄇ" to "b",
        "ᄈ" to "pp", "ᄉ" to "s", "ᄊ" to "ss", "ᄋ" to "",
        "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch", "ᄏ" to "k",
        "ᄐ" to "t", "ᄑ" to "p", "ᄒ" to "h",
    ),
    "jung" to mapOf(
        "ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae",
        "ᅥ" to "eo", "ᅦ" to "e", "ᅧ" to "yeo", "ᅨ" to "ye",
        "ᅩ" to "o", "ᅪ" to "wa", "ᅫ" to "wae", "ᅬ" to "oe",
        "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo", "ᅰ" to "we",
        "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu", "ᅴ" to "eui",
        "ᅵ" to "i",
    ),
    "jong" to mapOf(
        "ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh",
        "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh",
        "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch",
        "ᆫ" to "n", "ᆫᄅ" to "ll", "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn",
        "ᆬᄆ" to "nm", "ᆬㅎ" to "nch", "ᆭ" to "n", "ᆭᄋ" to "nh", "ᆭᄅ" to "nn", "ᆮ" to "t",
        "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn", "ᆮᄆ" to "nm", "ᆮᄒ" to "th", "ᆯ" to "l",
        "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆯᄅ" to "ll", "ᆰ" to "k", "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn",
        "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh", "ᆱ" to "m", "ᆱᄋ" to "lm", "ᆱᄂ" to "mn",
        "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh", "ᆲ" to "p", "ᆲᄋ" to "lb", "ᆲᄂ" to "mn",
        "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph", "ᆳ" to "t", "ᆳᄋ" to "ls", "ᆳᄂ" to "nn",
        "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh", "ᆴ" to "t", "ᆴᄋ" to "lt", "ᆴᄂ" to "nn",
        "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth", "ᆵ" to "p", "ᆵᄋ" to "lp", "ᆵᄂ" to "mn",
        "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph", "ᆶ" to "l", "ᆶᄋ" to "lh", "ᆶᄂ" to "ll",
        "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh", "ᆷ" to "m", "ᆷᄅ" to "mn", "ᆸ" to "p",
        "ᆸᄋ" to "b", "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph", "ᆹ" to "p",
        "ᆹᄋ" to "ps", "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh", "ᆺ" to "t",
        "ᆺᄋ" to "s", "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh", "ᆻ" to "t",
        "ᆻᄋ" to "ss", "ᆻᄂ" to "tn", "ᆻᄅ" to "tn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th", "ᆼ" to "ng",
        "ᆽ" to "t", "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch",
        "ᆾ" to "t", "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch",
        "ᆿ" to "k", "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh",
        "ᇀ" to "t", "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th",
        "ᇁ" to "p", "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph",
        "ᇂ" to "t", "ᇂᄋ" to "h", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn", "ᇂᄆ" to "mm", "ᇂᄒ" to "t",
        "ᇂᄀ" to "k",
    ),
)

private val KanaRomajiMap = mapOf(
    "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
    "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
    "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
    "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
    "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
    "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
    "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
    "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
    "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
    "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
    "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
    "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
    "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
    "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
    "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
    "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
    "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
    "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
    "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
    "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
    "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
    "ワ" to "wa", "ヲ" to "o", "ン" to "n",
    "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
    "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
    "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
    "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
    "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
    "ァ" to "a", "ィ" to "i", "ゥ" to "u", "ェ" to "e", "ォ" to "o",
    "ャ" to "ya", "ュ" to "yu", "ョ" to "yo",
    "ヴ" to "vu", "ー" to "",
)

private const val LyricColorLabel = "lyric_color"
private const val LyricBackgroundLabel = "lyric_bg"
private const val LyricAlphaLabel = "lyric_alpha"
private const val LyricScaleLabel = "lyric_scale"
private const val LyricOffsetLabel = "lyric_offset"
private const val LyricKaraokeProgressLabel = "lyric_karaoke_progress"
private const val ShareImageRenderSizePx = 440
private const val KaraokeLeadMs = 0L
private const val PlaybackJitterToleranceMs = 140f
