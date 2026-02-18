package com.anitail.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.SpanStyle
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
import kotlin.math.roundToInt

private val CapsuleBackground = Color(0xFF050609)
private val CapsuleCardBackground = Color(0xFF1C1D21)
private val CapsuleMutedText = Color(0xFFB4B6BE)
private val CapsuleSubtleText = Color(0xFF8A8D96)
private val CapsuleGreen = Color(0xFF1ED760)
private val CapsuleBlue = Color(0xFF4C8EEC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleTimeListenedScreen(
    navController: NavController,
    year: Int,
    month: Int,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
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
                .background(CapsuleBackground),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 84.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    Text(
                        text = monthYearLabel(state.yearMonth),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    TimeHeadline(totalMinutes = state.totalMinutes)
                    Text(
                        text = stringResource(R.string.sound_capsule_daily_average, averageMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = CapsuleMutedText,
                    )
                    DailyMinutesChart(
                        dailyPlayTimeMs = state.dailyPlayTimeMs,
                        averageMinutes = averageMinutes,
                    )
                }
            }

            item {
                HorizontalDivider(
                    color = Color(0xFF2A2C34),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    DominantPeriodTitle(period = dominantPeriod)
                    PeriodBubbleRow(state = state)
                }
            }

            item {
                HorizontalDivider(
                    color = Color(0xFF2A2C34),
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
                        color = Color.White,
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
                        tint = Color.White,
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
                .background(CapsuleBackground),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 84.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    Text(
                        text = monthYearLabel(state.yearMonth),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    TopArtistsHeadline(count = state.rankedArtists.size)
                }
            }

            if (state.rankedArtists.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sound_capsule_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CapsuleMutedText,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(items = state.rankedArtists, key = { artist -> artist.id }) { artist ->
                    TopArtistRow(
                        artist = artist,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
                        tint = Color.White,
                    )
                }
            },
        )
    }
}

@Composable
private fun TimeHeadline(totalMinutes: Int) {
    Text(
        text =
            buildAnnotatedString {
                append(stringResource(R.string.sound_capsule_time_prefix))
                withStyle(SpanStyle(color = CapsuleGreen)) {
                    append(stringResource(R.string.sound_capsule_minutes_value, totalMinutes))
                }
                append(stringResource(R.string.sound_capsule_time_suffix))
            },
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
    )
}

@Composable
private fun TopArtistsHeadline(count: Int) {
    Text(
        text =
            buildAnnotatedString {
                append(stringResource(R.string.sound_capsule_top_artists_prefix))
                withStyle(SpanStyle(color = CapsuleBlue)) {
                    append(count.toString())
                    append(" ")
                    append(stringResource(R.string.artists).lowercase())
                }
                append(stringResource(R.string.sound_capsule_top_artists_suffix))
            },
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
    )
}

@Composable
private fun DailyMinutesChart(
    dailyPlayTimeMs: List<Long>,
    averageMinutes: Int,
) {
    val dailyMinutes = dailyPlayTimeMs.map { (it / 60_000f) }
    val maxMinutes = dailyMinutes.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val chartMax = maxOf(maxMinutes, averageMinutes.toFloat(), 1f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
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
                            color = CapsuleGreen,
                            start = Offset(x, drawHeight),
                            end = Offset(x, drawHeight - barHeight),
                            strokeWidth = stepX * 0.55f,
                            cap = StrokeCap.Round,
                        )
                    }

                    val averageY = drawHeight - ((averageMinutes / chartMax) * drawHeight)
                    drawLine(
                        color = Color.White,
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
                        color = CapsuleSubtleText,
                    )
                    Text(
                        text = (chartMax / 2f).roundToInt().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CapsuleSubtleText,
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.bodySmall,
                        color = CapsuleSubtleText,
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
                        color = CapsuleMutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DominantPeriodTitle(period: ListeningPeriod) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = CapsuleGreen)) {
                    append(periodLabel(period))
                }
                append(stringResource(R.string.sound_capsule_period_suffix))
            },
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
    )
}

@Composable
private fun PeriodBubbleRow(state: SoundCapsuleMonthUiState) {
    val periodMinutes =
        ListeningPeriod.entries.map { period ->
            period to ((state.periodPlayTimeMs[period] ?: 0L) / 60_000L).toInt()
        }
    val maxMinutes = periodMinutes.maxOfOrNull { (_, value) -> value }?.coerceAtLeast(1) ?: 1

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        periodMinutes.forEach { (period, minutes) ->
            val ratio = minutes.toFloat() / maxMinutes.toFloat()
            val bubbleSize = 56.dp + (62.dp * ratio)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(bubbleSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xAA29E66E), Color(0x402E1238)),
                                ),
                            ),
                ) {
                    Text(
                        text =
                            if (minutes > 0) {
                                stringResource(R.string.sound_capsule_minutes_short, minutes)
                            } else {
                                "0"
                            },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                Text(
                    text = periodLabel(period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CapsuleMutedText,
                )
            }
        }
    }
}

@Composable
private fun MusicForNowCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A2B8D)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.spotify),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playlist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CapsuleMutedText,
                )
                Text(
                    text = stringResource(R.string.sound_capsule_on_repeat),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TopArtistRow(
    artist: RankedArtistUi,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = artist.rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = CapsuleBlue,
            modifier = Modifier.width(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text =
                    if (artist.isNewTopFive) {
                        stringResource(R.string.sound_capsule_new_to_top_five)
                    } else {
                        stringResource(R.string.sound_capsule_top_artist_of_month)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = CapsuleMutedText,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF101217)),
        ) {
            if (artist.thumbnailUrl.isNullOrBlank()) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = CapsuleMutedText,
                    modifier = Modifier.size(26.dp),
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

private fun emptyState(yearMonth: YearMonth): SoundCapsuleMonthUiState =
    SoundCapsuleMonthUiState(
        year = yearMonth.year,
        month = yearMonth.monthValue,
        totalPlayTimeMs = 0L,
        totalSongsPlayed = 0,
        topArtist = null,
        topSong = null,
        rankedArtists = emptyList(),
        dailyPlayTimeMs = List(yearMonth.lengthOfMonth()) { 0L },
        periodPlayTimeMs = ListeningPeriod.entries.associateWith { 0L },
    )
