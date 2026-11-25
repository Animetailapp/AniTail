package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.rememberExpressiveAvatarShape
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal sealed class CapsuleSubView {
    data object Overview : CapsuleSubView()
    data class TimeListened(val year: Int, val month: Int) : CapsuleSubView()
    data class TopArtists(val year: Int, val month: Int) : CapsuleSubView()
    data class TopSongs(val year: Int, val month: Int) : CapsuleSubView()
}

@Composable
internal fun SoundCapsulePane(
    overview: SoundCapsuleOverview,
    songsById: Map<String, SongEntity>,
    playerState: PlayerState,
    onOpenArtist: (String, String?) -> Unit,
    onSearchArtist: (String) -> Unit,
    onCopySummary: (String) -> Unit,
    subView: CapsuleSubView,
    onNavigateSubView: (CapsuleSubView) -> Unit,
) {
    var copiedMessageKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedMessageKey) {
        if (copiedMessageKey != null) {
            delay(1800)
            copiedMessageKey = null
        }
    }

    val spatialSpec = spring<IntOffset>(dampingRatio = 0.84f, stiffness = 400f)
    val scaleSpec = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

    AnimatedContent(
        targetState = subView,
        transitionSpec = {
            val forward = targetState !is CapsuleSubView.Overview
            val dir = if (forward) 1 else -1
            (slideInHorizontally(animationSpec = spatialSpec) { w -> (w * 0.14f).toInt() * dir } +
                scaleIn(initialScale = 0.97f, animationSpec = scaleSpec) +
                fadeIn(animationSpec = tween(220)))
                .togetherWith(
                    slideOutHorizontally(animationSpec = spatialSpec) { w -> -(w * 0.10f).toInt() * dir } +
                        scaleOut(targetScale = 0.97f, animationSpec = scaleSpec) +
                        fadeOut(animationSpec = tween(160)),
                )
        },
        label = "SoundCapsuleSubViewTransition",
        modifier = Modifier.fillMaxSize(),
    ) { activeSubView ->
        when (activeSubView) {
            is CapsuleSubView.Overview -> {
                SoundCapsuleOverviewContent(
                    overview = overview,
                    songsById = songsById,
                    playerState = playerState,
                    copiedMonthKey = copiedMessageKey,
                    onTimeListenedClick = { month ->
                        onNavigateSubView(CapsuleSubView.TimeListened(month.year, month.month))
                    },
                    onTopArtistsClick = { month ->
                        onNavigateSubView(CapsuleSubView.TopArtists(month.year, month.month))
                    },
                    onTopSongsClick = { month ->
                        onNavigateSubView(CapsuleSubView.TopSongs(month.year, month.month))
                    },
                    onShareMonth = { month, summaryText ->
                        onCopySummary(summaryText)
                        copiedMessageKey = month.monthKey
                    },
                    onOpenArtistClick = { artistId, artistName ->
                        if (artistId.startsWith("local:")) {
                            onSearchArtist(artistName)
                        } else {
                            onOpenArtist(artistId, artistName)
                        }
                    },
                    onPlaySongClick = { songId ->
                        val song = songsById[songId] ?: return@SoundCapsuleOverviewContent
                        val item = song.toLibraryItem()
                        if (playerState.currentItem?.id == item.id) {
                            playerState.togglePlayPause()
                        } else {
                            playerState.playQueue(listOf(item), startIndex = 0)
                        }
                    },
                )
            }

            is CapsuleSubView.TimeListened -> {
                val month = overview.months.firstOrNull {
                    it.year == activeSubView.year && it.month == activeSubView.month
                } ?: emptyCapsuleFallback(activeSubView.year, activeSubView.month)
                SoundCapsuleTimeDetailContent(
                    month = month,
                    songsById = songsById,
                    playerState = playerState,
                    copied = copiedMessageKey == "time-${month.monthKey}",
                    onBack = { onNavigateSubView(CapsuleSubView.Overview) },
                    onShare = { summaryText ->
                        onCopySummary(summaryText)
                        copiedMessageKey = "time-${month.monthKey}"
                    },
                    onOpenArtist = { artistId, artistName ->
                        if (artistId.startsWith("local:")) onSearchArtist(artistName)
                        else onOpenArtist(artistId, artistName)
                    },
                )
            }

            is CapsuleSubView.TopArtists -> {
                val month = overview.months.firstOrNull {
                    it.year == activeSubView.year && it.month == activeSubView.month
                } ?: emptyCapsuleFallback(activeSubView.year, activeSubView.month)
                SoundCapsuleTopArtistsDetailContent(
                    month = month,
                    copied = copiedMessageKey == "artists-${month.monthKey}",
                    onBack = { onNavigateSubView(CapsuleSubView.Overview) },
                    onShare = { summaryText ->
                        onCopySummary(summaryText)
                        copiedMessageKey = "artists-${month.monthKey}"
                    },
                    onOpenArtist = { artistId, artistName ->
                        if (artistId.startsWith("local:")) onSearchArtist(artistName)
                        else onOpenArtist(artistId, artistName)
                    },
                )
            }

            is CapsuleSubView.TopSongs -> {
                val month = overview.months.firstOrNull {
                    it.year == activeSubView.year && it.month == activeSubView.month
                } ?: emptyCapsuleFallback(activeSubView.year, activeSubView.month)
                SoundCapsuleTopSongsDetailContent(
                    month = month,
                    songsById = songsById,
                    playerState = playerState,
                    copied = copiedMessageKey == "songs-${month.monthKey}",
                    onBack = { onNavigateSubView(CapsuleSubView.Overview) },
                    onShare = { summaryText ->
                        onCopySummary(summaryText)
                        copiedMessageKey = "songs-${month.monthKey}"
                    },
                )
            }
        }
    }
}

@Composable
private fun SoundCapsuleOverviewContent(
    overview: SoundCapsuleOverview,
    songsById: Map<String, SongEntity>,
    playerState: PlayerState,
    copiedMonthKey: String?,
    onTimeListenedClick: (SoundCapsuleMonthUiState) -> Unit,
    onTopArtistsClick: (SoundCapsuleMonthUiState) -> Unit,
    onTopSongsClick: (SoundCapsuleMonthUiState) -> Unit,
    onShareMonth: (SoundCapsuleMonthUiState, String) -> Unit,
    onOpenArtistClick: (String, String) -> Unit,
    onPlaySongClick: (String) -> Unit,
) {
    val capsuleTitle = stringResource("sound_capsule_title")
    val timeLabel = stringResource("time_listened")
    val topArtistLabel = stringResource("sound_capsule_top_artist")
    val topSongLabel = stringResource("sound_capsule_top_song")
    val noDataLabel = stringResource("sound_capsule_no_data")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 880.dp && overview.months.size > 1
        val monthRows = remember(overview.months, twoColumns) {
            if (twoColumns) overview.months.chunked(2) else overview.months.map { listOf(it) }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "lifetime-hero") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LifetimeInsightCard(
                        overview = overview,
                        modifier = Modifier.widthIn(max = 1040.dp).fillMaxWidth(),
                    )
                }
            }

            itemsIndexed(
                items = monthRows,
                key = { _, row -> row.joinToString("-") { it.monthKey } },
            ) { rowIndex, rowMonths ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.widthIn(max = 1040.dp).fillMaxWidth(),
                    ) {
                        rowMonths.forEachIndexed { colIndex, month ->
                            val summaryText = buildMonthSummaryText(
                                title = capsuleTitle,
                                month = month,
                                timeLabel = timeLabel,
                                topArtistLabel = topArtistLabel,
                                topSongLabel = topSongLabel,
                                noDataLabel = noDataLabel,
                                minutesText = stringResource("sound_capsule_minutes_value", month.totalMinutes),
                            )
                            SoundCapsuleMonthSection(
                                month = month,
                                cardSeed = rowIndex * 2 + colIndex,
                                isCopied = copiedMonthKey == month.monthKey,
                                isCurrentSongPlaying = month.topSong?.id == playerState.currentItem?.id && playerState.isPlaying,
                                onTimeListenedClick = { onTimeListenedClick(month) },
                                onTopArtistsClick = { onTopArtistsClick(month) },
                                onTopSongsClick = { onTopSongsClick(month) },
                                onShareClick = { onShareMonth(month, summaryText) },
                                onOpenArtistClick = onOpenArtistClick,
                                onPlaySongClick = onPlaySongClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (twoColumns && rowMonths.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LifetimeInsightCard(
    overview: SoundCapsuleOverview,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    CapsuleSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(164.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent.copy(alpha = 0.12f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 68.dp)
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryAccent.copy(alpha = 0.10f)),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                HeroPulseGraphic()
                Text(
                    text = stringResource("sound_capsule_lifetime_headline", overview.totalSongsSinceJoining),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource("sound_capsule_lifetime_description"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val totalTimeFormatted = formatDurationCompact(overview.totalPlayTimeMs).ifBlank { "0m" }
                    HeroStatChip(
                        icon = IconAssets.history(),
                        label = totalTimeFormatted,
                        accent = colors.primaryAccent,
                    )
                    HeroStatChip(
                        icon = IconAssets.artist(),
                        label = "${formatCount(overview.uniqueArtistsCount)} ${stringResource("artists").lowercase(Locale.getDefault())}",
                        accent = colors.secondaryAccent,
                    )
                    HeroStatChip(
                        icon = IconAssets.musicNote(),
                        label = "${formatCount(overview.uniqueSongsCount)} ${stringResource("songs").lowercase(Locale.getDefault())}",
                        accent = colors.tertiaryAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    icon: ImageVector,
    label: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeroPulseGraphic() {
    val colors = capsuleColors()
    val barHeights = listOf(14.dp, 24.dp, 36.dp, 26.dp, 16.dp, 28.dp, 18.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        barHeights.forEachIndexed { index, height ->
            val accent = if (index % 2 == 0) colors.primaryAccent else colors.secondaryAccent
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(accent.copy(alpha = 0.40f), accent),
                        ),
                    ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SoundCapsuleMonthSection(
    month: SoundCapsuleMonthUiState,
    cardSeed: Int,
    isCopied: Boolean,
    isCurrentSongPlaying: Boolean,
    onTimeListenedClick: () -> Unit,
    onTopArtistsClick: () -> Unit,
    onTopSongsClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenArtistClick: (artistId: String, artistName: String) -> Unit,
    onPlaySongClick: (songId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = monthYearLabel(month.yearMonth),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isCopied) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.primaryAccent.copy(alpha = 0.16f),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = stringResource("copied_to_clipboard"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primaryAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.thumbnailBackground)
                    .capsuleInteractive(onClick = onShareClick),
            ) {
                Icon(
                    imageVector = IconAssets.share(),
                    contentDescription = stringResource("share"),
                    tint = colors.mutedText,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        if (month.totalSongsPlayed <= 0) {
            NoMusicMonthCard()
            return@Column
        }

        CapsuleSurface(
            accent = colors.primaryAccent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                TimeInsightRow(
                    month = month,
                    onClick = onTimeListenedClick,
                )
                HorizontalDivider(color = colors.divider, thickness = 0.8.dp)
                TopArtistInsightRow(
                    month = month,
                    avatarShape = rememberExpressiveAvatarShape("${cardSeed}:${month.topArtist?.id.orEmpty()}"),
                    onClick = onTopArtistsClick,
                    onOpenArtistClick = onOpenArtistClick,
                )
                HorizontalDivider(color = colors.divider, thickness = 0.8.dp)
                TopSongInsightRow(
                    month = month,
                    isPlaying = isCurrentSongPlaying,
                    onClick = onTopSongsClick,
                    onPlaySongClick = onPlaySongClick,
                )
            }
        }
    }
}

@Composable
private fun TimeInsightRow(
    month: SoundCapsuleMonthUiState,
    onClick: () -> Unit,
) {
    val colors = capsuleColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource("time_listened"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.mutedText,
            )
            Text(
                text = stringResource("sound_capsule_minutes_value", month.totalMinutes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primaryAccent,
            )
            Text(
                text = stringResource("sound_capsule_daily_average", dailyAverageForMonth(month)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.primaryAccent.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${formatCount(month.totalSongsPlayed)} ${stringResource("songs").lowercase(Locale.getDefault())}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primaryAccent,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = IconAssets.navigateNext(),
            contentDescription = null,
            tint = colors.subtleText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TopArtistInsightRow(
    month: SoundCapsuleMonthUiState,
    avatarShape: Shape,
    onClick: () -> Unit,
    onOpenArtistClick: (artistId: String, artistName: String) -> Unit,
) {
    val colors = capsuleColors()
    val topArtist = month.topArtist
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CapsuleThumbnail(
            imageUrl = topArtist?.thumbnailUrl,
            fallbackIcon = IconAssets.artist(),
            shape = avatarShape,
            size = 54.dp,
            onClick = topArtist?.let { artist ->
                { onOpenArtistClick(artist.id, artist.name) }
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource("sound_capsule_top_artist"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
            )
            Text(
                text = topArtist?.name ?: stringResource("sound_capsule_no_data"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (topArtist?.isNewTopFive == true) {
                Text(
                    text = stringResource("sound_capsule_new_to_top_five"),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.secondaryAccent,
                )
            }
        }
        Icon(
            imageVector = IconAssets.navigateNext(),
            contentDescription = null,
            tint = colors.subtleText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TopSongInsightRow(
    month: SoundCapsuleMonthUiState,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlaySongClick: (songId: String) -> Unit,
) {
    val colors = capsuleColors()
    val topSong = month.topSong
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            CapsuleThumbnail(
                imageUrl = topSong?.thumbnailUrl,
                fallbackIcon = IconAssets.musicNote(),
                shape = RoundedCornerShape(12.dp),
                size = 54.dp,
                onClick = topSong?.let { song -> { onPlaySongClick(song.id) } },
            )
            if (topSong != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                ) {
                    Icon(
                        imageVector = if (isPlaying) IconAssets.pause() else IconAssets.play(),
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource("sound_capsule_top_song"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
            )
            Text(
                text = topSong?.title ?: stringResource("sound_capsule_no_data"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = topSong?.subtitle ?: stringResource("sound_capsule_top_song"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (topSong?.isNew == true) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.primaryAccent.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = stringResource("sound_capsule_new"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = IconAssets.navigateNext(),
            contentDescription = null,
            tint = colors.subtleText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NoMusicMonthCard() {
    val colors = capsuleColors()
    CapsuleSurface(
        accent = colors.secondaryAccent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource("sound_capsule_musicless_title"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource("sound_capsule_musicless_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SoundCapsuleTimeDetailContent(
    month: SoundCapsuleMonthUiState,
    songsById: Map<String, SongEntity>,
    playerState: PlayerState,
    copied: Boolean,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onOpenArtist: (String, String) -> Unit,
) {
    val colors = capsuleColors()
    val averageMinutes = dailyAverageForMonth(month)
    val dominantPeriod = ListeningPeriod.entries.maxByOrNull {
        month.periodPlayTimeMs[it] ?: 0L
    } ?: ListeningPeriod.NIGHT
    val activeDays = month.dailyPlayTimeMs.count { it > 0L }

    val titleText = stringResource("time_listened")
    val summaryText = buildString {
        append(titleText)
        append(" - ")
        append(month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        append(" ")
        append(month.year)
        append("\n")
        append(stringResource("sound_capsule_minutes_value", month.totalMinutes))
        append("\n")
        append(stringResource("sound_capsule_daily_average", averageMinutes))
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "detail-header") {
            DetailSubViewHeader(
                title = titleText,
                copied = copied,
                onBack = onBack,
                onShare = { onShare(summaryText) },
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            )
        }

        item(key = "summary-and-chart") {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            ) {
                DetailSummaryCard(
                    monthLabel = monthYearLabel(month.yearMonth),
                    metricValue = formatCount(month.totalMinutes),
                    metricLabel = stringResource("sound_capsule_minutes_value", month.totalMinutes)
                        .replace(month.totalMinutes.toString(), "")
                        .trim()
                        .ifBlank { "min" },
                    supportingText = "${stringResource("sound_capsule_daily_average", averageMinutes)} • ${stringResource("sound_capsule_active_days", activeDays)}",
                    accentColor = colors.primaryAccent,
                )
                DailyMinutesChart(
                    dailyPlayTimeMs = month.dailyPlayTimeMs,
                    averageMinutes = averageMinutes,
                )
            }
        }

        item(key = "period-and-now") {
            BoxWithConstraints(modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
                if (maxWidth >= 740.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PeriodBreakdownCard(
                            state = month,
                            dominantPeriod = dominantPeriod,
                            modifier = Modifier.weight(1f),
                        )
                        MusicForNowCard(
                            state = month,
                            dominantPeriod = dominantPeriod,
                            onPlayTopSong = {
                                val topSong = month.rankedSongs.firstOrNull()?.let { songsById[it.id] }
                                if (topSong != null) {
                                    playerState.playQueue(listOf(topSong.toLibraryItem()), startIndex = 0)
                                }
                            },
                            onPlayTopMix = {
                                val mix = month.rankedSongs.take(5).mapNotNull { songsById[it.id]?.toLibraryItem() }
                                if (mix.isNotEmpty()) {
                                    playerState.playQueue(mix, startIndex = 0)
                                }
                            },
                            onOpenTopArtist = {
                                month.rankedArtists.firstOrNull()?.let { artist ->
                                    onOpenArtist(artist.id, artist.name)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PeriodBreakdownCard(
                            state = month,
                            dominantPeriod = dominantPeriod,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MusicForNowCard(
                            state = month,
                            dominantPeriod = dominantPeriod,
                            onPlayTopSong = {
                                val topSong = month.rankedSongs.firstOrNull()?.let { songsById[it.id] }
                                if (topSong != null) {
                                    playerState.playQueue(listOf(topSong.toLibraryItem()), startIndex = 0)
                                }
                            },
                            onPlayTopMix = {
                                val mix = month.rankedSongs.take(5).mapNotNull { songsById[it.id]?.toLibraryItem() }
                                if (mix.isNotEmpty()) {
                                    playerState.playQueue(mix, startIndex = 0)
                                }
                            },
                            onOpenTopArtist = {
                                month.rankedArtists.firstOrNull()?.let { artist ->
                                    onOpenArtist(artist.id, artist.name)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCapsuleTopArtistsDetailContent(
    month: SoundCapsuleMonthUiState,
    copied: Boolean,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onOpenArtist: (String, String) -> Unit,
) {
    val colors = capsuleColors()
    val titleText = stringResource("sound_capsule_top_artists_title")
    val summaryText = buildString {
        append(titleText)
        append(" - ")
        append(month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        append(" ")
        append(month.year)
        append("\n")
        month.rankedArtists.take(5).forEach { artist ->
            append("${artist.rank}. ${artist.name}\n")
        }
    }.trim()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "artists-header") {
            DetailSubViewHeader(
                title = titleText,
                copied = copied,
                onBack = onBack,
                onShare = { onShare(summaryText) },
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            )
        }

        item(key = "artists-summary") {
            DetailSummaryCard(
                monthLabel = monthYearLabel(month.yearMonth),
                metricValue = formatCount(month.rankedArtists.size),
                metricLabel = stringResource("artists"),
                supportingText = stringResource("sound_capsule_top_artists_title"),
                accentColor = colors.secondaryAccent,
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            )
        }

        if (month.rankedArtists.isEmpty()) {
            item(key = "artists-empty") {
                Text(
                    text = stringResource("sound_capsule_no_data"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mutedText,
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        } else {
            itemsIndexed(
                items = month.rankedArtists,
                key = { _, artist -> artist.id },
            ) { index, artist ->
                val accent = rankAccent(artist.rank, colors)
                CapsuleSurface(
                    accent = accent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(artist.id, artist.name) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        RankBadge(rank = artist.rank, accent = accent)
                        Spacer(modifier = Modifier.width(12.dp))
                        CapsuleThumbnail(
                            imageUrl = artist.thumbnailUrl,
                            fallbackIcon = IconAssets.artist(),
                            shape = rememberExpressiveAvatarShape("${index}:${artist.id}"),
                            size = 50.dp,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${pluralStringResource("n_time", artist.playCount, artist.playCount)} • ${formatDurationCompact(artist.timeListenedMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.mutedText,
                            )
                        }
                        if (artist.isNewTopFive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = colors.secondaryAccent.copy(alpha = 0.16f),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    text = stringResource("sound_capsule_new_to_top_five"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.secondaryAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Icon(
                            imageVector = IconAssets.navigateNext(),
                            contentDescription = null,
                            tint = colors.subtleText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCapsuleTopSongsDetailContent(
    month: SoundCapsuleMonthUiState,
    songsById: Map<String, SongEntity>,
    playerState: PlayerState,
    copied: Boolean,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
) {
    val colors = capsuleColors()
    val titleText = stringResource("top_songs")
    val queueItems = remember(month.rankedSongs, songsById) {
        month.rankedSongs.mapNotNull { songsById[it.id]?.toLibraryItem() }
    }
    val summaryText = buildString {
        append(titleText)
        append(" - ")
        append(month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        append(" ")
        append(month.year)
        append("\n")
        month.rankedSongs.take(5).forEachIndexed { idx, song ->
            append("${idx + 1}. ${song.title}\n")
        }
    }.trim()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "songs-header") {
            DetailSubViewHeader(
                title = titleText,
                copied = copied,
                onBack = onBack,
                onShare = { onShare(summaryText) },
                trailingAction = if (queueItems.isNotEmpty()) {
                    {
                        CapsuleActionPill(
                            label = stringResource("play"),
                            icon = IconAssets.play(),
                            onClick = { playerState.playQueue(queueItems, startIndex = 0) },
                        )
                    }
                } else null,
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            )
        }

        item(key = "songs-summary") {
            DetailSummaryCard(
                monthLabel = monthYearLabel(month.yearMonth),
                metricValue = formatCount(month.rankedSongs.size),
                metricLabel = stringResource("songs"),
                supportingText = stringResource("top_songs"),
                accentColor = colors.primaryAccent,
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            )
        }

        if (month.rankedSongs.isEmpty()) {
            item(key = "songs-empty") {
                Text(
                    text = stringResource("sound_capsule_no_data"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mutedText,
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        } else {
            itemsIndexed(
                items = month.rankedSongs,
                key = { _, song -> song.id },
            ) { index, song ->
                val rank = index + 1
                val accent = rankAccent(rank, colors)
                val isCurrent = song.id == playerState.currentItem?.id
                CapsuleSurface(
                    accent = if (isCurrent) colors.primaryAccent else accent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCurrent) {
                                    playerState.togglePlayPause()
                                } else if (queueItems.isNotEmpty()) {
                                    val queueIndex = queueItems.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    playerState.playQueue(queueItems, startIndex = queueIndex)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        RankBadge(rank = rank, accent = accent)
                        Spacer(modifier = Modifier.width(12.dp))
                        CapsuleThumbnail(
                            imageUrl = song.thumbnailUrl,
                            fallbackIcon = IconAssets.musicNote(),
                            shape = RoundedCornerShape(10.dp),
                            size = 50.dp,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isCurrent) colors.primaryAccent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    song.subtitle?.takeIf { it.isNotBlank() },
                                    pluralStringResource("n_time", song.playCount, song.playCount),
                                    formatDurationCompact(song.timeListenedMs).takeIf { it.isNotBlank() },
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (song.isNew) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = colors.primaryAccent.copy(alpha = 0.16f),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    text = stringResource("sound_capsule_new"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primaryAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isCurrent && playerState.isPlaying) IconAssets.pause() else IconAssets.play(),
                            contentDescription = null,
                            tint = if (isCurrent) colors.primaryAccent else colors.subtleText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSubViewHeader(
    title: String,
    copied: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = IconAssets.arrowBack(),
                contentDescription = stringResource("back"),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailingAction != null) {
            trailingAction()
        }
        if (copied) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.primaryAccent.copy(alpha = 0.16f),
            ) {
                Text(
                    text = stringResource("copied_to_clipboard"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primaryAccent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = IconAssets.share(),
                contentDescription = stringResource("share"),
                tint = colors.mutedText,
            )
        }
    }
}

@Composable
private fun DetailSummaryCard(
    monthLabel: AnnotatedString,
    metricValue: String,
    metricLabel: String,
    supportingText: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    CapsuleSurface(
        accent = accentColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.mutedText,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = metricValue,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                    )
                    Text(
                        text = metricLabel.lowercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.subtleText,
                )
            }
        }
    }
}

@Composable
private fun DailyMinutesChart(
    dailyPlayTimeMs: List<Long>,
    averageMinutes: Int,
) {
    val colors = capsuleColors()
    val averageLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val dailyMinutes = remember(dailyPlayTimeMs) { dailyPlayTimeMs.map { it / 60_000f } }
    val maxMinutes = remember(dailyMinutes) { dailyMinutes.maxOrNull()?.coerceAtLeast(1f) ?: 1f }
    val chartMax = maxOf(maxMinutes, averageMinutes.toFloat(), 1f)
    var hoveredDayIndex by remember(dailyPlayTimeMs) { mutableStateOf<Int?>(null) }

    CapsuleSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
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
                    text = stringResource("sound_capsule_daily_average", averageMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mutedText,
                    modifier = Modifier.weight(1f),
                )
                val activeDay = hoveredDayIndex
                if (activeDay != null && activeDay in dailyMinutes.indices) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.primaryAccent.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = "${activeDay + 1} • ${stringResource("sound_capsule_minutes_short", dailyMinutes[activeDay].roundToInt())}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                val gridColor = colors.divider.copy(alpha = 0.55f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 36.dp)
                        .height(188.dp)
                        .pointerInput(dailyMinutes.size) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Move,
                                        PointerEventType.Press,
                                        -> {
                                            val x = event.changes.firstOrNull()?.position?.x ?: continue
                                            val dayCount = dailyMinutes.size.coerceAtLeast(1)
                                            val idx = ((x / size.width.coerceAtLeast(1)) * dayCount)
                                                .toInt()
                                                .coerceIn(0, dayCount - 1)
                                            hoveredDayIndex = idx
                                        }

                                        PointerEventType.Exit -> hoveredDayIndex = null
                                        else -> Unit
                                    }
                                }
                            }
                        },
                ) {
                    val drawHeight = size.height - 16f
                    val dayCount = dailyMinutes.size.coerceAtLeast(1)
                    val stepX = size.width / dayCount

                    drawLine(gridColor, Offset(0f, drawHeight), Offset(size.width, drawHeight), strokeWidth = 1.5f)
                    drawLine(gridColor, Offset(0f, drawHeight / 2f), Offset(size.width, drawHeight / 2f), strokeWidth = 1.5f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)

                    dailyMinutes.forEachIndexed { index, value ->
                        val isHovered = hoveredDayIndex == index
                        val barWidth = (stepX * 0.58f).coerceAtLeast(4f)
                        val left = index * stepX + (stepX - barWidth) / 2f
                        if (value > 0f) {
                            val barHeight = ((value / chartMax) * drawHeight).coerceAtLeast(6f)
                            drawRoundRect(
                                color = if (isHovered) colors.secondaryAccent else colors.primaryAccent,
                                topLeft = Offset(left, drawHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                            )
                        } else if (isHovered) {
                            drawRoundRect(
                                color = colors.subtleText.copy(alpha = 0.25f),
                                topLeft = Offset(left, drawHeight - 6f),
                                size = Size(barWidth, 6f),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                            )
                        }
                    }

                    val averageY = drawHeight - ((averageMinutes / chartMax) * drawHeight)
                    drawLine(
                        color = averageLineColor,
                        start = Offset(0f, averageY),
                        end = Offset(size.width, averageY),
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(176.dp),
                ) {
                    Text(
                        text = chartMax.roundToInt().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtleText,
                    )
                    Text(
                        text = (chartMax / 2f).roundToInt().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtleText,
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtleText,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
            ) {
                val lastDay = dailyMinutes.size.coerceAtLeast(1)
                val labels = listOf(1, 8, 15, 22, lastDay).distinct()
                labels.forEach { day ->
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodBreakdownCard(
    state: SoundCapsuleMonthUiState,
    dominantPeriod: ListeningPeriod,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    val periodMinutes = remember(state.periodPlayTimeMs) {
        ListeningPeriod.entries.map { period ->
            period to ((state.periodPlayTimeMs[period] ?: 0L) / 60_000L).toInt()
        }
    }
    val maxMinutes = remember(periodMinutes) {
        periodMinutes.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    }

    CapsuleSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.primaryAccent)) {
                        append(periodLabel(dominantPeriod))
                    }
                    append(stringResource("sound_capsule_period_suffix"))
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            periodMinutes.forEach { (period, minutes) ->
                val targetProgress = (minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                val progress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                    label = "period_bar_${period.name}",
                )
                val isDominant = period == dominantPeriod

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = periodLabel(period),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isDominant) colors.primaryAccent else colors.mutedText,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource("sound_capsule_minutes_short", minutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.thumbnailBackground),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isDominant) {
                                        colors.primaryAccent
                                    } else {
                                        colors.primaryAccent.copy(alpha = 0.45f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicForNowCard(
    state: SoundCapsuleMonthUiState,
    dominantPeriod: ListeningPeriod,
    onPlayTopSong: () -> Unit,
    onPlayTopMix: () -> Unit,
    onOpenTopArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    val topSong = state.rankedSongs.firstOrNull()
    val topArtist = state.rankedArtists.firstOrNull()

    CapsuleSurface(
        accent = colors.secondaryAccent,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource("sound_capsule_music_for_right_now"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.secondaryAccent.copy(alpha = 0.18f)),
                ) {
                    if (topSong?.thumbnailUrl.isNullOrBlank()) {
                        Icon(
                            imageVector = IconAssets.musicNote(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        RemoteImage(
                            url = topSong?.thumbnailUrl,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "${periodLabel(dominantPeriod)} • ${stringResource("sound_capsule_quick_pick")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.secondaryAccent,
                    )
                    Text(
                        text = topSong?.title ?: stringResource("sound_capsule_on_repeat"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = topArtist?.name ?: topSong?.subtitle ?: stringResource("sound_capsule_top_song"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapsuleActionPill(
                    label = stringResource("sound_capsule_play_top_song"),
                    icon = IconAssets.play(),
                    onClick = onPlayTopSong,
                )
                CapsuleActionPill(
                    label = stringResource("sound_capsule_quick_mix"),
                    icon = IconAssets.playlistPlay(),
                    onClick = onPlayTopMix,
                )
                if (topArtist != null) {
                    CapsuleActionPill(
                        label = stringResource("sound_capsule_open_top_artist"),
                        icon = IconAssets.artist(),
                        onClick = onOpenTopArtist,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleActionPill(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = capsuleColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.primaryAccent.copy(alpha = 0.16f))
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .capsuleInteractive(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primaryAccent,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.primaryAccent,
        )
    }
}

@Composable
private fun RankBadge(
    rank: Int,
    accent: Color,
) {
    val isTopThree = rank <= 3
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isTopThree) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (isTopThree) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (isTopThree) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapsuleSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = capsuleColors()
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .border(1.dp, colors.outline, shape)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.cardBackground,
                        colors.cardBackground.copy(alpha = 0.92f),
                        accent.copy(alpha = 0.12f),
                    ),
                ),
            ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun CapsuleThumbnail(
    imageUrl: String?,
    fallbackIcon: ImageVector,
    shape: Shape,
    size: Dp,
    onClick: (() -> Unit)?,
) {
    val colors = capsuleColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.thumbnailBackground)
            .border(1.dp, colors.outline, shape),
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(22.dp),
            )
        } else {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .capsuleInteractive(onClick = onClick),
            )
        }
    }
}

@Composable
private fun Modifier.capsuleInteractive(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.95f
            hovered -> 1.03f
            else -> 1f
        },
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
        label = "capsule_interactive_scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

@Composable
private fun periodLabel(period: ListeningPeriod): String =
    when (period) {
        ListeningPeriod.MORNING -> stringResource("sound_capsule_period_morning")
        ListeningPeriod.AFTERNOON -> stringResource("sound_capsule_period_afternoon")
        ListeningPeriod.EVENING -> stringResource("sound_capsule_period_evening")
        ListeningPeriod.NIGHT -> stringResource("sound_capsule_period_night")
    }

@Composable
private fun monthYearLabel(yearMonth: YearMonth): AnnotatedString {
    val colors = capsuleColors()
    return buildAnnotatedString {
        val locale = Locale.getDefault()
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, locale)
        append(monthName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() })
        append(" ")
        withStyle(SpanStyle(color = colors.mutedText)) {
            append(yearMonth.year.toString())
        }
    }
}

private data class CapsuleColors(
    val cardBackground: Color,
    val thumbnailBackground: Color,
    val mutedText: Color,
    val subtleText: Color,
    val divider: Color,
    val outline: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
)

@Composable
private fun capsuleColors(): CapsuleColors {
    val scheme = MaterialTheme.colorScheme
    return CapsuleColors(
        cardBackground = scheme.surfaceVariant.copy(alpha = 0.55f),
        thumbnailBackground = scheme.surfaceContainer,
        mutedText = scheme.onSurfaceVariant,
        subtleText = scheme.onSurfaceVariant.copy(alpha = 0.75f),
        divider = scheme.outlineVariant.copy(alpha = 0.7f),
        outline = scheme.outlineVariant.copy(alpha = 0.45f),
        primaryAccent = scheme.primary,
        secondaryAccent = scheme.secondary,
        tertiaryAccent = scheme.tertiary,
    )
}

private fun rankAccent(rank: Int, colors: CapsuleColors): Color =
    when (rank) {
        1 -> colors.primaryAccent
        2 -> colors.secondaryAccent
        3 -> colors.tertiaryAccent
        else -> colors.primaryAccent
    }

private fun dailyAverageForMonth(month: SoundCapsuleMonthUiState): Int {
    val days = month.yearMonth.lengthOfMonth().coerceAtLeast(1)
    return month.totalMinutes / days
}

private fun formatCount(value: Int): String = "%,d".format(Locale.getDefault(), value)

private fun formatDurationCompact(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return when {
        hours > 0L && mins > 0L -> "${hours}h ${mins}m"
        hours > 0L -> "${hours}h"
        totalMinutes > 0L -> "${totalMinutes}m"
        else -> "${(durationMs / 1000L).coerceAtLeast(1L)}s"
    }
}

private fun emptyCapsuleFallback(year: Int, month: Int): SoundCapsuleMonthUiState {
    val ym = YearMonth.of(year, month)
    return SoundCapsuleMonthUiState(
        year = year,
        month = month,
        totalPlayTimeMs = 0L,
        totalSongsPlayed = 0,
        topArtist = null,
        topSong = null,
        rankedSongs = emptyList(),
        rankedArtists = emptyList(),
        dailyPlayTimeMs = List(ym.lengthOfMonth()) { 0L },
        periodPlayTimeMs = ListeningPeriod.entries.associateWith { 0L },
    )
}

private fun buildMonthSummaryText(
    title: String,
    month: SoundCapsuleMonthUiState,
    timeLabel: String,
    topArtistLabel: String,
    topSongLabel: String,
    noDataLabel: String,
    minutesText: String,
): String {
    val artist = month.topArtist?.name ?: noDataLabel
    val song = month.topSong?.title ?: noDataLabel
    val monthName = month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
    return buildString {
        append(title)
        append(" - ")
        append(monthName)
        append(" ")
        append(month.year)
        append("\n")
        append(timeLabel)
        append(": ")
        append(minutesText)
        append("\n")
        append(topArtistLabel)
        append(": ")
        append(artist)
        append("\n")
        append(topSongLabel)
        append(": ")
        append(song)
    }
}

