package com.anitail.desktop.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    showMoreText: String = "Show more",
    showLessText: String = "Show less",
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    toggleColor: Color = MaterialTheme.colorScheme.primary,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(initiallyExpanded) }
    var hasOverflow by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = text,
            style = style,
            color = bodyColor,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
        )
        if ((hasOverflow || expanded) && showMoreText.isNotBlank() && showLessText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (expanded) showLessText else showMoreText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = toggleColor,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

