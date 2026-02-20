package com.anitail.desktop.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val NavigationBarHeight = 80.dp
val MiniPlayerHeight = 64.dp
val MiniPlayerBottomSpacing = 8.dp
val PlayerQueueCollapsedHeight = 64.dp
val PlayerHorizontalPadding = 32.dp
val NavigationBarAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMediumLow)
