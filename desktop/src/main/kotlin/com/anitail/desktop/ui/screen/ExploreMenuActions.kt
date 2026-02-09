package com.anitail.desktop.ui.screen

import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.innertube.models.AlbumItem

fun buildExploreAlbumMenuActions(
    album: AlbumItem,
    onOpenAlbum: (String, String?) -> Unit,
    copyToClipboard: (String) -> Unit,
): List<ContextMenuAction> {
    return buildBrowseCollectionMenuActions(
        item = album,
        onOpen = { onOpenAlbum(album.browseId, album.title) },
        copyToClipboard = copyToClipboard,
    )
}
