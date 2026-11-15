package com.anitail.shared.settings

import com.anitail.shared.model.CollectionSort
import com.anitail.shared.model.PlaylistSongSort
import com.anitail.shared.model.SortDirection
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlaylistSongSorting(
    val sort: PlaylistSongSort,
    val direction: SortDirection,
)

@Serializable
data class SavedPlaylistCollectionSorting(
    val sort: CollectionSort,
    val direction: SortDirection,
)
