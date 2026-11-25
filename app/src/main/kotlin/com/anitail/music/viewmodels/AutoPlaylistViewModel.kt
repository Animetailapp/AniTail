package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.utils.completed
import com.anitail.music.constants.SongSortDescendingKey
import com.anitail.music.constants.SongSortType
import com.anitail.music.constants.SongSortTypeKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.AlbumEntity
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.reversed
import com.anitail.music.extensions.toEnum
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.DownloadUtil
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = requireNotNull(savedStateHandle.get<String>("playlist")) {
        "Missing playlist navigation argument"
    }

    private val uploadedSongsState = MutableStateFlow<List<Song>?>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map {
                it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey]
                    ?: true)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                when (playlist) {
                    "liked" -> database.likedSongs(sortType, descending)
                    "downloaded" -> downloadUtil.downloads
                        .flatMapLatest { downloads ->
                            database.allSongs()
                            .flowOn(Dispatchers.IO)
                            .map { allSongs ->
                                allSongs.filter { song ->
                                    downloads[song.id]?.state == Download.STATE_COMPLETED ||
                                        (!song.song.isLocal && !song.song.mediaStoreUri.isNullOrEmpty())
                                }.distinctBy { it.id }
                            }
                            .map { songs ->
                                when (sortType) {
                                    SongSortType.CREATE_DATE -> songs.sortedBy {
                                        it.song.dateDownload?.toString() ?: ""
                                    }

                                    SongSortType.NAME -> songs.sortedBy { it.song.title }
                                    SongSortType.ARTIST -> songs.sortedBy { song ->
                                        song.song.artistName ?: song.artists.joinToString(separator = "") { it.name }
                                    }

                                    SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
                                }.reversed(descending)
                            }
                    }

                    "uploaded" -> uploadedSongsState.map { remoteSongs ->
                        val songs = remoteSongs.orEmpty()
                        when (sortType) {
                            SongSortType.CREATE_DATE -> songs
                            SongSortType.NAME -> songs.sortedBy { it.song.title.lowercase() }
                            SongSortType.ARTIST -> songs.sortedBy { song ->
                                (song.song.artistName ?: song.artists.joinToString("") { it.name }).lowercase()
                            }
                            SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
                        }.let { sorted ->
                            if (sortType == SongSortType.CREATE_DATE) {
                                if (descending) sorted else sorted.reversed()
                            } else {
                                sorted.reversed(descending)
                            }
                        }
                    }

                    else -> MutableStateFlow(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.library("FEmusic_library_privately_owned_tracks").completed()
                .onSuccess { page ->
                    val songs = page.items
                        .filterIsInstance<SongItem>()
                        .distinctBy { it.id }
                        .mapIndexed { index, item ->
                            val metadata = item.toMediaMetadata()
                            Song(
                                song = metadata.toSongEntity().copy(
                                    artistName = item.artists.joinToString(", ") { it.name }.ifBlank { null },
                                ),
                                artists = item.artists.map { artist ->
                                    ArtistEntity(
                                        id = artist.id ?: "UC_UPLOADED_${artist.name.hashCode()}",
                                        name = artist.name,
                                    )
                                },
                                album = item.album?.let { album ->
                                    AlbumEntity(
                                        id = album.id,
                                        title = album.name,
                                        songCount = 0,
                                        duration = 0,
                                    )
                                },
                            )
                        }
                    uploadedSongsState.value = songs
                }
                .onFailure { reportException(it) }
        }
    }
}
