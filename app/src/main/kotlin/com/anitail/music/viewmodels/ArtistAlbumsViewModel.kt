package com.anitail.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "Missing artistId navigation argument"
    }
    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, null)

    val albums = database.artistAlbumsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Companion.Lazily, emptyList())
}