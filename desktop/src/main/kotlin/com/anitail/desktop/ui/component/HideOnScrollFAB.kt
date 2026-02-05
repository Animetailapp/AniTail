package com.anitail.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * FAB flotante que se oculta al hacer scroll hacia abajo
 * y aparece al hacer scroll hacia arriba.
 * Similar al HideOnScrollFAB de Android.
 */
@Composable
fun HideOnScrollFAB(
    visible: Boolean,
    lazyListState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Detectar dirección del scroll
    var previousIndex by remember { mutableStateOf(0) }
    var previousOffset by remember { mutableStateOf(0) }
    var isScrollingUp by remember { mutableStateOf(true) }

    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
    val firstVisibleOffset = lazyListState.firstVisibleItemScrollOffset

    // Comparar con valores anteriores para determinar dirección
    val scrollingUp by remember {
        derivedStateOf {
            if (firstVisibleIndex != previousIndex) {
                isScrollingUp = firstVisibleIndex < previousIndex
                previousIndex = firstVisibleIndex
                previousOffset = firstVisibleOffset
            } else if (firstVisibleOffset != previousOffset) {
                isScrollingUp = firstVisibleOffset < previousOffset
                previousOffset = firstVisibleOffset
            }
            isScrollingUp
        }
    }

    // Mostrar FAB cuando: hay contenido Y (scroll hacia arriba O está al inicio)
    val showFab = visible && (scrollingUp || lazyListState.firstVisibleItemIndex == 0)

    AnimatedVisibility(
        visible = showFab,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Reproducir aleatorio",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
