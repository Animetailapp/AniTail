package com.anitail.desktop.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTopBar(
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    pureBlack: Boolean,
    showUpdateBadge: Boolean = false,
    onRefreshHome: (() -> Unit)? = null,
) {
    val logoBitmap = remember { loadBitmapResource("drawable/ic_anitail.png") }
    TopAppBar(
        title = {
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
                )
            }
        },
        actions = {
            if (onRefreshHome != null) {
                IconButton(onClick = onRefreshHome) {
                    Icon(IconAssets.refresh(), contentDescription = "Actualizar")
                }
            }
            IconButton(onClick = onHistory) {
                Icon(IconAssets.history(), contentDescription = "Historial")
            }
            IconButton(onClick = onStats) {
                Icon(IconAssets.stats(), contentDescription = "Estadisticas")
            }
            IconButton(onClick = onSearch) {
                Icon(IconAssets.search(), contentDescription = "Buscar")
            }
            IconButton(onClick = onSettings) {
                BadgedBox(badge = { if (showUpdateBadge) Badge() }) {
                    Icon(IconAssets.settings(), contentDescription = "Ajustes")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

private fun loadBitmapResource(path: String): ImageBitmap? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        ?: return null
    return stream.use { resource ->
        runCatching {
            SkiaImage.makeFromEncoded(resource.readBytes()).asImageBitmap()
        }.getOrNull()
    }
}
