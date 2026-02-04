package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.di.DownloadCache
import com.anitail.music.di.PlayerCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val cachedIds = playerCache.keys.mapNotNull { it?.toString() }.toList()

                    if (cachedIds.isEmpty()) {
                        _cachedSongs.value = emptyList()
                        delay(1000)
                        continue
                    }

                    // Usar getSongsByIds para obtener directamente las canciones por sus IDs
                    val cachedSongsList = database.getSongsByIds(cachedIds)

                    _cachedSongs.value = cachedSongsList.sortedByDescending {
                        it.song.dateDownload ?: it.song.inLibrary ?: LocalDateTime.MIN
                    }

                    timber.log.Timber.d("ViewModel: ${cachedIds.size} IDs in cache, ${cachedSongsList.size} found in database")
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error loading cached songs in ViewModel")
                }
                
                delay(1000)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        try {
            playerCache.removeResource(songId)
        } catch (e: Exception) {
            // Ignore errors
        }
    }
}