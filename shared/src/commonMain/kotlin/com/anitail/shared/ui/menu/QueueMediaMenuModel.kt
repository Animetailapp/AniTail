/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.shared.ui.menu

import androidx.compose.runtime.Composable
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.details
import com.anitail.shared.generated.resources.details_desc
import com.anitail.shared.generated.resources.listen_together
import com.anitail.shared.generated.resources.refetch
import com.anitail.shared.generated.resources.refetch_desc
import com.anitail.shared.generated.resources.remove_from_queue
import com.anitail.shared.model.ArtistTiny
import com.anitail.shared.settings.Settings
import com.anitail.shared.ui.components.PlayerMenuAction
import com.anitail.shared.ui.icons.AnitailIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun queueMediaMenuModel(
    songTitle: String,
    artistName: String,
    artists: List<ArtistTiny>,
    albumName: String?,
    thumbnailUrl: String?,
    isDisliked: Boolean,
    onDismiss: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: ((String) -> Unit)?,
    onShare: (() -> Unit)?,
    onAltShare: (() -> Unit)?,
    onRefetch: (() -> Unit)?,
    onShowInfo: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?,
    onNavigateToListenTogether: (() -> Unit)?,
    onDislike: (() -> Unit)?,
    deleteUploadAction: PlayerMenuAction? = null,
): MediaMenuModel =
    MediaMenuModel(
        title = songTitle,
        subtitle = artistName,
        thumbnail = thumbnailUrl,
        primaryActions =
            songPrimaryActions(
                onDismiss = onDismiss,
                onStartRadio = onStartRadio,
                onAddToPlaylist =
                    onAddToPlaylist?.let {
                        {
                            it()
                            onDismiss()
                        }
                    },
                onShare = onShare,
                onAltShare = onAltShare,
            ),
        actionGroups =
            listOf(
                listOfNotNull(
                    dislikeMenuAction(isDisliked, onDismiss, onDislike),
                    onRemoveFromQueue?.let {
                        action(AnitailIcons.Outlined.Delete, stringResource(Res.string.remove_from_queue), onDismiss, it)
                    },
                ) + queueActions(onDismiss, onPlayNext, onAddToQueue),
                listOfNotNull(downloadAction(MediaDownloadState.NOT_DOWNLOADED, 0, false, onDismiss, onDownload), deleteUploadAction),
                listOfNotNull(
                    onNavigateToListenTogether?.takeIf { Settings.shared.listenTogetherEnabled }?.let {
                        action(AnitailIcons.Outlined.Group, stringResource(Res.string.listen_together), onDismiss, it)
                    },
                ),
                listOfNotNull(
                    artistNavigationMenuAction(artists, onGoToArtist, onDismiss),
                    albumNavigationMenuAction(onDismiss, onGoToAlbum.takeUnless { albumName.isNullOrBlank() }, albumName),
                ),
                listOfNotNull(
                    onRefetch?.let {
                        action(
                            AnitailIcons.Outlined.Refresh,
                            stringResource(Res.string.refetch),
                            onDismiss,
                            it,
                            stringResource(Res.string.refetch_desc),
                        )
                    },
                    onShowInfo?.let {
                        PlayerMenuAction(
                            AnitailIcons.Outlined.Info,
                            stringResource(Res.string.details),
                            stringResource(Res.string.details_desc),
                            onClick = it,
                        )
                    },
                ),
            ),
    )
