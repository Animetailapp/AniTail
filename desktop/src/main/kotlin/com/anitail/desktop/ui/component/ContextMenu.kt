package com.anitail.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import com.anitail.shared.model.LibraryItem

/**
 * Representa una acción del menú contextual.
 */
data class ContextMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Menú contextual para items de la biblioteca.
 * Se muestra al hacer click derecho o al presionar el botón de menú.
 */
@Composable
fun ItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    item: LibraryItem,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToLibrary: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    offset: DpOffset = DpOffset.Zero,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Header con info del item
        ItemContextMenuHeader(item = item)

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Opciones
        ContextMenuItem(
            label = "Reproducir",
            icon = IconAssets.play(),
            onClick = {
                onPlay()
                onDismiss()
            },
        )

        ContextMenuItem(
            label = "Reproducir siguiente",
            icon = IconAssets.queueMusic(),
            onClick = {
                onPlayNext()
                onDismiss()
            },
        )

        ContextMenuItem(
            label = "Agregar a la cola",
            icon = IconAssets.playlistAdd(),
            onClick = {
                onAddToQueue()
                onDismiss()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ContextMenuItem(
            label = "Agregar a biblioteca",
            icon = IconAssets.add(),
            onClick = {
                onAddToLibrary()
                onDismiss()
            },
        )

        if (onToggleFavorite != null) {
            ContextMenuItem(
                label = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                icon = if (isFavorite) IconAssets.favorite() else IconAssets.favoriteBorder(),
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ContextMenuItem(
            label = "Compartir",
            icon = IconAssets.share(),
            onClick = {
                // Copy URL to clipboard
                val url = item.playbackUrl
                java.awt.Toolkit.getDefaultToolkit()
                    .systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(url), null)
                onDismiss()
            },
        )
    }
}

@Composable
private fun ItemContextMenuHeader(item: LibraryItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
