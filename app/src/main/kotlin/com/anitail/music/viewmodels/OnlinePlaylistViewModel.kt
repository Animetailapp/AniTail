package com.anitail.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.Album
import com.anitail.music.constants.SongSortType
import com.anitail.music.db.MusicDatabase
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase
) : ViewModel() {
    private val playlistId = requireNotNull(savedStateHandle.get<String>("playlistId")) {
        "Missing playlistId navigation argument"
    }

    private val normalizedPlaylistId = playlistId.removePrefix("VL")
    val isPodcastPlaylist = normalizedPlaylistId == "RDPN" || normalizedPlaylistId == "SE"

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            when (normalizedPlaylistId) {
                "RDPN" -> {
                    YouTube.newEpisodesPlaylistInfo()
                        .onSuccess { playlistInfo ->
                            playlist.value = playlistInfo
                        }.onFailure {
                            playlist.value = PlaylistItem(
                                id = "RDPN",
                                title = "New Episodes",
                                author = null,
                                songCountText = null,
                                thumbnail = null,
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null,
                            )
                        }

                    YouTube.newEpisodes()
                        .onSuccess { songs ->
                            playlistSongs.value = songs.distinctBy { it.id }
                            if (playlist.value?.thumbnail == null && songs.isNotEmpty()) {
                                playlist.value = playlist.value?.copy(thumbnail = songs.first().thumbnail)
                            }
                        }.onFailure {
                            reportException(it)
                        }
                }
                "SE" -> {
                    playlist.value = PlaylistItem(
                        id = "SE",
                        title = "Episodes for Later",
                        author = null,
                        songCountText = null,
                        thumbnail = null,
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )

                    val result = YouTube.episodesForLater()
                    val episodes = result.getOrNull() ?: emptyList()

                    if (result.isSuccess && episodes.isNotEmpty()) {
                        playlistSongs.value = episodes.distinctBy { it.id }
                        playlist.value = playlist.value?.copy(
                            thumbnail = episodes.first().thumbnail,
                            songCountText = "${episodes.size} episodes"
                        )
                    } else {
                        loadLocalSavedEpisodes()
                    }
                }
                else -> {
                    YouTube.playlist(playlistId)
                        .onSuccess { playlistPage ->
                            playlist.value = playlistPage.playlist
                            playlistSongs.value = playlistPage.songs.distinctBy { it.id }
                            continuation = playlistPage.songsContinuation
                        }.onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    private suspend fun loadLocalSavedEpisodes() {
        val savedEpisodes = database.savedPodcastEpisodes(SongSortType.CREATE_DATE, true).firstOrNull() ?: emptyList()
        if (savedEpisodes.isNotEmpty()) {
            val songItems = savedEpisodes.map { song ->
                SongItem(
                    id = song.song.id,
                    title = song.song.title,
                    artists = song.artists.map { Artist(it.name, it.id) },
                    album = song.album?.let { Album(it.title, it.id) },
                    duration = song.song.duration,
                    thumbnail = song.song.thumbnailUrl ?: "",
                    explicit = song.song.explicit,
                    endpoint = null,
                )
            }
            playlist.value = PlaylistItem(
                id = playlistId,
                title = "Episodes for Later",
                author = null,
                songCountText = "${songItems.size} episodes",
                thumbnail = songItems.firstOrNull()?.thumbnail ?: "",
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            playlistSongs.value = songItems.distinctBy { it.id }
        }
    }

    fun loadMoreSongs() {
        continuation?.let {
            viewModelScope.launch(Dispatchers.IO) {
                YouTube.playlistContinuation(it)
                    .onSuccess { playlistContinuationPage ->
                        val currentSongs = playlistSongs.value.toMutableList()
                        currentSongs.addAll(playlistContinuationPage.songs)
                        playlistSongs.value = currentSongs.distinctBy { it.id }
                        continuation = playlistContinuationPage.continuation
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }
}
