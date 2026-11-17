package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.innertube.YouTube as InnertubeYouTube
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.rememberExpressiveAvatarShape
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPodcastsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    onDeselect: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var podcastFilter by remember { mutableStateOf(PodcastFilter.EPISODES) }
    var sePlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var rdpnPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var podcastChannels by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val songs by database.songs.collectAsState(initial = emptyList())
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val songsById = remember(songs) { songs.associateBy { it.id } }

    suspend fun loadPodcastData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            InnertubeYouTube.newEpisodesPlaylistInfo().onSuccess { rdpnPlaylist = it }
            InnertubeYouTube.library("FEmusic_liked_playlists").onSuccess { page ->
                sePlaylist = page.items
                    .filterIsInstance<PlaylistItem>()
                    .find { it.id == "SE" }
            }
            InnertubeYouTube.libraryPodcastChannels().onSuccess { page ->
                podcastChannels = page.items.filterIsInstance<ArtistItem>()
            }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadPodcastData()
    }

    val downloadedEntries = remember(downloadedSongs, songsById, podcastChannels) {
        val channelNames = podcastChannels.map { it.title.lowercase() }.toSet()
        downloadedSongs
            .filter { downloaded ->
                channelNames.isNotEmpty() && channelNames.any { downloaded.artist.lowercase().contains(it) }
            }
            .map { downloaded ->
                val songEntity = songsById[downloaded.songId] ?: com.anitail.desktop.db.entities.SongEntity(
                    id = downloaded.songId,
                    title = downloaded.title,
                    artistName = downloaded.artist,
                    duration = downloaded.duration,
                    thumbnailUrl = downloaded.thumbnailUrl,
                )
                val item = LibraryItem(
                    id = downloaded.songId,
                    title = downloaded.title,
                    artist = downloaded.artist,
                    artworkUrl = downloaded.thumbnailUrl,
                    playbackUrl = downloaded.filePath,
                    durationMs = downloaded.duration.toLong() * 1000L,
                )
                Triple(downloaded, songEntity, item)
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "podcast-filter-row") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    label = { Text(stringResource("filter_podcasts")) },
                    selected = true,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    onClick = onDeselect,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(imageVector = IconAssets.close(), contentDescription = null)
                    },
                )
                ChipsRow(
                    chips = listOf(
                        PodcastFilter.EPISODES to stringResource("filter_episodes"),
                        PodcastFilter.CHANNELS to stringResource("filter_channels"),
                        PodcastFilter.DOWNLOADED to stringResource("filter_downloaded"),
                    ),
                    currentValue = podcastFilter,
                    onValueUpdate = { podcastFilter = it },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { scope.launch { loadPodcastData() } },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.refresh(),
                        contentDescription = null,
                    )
                }
            }
        }

        when (podcastFilter) {
            PodcastFilter.EPISODES -> {
                val playlists = listOfNotNull(rdpnPlaylist, sePlaylist)
                if (isLoading && playlists.isEmpty()) {
                    item(key = "podcasts-loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (playlists.isEmpty()) {
                    item(key = "podcasts-episodes-empty") {
                        EmptyPodcastPlaceholder(
                            title = stringResource("filter_podcasts"),
                            subtitle = stringResource("filter_episodes"),
                        )
                    }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        PodcastPlaylistRow(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id, playlist.title) },
                        )
                    }
                }
            }

            PodcastFilter.CHANNELS -> {
                if (isLoading && podcastChannels.isEmpty()) {
                    item(key = "channels-loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (podcastChannels.isEmpty()) {
                    item(key = "podcasts-channels-empty") {
                        EmptyPodcastPlaceholder(
                            title = stringResource("filter_podcasts"),
                            subtitle = stringResource("filter_channels"),
                        )
                    }
                } else {
                    items(podcastChannels, key = { it.id }) { channel ->
                        val shape = rememberExpressiveAvatarShape(channel.id)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenArtist(channel.id, channel.title) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                RemoteImage(
                                    url = channel.thumbnail,
                                    shape = shape,
                                    modifier = Modifier.size(52.dp).clip(shape),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource("filter_channels"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = IconAssets.navigateNext(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            PodcastFilter.DOWNLOADED -> {
                if (downloadedEntries.isEmpty()) {
                    item(key = "podcasts-downloaded-empty") {
                        EmptyPodcastPlaceholder(
                            title = stringResource("filter_podcasts"),
                            subtitle = stringResource("filter_downloaded"),
                        )
                    }
                } else {
                    items(downloadedEntries, key = { it.first.songId }) { (downloaded, songEntity, libraryItem) ->
                        LibrarySongListItem(
                            song = songEntity,
                            showInLibraryIcon = false,
                            downloaded = true,
                            isActive = downloaded.songId == playerState.currentItem?.id,
                            isPlaying = playerState.isPlaying,
                            isSelected = false,
                            onClick = {
                                if (downloaded.songId == playerState.currentItem?.id) {
                                    playerState.togglePlayPause()
                                } else {
                                    playerState.playQueue(listOf(libraryItem), startIndex = 0)
                                }
                            },
                            onMenuClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastPlaylistRow(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            RemoteImage(
                url = playlist.thumbnail,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = playlist.songCountText ?: playlist.author?.name.orEmpty()
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = IconAssets.navigateNext(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPodcastPlaceholder(
    title: String,
    subtitle: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = IconAssets.libraryOutlined(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

