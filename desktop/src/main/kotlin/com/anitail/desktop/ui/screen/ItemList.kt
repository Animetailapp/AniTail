package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anitail.shared.model.LibraryItem

/**
 * ItemList - Lista vertical de items de biblioteca con acciones
 */
@Composable
fun ItemList(
    items: List<LibraryItem>,
    primaryAction: String,
    onPrimaryAction: (LibraryItem) -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: ((LibraryItem) -> Unit)? = null,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items) { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = item.artist, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = { onPrimaryAction(item) }) {
                    Text(primaryAction)
                }
                if (secondaryAction != null && onSecondaryAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onSecondaryAction(item) }) {
                        Text(secondaryAction)
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
