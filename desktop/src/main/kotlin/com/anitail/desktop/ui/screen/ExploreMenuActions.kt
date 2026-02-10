package com.anitail.desktop.ui.screen

import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.i18n.StringResolver

fun buildExploreAlbumMenuActions(
    strings: StringResolver,
    hasArtists: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onOpenArtist: () -> Unit,
    onShare: () -> Unit,
): List<ContextMenuAction> {
    val actions = mutableListOf(
        ContextMenuAction(
            label = strings.get("start_radio"),
            icon = IconAssets.radio(),
            onClick = onStartRadio,
        ),
        ContextMenuAction(
            label = strings.get("play_next"),
            icon = IconAssets.queueMusic(),
            onClick = onPlayNext,
        ),
        ContextMenuAction(
            label = strings.get("add_to_queue"),
            icon = IconAssets.playlistAdd(),
            onClick = onAddToQueue,
        ),
        ContextMenuAction(
            label = strings.get("add_to_playlist"),
            icon = IconAssets.playlistAdd(),
            onClick = onAddToPlaylist,
        ),
        ContextMenuAction(
            label = downloadLabel,
            icon = IconAssets.download(),
            onClick = onDownload,
            enabled = downloadEnabled,
        ),
    )

    if (hasArtists) {
        actions.add(
            ContextMenuAction(
                label = strings.get("view_artist"),
                icon = IconAssets.artist(),
                onClick = onOpenArtist,
            ),
        )
    }

    actions.add(
        ContextMenuAction(
            label = strings.get("share"),
            icon = IconAssets.share(),
            onClick = onShare,
        ),
    )

    return actions
}
