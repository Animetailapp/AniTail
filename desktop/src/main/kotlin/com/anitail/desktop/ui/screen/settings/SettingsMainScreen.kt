package com.anitail.desktop.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.AvatarSourcePreference
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.loadBitmapResource
import java.net.InetAddress
import java.time.LocalTime

private data class DesktopSettingItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val section: String,
    val icon: ImageVector,
    val iconColor: Color,
    val destination: SettingsDestination,
)

@Composable
internal fun SettingsMainScreen(
    onNavigate: (SettingsDestination) -> Unit,
    preferredAvatarSource: AvatarSourcePreference,
    googleAccountName: String?,
    googleAvatarUrl: String?,
    discordUsername: String?,
    discordAvatarUrl: String?,
    hasUpdate: Boolean,
) {
    var query by remember { mutableStateOf("") }
    val currentHour = remember { LocalTime.now().hour }

    val greetingText = when (currentHour) {
        in 6..11 -> stringResource("good_morning")
        in 12..18 -> stringResource("good_afternoon")
        else -> stringResource("good_evening")
    }

    val greetingImagePath = remember {
        when (currentHour) {
            in 6..11 -> "drawable/ic_user_device_day.png"
            in 12..18 -> "drawable/ic_user_device_afternoon.png"
            else -> "drawable/ic_user_device_night.png"
        }
    }

    val greetingImage = remember(greetingImagePath) { loadBitmapResource(greetingImagePath) }
    val deviceName = remember { resolveDeviceName() }
    val displayName = remember(
        preferredAvatarSource,
        googleAccountName,
        googleAvatarUrl,
        discordUsername,
        discordAvatarUrl,
        deviceName,
    ) {
        val preferredName = when (preferredAvatarSource) {
            AvatarSourcePreference.YOUTUBE -> {
                if (googleAvatarUrl.isNullOrBlank()) "" else googleAccountName?.trim().orEmpty()
            }
            AvatarSourcePreference.DISCORD -> {
                if (discordAvatarUrl.isNullOrBlank()) "" else discordUsername?.trim().orEmpty()
            }
        }
        preferredName.ifBlank { deviceName }
    }

    val sectionInterface = stringResource("category_interface")
    val sectionContent = stringResource("category_content")
    val sectionPlayer = stringResource("category_player")
    val sectionSystem = stringResource("category_system")

    val appearanceText = stringResource("appearance")
    val accountText = stringResource("account")
    val lastFmText = stringResource("lastfm_settings")
    val contentText = stringResource("content")
    val privacyText = stringResource("privacy")
    val playerText = stringResource("player_and_audio")
    val storageText = stringResource("storage")
    val backupRestoreText = stringResource("backup_restore")
    val updateText = stringResource("update_settings")
    val newVersionAvailableText = stringResource("new_version_available")
    val aboutText = stringResource("about")

    val allItems = remember(
        sectionInterface,
        sectionContent,
        sectionPlayer,
        sectionSystem,
        appearanceText,
        accountText,
        lastFmText,
        contentText,
        privacyText,
        playerText,
        storageText,
        backupRestoreText,
        updateText,
        newVersionAvailableText,
        aboutText,
        hasUpdate,
    ) {
        listOf(
            DesktopSettingItem(
                id = "appearance",
                title = appearanceText,
                section = sectionInterface,
                icon = IconAssets.palette(),
                iconColor = Color(0xFF5C6BC0),
                destination = SettingsDestination.APPEARANCE,
            ),
            DesktopSettingItem(
                id = "account",
                title = accountText,
                section = sectionInterface,
                icon = IconAssets.account(),
                iconColor = Color(0xFFAB47BC),
                destination = SettingsDestination.ACCOUNT,
            ),
            DesktopSettingItem(
                id = "lastfm",
                title = lastFmText,
                section = sectionInterface,
                icon = IconAssets.musicNote(),
                iconColor = Color(0xFFEF5350),
                destination = SettingsDestination.LASTFM,
            ),
            DesktopSettingItem(
                id = "content",
                title = contentText,
                section = sectionContent,
                icon = IconAssets.language(),
                iconColor = Color(0xFF26A69A),
                destination = SettingsDestination.CONTENT,
            ),
            DesktopSettingItem(
                id = "privacy",
                title = privacyText,
                section = sectionContent,
                icon = IconAssets.security(),
                iconColor = Color(0xFF78909C),
                destination = SettingsDestination.PRIVACY,
            ),
            DesktopSettingItem(
                id = "player",
                title = playerText,
                section = sectionPlayer,
                icon = IconAssets.play(),
                iconColor = Color(0xFFFFA726),
                destination = SettingsDestination.PLAYER,
            ),
            DesktopSettingItem(
                id = "storage",
                title = storageText,
                section = sectionSystem,
                icon = IconAssets.storage(),
                iconColor = Color(0xFF8D6E63),
                destination = SettingsDestination.STORAGE,
            ),
            DesktopSettingItem(
                id = "backup",
                title = backupRestoreText,
                section = sectionSystem,
                icon = IconAssets.windowRestore(),
                iconColor = Color(0xFF5C6BC0),
                destination = SettingsDestination.BACKUP,
            ),
            DesktopSettingItem(
                id = "update",
                title = updateText,
                subtitle = if (hasUpdate) newVersionAvailableText else null,
                section = sectionSystem,
                icon = IconAssets.update(),
                iconColor = if (hasUpdate) Color(0xFF66BB6A) else Color(0xFFBDBDBD),
                destination = SettingsDestination.UPDATE,
            ),
            DesktopSettingItem(
                id = "about",
                title = aboutText,
                section = sectionSystem,
                icon = IconAssets.info(),
                iconColor = Color(0xFF7E57C2),
                destination = SettingsDestination.ABOUT,
            ),
        )
    }

    val filteredItems = remember(query, allItems) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            allItems
        } else {
            allItems.filter { item ->
                item.title.contains(needle, ignoreCase = true) ||
                    item.section.contains(needle, ignoreCase = true) ||
                    item.id.contains(needle, ignoreCase = true) ||
                    item.subtitle.orEmpty().contains(needle, ignoreCase = true)
            }
        }
    }

    val sectionOrder = remember(sectionInterface, sectionContent, sectionPlayer, sectionSystem) {
        listOf(sectionInterface, sectionContent, sectionPlayer, sectionSystem)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                text = stringResource("settings"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        item {
            if (query.isBlank()) {
                GreetingCard(
                    greetingText = greetingText,
                    displayName = displayName,
                    imageBitmap = greetingImage,
                )
            }
        }

        item {
            if (query.isBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            SettingsSearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        if (filteredItems.isEmpty()) {
            item {
                Text(
                    text = stringResource("no_settings_found"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                )
            }
        } else {
            sectionOrder.forEach { section ->
                val itemsInSection = filteredItems.filter { it.section == section }
                if (itemsInSection.isNotEmpty()) {
                    item {
                        MainSettingsSectionTitle(title = section.uppercase())
                    }
                    item {
                        SettingsSectionCard {
                            itemsInSection.forEachIndexed { index, entry ->
                                SettingsSectionItem(
                                    item = entry,
                                    onClick = { onNavigate(entry.destination) },
                                )
                                if (index < itemsInSection.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 74.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

private fun resolveDeviceName(): String {
    val envComputerName = System.getenv("COMPUTERNAME").orEmpty().trim()
    if (envComputerName.isNotBlank()) return envComputerName

    val envHostName = System.getenv("HOSTNAME").orEmpty().trim()
    if (envHostName.isNotBlank()) return envHostName

    val networkHost = runCatching { InetAddress.getLocalHost().hostName.orEmpty().trim() }.getOrDefault("")
    if (networkHost.isNotBlank()) return networkHost

    val osName = System.getProperty("os.name").orEmpty().trim()
    return if (osName.isNotBlank()) osName else "AniTail Device"
}

@Composable
private fun GreetingCard(
    greetingText: String,
    displayName: String,
    imageBitmap: ImageBitmap?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (displayName.isNotBlank()) displayName else "AniTail",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (imageBitmap != null) {
                Image(
                    painter = BitmapPainter(imageBitmap),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .size(122.dp),
                )
            } else {
                Icon(
                    imageVector = IconAssets.icAni(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource("search_settings_placeholder"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = IconAssets.search(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = IconAssets.close(),
                        contentDescription = null,
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun MainSettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSectionItem(
    item: DesktopSettingItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.iconColor.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Icon(
            imageVector = IconAssets.chevronRight(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}
