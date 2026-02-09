package com.anitail.desktop.ui.screen

import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ContextMenuAction

fun buildExploreAlbumMenuActions(
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
            label = "Iniciar radio",
            icon = IconAssets.radio(),
            onClick = onStartRadio,
        ),
        ContextMenuAction(
            label = "Reproducir siguiente",
            icon = IconAssets.queueMusic(),
            onClick = onPlayNext,
        ),
        ContextMenuAction(
            label = "Agregar a la cola",
            icon = IconAssets.playlistAdd(),
            onClick = onAddToQueue,
        ),
        ContextMenuAction(
            label = "Agregar a playlist",
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
                label = "Ir al artista",
                icon = IconAssets.artist(),
                onClick = onOpenArtist,
            ),
        )
    }

    actions.add(
        ContextMenuAction(
            label = "Compartir",
            icon = IconAssets.share(),
            onClick = onShare,
        ),
    )

    return actions
}
