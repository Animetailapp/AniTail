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
import com.anitail.shared.model.LibraryItem

@Composable
fun StatsScreen(
    items: List<LibraryItem>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NavigationTitle(title = "Estadisticas")
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Canciones en biblioteca", style = MaterialTheme.typography.titleMedium)
                Text(items.size.toString(), style = MaterialTheme.typography.headlineMedium)
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
                Text("Reproducciones recientes", style = MaterialTheme.typography.titleMedium)
                Text(items.take(5).size.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(
            text = "Mas estadisticas llegaran pronto en Desktop.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
