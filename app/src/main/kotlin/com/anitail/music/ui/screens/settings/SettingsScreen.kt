package com.anitail.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.screens.settings.designs.IconResource
import com.anitail.music.ui.screens.settings.designs.SettingCategory
import com.anitail.music.ui.screens.settings.designs.SettingsBox
import com.anitail.music.ui.screens.settings.designs.shapeManager
import com.anitail.music.ui.utils.backToMain
import java.util.Calendar

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Greeting message based on time of day
    val welcomeMessage = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 6..11 -> context.getString(R.string.good_morning)
            in 12..18 -> context.getString(R.string.good_afternoon)
            else -> context.getString(R.string.good_evening)
        }
    }
    val timeBasedImage = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 6..11 -> R.drawable.ic_user_device_day
            in 12..18 -> R.drawable.ic_user_device_afternoon
            else -> R.drawable.ic_user_device_night
        }
    }


    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = timeBasedImage),
                    contentDescription = null,
                    modifier = Modifier
                        .size(170.dp)
                        .padding(end = 24.dp)
                )
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = welcomeMessage,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stringResource(R.string.device)} ${Build.MODEL}\n(${Build.DEVICE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interface category
        SettingCategory(title = stringResource(id = R.string.category_interface))
        SettingsBox(
            title = stringResource(R.string.appearance),
            icon = IconResource.Drawable(painterResource(R.drawable.palette)),
            shape = shapeManager(isFirst = true),
            onClick = { navController.navigate("settings/appearance") }
        )
        SettingsBox(
            title = stringResource(R.string.account),
            icon = IconResource.Drawable(painterResource(R.drawable.account)),
            shape = shapeManager(),
            onClick = { navController.navigate("settings/account") }
        )
        SettingsBox(
            title = stringResource(R.string.lastfm_settings),
            icon = IconResource.Drawable(painterResource(R.drawable.music_note)),
            shape = shapeManager(isLast = true),
            onClick = { navController.navigate("settings/lastfm") }
        )

        // Content category
        SettingCategory(title = stringResource(id = R.string.category_content))
        SettingsBox(
            title = stringResource(R.string.content),
            icon = IconResource.Drawable(painterResource(R.drawable.language)),
            shape = shapeManager(isFirst = true),
            onClick = { navController.navigate("settings/content") }
        )
        SettingsBox(
            title = stringResource(R.string.privacy),
            icon = IconResource.Drawable(painterResource(R.drawable.security)),
            shape = shapeManager(isLast = true),
            onClick = { navController.navigate("settings/privacy") }
        )

        // Player category
        SettingCategory(title = stringResource(id = R.string.category_player))
        SettingsBox(
            title = stringResource(R.string.player_and_audio),
            icon = IconResource.Drawable(painterResource(R.drawable.play)),
            shape = shapeManager(isFirst = true),
            onClick = { navController.navigate("settings/player") }
        )
        SettingsBox(
            title = stringResource(R.string.jam_lan_sync),
            icon = IconResource.Drawable(painterResource(R.drawable.sync)),
            shape = shapeManager(isLast = true),
            onClick = { navController.navigate("settings/jam") }
        )

        // System category
        SettingCategory(title = stringResource(id = R.string.category_system))
        SettingsBox(
            title = stringResource(R.string.storage),
            icon = IconResource.Drawable(painterResource(R.drawable.storage)),
            shape = shapeManager(isFirst = true),
            onClick = { navController.navigate("settings/storage") }
        )
        SettingsBox(
            title = stringResource(R.string.backup_restore),
            icon = IconResource.Drawable(painterResource(R.drawable.restore)),
            shape = shapeManager(),
            onClick = { navController.navigate("settings/backup_restore") }
        )
        SettingsBox(
            title = stringResource(R.string.update_settings),
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Icon with optional badge
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .then(Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(badge = {
                            if (latestVersionName != BuildConfig.VERSION_NAME) {
                                Badge { }
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.update),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.padding(start = 16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_settings),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = shapeManager(),
            onClick = { navController.navigate("settings/updates") }
        )

        if (isAndroid12OrLater) {
            SettingsBox(
                title = stringResource(R.string.default_links),
                icon = IconResource.Drawable(painterResource(R.drawable.link)),
                shape = shapeManager(),
                onClick = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        when (e) {
                            is ActivityNotFoundException, is SecurityException -> {
                                Toast.makeText(
                                    context,
                                    R.string.open_app_settings_error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    context,
                                    R.string.open_app_settings_error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }
        SettingsBox(
            title = stringResource(R.string.about),
            icon = IconResource.Drawable(painterResource(R.drawable.info)),
            shape = shapeManager(isLast = true),
            onClick = { navController.navigate("settings/about") }
        )
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Bottom
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}

