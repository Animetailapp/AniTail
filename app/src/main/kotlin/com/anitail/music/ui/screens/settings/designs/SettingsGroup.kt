package com.anitail.music.ui.screens.settings.designs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun shapeManager(
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isBoth: Boolean = false,
    radius: Int = 24
): Shape {
    return when {
        isBoth -> RoundedCornerShape(radius.dp)
        isFirst -> RoundedCornerShape(topStart = radius.dp, topEnd = radius.dp)
        isLast -> RoundedCornerShape(bottomStart = radius.dp, bottomEnd = radius.dp)
        else -> RoundedCornerShape(0.dp)
    }
}