package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle

@Composable
internal fun SettingsMainScreen(
    onNavigate: (SettingsDestination) -> Unit,
) {
    val settingsCategories = listOf(
        SettingsCategory(
            title = stringResource("account"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.account(),
            destination = SettingsDestination.ACCOUNT,
        ),
        SettingsCategory(
            title = stringResource("lastfm_settings"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.musicNote(),
            destination = SettingsDestination.LASTFM,
        ),
        SettingsCategory(
            title = stringResource("appearance"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.palette(),
            destination = SettingsDestination.APPEARANCE,
        ),
        SettingsCategory(
            title = stringResource("player_and_audio"),
            subtitle = stringResource("category_player"),
            icon = IconAssets.play(),
            destination = SettingsDestination.PLAYER,
        ),
        SettingsCategory(
            title = stringResource("content"),
            subtitle = stringResource("category_content"),
            icon = IconAssets.language(),
            destination = SettingsDestination.CONTENT,
        ),
        SettingsCategory(
            title = stringResource("privacy"),
            subtitle = stringResource("category_content"),
            icon = IconAssets.security(),
            destination = SettingsDestination.PRIVACY,
        ),
        SettingsCategory(
            title = stringResource("storage"),
            subtitle = stringResource("category_system"),
            icon = IconAssets.storage(),
            destination = SettingsDestination.STORAGE,
        ),
        SettingsCategory(
            title = stringResource("about"),
            subtitle = stringResource("category_system"),
            icon = IconAssets.info(),
            destination = SettingsDestination.ABOUT,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            NavigationTitle(title = stringResource("settings"))
        }

        items(settingsCategories) { category ->
            SettingsCategoryItem(
                category = category,
                onClick = { onNavigate(category.destination) },
            )
        }
    }
}
