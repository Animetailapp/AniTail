package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.di.DownloadCache
import com.anitail.music.di.PlayerCache
import com.anitail.music.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val cachedIds = playerCache.keys.mapNotNull { it?.toString() }.toSet()
                val downloadedIds = downloadCache.keys.mapNotNull { it?.toString() }.toSet()
                val managedDownloadIds = downloadUtil.downloads.value
                    .filterValues { download ->
                        download.state == Download.STATE_COMPLETED
                    }
                    .keys
                val pureCacheIds = cachedIds.subtract(downloadedIds).subtract(managedDownloadIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                val completeSongs = songs.filter {
                    val contentLength = it.format?.contentLength
                    val hasAnyCachedSpan = playerCache.getCachedSpans(it.song.id).isNotEmpty()
                    contentLength == null || playerCache.isCached(it.song.id, 0, contentLength) || hasAnyCachedSpan
                }

                _cachedSongs.value = completeSongs
                    .sortedByDescending { it.song.dateDownload ?: java.time.LocalDateTime.MIN }

                delay(2500)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}
