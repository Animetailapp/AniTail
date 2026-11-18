@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

object DesktopSideSheetDefaults {
    val MinWidth: Dp = 320.dp
    val DefaultWidth: Dp = 360.dp
    val ExpandedWidth: Dp = 440.dp
}

/**
 * Dockable Material 3 Expressive side sheet wrapping [LyricsPanel], wired directly to
 * [PlayerState] and [DesktopDatabase].
 */
@Composable
fun LyricsSideSheet(
    visible: Boolean,
    playerState: PlayerState,
    database: DesktopDatabase,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DesktopSideSheetDefaults.DefaultWidth,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val item = playerState.currentItem
    val coroutineScope = rememberCoroutineScope()
    val songEntity by remember(item?.id, database) {
        item?.id?.let { database.song(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val songRomanizeLyrics = songEntity?.romanizeLyrics ?: true

    val subtitle = remember(item?.title, item?.artist) {
        val title = item?.title.orEmpty()
        val artist = item?.artist.orEmpty()
        when {
            title.isNotBlank() && artist.isNotBlank() -> "$title • $artist"
            title.isNotBlank() -> title
            else -> null
        }
    }

    DockableSideSheet(
        visible = visible,
        title = stringResource("lyrics"),
        subtitle = subtitle,
        icon = IconAssets.lyrics(),
        onClose = onClose,
        modifier = modifier,
        baseWidth = width,
        pinned = pinned,
        onTogglePin = onTogglePin,
    ) {
        LyricsPanel(
            title = item?.title.orEmpty(),
            artist = item?.artist.orEmpty(),
            videoId = item?.id,
            artworkUrl = item?.artworkUrl,
            durationSec = ((item?.durationMs ?: playerState.duration) / 1000L).toInt().coerceAtLeast(0),
            currentPositionMs = playerState.position,
            isPlaying = playerState.isPlaying,
            songRomanizeLyrics = songRomanizeLyrics,
            onSetSongRomanizeLyrics = { enabled ->
                val current = item ?: return@LyricsPanel
                coroutineScope.launch {
                    val existing = songEntity
                    if (existing == null) {
                        database.insertSong(current.toSongEntity().copy(romanizeLyrics = enabled))
                    } else {
                        database.updateSong(existing.copy(romanizeLyrics = enabled))
                    }
                }
            },
            onSeek = { playerState.seekTo(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Dockable Material 3 Expressive side sheet wrapping [LyricsPanel] with explicit track state.
 */
@Composable
fun LyricsSideSheet(
    visible: Boolean,
    title: String,
    artist: String,
    videoId: String? = null,
    artworkUrl: String? = null,
    durationSec: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    songRomanizeLyrics: Boolean,
    onSetSongRomanizeLyrics: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DesktopSideSheetDefaults.DefaultWidth,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val subtitle = remember(title, artist) {
        when {
            title.isNotBlank() && artist.isNotBlank() -> "$title • $artist"
            title.isNotBlank() -> title
            else -> null
        }
    }

    DockableSideSheet(
        visible = visible,
        title = stringResource("lyrics"),
        subtitle = subtitle,
        icon = IconAssets.lyrics(),
        onClose = onClose,
        modifier = modifier,
        baseWidth = width,
        pinned = pinned,
        onTogglePin = onTogglePin,
    ) {
        LyricsPanel(
            title = title,
            artist = artist,
            videoId = videoId,
            artworkUrl = artworkUrl,
            durationSec = durationSec,
            currentPositionMs = currentPositionMs,
            isPlaying = isPlaying,
            songRomanizeLyrics = songRomanizeLyrics,
            onSetSongRomanizeLyrics = onSetSongRomanizeLyrics,
            onSeek = onSeek,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Dockable Material 3 Expressive side sheet wrapping [QueuePanel].
 */
@Composable
fun QueueSideSheet(
    visible: Boolean,
    playerState: PlayerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit = { index ->
        playerState.queue.getOrNull(index)?.let { item ->
            playerState.play(item)
        }
    },
    onRemoveItem: (Int) -> Unit = { index ->
        playerState.removeFromQueue(index)
    },
    width: Dp = DesktopSideSheetDefaults.DefaultWidth,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val queueSize = playerState.queue.size
    val subtitle = pluralStringResource("n_song", queueSize, queueSize)

    DockableSideSheet(
        visible = visible,
        title = stringResource("queue"),
        subtitle = subtitle,
        icon = IconAssets.queueMusic(),
        onClose = onClose,
        modifier = modifier,
        baseWidth = width,
        pinned = pinned,
        onTogglePin = onTogglePin,
    ) {
        QueuePanel(
            playerState = playerState,
            onItemClick = onItemClick,
            onRemoveItem = onRemoveItem,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DockableSideSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    baseWidth: Dp = DesktopSideSheetDefaults.DefaultWidth,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val motion = rememberDesktopMotion()
    var isExpandedWidth by remember { mutableStateOf(false) }
    val targetWidth = if (isExpandedWidth) {
        maxOf(DesktopSideSheetDefaults.ExpandedWidth, baseWidth + 80.dp)
    } else {
        baseWidth.coerceAtLeast(DesktopSideSheetDefaults.MinWidth)
    }
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = DesktopPlayerMotion.panelWidthSpec,
        label = "sideSheetWidth",
    )

    val colorScheme = MaterialTheme.colorScheme
    val isSpanish = LocalStrings.current.locale.language.equals("es", ignoreCase = true)
    val closeLabel = stringResource("close")
    val pinLabel = if (pinned) {
        if (isSpanish) "Desfijar panel" else "Unpin panel"
    } else {
        if (isSpanish) "Fijar panel" else "Pin panel"
    }
    val widthLabel = if (isExpandedWidth) {
        if (isSpanish) "Ancho compacto" else "Compact width"
    } else {
        if (isSpanish) "Expandir ancho" else "Expand width"
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxHeight(),
        enter = motion.sideSheetEnter,
        exit = motion.sideSheetExit,
    ) {
        Surface(
            modifier = Modifier
                .width(animatedWidth)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surfaceContainerLow,
            tonalElevation = if (pinned) 3.dp else 1.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (pinned) {
                    colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    colorScheme.outlineVariant.copy(alpha = 0.28f)
                },
            ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (pinned) {
                            colorScheme.primaryContainer
                        } else {
                            colorScheme.secondaryContainer
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (pinned) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (onTogglePin != null) {
                            IconButton(
                                onClick = onTogglePin,
                                modifier = Modifier.size(34.dp),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = pushPinIcon(filled = pinned),
                                    contentDescription = pinLabel,
                                    tint = if (pinned) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = { isExpandedWidth = !isExpandedWidth },
                            modifier = Modifier.size(34.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = if (isExpandedWidth) {
                                    IconAssets.chevronRight()
                                } else {
                                    IconAssets.arrowBack()
                                },
                                contentDescription = widthLabel,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(34.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = IconAssets.close(),
                                contentDescription = closeLabel,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.22f),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    content()
                }
            }
        }
    }
}

private val PushPinFilledIcon by lazy { buildPushPinIcon(filled = true) }
private val PushPinOutlinedIcon by lazy { buildPushPinIcon(filled = false) }

private fun pushPinIcon(filled: Boolean): ImageVector =
    if (filled) PushPinFilledIcon else PushPinOutlinedIcon

private fun buildPushPinIcon(filled: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (filled) "PushPinFilled" else "PushPinOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            if (filled) {
                moveTo(16f, 12f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(12f)
                lineTo(6f, 14f)
                verticalLineTo(16f)
                horizontalLineTo(11.2f)
                verticalLineTo(22f)
                horizontalLineTo(12.8f)
                verticalLineTo(16f)
                horizontalLineTo(18f)
                verticalLineTo(14f)
                lineTo(16f, 12f)
                close()
            } else {
                moveTo(14f, 4f)
                verticalLineTo(11f)
                curveTo(14f, 12.12f, 14.37f, 13.16f, 15f, 14f)
                horizontalLineTo(9f)
                curveTo(9.65f, 13.14f, 10f, 12.1f, 10f, 11f)
                verticalLineTo(4f)
                horizontalLineTo(14f)
                moveTo(17f, 2f)
                horizontalLineTo(7f)
                curveTo(6.45f, 2f, 6f, 2.45f, 6f, 3f)
                curveTo(6f, 3.55f, 6.45f, 4f, 7f, 4f)
                horizontalLineTo(8f)
                verticalLineTo(11f)
                curveTo(8f, 12.66f, 6.66f, 14f, 5f, 14f)
                verticalLineTo(16f)
                horizontalLineTo(10.97f)
                verticalLineTo(23f)
                lineTo(11.97f, 24f)
                lineTo(12.97f, 23f)
                verticalLineTo(16f)
                horizontalLineTo(19f)
                verticalLineTo(14f)
                curveTo(17.34f, 14f, 16f, 12.66f, 16f, 11f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                curveTo(17.55f, 4f, 18f, 3.55f, 18f, 3f)
                curveTo(18f, 2.45f, 17.55f, 2f, 17f, 2f)
                close()
            }
        }
    }.build()
