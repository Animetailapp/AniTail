package com.anitail.desktop.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.anitail.desktop.ui.IconAssets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTopBar(
    title: String,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
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
                Icon(IconAssets.settings(), contentDescription = "Ajustes")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
