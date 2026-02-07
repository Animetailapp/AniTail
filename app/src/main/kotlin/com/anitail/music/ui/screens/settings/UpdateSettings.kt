package com.anitail.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.AutoUpdateCheckFrequencyKey
import com.anitail.music.constants.AutoUpdateEnabledKey
import com.anitail.music.constants.UpdateCheckFrequency
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.ReleaseNotesCard
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.Updater
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferencias
    val autoUpdateEnabled by rememberPreference(AutoUpdateEnabledKey, true)
    val updateFrequency by rememberPreference(
        AutoUpdateCheckFrequencyKey,
        UpdateCheckFrequency.DAILY.name
    )

    // Estados UI
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) } // Para animación de carga
    val isUpdateAvailable = latestVersionName != BuildConfig.VERSION_NAME

    // Animación de rotación para el botón de check
    val rotationAngle by animateFloatAsState(
        targetValue = if (isChecking) 360f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_settings)) },
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
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. HERO SECTION: Estado actual
            CurrentVersionHeader(
                currentVersion = BuildConfig.VERSION_NAME,
                isUpdateAvailable = isUpdateAvailable
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. UPDATE AVAILABLE CARD (Solo si hay actualización)
            AnimatedVisibility(
                visible = isUpdateAvailable,
                enter = fadeIn() + expandVertically()
            ) {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.update), // Asegúrate de que este icono exista o usa Icons.Filled.SystemUpdate
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.new_version_available),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "v$latestVersionName is ready to install.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Aquí podrías poner un botón de "Instalar ahora" si tu lógica lo permite directamente
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ReleaseNotesCard()
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // 3. CONFIGURATION GROUP
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                // Auto Update Switch
                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_update_enabled)) },
                    supportingContent = { Text(stringResource(R.string.auto_update_enabled_description)) },
                    leadingContent = {
                        Icon(painterResource(R.drawable.update), null)
                    },
                    trailingContent = {
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = { newValue ->
                                coroutineScope.launch {
                                    context.dataStore.edit { prefs ->
                                        prefs[AutoUpdateEnabledKey] = newValue
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.tvClickable { /* Toggle switch action */ }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Frequency Selector
                val frequency = UpdateCheckFrequency.valueOf(updateFrequency)
                val frequencyLabel = when (frequency) {
                    UpdateCheckFrequency.DAILY -> stringResource(R.string.daily)
                    UpdateCheckFrequency.WEEKLY -> stringResource(R.string.weekly)
                    UpdateCheckFrequency.MONTHLY -> stringResource(R.string.monthly)
                    UpdateCheckFrequency.NEVER -> stringResource(R.string.never)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_check_frequency)) },
                    supportingContent = { Text(frequencyLabel) },
                    leadingContent = { Icon(painterResource(R.drawable.clock), null) },
                    trailingContent = {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier
                        .tvClickable(enabled = autoUpdateEnabled) { showFrequencyDialog = true },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = if (autoUpdateEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f
                        )
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. MANUAL CHECK ACTION
            Button(
                onClick = {
                    isChecking = true
                    coroutineScope.launch {
                        // Simular un pequeño delay visual para que la animación se vea
                        delay(800)
                        try {
                            val result = Updater.getLatestReleaseInfo()
                            if (result.isSuccess) {
                                val releaseInfo = result.getOrThrow()
                                if (releaseInfo.versionName != BuildConfig.VERSION_NAME) {
                                    // Lógica original de actualización...
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.new_version_available_toast,
                                            releaseInfo.versionName
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    if (autoUpdateEnabled) {
                                        val downloadId =
                                            Updater.downloadUpdate(context, releaseInfo.downloadUrl)
                                        if (downloadId != -1L) {
                                            Toast.makeText(
                                                context,
                                                R.string.downloading_update,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.app_up_to_date,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.update_check_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isChecking = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.check_for_updates_now))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Frequency Dialog (Modernized with AlertDialog for better styling)
    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            icon = { Icon(painterResource(R.drawable.clock), null) },
            title = { Text(stringResource(R.string.update_check_frequency)) },
            text = {
                Column {
                    UpdateCheckFrequency.entries.forEach { option ->
                        val label = when (option) {
                            UpdateCheckFrequency.DAILY -> stringResource(R.string.daily)
                            UpdateCheckFrequency.WEEKLY -> stringResource(R.string.weekly)
                            UpdateCheckFrequency.MONTHLY -> stringResource(R.string.monthly)
                            UpdateCheckFrequency.NEVER -> stringResource(R.string.never)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable {
                                    coroutineScope.launch {
                                        context.dataStore.edit { preferences ->
                                            preferences[AutoUpdateCheckFrequencyKey] = option.name
                                        }
                                    }
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.name == updateFrequency,
                                onClick = null // Handled by Row
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrequencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CurrentVersionHeader(currentVersion: String, isUpdateAvailable: Boolean) {
    val color =
        if (isUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val iconVector = if (isUpdateAvailable) Icons.Outlined.Info else Icons.Filled.CheckCircle

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isUpdateAvailable) "Update Available" else stringResource(R.string.app_up_to_date),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.current_version, currentVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
