package com.anitail.desktop.ui.screen

import androidx.compose.runtime.Composable

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
) {
    LegacyAboutScreen(onBack = onBack)
}
