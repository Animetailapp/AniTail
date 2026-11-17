package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AccountInfo
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.AvatarSourcePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.loadBitmapResource
import com.anitail.desktop.update.DesktopUpdater
import java.time.LocalTime

private data class SettingsSidebarItem(
    val destination: SettingsDestination,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
)

private data class SettingsSidebarGroup(
    val title: String,
    val items: List<SettingsSidebarItem>,
)

@Composable
fun SettingsDialogOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    database: DesktopDatabase = DesktopDatabase.getInstance(),
    preferences: DesktopPreferences = DesktopPreferences.getInstance(),
    downloadService: DesktopDownloadService,
    authService: DesktopAuthService,
    authCredentials: AuthCredentials?,
    accountInfo: AccountInfo? = null,
    playerState: PlayerState? = null,
    onOpenLogin: () -> Unit,
    onAuthChanged: (AuthCredentials?) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)),
    ) {
        var currentDestination by remember { mutableStateOf(SettingsDestination.APPEARANCE) }
        val searchState = rememberTextFieldState()
        val searchQuery = searchState.text.toString()

        val latestVersionName by preferences.latestVersionName.collectAsState()
        val preferredAvatarSource by preferences.preferredAvatarSource.collectAsState()
        val discordUsername by preferences.discordUsername.collectAsState()
        val discordAvatarUrl by preferences.discordAvatarUrl.collectAsState()
        val currentVersionName = remember { DesktopUpdater.currentVersionName() }
        val hasUpdate = remember(latestVersionName, currentVersionName) {
            latestVersionName.isNotBlank() && DesktopUpdater.isVersionNewer(latestVersionName, currentVersionName)
        }

        val currentHour = remember { LocalTime.now().hour }
        val greetingText = when (currentHour) {
            in 6..11 -> stringResource("good_morning")
            in 12..18 -> stringResource("good_afternoon")
            else -> stringResource("good_evening")
        }
        val greetingImagePath = remember(currentHour) {
            when (currentHour) {
                in 6..11 -> "drawable/ic_user_device_day.png"
                in 12..18 -> "drawable/ic_user_device_afternoon.png"
                else -> "drawable/ic_user_device_night.png"
            }
        }
        val greetingImage = remember(greetingImagePath) { loadBitmapResource(greetingImagePath) }
        val deviceName = remember { resolveDeviceName() }
        val googleAccountName = accountInfo?.name ?: authCredentials?.accountName
        val googleAvatarUrl = accountInfo?.thumbnailUrl ?: authCredentials?.accountImageUrl
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
                    if (discordAvatarUrl.isNullOrBlank()) "" else discordUsername.trim().takeIf { it.isNotEmpty() }?.let { "@$it" }.orEmpty()
                }
            }
            preferredName.ifBlank { deviceName }
        }

        val sectionInterface = stringResource("category_interface")
        val sectionPlayer = stringResource("category_player")
        val sectionPrivacy = stringResource("privacy")
        val sectionSystem = stringResource("category_system")

        val appearanceText = stringResource("appearance")
        val contentText = stringResource("content")
        val accountText = stringResource("account")
        val lastFmText = stringResource("lastfm_settings")
        val discordText = stringResource("discord_integration")
        val playerText = stringResource("player_and_audio")
        val privacyText = stringResource("privacy")
        val storageText = stringResource("storage")
        val backupRestoreText = stringResource("backup_restore")
        val updateText = stringResource("update_settings")
        val newVersionAvailableText = stringResource("new_version_available")
        val aboutText = stringResource("about")

        val groups = remember(
            sectionInterface,
            sectionPlayer,
            sectionPrivacy,
            sectionSystem,
            appearanceText,
            contentText,
            accountText,
            lastFmText,
            discordText,
            playerText,
            privacyText,
            storageText,
            backupRestoreText,
            updateText,
            newVersionAvailableText,
            aboutText,
            hasUpdate,
        ) {
            listOf(
                SettingsSidebarGroup(
                    title = sectionInterface,
                    items = listOf(
                        SettingsSidebarItem(
                            destination = SettingsDestination.APPEARANCE,
                            title = appearanceText,
                            icon = IconAssets.palette(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.CONTENT,
                            title = contentText,
                            icon = IconAssets.language(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.ACCOUNT,
                            title = accountText,
                            icon = IconAssets.account(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.LASTFM,
                            title = lastFmText,
                            icon = IconAssets.musicNote(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.DISCORD,
                            title = discordText,
                            icon = IconAssets.discord(),
                        ),
                    ),
                ),
                SettingsSidebarGroup(
                    title = sectionPlayer,
                    items = listOf(
                        SettingsSidebarItem(
                            destination = SettingsDestination.PLAYER,
                            title = playerText,
                            icon = IconAssets.play(),
                        ),
                    ),
                ),
                SettingsSidebarGroup(
                    title = sectionPrivacy,
                    items = listOf(
                        SettingsSidebarItem(
                            destination = SettingsDestination.PRIVACY,
                            title = privacyText,
                            icon = IconAssets.security(),
                        ),
                    ),
                ),
                SettingsSidebarGroup(
                    title = sectionSystem,
                    items = listOf(
                        SettingsSidebarItem(
                            destination = SettingsDestination.STORAGE,
                            title = storageText,
                            icon = IconAssets.storage(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.BACKUP,
                            title = backupRestoreText,
                            icon = IconAssets.windowRestore(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.UPDATE,
                            title = updateText,
                            subtitle = if (hasUpdate) newVersionAvailableText else null,
                            icon = IconAssets.update(),
                        ),
                        SettingsSidebarItem(
                            destination = SettingsDestination.ABOUT,
                            title = aboutText,
                            icon = IconAssets.info(),
                        ),
                    ),
                ),
            )
        }

        val filteredGroups = remember(groups, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                groups
            } else {
                groups.mapNotNull { group ->
                    val matching = group.items.filter { item ->
                        item.title.contains(q, ignoreCase = true) ||
                            (item.subtitle?.contains(q, ignoreCase = true) == true) ||
                            group.title.contains(q, ignoreCase = true)
                    }
                    if (matching.isEmpty()) null else group.copy(items = matching)
                }
            }
        }

        val activeSidebarDestination = when (currentDestination) {
            SettingsDestination.SPOTIFY_IMPORT -> SettingsDestination.ACCOUNT
            SettingsDestination.CONTENT_ROMANIZATION -> SettingsDestination.CONTENT
            SettingsDestination.AUTO_BACKUP -> SettingsDestination.BACKUP
            else -> currentDestination
        }

        val nestedBackDestination = when (currentDestination) {
            SettingsDestination.SPOTIFY_IMPORT -> SettingsDestination.ACCOUNT
            SettingsDestination.CONTENT_ROMANIZATION -> SettingsDestination.CONTENT
            SettingsDestination.AUTO_BACKUP -> SettingsDestination.BACKUP
            else -> null
        }

        val currentTitle = when (currentDestination) {
            SettingsDestination.MAIN, SettingsDestination.APPEARANCE -> appearanceText
            SettingsDestination.CONTENT -> contentText
            SettingsDestination.CONTENT_ROMANIZATION -> stringResource("romanization")
            SettingsDestination.ACCOUNT -> accountText
            SettingsDestination.SPOTIFY_IMPORT -> stringResource("import_from_spotify")
            SettingsDestination.LASTFM -> lastFmText
            SettingsDestination.DISCORD -> discordText
            SettingsDestination.PLAYER -> playerText
            SettingsDestination.PRIVACY -> privacyText
            SettingsDestination.STORAGE -> storageText
            SettingsDestination.BACKUP -> backupRestoreText
            SettingsDestination.AUTO_BACKUP -> stringResource("auto_backup_settings")
            SettingsDestination.UPDATE -> updateText
            SettingsDestination.ABOUT -> aboutText
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 760.dp, max = 980.dp)
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.86f)
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                shadowElevation = 20.dp,
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar
                    Column(
                        modifier = Modifier
                            .width(276.dp)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                    ) {
                        SettingsDialogSearchBar(
                            state = searchState,
                            onClear = { searchState.clearText() },
                        )

                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsDialogGreetingCard(
                                greetingText = greetingText,
                                displayName = displayName,
                                imageBitmap = greetingImage,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            filteredGroups.forEach { group ->
                                item(key = group.title) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = group.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            group.items.forEachIndexed { index, item ->
                                                val shape = when {
                                                    group.items.size == 1 -> RoundedCornerShape(16.dp)
                                                    index == 0 -> RoundedCornerShape(
                                                        topStart = 16.dp,
                                                        topEnd = 16.dp,
                                                        bottomStart = 4.dp,
                                                        bottomEnd = 4.dp,
                                                    )
                                                    index == group.items.lastIndex -> RoundedCornerShape(
                                                        topStart = 4.dp,
                                                        topEnd = 4.dp,
                                                        bottomStart = 16.dp,
                                                        bottomEnd = 16.dp,
                                                    )
                                                    else -> RoundedCornerShape(4.dp)
                                                }
                                                val selected = activeSidebarDestination == item.destination
                                                SettingsSidebarRow(
                                                    item = item,
                                                    selected = selected,
                                                    shape = shape,
                                                    onClick = { currentDestination = item.destination },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Subtle vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    )

                    // Right Content Pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (nestedBackDestination != null) {
                                IconButton(
                                    onClick = { currentDestination = nestedBackDestination },
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = IconAssets.arrowBack(),
                                        contentDescription = stringResource("back"),
                                    )
                                }
                            }
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = IconAssets.close(),
                                    contentDescription = stringResource("close"),
                                )
                            }
                        }

                        CompositionLocalProvider(LocalSettingsEmbeddedInDialog provides true) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (currentDestination) {
                                    SettingsDestination.MAIN,
                                    SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
                                        preferences = preferences,
                                        onBack = {},
                                    )

                                    SettingsDestination.CONTENT -> ContentSettingsScreen(
                                        preferences = preferences,
                                        onBack = {},
                                        onOpenRomanization = {
                                            currentDestination = SettingsDestination.CONTENT_ROMANIZATION
                                        },
                                    )

                                    SettingsDestination.CONTENT_ROMANIZATION -> RomanizationSettingsScreen(
                                        preferences = preferences,
                                        onBack = { currentDestination = SettingsDestination.CONTENT },
                                    )

                                    SettingsDestination.ACCOUNT -> AccountSettingsScreen(
                                        preferences = preferences,
                                        authService = authService,
                                        authCredentials = authCredentials,
                                        onBack = {},
                                        onOpenLogin = onOpenLogin,
                                        onOpenSpotifyImport = {
                                            currentDestination = SettingsDestination.SPOTIFY_IMPORT
                                        },
                                        onOpenDiscordSettings = {
                                            currentDestination = SettingsDestination.DISCORD
                                        },
                                        onAuthChanged = onAuthChanged,
                                    )

                                    SettingsDestination.SPOTIFY_IMPORT -> SpotifyImportSettingsScreen(
                                        preferences = preferences,
                                        onBack = { currentDestination = SettingsDestination.ACCOUNT },
                                    )

                                    SettingsDestination.LASTFM -> LastFmSettingsScreen(
                                        preferences = preferences,
                                        onBack = {},
                                    )

                                    SettingsDestination.DISCORD -> DiscordSettingsScreen(
                                        preferences = preferences,
                                        previewItem = playerState?.currentItem,
                                        previewPositionMs = playerState?.position ?: 0L,
                                        isPreviewPlaying = playerState?.isPlaying == true,
                                        onBack = {},
                                    )

                                    SettingsDestination.PLAYER -> PlayerSettingsScreen(
                                        preferences = preferences,
                                        onBack = {},
                                    )

                                    SettingsDestination.PRIVACY -> PrivacySettingsScreen(
                                        preferences = preferences,
                                        authService = authService,
                                        onBack = {},
                                    )

                                    SettingsDestination.STORAGE -> StorageSettingsScreen(
                                        preferences = preferences,
                                        downloadService = downloadService,
                                        onBack = {},
                                    )

                                    SettingsDestination.BACKUP -> BackupRestoreSettingsScreen(
                                        database = database,
                                        preferences = preferences,
                                        authService = authService,
                                        onAuthChanged = onAuthChanged,
                                        onOpenAutoBackup = {
                                            currentDestination = SettingsDestination.AUTO_BACKUP
                                        },
                                        onBack = {},
                                    )

                                    SettingsDestination.AUTO_BACKUP -> AutoBackupSettingsScreen(
                                        preferences = preferences,
                                        authService = authService,
                                        onAuthChanged = onAuthChanged,
                                        onBack = { currentDestination = SettingsDestination.BACKUP },
                                    )

                                    SettingsDestination.UPDATE -> UpdateSettingsScreen(
                                        preferences = preferences,
                                        onBack = {},
                                    )

                                    SettingsDestination.ABOUT -> AboutScreen(
                                        onBack = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialogSearchBar(
    state: TextFieldState,
    onClear: () -> Unit,
) {
    val isEmpty = state.text.isEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = IconAssets.search(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isEmpty) {
                    Text(
                        text = stringResource("search_settings_placeholder"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    state = state,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!isEmpty) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = IconAssets.close(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSidebarRow(
    item: SettingsSidebarItem,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialogGreetingCard(
    greetingText: String,
    displayName: String,
    imageBitmap: ImageBitmap?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (displayName.isNotBlank()) displayName else "AniTail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (imageBitmap != null) {
                Image(
                    painter = BitmapPainter(imageBitmap),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

