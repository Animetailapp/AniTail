package com.anitail.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton as AppIconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.viewmodels.SoundCapsuleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCapsuleScreen(
    navController: NavController,
    viewModel: SoundCapsuleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val months by viewModel.monthlyCapsules.collectAsState()
    val totalSongs by viewModel.totalSongsSinceJoining.collectAsState()
    val bottomInsets =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
    val colors = capsuleColors()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 82.dp, bottom = bottomInsets + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
            item {
                StaggeredEntry(index = 0) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LifetimeInsightCard(totalSongs = totalSongs)
                    }
                }
            }

            itemsIndexed(items = months, key = { _, month -> month.monthKey }) { index, month ->
                StaggeredEntry(index = index + 1) {
                    SoundCapsuleMonthSection(
                        month = month,
                        onTimeListenedClick = {
                            navController.navigate("stats/time/${month.year}/${month.month}")
                        },
                        onTopArtistsClick = {
                            navController.navigate("stats/top-artists/${month.year}/${month.month}")
                        },
                        onTopSongsClick = {
                            navController.navigate("stats/top-songs/${month.year}/${month.month}")
                        },
                        onShareClick = {
                            context.shareSoundCapsuleMonth(month)
                        },
                        onOpenArtistClick = { artistId, artistName ->
                            navController.openArtistOrSearch(artistId = artistId, artistName = artistName)
                        },
                        onPlaySongClick = { songId ->
                            coroutineScope.launch {
                                val mediaItem = viewModel.mediaItemForSong(songId)
                                if (mediaItem == null) {
                                    Toast.makeText(context, R.string.sound_capsule_no_data, Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.top_songs),
                                        items = listOf(mediaItem),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
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
                        tint = MaterialTheme.colorScheme.onSurface,
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
    onTopSongsClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenArtistClick: (artistId: String, artistName: String) -> Unit,
    onPlaySongClick: (songId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = capsuleColors()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.thumbnailBackground)
                        .premiumClickable(onClick = onShareClick),
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp),
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
                    onClick = onTopArtistsClick,
                    onOpenArtistClick = onOpenArtistClick,
                )
                HorizontalDivider(color = colors.divider, thickness = 0.8.dp)
                TopSongInsightRow(
                    month = month,
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
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.time_listened),
                style = MaterialTheme.typography.labelLarge,
                color = colors.mutedText,
            )
            Text(
                text = stringResource(R.string.sound_capsule_minutes_value, month.totalMinutes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primaryAccent,
            )
            Text(
                text = stringResource(R.string.sound_capsule_daily_average, dailyAverageForMonth(month)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${formatCount(month.totalSongsPlayed)} ${stringResource(R.string.songs).lowercase(Locale.getDefault())}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primaryAccent,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = colors.subtleText,
        )
    }
}

@Composable
private fun TopArtistInsightRow(
    month: SoundCapsuleMonthUiState,
    onClick: () -> Unit,
    onOpenArtistClick: (artistId: String, artistName: String) -> Unit,
) {
    val colors = capsuleColors()
    val topArtist = month.topArtist
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        ThumbnailCircle(
            imageUrl = topArtist?.thumbnailUrl,
            fallbackIcon = R.drawable.person,
            size = 50.dp,
            onClick = {
                topArtist?.let { artist -> onOpenArtistClick(artist.id, artist.name) }
            },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.sound_capsule_top_artist),
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
            )
            Text(
                text = topArtist?.name ?: stringResource(R.string.sound_capsule_no_data),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (topArtist?.isNewTopFive == true) {
                Text(
                    text = stringResource(R.string.sound_capsule_new_to_top_five),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryAccent,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = colors.subtleText,
        )
    }
}

@Composable
private fun TopSongInsightRow(
    month: SoundCapsuleMonthUiState,
    onClick: () -> Unit,
    onPlaySongClick: (songId: String) -> Unit,
) {
    val colors = capsuleColors()
    val topSong = month.topSong
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        ThumbnailSquare(
            imageUrl = topSong?.thumbnailUrl,
            fallbackIcon = R.drawable.music_note,
            size = 50.dp,
            onClick = { topSong?.let { song -> onPlaySongClick(song.id) } },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.sound_capsule_top_song),
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
            )
            Text(
                text = topSong?.title ?: stringResource(R.string.sound_capsule_no_data),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = topSong?.subtitle ?: stringResource(R.string.sound_capsule_top_song),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (topSong?.isNew == true) {
                Text(
                    text = stringResource(R.string.sound_capsule_new),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primaryAccent,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = colors.subtleText,
        )
    }
}

@Composable
private fun NoMusicMonthCard() {
    val colors = capsuleColors()
    CapsuleSurface(
        accent = colors.secondaryAccent,
        shape = RoundedCornerShape(16.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.sound_capsule_musicless_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LifetimeInsightCard(totalSongs: Int) {
    val colors = capsuleColors()
    CapsuleSurface(
        accent = colors.primaryAccent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(colors.primaryAccent.copy(alpha = 0.12f)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 54.dp, end = 54.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryAccent.copy(alpha = 0.10f)),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                HeroPulseGraphic()
                Text(
                    text =
                        stringResource(
                            R.string.sound_capsule_lifetime_headline,
                            totalSongs,
                        ),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.sound_capsule_lifetime_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
        }
    }
}

@Composable
private fun HeroPulseGraphic() {
    val colors = capsuleColors()
    val barHeights = listOf(12.dp, 20.dp, 30.dp, 20.dp, 12.dp)
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
                        .width(12.dp)
                        .height(height)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(colors.primaryAccent.copy(alpha = 0.45f), colors.primaryAccent),
                            ),
                        ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CapsuleSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = capsuleColors()
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
                                accent.copy(alpha = 0.12f),
                            ),
                    ),
                ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ThumbnailCircle(
    imageUrl: String?,
    fallbackIcon: Int,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = capsuleColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.thumbnailBackground)
                .border(1.dp, colors.outline, CircleShape),
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(22.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (onClick != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .premiumClickable(onClick = onClick),
            )
        }
    }
}

@Composable
private fun ThumbnailSquare(
    imageUrl: String?,
    fallbackIcon: Int,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = capsuleColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.thumbnailBackground)
                .border(1.dp, colors.outline, RoundedCornerShape(8.dp)),
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(22.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (onClick != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .premiumClickable(onClick = onClick),
            )
        }
    }
}

@Composable
fun monthYearLabel(yearMonth: YearMonth): AnnotatedString {
    val colors = capsuleColors()
    return buildAnnotatedString {
        val locale = Locale.getDefault()
        val monthName = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        append(monthName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() })
        append(" ")
        withStyle(SpanStyle(color = colors.mutedText)) {
            append(yearMonth.year.toString())
        }
    }
}

private data class CapsuleColors(
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
private fun capsuleColors(): CapsuleColors {
    val scheme = MaterialTheme.colorScheme
    return CapsuleColors(
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

private fun dailyAverageForMonth(month: SoundCapsuleMonthUiState): Int {
    val days = month.yearMonth.lengthOfMonth().coerceAtLeast(1)
    return month.totalMinutes / days
}

private fun formatCount(value: Int): String = "%,d".format(Locale.getDefault(), value)

@Composable
private fun Modifier.premiumClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.82f),
        label = "capsule_press_scale",
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
private fun StaggeredEntry(
    index: Int,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay((index * 45L).coerceAtMost(320L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 360)) + slideInVertically(initialOffsetY = { it / 6 }),
    ) {
        content()
    }
}

private fun NavController.openArtistOrSearch(
    artistId: String,
    artistName: String,
) {
    if (artistId.startsWith("local:")) {
        navigate("search/${Uri.encode(artistName)}")
    } else {
        navigate("artist/${Uri.encode(artistId)}")
    }
}

private fun android.content.Context.shareSoundCapsuleMonth(month: SoundCapsuleMonthUiState) {
    val artist = month.topArtist?.name ?: getString(R.string.sound_capsule_no_data)
    val song = month.topSong?.title ?: getString(R.string.sound_capsule_no_data)
    val text =
        buildString {
            append(getString(R.string.sound_capsule_title))
            append(" - ")
            append(month.yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() })
            append(" ")
            append(month.year)
            append("\n")
            append(getString(R.string.time_listened))
            append(": ")
            append(getString(R.string.sound_capsule_minutes_value, month.totalMinutes))
            append("\n")
            append(getString(R.string.sound_capsule_top_artist))
            append(": ")
            append(artist)
            append("\n")
            append(getString(R.string.sound_capsule_top_song))
            append(": ")
            append(song)
        }

    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    startActivity(Intent.createChooser(intent, getString(R.string.share)))
}
