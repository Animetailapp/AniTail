package com.anitail.desktop.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.loadBitmapResource
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window

@Composable
fun DesktopTopBar(
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    pureBlack: Boolean,
    window: Window,
    windowState: WindowState,
    onWindowClose: () -> Unit,
    showUpdateBadge: Boolean = false,
    onRefreshHome: (() -> Unit)? = null,
) {
    val logoBitmap = remember { loadBitmapResource("drawable/ic_anitail.png") }
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val titleColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    val iconColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val actionColors = IconButtonDefaults.iconButtonColors(contentColor = iconColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(surfaceColor)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDragArea(
            window = window,
            windowState = windowState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TitleContent(logoBitmap = logoBitmap, titleColor = titleColor)
        }
        ActionButtons(
            onRefreshHome = onRefreshHome,
            onHistory = onHistory,
            onStats = onStats,
            onSearch = onSearch,
            onSettings = onSettings,
            showUpdateBadge = showUpdateBadge,
            colors = actionColors,
        )
        Spacer(modifier = Modifier.width(8.dp))
        WindowControls(
            isMaximized = isMaximized,
            windowState = windowState,
            onWindowClose = onWindowClose,
            colors = actionColors,
        )
    }
}

@Composable
private fun TitleContent(
    logoBitmap: ImageBitmap?,
    titleColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = "AniTail",
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AniTail",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = titleColor,
        )
    }
}

@Composable
private fun ActionButtons(
    onRefreshHome: (() -> Unit)?,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    showUpdateBadge: Boolean,
    colors: IconButtonColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onRefreshHome != null) {
            IconButton(onClick = onRefreshHome, colors = colors) {
                Icon(IconAssets.refresh(), contentDescription = "Actualizar")
            }
        }
        IconButton(onClick = onHistory, colors = colors) {
            Icon(IconAssets.history(), contentDescription = "Historial")
        }
        IconButton(onClick = onStats, colors = colors) {
            Icon(IconAssets.stats(), contentDescription = "Estadisticas")
        }
        IconButton(onClick = onSearch, colors = colors) {
            Icon(IconAssets.search(), contentDescription = "Buscar")
        }
        IconButton(onClick = onSettings, colors = colors) {
            BadgedBox(badge = { if (showUpdateBadge) Badge() }) {
                Icon(IconAssets.settings(), contentDescription = "Ajustes")
            }
        }
    }
}

@Composable
private fun WindowControls(
    isMaximized: Boolean,
    windowState: WindowState,
    onWindowClose: () -> Unit,
    colors: IconButtonColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { windowState.isMinimized = true },
            colors = colors,
        ) {
            Icon(IconAssets.windowMinimize(), contentDescription = "Minimizar")
        }
        IconButton(
            onClick = {
                windowState.placement = if (isMaximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            },
            colors = colors,
        ) {
            val icon = if (isMaximized) IconAssets.windowRestore() else IconAssets.windowMaximize()
            val description = if (isMaximized) "Restaurar" else "Maximizar"
            Icon(icon, contentDescription = description)
        }
        IconButton(onClick = onWindowClose, colors = colors) {
            Icon(IconAssets.close(), contentDescription = "Cerrar")
        }
    }
}

@Composable
private fun WindowDragArea(
    window: Window,
    windowState: WindowState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(windowState.placement) {
            var dragStartPointer: Point? = null
            var dragStartWindow: Point? = null
            detectDragGestures(
                onDragStart = {
                    if (windowState.placement == WindowPlacement.Maximized) {
                        windowState.placement = WindowPlacement.Floating
                    }
                    dragStartPointer = MouseInfo.getPointerInfo()?.location
                    dragStartWindow = window.location
                },
                onDragEnd = {
                    dragStartPointer = null
                    dragStartWindow = null
                },
                onDragCancel = {
                    dragStartPointer = null
                    dragStartWindow = null
                },
                onDrag = { _, _ ->
                    val startPointer = dragStartPointer
                    val startWindow = dragStartWindow
                    val currentPointer = MouseInfo.getPointerInfo()?.location
                    if (startPointer != null && startWindow != null && currentPointer != null) {
                        val dx = currentPointer.x - startPointer.x
                        val dy = currentPointer.y - startPointer.y
                        window.setLocation(
                            startWindow.x + dx,
                            startWindow.y + dy,
                        )
                    }
                },
            )
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}
