package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.shared.model.LibraryItem

@Composable
fun HistoryScreen(
    items: List<LibraryItem>,
    onPlay: (LibraryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NavigationTitle(title = "Historial")
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text(
                text = "No hay reproducciones recientes.",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            ItemList(items = items, primaryAction = "Reproducir", onPrimaryAction = onPlay)
        }
    }
}
