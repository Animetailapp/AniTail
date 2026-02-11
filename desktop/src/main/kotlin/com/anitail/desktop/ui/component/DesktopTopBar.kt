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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.loadBitmapResource
import com.anitail.desktop.ui.component.RemoteImage
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.roundToInt

@Composable
fun DesktopTopBar(
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    accountDisplayName: String?,
    accountAvatarUrl: String?,
    pureBlack: Boolean,
    isMaximized: Boolean,
    window: Window,
    windowState: WindowState,
    onToggleMaximize: () -> Unit,
    onRestoreFromSnap: () -> Unit,
    onSnapFromDragEnd: (Point) -> Unit,
    onWindowClose: () -> Unit,
    showUpdateBadge: Boolean = false,
    onRefreshHome: (() -> Unit)? = null,
) {
    val logoBitmap = remember { loadBitmapResource("drawable/ic_anitail.png") }
    var rightSideWidthPx by remember { mutableStateOf(0) }
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val titleColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    val iconColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
            isMaximized = isMaximized,
            onRestoreFromSnap = onRestoreFromSnap,
            onSnapFromDragEnd = onSnapFromDragEnd,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TitleContent(
                logoBitmap = logoBitmap,
                titleColor = titleColor,
                accountDisplayName = accountDisplayName,
                accountAvatarUrl = accountAvatarUrl,
                pureBlack = pureBlack,
                chipOffsetXPx = rightSideWidthPx / 2,
            )
        }
        Row(
            modifier = Modifier.onGloballyPositioned { rightSideWidthPx = it.size.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                onToggleMaximize = onToggleMaximize,
                onWindowClose = onWindowClose,
                colors = actionColors,
            )
        }
    }
}

@Composable
private fun TitleContent(
    logoBitmap: ImageBitmap?,
    titleColor: Color,
    accountDisplayName: String?,
    accountAvatarUrl: String?,
    pureBlack: Boolean,
    chipOffsetXPx: Int,
) {
    val appName = stringResource("app_name")
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = appName,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
        }

        if (!accountDisplayName.isNullOrBlank() || !accountAvatarUrl.isNullOrBlank()) {
            AccountChip(
                accountDisplayName = accountDisplayName,
                accountAvatarUrl = accountAvatarUrl,
                pureBlack = pureBlack,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(chipOffsetXPx, 0) },
            )
        }
    }
}

@Composable
private fun AccountChip(
    accountDisplayName: String?,
    accountAvatarUrl: String?,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val chipBackground = if (pureBlack) {
        Color(0x26FFFFFF)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val nameColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .height(34.dp)
            .widthIn(max = 340.dp),
        shape = RoundedCornerShape(18.dp),
        color = chipBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            if (!accountAvatarUrl.isNullOrBlank()) {
                RemoteImage(
                    url = accountAvatarUrl,
                    contentDescription = accountDisplayName,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                )
            } else {
                Icon(
                    imageVector = IconAssets.person(),
                    contentDescription = accountDisplayName,
                    modifier = Modifier.size(18.dp),
                    tint = nameColor.copy(alpha = 0.85f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = accountDisplayName?.trim().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = nameColor,
            )
        }
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
    val refreshLabel = stringResource("refresh")
    val historyLabel = stringResource("history")
    val statsLabel = stringResource("stats")
    val searchLabel = stringResource("search")
    val settingsLabel = stringResource("settings")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onRefreshHome != null) {
            IconButton(onClick = onRefreshHome, colors = colors) {
                Icon(IconAssets.cached(), contentDescription = refreshLabel)
            }
        }
        IconButton(onClick = onHistory, colors = colors) {
            Icon(IconAssets.history(), contentDescription = historyLabel)
        }
        IconButton(onClick = onStats, colors = colors) {
            Icon(IconAssets.stats(), contentDescription = statsLabel)
        }
        IconButton(onClick = onSearch, colors = colors) {
            Icon(IconAssets.search(), contentDescription = searchLabel)
        }
        IconButton(onClick = onSettings, colors = colors) {
            BadgedBox(badge = { if (showUpdateBadge) Badge() }) {
                Icon(IconAssets.settings(), contentDescription = settingsLabel)
            }
        }
    }
}

@Composable
private fun WindowControls(
    isMaximized: Boolean,
    windowState: WindowState,
    onToggleMaximize: () -> Unit,
    onWindowClose: () -> Unit,
    colors: IconButtonColors,
) {
    val minimizeLabel = stringResource("minimize")
    val restoreLabel = stringResource("restore")
    val maximizeLabel = stringResource("maximize")
    val closeLabel = stringResource("close")
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { windowState.isMinimized = true },
            colors = colors,
        ) {
            Icon(IconAssets.windowMinimize(), contentDescription = minimizeLabel)
        }
        IconButton(
            onClick = onToggleMaximize,
            colors = colors,
        ) {
            val icon = if (isMaximized) IconAssets.windowRestore() else IconAssets.windowMaximize()
            val description = if (isMaximized) restoreLabel else maximizeLabel
            Icon(icon, contentDescription = description)
        }
        IconButton(onClick = onWindowClose, colors = colors) {
            Icon(IconAssets.close(), contentDescription = closeLabel)
        }
    }
}

@Composable
private fun WindowDragArea(
    window: Window,
    windowState: WindowState,
    isMaximized: Boolean,
    onRestoreFromSnap: () -> Unit,
    onSnapFromDragEnd: (Point) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dragModifier = Modifier.pointerInput(windowState.placement, isMaximized) {
        var dragStartPointer: Point? = null
        var dragStartWindow: Point? = null
        var manualPointer: Point? = null
        var manualWindowLocation: Point? = null
        var systemDragPending = false
        var systemDragStartPointer: Point? = null
        var systemDragStartWindow: Point? = null
        var systemDragStartTimeNs = 0L
        detectDragGestures(
            onDragStart = {
                if (isMaximized) {
                    onRestoreFromSnap()
                } else if (windowState.placement == WindowPlacement.Maximized) {
                    windowState.placement = WindowPlacement.Floating
                }
                dragStartPointer = MouseInfo.getPointerInfo()?.location
                dragStartWindow = window.location
                val nativeEnabled = WindowsCaptionHitTest.isEnabled()
                val nativeStarted = nativeEnabled && startNativeWindowDrag(window)
                systemDragPending = nativeStarted
                if (nativeStarted) {
                    systemDragStartPointer = dragStartPointer
                    systemDragStartWindow = dragStartWindow
                    systemDragStartTimeNs = System.nanoTime()
                    manualPointer = null
                    manualWindowLocation = null
                } else {
                    systemDragStartPointer = null
                    systemDragStartWindow = null
                    systemDragStartTimeNs = 0L
                    manualPointer = dragStartPointer
                    manualWindowLocation = window.location
                }
            },
            onDragEnd = {
                MouseInfo.getPointerInfo()?.location?.let { onSnapFromDragEnd(it) }
                systemDragPending = false
                systemDragStartPointer = null
                systemDragStartWindow = null
                systemDragStartTimeNs = 0L
                dragStartPointer = null
                dragStartWindow = null
                manualPointer = null
                manualWindowLocation = null
            },
            onDragCancel = {
                systemDragPending = false
                systemDragStartPointer = null
                systemDragStartWindow = null
                systemDragStartTimeNs = 0L
                dragStartPointer = null
                dragStartWindow = null
                manualPointer = null
                manualWindowLocation = null
            },
            onDrag = { _, _ ->
                if (systemDragPending) {
                    val startPointer = systemDragStartPointer
                    val startWindow = systemDragStartWindow
                    val currentPointer = MouseInfo.getPointerInfo()?.location
                    if (startPointer != null && startWindow != null && currentPointer != null) {
                        val pointerMoved =
                            kotlin.math.abs(currentPointer.x - startPointer.x) >= NATIVE_DRAG_SWITCH_PX ||
                                kotlin.math.abs(currentPointer.y - startPointer.y) >= NATIVE_DRAG_SWITCH_PX
                        val windowMoved = window.location != startWindow
                        if (windowMoved) {
                            return@detectDragGestures
                        }
                        val elapsedMs = (System.nanoTime() - systemDragStartTimeNs) / 1_000_000
                        if (!pointerMoved || elapsedMs < NATIVE_DRAG_GRACE_MS) {
                            return@detectDragGestures
                        }
                        systemDragPending = false
                        dragStartPointer = currentPointer
                        dragStartWindow = window.location
                        manualPointer = currentPointer
                        manualWindowLocation = window.location
                    } else {
                        systemDragPending = false
                    }
                }
                if (systemDragPending) {
                    return@detectDragGestures
                }
                val currentPointer = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                val lastPointer = manualPointer
                val lastWindow = manualWindowLocation ?: window.location
                if (lastPointer == null) {
                    manualPointer = currentPointer
                    manualWindowLocation = window.location
                    return@detectDragGestures
                }
                val dx = currentPointer.x - lastPointer.x
                val dy = currentPointer.y - lastPointer.y
                if (dx == 0 && dy == 0) return@detectDragGestures
                val nextWindow = Point(lastWindow.x + dx, lastWindow.y + dy)
                manualPointer = currentPointer
                manualWindowLocation = nextWindow
                window.setLocation(nextWindow)
            },
        )
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val rect = coords.boundsInWindow()
                val transform = window.graphicsConfiguration?.defaultTransform
                val scaleX = transform?.scaleX ?: 1.0
                val scaleY = transform?.scaleY ?: 1.0
                WindowsCaptionHitTest.updateDragRegion(
                    Rectangle(
                        (rect.left * scaleX).roundToInt(),
                        (rect.top * scaleY).roundToInt(),
                        (rect.width * scaleX).roundToInt(),
                        (rect.height * scaleY).roundToInt(),
                    ),
                )
                WindowsCaptionHitTest.ensureInstalled(window)
            }
            .then(dragModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true
}

private fun startNativeWindowDrag(window: Window): Boolean {
    if (!isWindows()) return false
    if (!window.isDisplayable) return false
    val pointer = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return false
    val hwnd = HWND(pointer)
    runCatching { User32Ex.INSTANCE.ReleaseCapture() }
    val result = runCatching {
        User32Ex.INSTANCE.PostMessage(hwnd, WM_NCLBUTTONDOWN, WPARAM(HTCAPTION.toLong()), LPARAM(0))
    }.getOrNull()
    return result == true
}

private const val WM_NCHITTEST = 0x0084
private const val WM_NCCALCSIZE = 0x0083
private const val WM_NCLBUTTONDOWN = 0x00A1
private const val HTCAPTION = 0x0002
private const val NATIVE_DRAG_GRACE_MS = 0L
private const val NATIVE_DRAG_SWITCH_PX = 1

private object WindowsCaptionHitTest {
    @Volatile
    private var dragRegion: Rectangle? = null
    @Volatile
    private var enabled: Boolean = false
    @Volatile
    private var styleApplied: Boolean = false
    private var previousWndProc: Pointer? = null
    private var callback: WinUser.WindowProc? = null

    fun updateDragRegion(region: Rectangle) {
        dragRegion = region
    }

    fun ensureInstalled(window: Window) {
        if (!isWindows()) return
        if (!window.isDisplayable) return
        val pointer = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return
        val hwnd = HWND(pointer)
        extendFrameIntoClient(hwnd)
        applySnapStyles(hwnd)
        if (enabled) return
        val proc = WinUser.WindowProc { hWnd, msg, wParam, lParam ->
            handleHitTest(hWnd, msg, wParam, lParam)
        }
        val procPointer = CallbackReference.getFunctionPointer(proc)
        Native.setLastError(0)
        val prev = runCatching {
            User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, procPointer)
        }.getOrNull()
        val lastError = Native.getLastError()
        if (prev == null || (Pointer.nativeValue(prev) == 0L && lastError != 0)) {
            return
        }
        previousWndProc = prev
        callback = proc
        enabled = true
    }

    private fun applySnapStyles(hwnd: HWND) {
        if (styleApplied) return
        val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        val desired = style or WS_THICKFRAME or WS_MAXIMIZEBOX or WS_MINIMIZEBOX or WS_SYSMENU
        if (desired != style) {
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, desired)
            User32.INSTANCE.SetWindowPos(
                hwnd,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED,
            )
        }
        extendFrameIntoClient(hwnd)
        styleApplied = true
    }

    private fun extendFrameIntoClient(hwnd: HWND) {
        runCatching {
            val margins = MARGINS(-1, -1, -1, -1)
            Dwmapi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins)
        }
    }

    fun isEnabled(): Boolean = enabled

    private fun handleHitTest(
        hWnd: HWND,
        msg: Int,
        wParam: WPARAM,
        lParam: LPARAM,
    ): LRESULT {
        if (msg == WM_NCCALCSIZE) {
            return LRESULT(0)
        }
        if (msg == WM_NCHITTEST) {
            val region = dragRegion
            if (region != null) {
                val rect = RECT()
                if (User32.INSTANCE.GetWindowRect(hWnd, rect)) {
                    val x = getXFromLParam(lParam) - rect.left
                    val y = getYFromLParam(lParam) - rect.top
                    if (region.contains(x, y)) {
                        return LRESULT(HTCAPTION.toLong())
                    }
                }
            }
        }
        val prev = previousWndProc
        return if (prev != null) {
            User32.INSTANCE.CallWindowProc(prev, hWnd, msg, wParam, lParam)
        } else {
            LRESULT(0)
        }
    }

    private fun getXFromLParam(lParam: LPARAM): Int {
        val value = lParam.toInt()
        return (value and 0xFFFF).toShort().toInt()
    }

    private fun getYFromLParam(lParam: LPARAM): Int {
        val value = lParam.toInt()
        return (value shr 16).toShort().toInt()
    }
}

private const val WS_THICKFRAME = 0x00040000
private const val WS_MAXIMIZEBOX = 0x00010000
private const val WS_MINIMIZEBOX = 0x00020000
private const val WS_SYSMENU = 0x00080000
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_FRAMECHANGED = 0x0020

private interface Dwmapi : StdCallLibrary {
    fun DwmExtendFrameIntoClientArea(hwnd: HWND, margins: MARGINS): Int

    companion object {
        val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java)
    }
}

private interface User32Ex : StdCallLibrary {
    fun ReleaseCapture(): Boolean
    fun PostMessage(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): Boolean

    companion object {
        val INSTANCE: User32Ex = Native.load("user32", User32Ex::class.java)
    }
}

private class MARGINS(
    @JvmField val cxLeftWidth: Int,
    @JvmField val cxRightWidth: Int,
    @JvmField val cyTopHeight: Int,
    @JvmField val cyBottomHeight: Int,
) : Structure() {
    override fun getFieldOrder(): List<String> {
        return listOf("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
    }
}
