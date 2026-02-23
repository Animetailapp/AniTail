/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.Song
import com.anitail.music.utils.LocalMusicRepository
import com.anitail.music.utils.LocalSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val localMusicRepository: LocalMusicRepository,
) : ViewModel() {

    init {
        Timber.d("LocalMusicViewModel: INIT - ViewModel created")
    }

    private val _hasPermission = MutableStateFlow(localMusicRepository.hasPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val syncStatus: StateFlow<LocalSyncStatus> = localMusicRepository.syncStatus

    val syncProgress: StateFlow<Float> = localMusicRepository.syncProgress

    val localArtists = database.localArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allLocalSongs = database.allLocalSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allLocalAlbums = database.allLocalAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Root-level folders (computed from paths)
    val rootFolders: StateFlow<List<Pair<String, Int>>> = database.localSongPaths()
        .map { paths ->
            Timber.d("LocalMusicViewModel: Computing rootFolders from ${paths.size} song paths")

            // Get all unique folder paths that directly contain songs
            val folderCounts = paths.mapNotNull { path ->
                path.substringBeforeLast('/')
            }.groupingBy { it }.eachCount()

            Timber.d("LocalMusicViewModel: Found ${folderCounts.size} unique folders")

            // Find the common prefix (e.g., /storage/emulated/0)
            val allFolders = folderCounts.keys.toList()
            val commonPrefix = if (allFolders.isNotEmpty()) {
                val firstParts = allFolders.first().split("/")
                var prefix = ""
                for (i in firstParts.indices) {
                    val candidate = firstParts.take(i + 1).joinToString("/")
                    if (allFolders.all { it.startsWith("$candidate/") || it == candidate }) {
                        prefix = candidate
                    } else {
                        break
                    }
                }
                prefix
            } else ""

            Timber.d("LocalMusicViewModel: Common prefix = '$commonPrefix'")

            // Get immediate children of the common prefix as root folders
            val rootFolders = allFolders.mapNotNull { folder ->
                if (folder.startsWith("$commonPrefix/")) {
                    val remaining = folder.removePrefix("$commonPrefix/")
                    val firstChild = remaining.split("/").firstOrNull()
                    if (firstChild != null) "$commonPrefix/$firstChild" else null
                } else null
            }.distinct()

            Timber.d("LocalMusicViewModel: Found ${rootFolders.size} root folders")

            // Count all songs under each root (including subfolders)
            rootFolders.map { root ->
                val totalSongs = folderCounts.entries
                    .filter { it.key == root || it.key.startsWith("$root/") }
                    .sumOf { it.value }
                root to totalSongs
            }.sortedBy { it.first.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getSongsInFolder(folderPath: String) = database.localSongsInFolder(folderPath)

    fun getSubfolders(parentPath: String): StateFlow<List<String>> = database.localSongPaths()
        .map { paths ->
            paths.mapNotNull { path ->
                val folder = path.substringBeforeLast('/')
                if (folder.startsWith("$parentPath/") && folder != parentPath) {
                    val remaining = folder.removePrefix("$parentPath/")
                    val nextFolder = remaining.split("/").firstOrNull()
                    if (nextFolder != null) "$parentPath/$nextFolder" else null
                } else null
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun syncLocalMusic() {
        Timber.d("LocalMusicViewModel: syncLocalMusic() called")
        // Update permission state before syncing
        _hasPermission.value = localMusicRepository.hasPermission()
        localMusicRepository.syncLocalMusic()
        Timber.d("LocalMusicViewModel: syncLocalMusic() completed")
    }

    fun refreshPermissionState() {
        val newState = localMusicRepository.hasPermission()
        Timber.d("LocalMusicViewModel: refreshPermissionState() = $newState (was ${_hasPermission.value})")
        _hasPermission.value = newState
    }

    fun checkPermission(): Boolean {
        val result = localMusicRepository.hasPermission()
        Timber.d("LocalMusicViewModel: checkPermission() = $result")
        _hasPermission.value = result
        return result
    }

    init {
        Timber.d("LocalMusicViewModel: Secondary init block - checking permission")
        if (localMusicRepository.hasPermission()) {
            Timber.d("LocalMusicViewModel: Permission granted, auto-starting sync")
            viewModelScope.launch {
                localMusicRepository.syncLocalMusic()
            }
        } else {
            Timber.d("LocalMusicViewModel: Permission NOT granted, skipping auto-sync")
        }
    }
}

@HiltViewModel
class LocalArtistViewModel @Inject constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("artistId")!!

    init {
        Timber.d("LocalArtistViewModel: INIT - artistId=$artistId")
    }

    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val albums = database.localAlbumsByArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val songs = database.localSongsByArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LocalAlbumViewModel @Inject constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>("albumId")!!

    init {
        Timber.d("LocalAlbumViewModel: INIT - albumId=$albumId")
    }

    val album = database.album(albumId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = database.localSongsByAlbum(albumId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LocalFolderViewModel @Inject constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderPath: String = savedStateHandle.get<String>("folderPath")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: ""

    init {
        Timber.d("LocalFolderViewModel: INIT - folderPath=$folderPath")
    }

    // Songs directly in this folder
    val songs = database.localSongsInFolder(folderPath)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All songs including subfolders (for play all/shuffle)
    val allSongs = database.localSongsInFolderRecursive(folderPath)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val subfolders: StateFlow<List<String>> = database.localSongPaths()
        .map { paths ->
            paths.mapNotNull { path ->
                val folder = path.substringBeforeLast('/')
                if (folder.startsWith("$folderPath/") && folder != folderPath) {
                    val remaining = folder.removePrefix("$folderPath/")
                    val nextFolder = remaining.split("/").firstOrNull()
                    if (nextFolder != null) "$folderPath/$nextFolder" else null
                } else null
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

