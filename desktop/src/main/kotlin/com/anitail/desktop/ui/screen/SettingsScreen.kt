package com.anitail.desktop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DarkModePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
import com.anitail.desktop.storage.QuickPicks
import com.anitail.desktop.storage.SliderStyle
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle

/**
 * Settings navigation destinations
 */
enum class SettingsDestination {
    MAIN,
    APPEARANCE,
    PLAYER,
    CONTENT,
    PRIVACY,
    STORAGE,
    ABOUT,
}

/**
 * Main Settings Screen with navigation to sub-settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: DesktopPreferences = DesktopPreferences.getInstance(),
) {
    var currentDestination by remember { mutableStateOf(SettingsDestination.MAIN) }

    Column(modifier = Modifier.fillMaxSize()) {
        when (currentDestination) {
            SettingsDestination.MAIN -> SettingsMainScreen(
                onNavigate = { currentDestination = it },
            )

            SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.PLAYER -> PlayerSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.CONTENT -> ContentSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.PRIVACY -> PrivacySettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.STORAGE -> StorageSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.ABOUT -> AboutScreen(
                onBack = { currentDestination = SettingsDestination.MAIN },
            )
        }
    }
}

@Composable
private fun SettingsMainScreen(
    onNavigate: (SettingsDestination) -> Unit,
) {
    val settingsCategories = listOf(
        SettingsCategory(
            title = "Apariencia",
            subtitle = "Tema, colores, fuente",
            icon = IconAssets.palette(),
            destination = SettingsDestination.APPEARANCE,
        ),
        SettingsCategory(
            title = "Reproducción",
            subtitle = "Calidad, crossfade, cola",
            icon = IconAssets.play(),
            destination = SettingsDestination.PLAYER,
        ),
        SettingsCategory(
            title = "Contenido",
            subtitle = "Idioma, país, filtros",
            icon = IconAssets.language(),
            destination = SettingsDestination.CONTENT,
        ),
        SettingsCategory(
            title = "Privacidad",
            subtitle = "Historial, datos",
            icon = IconAssets.security(),
            destination = SettingsDestination.PRIVACY,
        ),
        SettingsCategory(
            title = "Almacenamiento",
            subtitle = "Caché, espacio",
            icon = IconAssets.storage(),
            destination = SettingsDestination.STORAGE,
        ),
        SettingsCategory(
            title = "Acerca de",
            subtitle = "Versión, licencias",
            icon = IconAssets.info(),
            destination = SettingsDestination.ABOUT,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            NavigationTitle(title = "Ajustes")
        }

        items(settingsCategories) { category ->
            SettingsCategoryItem(
                category = category,
                onClick = { onNavigate(category.destination) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = IconAssets.chevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// === Sub-screens ===

@Composable
private fun AppearanceSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()

    SettingsSubScreen(
        title = "Apariencia",
        onBack = onBack,
    ) {
        // Dark Mode
        SettingsDropdown(
            title = "Modo oscuro",
            subtitle = when (darkMode) {
                DarkModePreference.ON -> "Oscuro"
                DarkModePreference.OFF -> "Claro"
                DarkModePreference.AUTO -> "Sistema"
                DarkModePreference.TIME_BASED -> "Horario"
            },
            options = DarkModePreference.entries.map {
                when (it) {
                    DarkModePreference.ON -> "Oscuro"
                    DarkModePreference.OFF -> "Claro"
                    DarkModePreference.AUTO -> "Sistema"
                    DarkModePreference.TIME_BASED -> "Horario"
                }
            },
            selectedIndex = DarkModePreference.entries.indexOf(darkMode),
            onSelect = { index ->
                preferences.setDarkMode(DarkModePreference.entries[index])
            },
        )

        // Pure Black
        SettingsSwitch(
            title = "Negro puro",
            subtitle = "Usa negro puro en modo oscuro (OLED)",
            checked = pureBlack,
            onCheckedChange = { preferences.setPureBlack(it) },
        )

        // Dynamic Color
        SettingsSwitch(
            title = "Color dinámico",
            subtitle = "Usa colores de la carátula actual",
            checked = dynamicColor,
            onCheckedChange = { preferences.setDynamicColor(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsDropdown(
            title = "Fondo del reproductor",
            subtitle = playerBackgroundStyle.displayName,
            options = PlayerBackgroundStyle.entries.map { it.displayName },
            selectedIndex = PlayerBackgroundStyle.entries.indexOf(playerBackgroundStyle),
            onSelect = { index ->
                preferences.setPlayerBackgroundStyle(PlayerBackgroundStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = "Estilo de botones",
            subtitle = playerButtonsStyle.displayName,
            options = PlayerButtonsStyle.entries.map { it.displayName },
            selectedIndex = PlayerButtonsStyle.entries.indexOf(playerButtonsStyle),
            onSelect = { index ->
                preferences.setPlayerButtonsStyle(PlayerButtonsStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = "Estilo del deslizador",
            subtitle = sliderStyle.displayName,
            options = SliderStyle.entries.map { it.displayName },
            selectedIndex = SliderStyle.entries.indexOf(sliderStyle),
            onSelect = { index ->
                preferences.setSliderStyle(SliderStyle.entries[index])
            },
        )
    }
}

@Composable
private fun PlayerSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val audioQuality by preferences.audioQuality.collectAsState()
    val skipSilence by preferences.skipSilence.collectAsState()
    val crossfadeDuration by preferences.crossfadeDuration.collectAsState()
    val historyDuration by preferences.historyDuration.collectAsState()
    val persistentQueue by preferences.persistentQueue.collectAsState()
    val autoStartRadio by preferences.autoStartRadio.collectAsState()
    val showLyrics by preferences.showLyrics.collectAsState()
    val romanizeLyrics by preferences.romanizeLyrics.collectAsState()

    SettingsSubScreen(
        title = "Reproducción",
        onBack = onBack,
    ) {
        // Audio Quality
        SettingsDropdown(
            title = "Calidad de audio",
            subtitle = audioQuality.displayName,
            options = AudioQuality.entries.map { it.displayName },
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onSelect = { index ->
                preferences.setAudioQuality(AudioQuality.entries[index])
            },
        )

        // Historial de reproducción
        SettingsSlider(
            title = "Duración del historial",
            subtitle = if (historyDuration <= 0f) "Ilimitado" else "${historyDuration.toInt()} segundos",
            value = historyDuration.coerceIn(0f, 60f),
            valueRange = 0f..60f,
            steps = 59,
            onValueChange = { preferences.setHistoryDuration(it) },
        )

        // Skip Silence
        SettingsSwitch(
            title = "Saltar silencios",
            subtitle = "Salta automáticamente las partes silenciosas",
            checked = skipSilence,
            onCheckedChange = { preferences.setSkipSilence(it) },
        )

        // Crossfade
        SettingsSlider(
            title = "Crossfade",
            subtitle = if (crossfadeDuration == 0) "Desactivado" else "$crossfadeDuration segundos",
            value = crossfadeDuration.toFloat(),
            valueRange = 0f..12f,
            steps = 11,
            onValueChange = { preferences.setCrossfadeDuration(it.toInt()) },
        )

        // Persistent Queue
        SettingsSwitch(
            title = "Cola persistente",
            subtitle = "Restaura la cola al iniciar la app",
            checked = persistentQueue,
            onCheckedChange = { preferences.setPersistentQueue(it) },
        )

        // Auto Start Radio
        SettingsSwitch(
            title = "Radio automática",
            subtitle = "Inicia radio cuando termina la cola",
            checked = autoStartRadio,
            onCheckedChange = { preferences.setAutoStartRadio(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Show Lyrics
        SettingsSwitch(
            title = "Mostrar letras",
            subtitle = "Muestra letras en el reproductor",
            checked = showLyrics,
            onCheckedChange = { preferences.setShowLyrics(it) },
        )

        // Romanize Lyrics
        SettingsSwitch(
            title = "Romanizar letras",
            subtitle = "Convierte letras asiáticas a romanji",
            checked = romanizeLyrics,
            onCheckedChange = { preferences.setRomanizeLyrics(it) },
            enabled = showLyrics,
        )
    }
}

@Composable
private fun ContentSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicks by preferences.quickPicks.collectAsState()

    val languages = listOf(
        "es" to "Español",
        "en" to "English",
        "pt" to "Português",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
    )

    val countries = listOf(
        "ES" to "España",
        "MX" to "México",
        "AR" to "Argentina",
        "US" to "Estados Unidos",
        "GB" to "Reino Unido",
        "DE" to "Alemania",
        "FR" to "Francia",
        "JP" to "Japón",
    )

    SettingsSubScreen(
        title = "Contenido",
        onBack = onBack,
    ) {
        // Language
        SettingsDropdown(
            title = "Idioma del contenido",
            subtitle = languages.find { it.first == contentLanguage }?.second ?: contentLanguage,
            options = languages.map { it.second },
            selectedIndex = languages.indexOfFirst { it.first == contentLanguage }.coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentLanguage(languages[index].first)
            },
        )

        // Country
        SettingsDropdown(
            title = "País/Región",
            subtitle = countries.find { it.first == contentCountry }?.second ?: contentCountry,
            options = countries.map { it.second },
            selectedIndex = countries.indexOfFirst { it.first == contentCountry }.coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentCountry(countries[index].first)
            },
        )

        // Hide Explicit
        SettingsSwitch(
            title = "Ocultar contenido explícito",
            subtitle = "Filtra canciones con contenido explícito",
            checked = hideExplicit,
            onCheckedChange = { preferences.setHideExplicit(it) },
        )

        // Quick Picks mode
        SettingsDropdown(
            title = "Quick picks",
            subtitle = when (quickPicks) {
                QuickPicks.QUICK_PICKS -> "Quick picks"
                QuickPicks.LAST_LISTEN -> "Última canción escuchada"
            },
            options = listOf("Quick picks", "Última canción escuchada"),
            selectedIndex = if (quickPicks == QuickPicks.QUICK_PICKS) 0 else 1,
            onSelect = { index ->
                preferences.setQuickPicks(if (index == 0) QuickPicks.QUICK_PICKS else QuickPicks.LAST_LISTEN)
            },
        )
    }
}

@Composable
private fun PrivacySettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val pauseListenHistory by preferences.pauseListenHistory.collectAsState()
    val pauseSearchHistory by preferences.pauseSearchHistory.collectAsState()

    SettingsSubScreen(
        title = "Privacidad",
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = "Pausar historial de escucha",
            subtitle = "No guardar canciones reproducidas",
            checked = pauseListenHistory,
            onCheckedChange = { preferences.setPauseListenHistory(it) },
        )

        SettingsSwitch(
            title = "Pausar historial de búsqueda",
            subtitle = "No guardar búsquedas realizadas",
            checked = pauseSearchHistory,
            onCheckedChange = { preferences.setPauseSearchHistory(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // YouTube Cookie
        val youtubeCookie by preferences.youtubeCookie.collectAsState()
        var tempCookie by remember { mutableStateOf(youtubeCookie ?: "") }

        SettingsTextField(
            title = "Cookie de YouTube",
            subtitle = "Pega aquí tus cookies de music.youtube.com para evitar errores '403' o 'Bot detection'.\nInstrucciones: F12 en Google Chrome -> Application -> Cookies -> Selecciona music.youtube.com -> Copia el valor de 'Cookie' de las cabeceras de red o exporta con una extensión.",
            value = tempCookie,
            onValueChange = { tempCookie = it },
            onSave = { preferences.setYoutubeCookie(tempCookie.takeIf { it.isNotBlank() }) },
            placeholder = "Papisid=...; SID=...;"
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Clear buttons
        var showClearHistoryDialog by remember { mutableStateOf(false) }
        var showClearSearchDialog by remember { mutableStateOf(false) }

        SettingsButton(
            title = "Borrar historial de escucha",
            subtitle = "Elimina todo el historial",
            onClick = { showClearHistoryDialog = true },
        )

        SettingsButton(
            title = "Borrar historial de búsqueda",
            subtitle = "Elimina todas las búsquedas",
            onClick = { showClearSearchDialog = true },
        )

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("¿Borrar historial?") },
                text = { Text("Se eliminará todo tu historial de escucha. Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear history via database
                        showClearHistoryDialog = false
                    }) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }

        if (showClearSearchDialog) {
            AlertDialog(
                onDismissRequest = { showClearSearchDialog = false },
                title = { Text("¿Borrar búsquedas?") },
                text = { Text("Se eliminará todo tu historial de búsqueda. Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear search history via database
                        showClearSearchDialog = false
                    }) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearSearchDialog = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun StorageSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val maxImageCacheSizeMB by preferences.maxImageCacheSizeMB.collectAsState()
    val maxSongCacheSizeMB by preferences.maxSongCacheSizeMB.collectAsState()

    SettingsSubScreen(
        title = "Almacenamiento",
        onBack = onBack,
    ) {
        // Image Cache Size
        SettingsSlider(
            title = "Caché de imágenes",
            subtitle = "$maxImageCacheSizeMB MB",
            value = maxImageCacheSizeMB.toFloat(),
            valueRange = 100f..2000f,
            steps = 18,
            onValueChange = { preferences.setMaxImageCacheSizeMB(it.toInt()) },
        )

        // Song Cache Size
        SettingsSlider(
            title = "Caché de canciones",
            subtitle = "$maxSongCacheSizeMB MB",
            value = maxSongCacheSizeMB.toFloat(),
            valueRange = 500f..10000f,
            steps = 18,
            onValueChange = { preferences.setMaxSongCacheSizeMB(it.toInt()) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        var showClearCacheDialog by remember { mutableStateOf(false) }

        SettingsButton(
            title = "Limpiar caché",
            subtitle = "Libera espacio eliminando archivos temporales",
            onClick = { showClearCacheDialog = true },
        )

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text("¿Limpiar caché?") },
                text = { Text("Se eliminarán las imágenes y datos en caché. Las canciones descargadas no se verán afectadas.") },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear cache
                        showClearCacheDialog = false
                    }) {
                        Text("Limpiar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
) {
    SettingsSubScreen(
        title = "Acerca de",
        onBack = onBack,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = IconAssets.musicNote(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AniTail",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Desktop Edition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "v1.0.0-desktop",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsInfoItem(
            title = "Versión",
            value = "1.0.0-desktop",
        )

        SettingsInfoItem(
            title = "Kotlin",
            value = "2.0+",
        )

        SettingsInfoItem(
            title = "Compose Desktop",
            value = "1.7.x",
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsButton(
            title = "Código fuente",
            subtitle = "Ver en GitHub",
            onClick = { /* TODO: Open GitHub */ },
        )

        SettingsButton(
            title = "Licencias",
            subtitle = "Bibliotecas de código abierto",
            onClick = { /* TODO: Show licenses */ },
        )
    }
}

// === Helper Components ===

@Composable
private fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = IconAssets.arrowBack(),
                    contentDescription = "Volver",
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = if (expanded) IconAssets.expandLess() else IconAssets.expandMore(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                        leadingIcon = if (index == selectedIndex) {
                            { Icon(IconAssets.check(), contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

@Composable
private fun SettingsButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = IconAssets.link(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    placeholder: String = "",
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Guardar Cookie")
            }
        }
    }
}

@Composable
private fun SettingsInfoItem(
    title: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: SettingsDestination,
)
