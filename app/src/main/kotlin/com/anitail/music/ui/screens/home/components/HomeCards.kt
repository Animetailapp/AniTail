package com.anitail.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.music.R
import com.anitail.music.viewmodels.CommunityPlaylistItem
import com.anitail.music.viewmodels.DailyDiscoverItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialTile(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick),
    ) {
        AsyncImage(
            model = highResThumbnail(item.thumbnail, 544, 544) ?: item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialSurpriseTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF6A5D82))
            .combinedClickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2D8F3)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D8F3)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCollage(
    thumbnails: List<String?>,
    modifier: Modifier = Modifier,
) {
    val cells = androidx.compose.runtime.remember(thumbnails) {
        val prepared = thumbnails.filterNotNull().toMutableList()
        if (prepared.isEmpty()) prepared += ""
        while (prepared.size < 4) prepared += prepared.last()
        prepared.take(4)
    }

    Column(
        modifier = modifier
            .size(90.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        repeat(2) { rowIndex ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(2) { columnIndex ->
                    val index = rowIndex * 2 + columnIndex
                    AsyncImage(
                        model = highResThumbnail(cells[index], 544, 544) ?: cells[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityActionButton(
    iconRes: Int,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
            )
            .combinedClickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (emphasized) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onOpenPlaylist: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: () -> Unit,
    onRadioClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .combinedClickable(onClick = onOpenPlaylist),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistCollage(
                    thumbnails = item.songs.take(4).map { it.thumbnail } + item.playlist.thumbnail,
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.playlist.songCountText ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            item.songs.take(3).forEach { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { onSongClick(song) }),
                ) {
                    AsyncImage(
                        model = highResThumbnail(song.thumbnail, 544, 544) ?: song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CommunityActionButton(
                    iconRes = R.drawable.play,
                    emphasized = true,
                    onClick = onPlayAllClick,
                )
                CommunityActionButton(
                    iconRes = R.drawable.radio,
                    onClick = onRadioClick,
                )
                CommunityActionButton(
                    iconRes = R.drawable.playlist_add,
                    onClick = onAddClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1828)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = highResThumbnail(item.recommendation.thumbnail, 1200, 1200)
                    ?: item.recommendation.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                Text(
                    text = item.recommendation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.recommendation.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.daily_discover_based_on, item.seed.title),
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
