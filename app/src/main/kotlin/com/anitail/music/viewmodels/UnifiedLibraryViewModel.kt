package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.anitail.innertube.YouTube
import com.anitail.music.constants.AlbumSortType
import com.anitail.music.constants.ArtistSortType
import com.anitail.music.constants.PlaylistSortType
import com.anitail.music.constants.SongSortType
import com.anitail.music.constants.TopSize
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.PodcastEntity
import com.anitail.music.db.entities.Song
import com.anitail.music.downloads.DownloadLibraryRepository
import com.anitail.music.playback.DownloadUtil
import com.anitail.music.ui.screens.library.LibraryEntry
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

data class UnifiedLibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val hasLocalSongs: Boolean = false,
    val topSize: String = "100",
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val syncError: Boolean = false,
)

private data class BaseCollectionsSnapshot(
    val songs: List<Song>,
    val likedSongs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
)

private data class ExtraCollectionsSnapshot(
    val podcasts: List<PodcastEntity>,
    val podcastChannels: List<Artist>,
    val downloadedSongIds: Set<String>,
    val downloadedAlbumIds: Set<String>,
    val topSize: String,
)

@HiltViewModel
class UnifiedLibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    downloadRepository: DownloadLibraryRepository,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val syncError = MutableStateFlow(false)

    private val baseCollections = combine(
        database.songs(SongSortType.CREATE_DATE, true),
        database.likedSongs(SongSortType.CREATE_DATE, true),
        database.albumsLiked(AlbumSortType.CREATE_DATE, true),
        database.artistsBookmarked(ArtistSortType.CREATE_DATE, true),
        database.playlists(PlaylistSortType.CREATE_DATE, true),
    ) { songs, likedSongs, albums, artists, playlists ->
        BaseCollectionsSnapshot(
            songs = songs,
            likedSongs = likedSongs,
            albums = albums,
            artists = artists,
            playlists = playlists,
        )
    }

    private val downloadIds = combine(
        downloadRepository.observeDownloads(),
        downloadUtil.downloads,
    ) { mediaStoreTracks, exoDownloads ->
        val completedExoIds = exoDownloads
            .filterValues { it.state == Download.STATE_COMPLETED }
            .keys
        val mediaStoreIds = mediaStoreTracks.mapNotNull { it.song?.id }
        val albumIds = mediaStoreTracks
            .mapNotNull { track -> track.song?.song?.albumId ?: track.song?.album?.id }
            .toSet()
        (completedExoIds + mediaStoreIds) to albumIds
    }

    private val extraCollections = combine(
        database.subscribedPodcasts(),
        database.bookmarkedPodcastChannels(),
        downloadIds,
        context.dataStore.data.map { it[TopSize] ?: "100" }.distinctUntilChanged(),
    ) { podcasts, podcastChannels, (downloadedSongIds, downloadedAlbumIds), topSize ->
        ExtraCollectionsSnapshot(
            podcasts = podcasts,
            podcastChannels = podcastChannels,
            downloadedSongIds = downloadedSongIds,
            downloadedAlbumIds = downloadedAlbumIds,
            topSize = topSize,
        )
    }

    val uiState: StateFlow<UnifiedLibraryUiState> = combine(
        baseCollections,
        extraCollections,
        isRefreshing,
        syncError,
    ) { base, extra, refreshing, hasError ->
        val mergedSongs = (base.songs + base.likedSongs)
            .filterNot { it.song.isEpisode }
            .distinctBy { it.id }
        val hasLocal = mergedSongs.any { it.song.isLocal }
        val entries = buildList<LibraryEntry> {
            mergedSongs.forEach { song ->
                val isDownloaded = song.id in extra.downloadedSongIds ||
                    (!song.song.isLocal && !song.song.mediaStoreUri.isNullOrEmpty())
                add(LibraryEntry.SongEntry(song = song, isDownloaded = isDownloaded))
            }
            base.albums.forEach { album ->
                val isDownloaded = album.id in extra.downloadedAlbumIds
                add(LibraryEntry.AlbumEntry(album = album, isDownloaded = isDownloaded))
            }
            base.artists.forEach { artist ->
                add(LibraryEntry.ArtistEntry(artist = artist))
            }
            base.playlists.forEach { playlist ->
                add(LibraryEntry.PlaylistEntry(playlist = playlist))
            }
            extra.podcasts.forEach { podcast ->
                add(LibraryEntry.PodcastEntry(podcast = podcast))
            }
            extra.podcastChannels.forEach { channel ->
                add(
                    LibraryEntry.PodcastChannelEntry(
                        channelId = channel.id,
                        artist = channel,
                    ),
                )
            }
        }
        UnifiedLibraryUiState(
            entries = entries,
            hasLocalSongs = hasLocal,
            topSize = extra.topSize,
            isInitialLoading = false,
            isRefreshing = refreshing,
            syncError = hasError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UnifiedLibraryUiState(),
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            baseCollections.collect { snapshot ->
                snapshot.albums
                    .filter { it.album.songCount == 0 }
                    .forEach { album ->
                        YouTube.album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }
                            .onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query { delete(album.album) }
                                }
                            }
                    }

                snapshot.artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                            Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
                    }
                    .forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query { update(artist, artistPage) }
                        }
                    }
            }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            syncError.value = false
            try {
                val results = coroutineScope {
                    listOf(
                        async { runCatching { syncUtils.syncLikedSongs() } },
                        async { runCatching { syncUtils.syncLibrarySongs() } },
                        async { runCatching { syncUtils.syncArtistsSubscriptions() } },
                        async { runCatching { syncUtils.syncLikedAlbums() } },
                        async { runCatching { syncUtils.syncSavedPlaylists() } },
                        async { runCatching { syncPodcastSubscriptions() } },
                    ).awaitAll()
                }
                val anyFailed = results.any { it.isFailure }
                results.forEach { res ->
                    res.exceptionOrNull()?.let { err ->
                        if (err is CancellationException) throw err
                        reportException(err)
                    }
                }
                syncError.update { anyFailed && uiState.value.entries.isEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                syncError.value = uiState.value.entries.isEmpty()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private suspend fun syncPodcastSubscriptions() {
        YouTube.savedPodcastShows().onSuccess { remotePodcasts ->
            val remoteById = remotePodcasts.associateBy { it.id }
            val localPodcasts = database.subscribedPodcasts().first()
            database.transaction {
                localPodcasts
                    .filter { it.id !in remoteById.keys }
                    .forEach { local ->
                        update(
                            local.copy(
                                bookmarkedAt = null,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                remotePodcasts.forEach { podcast ->
                    val existing = localPodcasts.firstOrNull { it.id == podcast.id }
                    upsert(
                        PodcastEntity(
                            id = podcast.id,
                            title = podcast.title,
                            author = podcast.author?.name,
                            thumbnailUrl = podcast.thumbnail,
                            channelId = podcast.channelId,
                            bookmarkedAt = existing?.bookmarkedAt ?: LocalDateTime.now(),
                            lastUpdateTime = LocalDateTime.now(),
                            libraryAddToken = podcast.libraryAddToken,
                            libraryRemoveToken = podcast.libraryRemoveToken,
                        ),
                    )
                }
            }
        }.onFailure { reportException(it) }
    }
}

