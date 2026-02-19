package com.anitail.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton as AppIconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.viewmodels.SoundCapsuleViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleTimeListenedScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    val colors = detailColors()
    val selectedMonth by viewModel.monthState(year, month).collectAsState(initial = null)
    val yearMonth = YearMonth.of(year, month)
    val state = selectedMonth ?: emptyState(yearMonth)
    val bottomInsets =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    val averageMinutes = calculateDailyAverage(state)
    val dominantPeriod =
        ListeningPeriod.entries.maxByOrNull { period ->
            state.periodPlayTimeMs[period] ?: 0L
        } ?: ListeningPeriod.NIGHT

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 84.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    DetailSummaryCard(
                        monthLabel = monthYearLabel(state.yearMonth),
                        metricValue = formatCount(state.totalMinutes),
                        metricLabel = stringResource(R.string.time_listened),
                        supportingText = stringResource(R.string.sound_capsule_daily_average, averageMinutes),
                        accentColor = colors.primaryAccent,
                    )
                    DailyMinutesChart(
                        dailyPlayTimeMs = state.dailyPlayTimeMs,
                        averageMinutes = averageMinutes,
                    )
                }
            }

            item {
                HorizontalDivider(
                    color = colors.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    PeriodBreakdownCard(
                        state = state,
                        dominantPeriod = dominantPeriod,
                    )
                }
            }

            item {
                HorizontalDivider(
                    color = colors.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sound_capsule_music_for_right_now),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MusicForNowCard()
                }
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.time_listened),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                AppIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleTopArtistsScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    val colors = detailColors()
    val selectedMonth by viewModel.monthState(year, month).collectAsState(initial = null)
    val state = selectedMonth ?: emptyState(YearMonth.of(year, month))
    val bottomInsets =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 68.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            item {
                DetailSummaryCard(
                    monthLabel = monthYearLabel(state.yearMonth),
                    metricValue = formatCount(state.rankedArtists.size),
                    metricLabel = stringResource(R.string.artists),
                    supportingText = stringResource(R.string.sound_capsule_top_artists_title),
                    accentColor = colors.secondaryAccent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.rankedArtists.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sound_capsule_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.mutedText,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                itemsIndexed(items = state.rankedArtists, key = { _, artist -> artist.id }) { index, artist ->
                    DetailSurface(
                        accent = rankAccent(index + 1, colors),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        TopArtistRow(
                            artist = artist,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.sound_capsule_top_artists_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                AppIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleTopSongsScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    val colors = detailColors()
    val selectedMonth by viewModel.monthState(year, month).collectAsState(initial = null)
    val state = selectedMonth ?: emptyState(YearMonth.of(year, month))
    val bottomInsets =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 68.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            item {
                DetailSummaryCard(
                    monthLabel = monthYearLabel(state.yearMonth),
                    metricValue = formatCount(state.rankedSongs.size),
                    metricLabel = stringResource(R.string.songs),
                    supportingText = stringResource(R.string.top_songs),
                    accentColor = colors.secondaryAccent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.rankedSongs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sound_capsule_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.mutedText,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                itemsIndexed(items = state.rankedSongs, key = { _, song -> song.id }) { index, song ->
                    DetailSurface(
                        accent = rankAccent(index + 1, colors),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        TopSongRow(
                            rank = index + 1,
                            song = song,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.top_songs),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                AppIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
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
    val colors = detailColors()
    DetailSurface(
        accent = accentColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(98.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
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
                        modifier = Modifier.padding(bottom = 2.dp),
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
    val colors = detailColors()
    val averageLineColor = MaterialTheme.colorScheme.onSurface
    val dailyMinutes = dailyPlayTimeMs.map { (it / 60_000f) }
    val maxMinutes = dailyMinutes.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val chartMax = maxOf(maxMinutes, averageMinutes.toFloat(), 1f)

    DetailSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(188.dp),
                ) {
                    val drawHeight = size.height - 24f
                    val dayCount = dailyMinutes.size.coerceAtLeast(1)
                    val stepX = size.width / dayCount

                    val gridColor = Color(0xFF383C46)
                    drawLine(gridColor, Offset(0f, drawHeight), Offset(size.width, drawHeight), strokeWidth = 2f)
                    drawLine(gridColor, Offset(0f, drawHeight / 2f), Offset(size.width, drawHeight / 2f), strokeWidth = 2f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f)

                    dailyMinutes.forEachIndexed { index, value ->
                        if (value <= 0f) return@forEachIndexed
                        val x = index * stepX + (stepX / 2f)
                        val barHeight = (value / chartMax) * drawHeight
                        drawLine(
                            color = colors.primaryAccent,
                            start = Offset(x, drawHeight),
                            end = Offset(x, drawHeight - barHeight),
                            strokeWidth = stepX * 0.55f,
                            cap = StrokeCap.Round,
                        )
                    }

                    val averageY = drawHeight - ((averageMinutes / chartMax) * drawHeight)
                    drawLine(
                        color = averageLineColor,
                        start = Offset(0f, averageY),
                        end = Offset(size.width, averageY),
                        strokeWidth = 3f,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier =
                        Modifier
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
                modifier = Modifier.fillMaxWidth(),
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
) {
    val colors = detailColors()
    val periodMinutes =
        ListeningPeriod.entries.map { period ->
            period to ((state.periodPlayTimeMs[period] ?: 0L) / 60_000L).toInt()
        }
    val maxMinutes = periodMinutes.maxOfOrNull { (_, value) -> value }?.coerceAtLeast(1) ?: 1

    DetailSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.primaryAccent)) {
                            append(periodLabel(dominantPeriod))
                        }
                        append(stringResource(R.string.sound_capsule_period_suffix))
                    },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            periodMinutes.forEach { (period, minutes) ->
                val progress = minutes.toFloat() / maxMinutes.toFloat()
                val isDominant = period == dominantPeriod

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            text = stringResource(R.string.sound_capsule_minutes_short, minutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.thumbnailBackground),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxSize()
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

@Composable
private fun MusicForNowCard() {
    val colors = detailColors()
    DetailSurface(
        accent = colors.secondaryAccent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryAccent.copy(alpha = 0.85f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.spotify),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playlist),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mutedText,
                )
                Text(
                    text = stringResource(R.string.sound_capsule_on_repeat),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DetailSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = detailColors()
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier =
            modifier
                .border(1.dp, colors.outline, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                colors.cardBackground,
                                colors.cardBackground.copy(alpha = 0.92f),
                                accent.copy(alpha = 0.11f),
                            ),
                    ),
                ),
    ) {
        Column(content = content)
    }
}

private fun rankAccent(
    rank: Int,
    colors: DetailColors,
): Color =
    when (rank) {
        1 -> colors.primaryAccent
        2 -> colors.secondaryAccent
        3 -> colors.primaryAccent.copy(alpha = 0.75f)
        else -> colors.secondaryAccent.copy(alpha = 0.45f)
    }

@Composable
private fun TopArtistRow(
    artist: RankedArtistUi,
    modifier: Modifier = Modifier,
) {
    val colors = detailColors()
    val rankColor = rankAccent(artist.rank, colors)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.18f)),
        ) {
            Text(
                text = artist.rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = rankColor,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (artist.isNewTopFive) {
                        stringResource(R.string.sound_capsule_new_to_top_five)
                    } else {
                        stringResource(R.string.sound_capsule_top_artist_of_month)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(colors.thumbnailBackground)
                    .border(1.dp, colors.outline, CircleShape),
        ) {
            if (artist.thumbnailUrl.isNullOrBlank()) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopSongRow(
    rank: Int,
    song: TopSongUi,
    modifier: Modifier = Modifier,
) {
    val colors = detailColors()
    val rankColor = rankAccent(rank, colors)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.18f)),
        ) {
            Text(
                text = rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = rankColor,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.thumbnailBackground)
                    .border(1.dp, colors.outline, RoundedCornerShape(10.dp)),
        ) {
            if (song.thumbnailUrl.isNullOrBlank()) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.subtitle ?: stringResource(R.string.sound_capsule_top_song),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun periodLabel(period: ListeningPeriod): String =
    when (period) {
        ListeningPeriod.MORNING -> stringResource(R.string.sound_capsule_period_morning)
        ListeningPeriod.AFTERNOON -> stringResource(R.string.sound_capsule_period_afternoon)
        ListeningPeriod.EVENING -> stringResource(R.string.sound_capsule_period_evening)
        ListeningPeriod.NIGHT -> stringResource(R.string.sound_capsule_period_night)
    }

private fun calculateDailyAverage(state: SoundCapsuleMonthUiState): Int {
    val today = LocalDate.now(Clock.systemUTC())
    val daysDivisor =
        if (state.yearMonth == YearMonth.of(today.year, today.monthValue)) {
            today.dayOfMonth
        } else {
            state.yearMonth.lengthOfMonth()
        }.coerceAtLeast(1)
    return (state.totalMinutes.toFloat() / daysDivisor.toFloat()).roundToInt()
}

private fun formatCount(value: Int): String = "%,d".format(Locale.getDefault(), value)

private fun emptyState(yearMonth: YearMonth): SoundCapsuleMonthUiState =
    SoundCapsuleMonthUiState(
        year = yearMonth.year,
        month = yearMonth.monthValue,
        totalPlayTimeMs = 0L,
        totalSongsPlayed = 0,
        topArtist = null,
        topSong = null,
        rankedSongs = emptyList(),
        rankedArtists = emptyList(),
        dailyPlayTimeMs = List(yearMonth.lengthOfMonth()) { 0L },
        periodPlayTimeMs = ListeningPeriod.entries.associateWith { 0L },
    )

private data class DetailColors(
    val background: Color,
    val cardBackground: Color,
    val thumbnailBackground: Color,
    val mutedText: Color,
    val subtleText: Color,
    val divider: Color,
    val outline: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
)

@Composable
private fun detailColors(): DetailColors {
    val scheme = MaterialTheme.colorScheme
    return DetailColors(
        background = scheme.background,
        cardBackground = scheme.surfaceVariant.copy(alpha = 0.65f),
        thumbnailBackground = scheme.surfaceContainer,
        mutedText = scheme.onSurfaceVariant,
        subtleText = scheme.onSurfaceVariant.copy(alpha = 0.75f),
        divider = scheme.outlineVariant.copy(alpha = 0.7f),
        outline = scheme.outlineVariant.copy(alpha = 0.45f),
        primaryAccent = scheme.primary,
        secondaryAccent = scheme.secondary,
    )
}
