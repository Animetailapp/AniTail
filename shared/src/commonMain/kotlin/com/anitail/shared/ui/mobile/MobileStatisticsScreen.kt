@file:Suppress("FunctionName")

package com.anitail.shared.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.shared.db.HistoryStatisticsSnapshot
import com.anitail.shared.db.MusicDatabase
import com.anitail.shared.db.historyChanges
import com.anitail.shared.db.historySong
import com.anitail.shared.db.mobileHistoryStatisticsSnapshot
import com.anitail.shared.db.statisticsSongs
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.share
import com.anitail.shared.generated.resources.statistics_load_failed
import com.anitail.shared.model.YTItem
import com.anitail.shared.ui.playback.PlaybackController
import com.anitail.shared.ui.screens.StatisticsItem
import com.anitail.shared.ui.screens.StatisticsPeriod
import com.anitail.shared.ui.screens.StatisticsScreen
import com.anitail.shared.ui.screens.StatisticsUiState
import com.anitail.shared.ui.screens.statisticsPlayCountDuration
import com.anitail.shared.ui.statistics.StatisticsDateRange
import com.anitail.shared.ui.statistics.StatisticsPeriodMode
import com.anitail.shared.ui.statistics.epochRange
import com.anitail.shared.ui.statistics.statisticsMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
internal fun MobileStatisticsScreen(
    database: MusicDatabase,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onExport: suspend (ImageBitmap) -> Boolean,
    onItemMenu: (YTItem) -> Unit,
    onError: (String) -> Unit,
    onBrowse: (MobileBrowseDestination) -> Unit,
    onAlbumClick: (String, String) -> Unit = { id, title -> onBrowse(MobileBrowseDestination(id, null, title)) },
    onArtistClick: (String, String) -> Unit = { id, name -> onBrowse(MobileBrowseDestination(id, null, name)) },
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val loadFailedMessage = stringResource(Res.string.statistics_load_failed)
    val scope = rememberCoroutineScope()
    var menuJob by remember { mutableStateOf<Job?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var mode by remember { mutableStateOf(StatisticsPeriodMode.CONTINUOUS) }
    var period by remember { mutableStateOf(StatisticsPeriod.WEEK) }
    var dateRange by remember { mutableStateOf<StatisticsDateRange?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember(database) { mutableStateOf<HistoryStatisticsSnapshot?>(null) }
    var isLoading by remember(database) { mutableStateOf(true) }
    var loadFailed by remember(database) { mutableStateOf(false) }
    LaunchedEffect(database, period, mode, dateRange, refreshKey) {
        database.historyChanges().collectLatest {
            isLoading = true
            loadFailed = false
            try {
                snapshot = database.loadMobileStatistics(period, dateRange = dateRange, mode = mode)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }
    val topSongs = snapshot?.songs.orEmpty()
    val topArtists = snapshot?.artists.orEmpty()
    val topAlbums = snapshot?.albums.orEmpty()

    StatisticsScreen(
        exportLabel = stringResource(Res.string.share),
        onExport = onExport,
        state =
            StatisticsUiState(
                period = period,
                mode = mode,
                dateRange = dateRange,
                isLoading = isLoading,
                hasLoaded = snapshot != null,
                errorMessage = if (loadFailed) stringResource(Res.string.statistics_load_failed) else null,
                totalPlays = snapshot?.totals?.playCount ?: 0,
                totalTimeMs = snapshot?.totals?.playTimeMs ?: 0L,
                artistCount = snapshot?.artists?.size ?: 0,
                albumCount = topAlbums.size,
                dailyStats = snapshot?.daily.orEmpty().associate { it.bucket to it.playCount },
                hourlyStats = snapshot?.hourly.orEmpty().associate { it.bucket.toInt() to it.playCount },
                tracks =
                    topSongs.mapIndexed { index, song ->
                        val count = song.playCount
                        StatisticsItem(
                            id = "track:${song.songId}",
                            title = song.title,
                            rankedTitle = "${index + 1}. ${song.title}",
                            subtitle = statisticsPlayCountDuration(count, song.playTimeMs),
                            thumbnailUrl = song.thumbnailUrl,
                        )
                    },
                albums =
                    topAlbums.mapIndexed { index, album ->
                        val count = album.playCount
                        StatisticsItem(
                            id = "album:$index",
                            isClickable = album.id != null,
                            title = album.name,
                            rankedTitle = "${index + 1}. ${album.name}",
                            subtitle = statisticsPlayCountDuration(count, album.playTimeMs),
                            thumbnailUrl = album.thumbnailUrl,
                        )
                    },
                artists =
                    topArtists.mapIndexed { index, artist ->
                        val count = artist.playCount
                        StatisticsItem(
                            id = "artist:$index",
                            isClickable = artist.id != null,
                            title = artist.name,
                            rankedTitle = "${index + 1}. ${artist.name}",
                            subtitle = statisticsPlayCountDuration(count, artist.playTimeMs),
                            thumbnailUrl = artist.thumbnailUrl,
                            circularArtwork = true,
                        )
                    },
            ),
        onModeSelected = {
            if (mode != it || dateRange != null) {
                isLoading = true
                mode = it
                dateRange = null
            }
        },
        onPlayAll = { shuffle ->
            playbackJob?.cancel()
            val ranking = topSongs
            playbackJob =
                scope.launch {
                    try {
                        val songs = database.statisticsSongs(ranking)
                        if (shuffle) playbackController.shuffleQueue(songs) else playbackController.playQueue(songs)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        onError(loadFailedMessage)
                    }
                }
        },
        onPeriodSelected = {
            if (period != it || dateRange != null) {
                isLoading = true
                period = it
                dateRange = null
            }
        },
        onDateRangeSelected = {
            if (dateRange != it) {
                isLoading = true
                dateRange = it
            }
        },
        onRefresh = { refreshKey++ },
        onItemClick = { item ->
            when {
                item.id.startsWith("track:") -> {
                    val id = item.id.removePrefix("track:")
                    playbackJob?.cancel()
                    playbackJob =
                        scope.launch {
                            try {
                                val song =
                                    snapshot?.songItems?.firstOrNull { it.id == id }
                                        ?: topSongs.firstOrNull { it.songId == id }?.let { database.historySong(it.lastEventId) }
                                song?.let(playbackController::play)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                onError(loadFailedMessage)
                            }
                        }
                }

                item.id.startsWith("album:") -> {
                    topAlbums.getOrNull(item.id.removePrefix("album:").toInt())?.let { album ->
                        album.id?.let { onAlbumClick(it, album.name) }
                    }
                }

                item.id.startsWith("artist:") -> {
                    topArtists.getOrNull(item.id.removePrefix("artist:").toInt())?.let { artist ->
                        artist.id?.let { onArtistClick(it, artist.name) }
                    }
                }
            }
        },
        onItemMenu = { item ->
            menuJob?.cancel()
            menuJob =
                scope.launch {
                    try {
                        val mediaItem =
                            when {
                                item.id.startsWith("track:") -> {
                                    val id = item.id.removePrefix("track:")
                                    snapshot?.songItems?.firstOrNull { it.id == id }
                                        ?: topSongs.firstOrNull { it.songId == id }?.let { database.historySong(it.lastEventId) }
                                }

                                item.id.startsWith(
                                    "album:",
                                ) -> {
                                    topAlbums.getOrNull(item.id.removePrefix("album:").toInt())?.statisticsMediaItem()
                                }

                                else -> {
                                    topArtists.getOrNull(item.id.removePrefix("artist:").toInt())?.statisticsMediaItem()
                                }
                            }
                        if (mediaItem != null) onItemMenu(mediaItem) else onError(loadFailedMessage)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        onError(loadFailedMessage)
                    }
                }
        },
        onPlayItem = { item ->
            val id = item.id.removePrefix("track:")
            snapshot
                ?.songItems
                ?.firstOrNull { it.id == id }
                ?.let(playbackController::play)
        },
        onNavigateBack = onBack,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
    )
}

internal suspend fun MusicDatabase.loadMobileStatistics(
    period: StatisticsPeriod,
    clock: Clock = Clock.System,
    dateRange: StatisticsDateRange? = null,
    mode: StatisticsPeriodMode = StatisticsPeriodMode.CONTINUOUS,
): HistoryStatisticsSnapshot {
    val range = period.epochRange(dateRange, clock, mode = mode)
    return mobileHistoryStatisticsSnapshot(range.first, range.second)
}
