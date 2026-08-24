package com.anitail.desktop.model

import com.anitail.desktop.db.entities.SongEntity
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem

data class DailyDiscoverItem(
    val seed: SongEntity,
    val recommendation: SongItem,
    val relatedEndpoint: BrowseEndpoint?,
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
)
