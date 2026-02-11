package com.anitail.desktop.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import java.io.File
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
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { DesktopPreferences.getInstance() }
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lyricsLabel = stringResource("lyrics")
    val syncedLabel = stringResource("lyrics_synced")
    val plainLabel = stringResource("lyrics_plain")
    val retryLabel = stringResource("retry")
    val lyricsNotFoundLabel = stringResource("lyrics_not_found")
    val lyricsEmptyLabel = stringResource("lyrics_start_playing")

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

    LaunchedEffect(title, artist, videoId, durationSec) {
        if (title.isBlank()) {
            lyricsResult = null
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        lyricsResult = null

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
            isLoading = false
        }
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
    LaunchedEffect(currentLineIndex, lyricsScroll, lyricsSmoothScroll, lyricsAnimationStyle, lyricsResult) {
        if (!lyricsScroll) return@LaunchedEffect
        if (currentLineIndex < 0 || lyricsResult?.parsedLines?.isEmpty() != false) return@LaunchedEffect

        val shouldAnimate = lyricsSmoothScroll || lyricsAnimationStyle == LyricsAnimationStylePreference.APPLE
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

                if (error != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    DesktopLyricsService.getLyrics(
                                        title = title,
                                        artist = artist,
                                        durationSec = durationSec,
                                        videoId = videoId,
                                    )
                                        .onSuccess { lyricsResult = it }
                                        .onFailure {
                                            if (it is CancellationException) return@onFailure
                                            error = it.message
                                        }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                    ) {
                        Icon(IconAssets.refresh(), contentDescription = retryLabel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
            }
        }
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
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            Color.Transparent
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
            .clickable(enabled = canSeek, onClick = onClick)
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

private const val LyricColorLabel = "lyric_color"
private const val LyricBackgroundLabel = "lyric_bg"
private const val LyricAlphaLabel = "lyric_alpha"
private const val LyricScaleLabel = "lyric_scale"
private const val LyricOffsetLabel = "lyric_offset"
private const val LyricKaraokeProgressLabel = "lyric_karaoke_progress"
private const val KaraokeLeadMs = 450L
private const val PlaybackJitterToleranceMs = 140f
