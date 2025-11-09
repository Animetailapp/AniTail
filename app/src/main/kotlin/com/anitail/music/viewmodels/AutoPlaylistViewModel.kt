package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.constants.SongSortDescendingKey
import com.anitail.music.constants.SongSortType
import com.anitail.music.constants.SongSortTypeKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.downloads.DownloadLibraryRepository
import com.anitail.music.extensions.reversed
import com.anitail.music.extensions.toEnum
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    downloadLibraryRepository: DownloadLibraryRepository,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

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
                    "downloaded" -> downloadLibraryRepository.observeDownloads()
                        .flatMapLatest { downloads ->
                            // Get only the downloads that have a Song mapped in the database
                            val downloadedUris =
                                downloads.mapNotNull { it.song?.song?.mediaStoreUri }.toSet()
                        
                        database.allSongs()
                            .flowOn(Dispatchers.IO)
                            .map { allSongs ->
                                // Filter songs that have mediaStoreUri in the downloads list
                                // Also deduplicate by song ID in case Room returns duplicates
                                allSongs.filter { song ->
                                    song.song.mediaStoreUri != null && song.song.mediaStoreUri in downloadedUris
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

                    else -> MutableStateFlow(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }
}
