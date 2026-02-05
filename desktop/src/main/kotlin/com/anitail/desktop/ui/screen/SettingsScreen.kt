package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.component.NavigationTitle

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NavigationTitle(title = "Ajustes")
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Text("Predeterminado (Desktop)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reproduccion", style = MaterialTheme.typography.titleMedium)
                Text("WebView YouTube Music", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "Mas opciones se agregaran a medida que portemos ajustes Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
