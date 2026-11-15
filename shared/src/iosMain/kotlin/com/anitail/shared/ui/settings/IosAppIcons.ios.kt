package com.anitail.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberIosAppIconActions(
    @Suppress("UNUSED_PARAMETER") onError: () -> Unit,
): IosAppIconActions = remember { IosAppIconActions(emptyList()) {} }
