package com.anitail.music.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.AvatarSource
import com.anitail.music.constants.PreferredAvatarSourceKey
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.viewmodels.HomeViewModel
import java.util.Calendar
import androidx.compose.material3.IconButton as M3IconButton

// Data class para definir cada opción de configuración y poder buscarla

data class SettingOptionData(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val section: String? = null
)

data class MatchedItem(
    val item: SettingItemData,
    val matchedOptions: List<SettingOptionData> = emptyList()
)

data class SettingItemData(
    val id: String,
    val title: String,
    val iconRes: Int,
    val category: String,
    val iconColor: Color,
    val onClick: () -> Unit,
    val badge: (@Composable BoxScope.() -> Unit)? = null,
    val subtitle: String? = null,
    val optionsProvider: (() -> List<SettingOptionData>)? = null,
)

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scrollState = rememberScrollState()

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val discordUsername by homeViewModel.discordUsername.collectAsState()
    val (preferredAvatarSource) = rememberEnumPreference(PreferredAvatarSourceKey, defaultValue = AvatarSource.YOUTUBE)

    val greetingText = remember(currentHour) {
        when (currentHour) {
            in 6..11 -> context.getString(R.string.good_morning)
            in 12..18 -> context.getString(R.string.good_afternoon)
            else -> context.getString(R.string.good_evening)
        }
    }

    val timeBasedImage = remember(currentHour) {
        when (currentHour) {
            in 6..11 -> R.drawable.ic_user_device_day
            in 12..18 -> R.drawable.ic_user_device_afternoon
            else -> R.drawable.ic_user_device_night
        }
    }

    val colorScheme = colorScheme
    val cardBackgroundColor = remember(currentHour, colorScheme) {
        when (currentHour) {
            in 6..11 -> colorScheme.primary.copy(alpha = 0.1f)
            in 12..18 -> colorScheme.primary.copy(alpha = 0.1f)
            else -> colorScheme.primary.copy(alpha = 0.1f)
        }
    }

    val allSettings = remember(latestVersionName) {
        val list = mutableListOf<SettingItemData>()
        fun optionsFor(id: String): () -> List<SettingOptionData> =
            { SettingsSearchOptions.forItem(id, context) }

        list.add(
            SettingItemData(
                "appearance",
                context.getString(R.string.appearance),
                R.drawable.palette,
                context.getString(R.string.category_interface),
                Color(0xFF5C6BC0),
                { navController.navigate("settings/appearance") },
                optionsProvider = optionsFor("appearance")
            )
        )
        list.add(
            SettingItemData(
                "account",
                context.getString(R.string.account),
                R.drawable.account,
                context.getString(R.string.category_interface),
                Color(0xFFAB47BC),
                { navController.navigate("settings/account") },
                optionsProvider = optionsFor("account")
            )
        )
        list.add(
            SettingItemData(
                "lastfm",
                context.getString(R.string.lastfm_settings),
                R.drawable.music_note,
                context.getString(R.string.category_interface),
                Color(0xFFEF5350),
                { navController.navigate("settings/lastfm") },
                optionsProvider = optionsFor("lastfm")
            )
        )

        list.add(
            SettingItemData(
                "content",
                context.getString(R.string.content),
                R.drawable.language,
                context.getString(R.string.category_content),
                Color(0xFF26A69A),
                { navController.navigate("settings/content") },
                optionsProvider = optionsFor("content")
            )
        )
        list.add(
            SettingItemData(
                "player",
                context.getString(R.string.player_and_audio),
                R.drawable.play,
                context.getString(R.string.category_player),
                Color(0xFFFFA726),
                { navController.navigate("settings/player") },
                optionsProvider = optionsFor("player")
            )
        )
        list.add(
            SettingItemData(
                "jam",
                context.getString(R.string.jam_lan_sync),
                R.drawable.sync,
                context.getString(R.string.category_player),
                Color(0xFF42A5F5),
                { navController.navigate("settings/jam") },
                optionsProvider = optionsFor("jam")
            )
        )
        list.add(
            SettingItemData(
                "privacy",
                context.getString(R.string.privacy),
                R.drawable.security,
                context.getString(R.string.category_content),
                Color(0xFF78909C),
                { navController.navigate("settings/privacy") },
                optionsProvider = optionsFor("privacy")
            )
        )

        list.add(
            SettingItemData(
                "storage",
                context.getString(R.string.storage),
                R.drawable.storage,
                context.getString(R.string.category_system),
                Color(0xFF8D6E63),
                { navController.navigate("settings/storage") },
                optionsProvider = optionsFor("storage")
            )
        )
        list.add(
            SettingItemData(
                "backup",
                context.getString(R.string.backup_restore),
                R.drawable.restore,
                context.getString(R.string.category_system),
                Color(0xFF5C6BC0),
                { navController.navigate("settings/backup_restore") },
                optionsProvider = optionsFor("backup")
            )
        )

        val hasUpdate = latestVersionName != BuildConfig.VERSION_NAME
        val updateBadge: (@Composable BoxScope.() -> Unit)? = if (hasUpdate) {
            { Badge() }
        } else null
        list.add(
            SettingItemData(
                id = "update",
                title = context.getString(R.string.update_settings),
                iconRes = R.drawable.update,
                category = context.getString(R.string.category_system),
                iconColor = if (hasUpdate) Color(0xFF66BB6A) else Color(0xFFBDBDBD),
                onClick = { navController.navigate("settings/updates") },
                badge = updateBadge,
                subtitle = if (hasUpdate) context.getString(R.string.new_version_available) else null,
                optionsProvider = optionsFor("update")
            )
        )

        if (isAndroid12OrLater) {
            list.add(
                SettingItemData(
                    "links",
                    context.getString(R.string.default_links),
                    R.drawable.link,
                    "More",
                    Color(0xFF29B6F6),
                    {
                        try {
                            val intent = Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                "package:${context.packageName}".toUri()
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                R.string.open_app_settings_error,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
            )
        }
        list.add(
            SettingItemData(
                "about",
                context.getString(R.string.about),
                R.drawable.info,
                "More",
                Color(0xFF7E57C2),
                { navController.navigate("settings/about") },
                optionsProvider = optionsFor("about")
            )
        )

        list
    }

    var searchQuery by remember { mutableStateOf("") }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = searchQuery.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .height(140.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 24.dp, top = 16.dp, bottom = 16.dp)
                                .weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = greetingText,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val displayName = when (preferredAvatarSource) {
                                AvatarSource.YOUTUBE -> accountName.takeUnless { it.isBlank() || it == "Guest" }
                                AvatarSource.DISCORD -> discordUsername
                            } ?: Build.MODEL
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Image(
                            painter = painterResource(id = timeBasedImage),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .padding(end = 8.dp),
                            alignment = Alignment.CenterEnd
                        )
                    }
                }
            }

            SearchBarField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = {
                    searchQuery = ""
                    focusManager.clearFocus()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isEmpty()) {
                val categories = allSettings.map { it.category }.distinct()

                categories.forEach { category ->
                    val itemsInCategory = allSettings.filter { it.category == category }

                    SettingsGroupTitle(category)
                    SettingsGroupCard {
                        itemsInCategory.forEachIndexed { index, item ->
                            SettingsItem(
                                title = item.title,
                                subtitle = item.subtitle,
                                iconRes = item.iconRes,
                                iconColor = item.iconColor,
                                badge = item.badge,
                                onClick = item.onClick
                            )
                            if (index < itemsInCategory.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                val categoriesAll = allSettings.map { it.category }.distinct()
                val filteredByCategory = categoriesAll.mapNotNull { category ->
                    val itemsInCategory = allSettings.filter { it.category == category }
                    val matchedItems = itemsInCategory.mapNotNull { item ->
                        if (category.contains(searchQuery, ignoreCase = true)) {
                            MatchedItem(item)
                        } else {
                            val itemMatches = item.title.contains(searchQuery, ignoreCase = true) ||
                                    (item.subtitle?.contains(
                                        searchQuery,
                                        ignoreCase = true
                                    ) == true) ||
                                    item.id.contains(searchQuery, ignoreCase = true)

                            val options = item.optionsProvider?.invoke().orEmpty()
                            val matchedOptions = options.filter { opt ->
                                opt.title.contains(searchQuery, ignoreCase = true) ||
                                        (opt.subtitle?.contains(
                                            searchQuery,
                                            ignoreCase = true
                                        ) == true) ||
                                        opt.id.contains(searchQuery, ignoreCase = true)
                            }

                            if (itemMatches) MatchedItem(item)
                            else if (matchedOptions.isNotEmpty()) MatchedItem(item, matchedOptions)
                            else null
                        }
                    }
                    if (matchedItems.isNotEmpty()) category to matchedItems else null
                }

                if (filteredByCategory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No settings found", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Text(
                        "Search Results",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    filteredByCategory.forEach { (category, matchedItems) ->
                        SettingsGroupTitle(category)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            matchedItems.forEach { matched ->
                                val item = matched.item
                                if (matched.matchedOptions.isEmpty()) {
                                    SearchResultCard(
                                        title = item.title,
                                        description = item.subtitle,
                                        label = item.category,
                                        iconRes = item.iconRes,
                                        iconColor = item.iconColor,
                                        onClick = item.onClick
                                    )
                                } else {
                                    matched.matchedOptions.forEach { opt ->
                                        SearchResultCard(
                                            title = opt.title,
                                            description = opt.subtitle,
                                            label = listOfNotNull(
                                                item.title,
                                                opt.section
                                            ).joinToString(" • "),
                                            iconRes = item.iconRes,
                                            iconColor = item.iconColor,
                                            onClick = item.onClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// --- COMPONENTES AUXILIARES ---

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search settings...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                M3IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = colorScheme.surfaceContainer,
            unfocusedContainerColor = colorScheme.surfaceContainerHigh
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { defaultKeyboardAction(ImeAction.Search) })
    )
}

@Composable
fun SettingsGroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp, top = 24.dp)
    )
}

@Composable
fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    iconRes: Int,
    iconColor: Color,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        if (badge != null) {
            BadgedBox(badge = badge) { }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun SearchResultCard(
    title: String,
    description: String?,
    label: String,
    iconRes: Int,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        color = colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.9f)
                )
                description?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
