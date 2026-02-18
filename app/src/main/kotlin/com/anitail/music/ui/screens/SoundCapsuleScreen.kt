package com.anitail.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.time.YearMonth
import java.util.Locale

private val CapsuleBackground = Color(0xFF050609)
private val CapsuleCardBackground = Color(0xFF1C1D21)
private val CapsuleMutedText = Color(0xFFB4B6BE)
private val CapsuleSubtleText = Color(0xFF8A8D96)
private val CapsuleGreen = Color(0xFF1ED760)
private val CapsuleBlue = Color(0xFF4C8EEC)
private val CapsuleYellow = Color(0xFFE8D96A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleScreen(
    navController: NavController,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    val months by viewModel.monthlyCapsules.collectAsState()
    val totalSongs by viewModel.totalSongsSinceJoining.collectAsState()
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LifetimeInsightCard(totalSongs = totalSongs)
                }
            }

            items(items = months, key = { month -> month.monthKey }) { month ->
                SoundCapsuleMonthSection(
                    month = month,
                    onTimeListenedClick = {
                        navController.navigate("stats/time/${month.year}/${month.month}")
                    },
                    onTopArtistsClick = {
                        navController.navigate("stats/top-artists/${month.year}/${month.month}")
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.sound_capsule_title),
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
private fun SoundCapsuleMonthSection(
    month: SoundCapsuleMonthUiState,
    onTimeListenedClick: () -> Unit,
    onTopArtistsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = null,
                tint = CapsuleMutedText,
                modifier = Modifier.size(20.dp),
            )
        }

        if (month.totalMinutes <= 0) {
            NoMusicMonthCard()
            return@Column
        }

        TimeListenedCard(
            month = month,
            onClick = onTimeListenedClick,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopArtistCard(
                month = month,
                onClick = onTopArtistsClick,
                modifier = Modifier.weight(1f),
            )
            TopSongCard(
                month = month,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimeListenedCard(
    month: SoundCapsuleMonthUiState,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.time_listened),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CapsuleMutedText,
                )
                Text(
                    text = stringResource(R.string.sound_capsule_minutes_value, month.totalMinutes),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CapsuleGreen,
                )
            }
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = CapsuleSubtleText,
            )
        }
    }
}

@Composable
private fun TopArtistCard(
    month: SoundCapsuleMonthUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topArtist = month.topArtist
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.sound_capsule_top_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = CapsuleMutedText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = CapsuleSubtleText,
                )
            }
            Text(
                text = topArtist?.name ?: stringResource(R.string.sound_capsule_no_data),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = CapsuleBlue,
                maxLines = 2,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThumbnailCircle(
                    imageUrl = topArtist?.thumbnailUrl,
                    fallbackIcon = R.drawable.person,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (topArtist?.isNewTopFive == true) {
                    NewBadge()
                }
            }
        }
    }
}

@Composable
private fun TopSongCard(
    month: SoundCapsuleMonthUiState,
    modifier: Modifier = Modifier,
) {
    val topSong = month.topSong
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.sound_capsule_top_song),
                    style = MaterialTheme.typography.bodySmall,
                    color = CapsuleMutedText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = CapsuleSubtleText,
                )
            }
            Text(
                text = topSong?.title ?: stringResource(R.string.sound_capsule_no_data),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = CapsuleYellow,
                maxLines = 2,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThumbnailSquare(
                    imageUrl = topSong?.thumbnailUrl,
                    fallbackIcon = R.drawable.music_note,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (topSong?.isNew == true) {
                    NewBadge()
                }
            }
        }
    }
}

@Composable
private fun NoMusicMonthCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.sound_capsule_musicless_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.sound_capsule_musicless_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CapsuleMutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LifetimeInsightCard(totalSongs: Int) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CapsuleCardBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            HeroPulseGraphic()
            Text(
                text =
                    stringResource(
                        R.string.sound_capsule_lifetime_headline,
                        totalSongs,
                    ),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.sound_capsule_lifetime_description),
                style = MaterialTheme.typography.bodyLarge,
                color = CapsuleMutedText,
            )
        }
    }
}

@Composable
private fun HeroPulseGraphic() {
    val barHeights = listOf(24.dp, 40.dp, 60.dp, 40.dp, 24.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        barHeights.forEach { height ->
            Box(
                modifier =
                    Modifier
                        .width(18.dp)
                        .height(height)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF295136), Color(0xFF20D665)),
                            ),
                        ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThumbnailCircle(
    imageUrl: String?,
    fallbackIcon: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Color(0xFF101217)),
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = CapsuleMutedText,
                modifier = Modifier.size(26.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ThumbnailSquare(
    imageUrl: String?,
    fallbackIcon: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101217)),
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = CapsuleMutedText,
                modifier = Modifier.size(26.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun NewBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(Color(0xFF4C4F59))
                .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(R.string.sound_capsule_new),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFE3E4E9),
        )
    }
}

@Composable
fun monthYearLabel(yearMonth: YearMonth): AnnotatedString =
    buildAnnotatedString {
        val locale = Locale.getDefault()
        val monthName = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        append(monthName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() })
        append(" ")
        withStyle(SpanStyle(color = CapsuleMutedText)) {
            append(yearMonth.year.toString())
        }
    }
