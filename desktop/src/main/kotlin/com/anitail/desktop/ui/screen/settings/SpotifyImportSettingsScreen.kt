package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.DesktopSpotifyService
import com.anitail.desktop.auth.SpotifyPlaylistSummary
import com.anitail.desktop.auth.SpotifyServiceResult
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SpotifyImportSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val database = remember { DesktopDatabase.getInstance() }

    val spotifyAccessToken by preferences.spotifyAccessToken.collectAsState()
    val spotifyRefreshToken by preferences.spotifyRefreshToken.collectAsState()
    val spotifyUserName by preferences.spotifyUserName.collectAsState()

    var clientId by rememberSaveable { mutableStateOf("") }
    var clientSecret by rememberSaveable { mutableStateOf("") }
    var authorizationCode by rememberSaveable { mutableStateOf("") }

    var isRequesting by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var totalPlaylists by remember { mutableStateOf(0) }
    var loadedPlaylists by remember { mutableStateOf<List<SpotifyPlaylistSummary>>(emptyList()) }
    var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    var includeLikedSongs by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var nextOffset by remember { mutableStateOf(0) }
    val likedSongsLabel = stringResource("liked_songs")

    fun openUrl(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url.trim())) }
    }

    suspend fun loadSpotifyData(
        accessToken: String,
        append: Boolean,
    ) {
        val currentOffset = if (append) nextOffset else 0
        if (!append) {
            selectedPlaylistIds = emptySet()
            includeLikedSongs = false
        }

        if (!append) {
            when (val profile = withContext(Dispatchers.IO) { DesktopSpotifyService.fetchUserProfile(accessToken) }) {
                is SpotifyServiceResult.Success -> {
                    preferences.setSpotifyUserName(profile.value.name)
                }

                is SpotifyServiceResult.Error -> {
                    errorMessage = profile.message
                    return
                }
            }
        }

        when (val page = withContext(Dispatchers.IO) {
            DesktopSpotifyService.fetchPlaylists(
                accessToken = accessToken,
                offset = currentOffset,
                limit = 50,
            )
        }) {
            is SpotifyServiceResult.Success -> {
                totalPlaylists = page.value.total
                loadedPlaylists = if (append) {
                    loadedPlaylists + page.value.items
                } else {
                    page.value.items
                }
                hasMore = page.value.hasMore
                nextOffset = page.value.nextOffset
                errorMessage = null
            }

            is SpotifyServiceResult.Error -> {
                errorMessage = page.message
            }
        }
    }

    fun executeLogin() {
        val cleanClientId = clientId.trim()
        val cleanClientSecret = clientSecret.trim()
        val cleanAuthCode = authorizationCode.trim()
        if (cleanClientId.isBlank() || cleanClientSecret.isBlank() || cleanAuthCode.isBlank()) {
            errorMessage = "Client ID, Client Secret y Authorization Code son obligatorios."
            return
        }

        scope.launch {
            isRequesting = true
            errorMessage = null
            infoMessage = null

            when (val result = withContext(Dispatchers.IO) {
                DesktopSpotifyService.exchangeAuthorizationCode(
                    clientId = cleanClientId,
                    clientSecret = cleanClientSecret,
                    authorizationCode = cleanAuthCode,
                )
            }) {
                is SpotifyServiceResult.Success -> {
                    val expiresAt = System.currentTimeMillis() + (result.value.expiresInSeconds * 1000L)
                    preferences.setSpotifyAccessToken(result.value.accessToken)
                    preferences.setSpotifyRefreshToken(result.value.refreshToken)
                    preferences.setSpotifyTokenExpiryEpochMillis(expiresAt)
                    loadSpotifyData(result.value.accessToken, append = false)
                    infoMessage = "Spotify conectado correctamente."
                }

                is SpotifyServiceResult.Error -> {
                    errorMessage = result.message
                }
            }
            isRequesting = false
        }
    }

    fun tryRefreshAccessToken(onSuccess: suspend (String) -> Unit) {
        val cleanClientId = clientId.trim()
        val cleanClientSecret = clientSecret.trim()
        val cleanRefreshToken = spotifyRefreshToken.trim()

        if (cleanClientId.isBlank() || cleanClientSecret.isBlank() || cleanRefreshToken.isBlank()) {
            scope.launch {
                if (spotifyAccessToken.isNotBlank()) {
                    onSuccess(spotifyAccessToken)
                } else {
                    errorMessage = "No hay token válido. Inicia sesión con Spotify API credentials."
                }
            }
            return
        }

        scope.launch {
            isRequesting = true
            when (val refreshed = withContext(Dispatchers.IO) {
                DesktopSpotifyService.refreshAccessToken(
                    clientId = cleanClientId,
                    clientSecret = cleanClientSecret,
                    refreshToken = cleanRefreshToken,
                )
            }) {
                is SpotifyServiceResult.Success -> {
                    val expiresAt = System.currentTimeMillis() + (refreshed.value.expiresInSeconds * 1000L)
                    preferences.setSpotifyAccessToken(refreshed.value.accessToken)
                    preferences.setSpotifyRefreshToken(refreshed.value.refreshToken)
                    preferences.setSpotifyTokenExpiryEpochMillis(expiresAt)
                    onSuccess(refreshed.value.accessToken)
                    errorMessage = null
                }

                is SpotifyServiceResult.Error -> {
                    errorMessage = refreshed.message
                }
            }
            isRequesting = false
        }
    }

    fun importSelected() {
        val selectedPlaylists = loadedPlaylists.filter { it.id in selectedPlaylistIds }
        if (selectedPlaylists.isEmpty() && !includeLikedSongs) {
            errorMessage = "No hay elementos seleccionados para importar."
            return
        }

        scope.launch {
            isRequesting = true
            errorMessage = null
            val resultMessage = withContext(Dispatchers.IO) {
                val existingNames = database.allPlaylists().first()
                    .map { it.name.lowercase().trim() }
                    .toMutableSet()

                var importedCount = 0
                var skippedCount = 0

                selectedPlaylists.forEach { playlist ->
                    val normalizedName = playlist.name.lowercase().trim()
                    if (normalizedName in existingNames) {
                        skippedCount++
                    } else {
                        database.insertPlaylist(
                            PlaylistEntity(
                                name = playlist.name,
                                thumbnailUrl = playlist.imageUrl,
                                isEditable = true,
                            )
                        )
                        existingNames.add(normalizedName)
                        importedCount++
                    }
                }

                if (includeLikedSongs) {
                    val normalizedLiked = likedSongsLabel.lowercase().trim()
                    if (normalizedLiked !in existingNames) {
                        database.insertPlaylist(
                            PlaylistEntity(
                                name = likedSongsLabel,
                                isEditable = true,
                            )
                        )
                        importedCount++
                    } else {
                        skippedCount++
                    }
                }

                "Importación completada. Importadas: $importedCount, omitidas: $skippedCount."
            }
            infoMessage = resultMessage
            isRequesting = false
        }
    }

    SettingsSubScreen(
        title = stringResource("import_from_spotify"),
        onBack = onBack,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource("login_with_spotify_api_credentials"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource("client_id")) },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource("client_secret")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )

                OutlinedTextField(
                    value = authorizationCode,
                    onValueChange = { authorizationCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource("authorization_code")) },
                    minLines = 2,
                    maxLines = 3,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val cleanClientId = clientId.trim()
                            if (cleanClientId.isBlank()) {
                                errorMessage = "Ingresa Client ID antes de abrir autorización."
                            } else {
                                openUrl(DesktopSpotifyService.buildAuthorizationUrl(cleanClientId))
                            }
                        },
                    ) {
                        Text(stringResource("open_in_browser"))
                    }

                    OutlinedButton(
                        onClick = { executeLogin() },
                        enabled = !isRequesting,
                    ) {
                        Text(stringResource("login"))
                    }

                    if (isRequesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (spotifyAccessToken.isNotBlank()) {
            if (loadedPlaylists.isEmpty() && !isRequesting) {
                TextButton(
                    onClick = {
                        tryRefreshAccessToken { token -> loadSpotifyData(token, append = false) }
                    },
                ) {
                    Text(stringResource("refresh"))
                }
            }

            val userLabel = spotifyUserName.ifBlank { "Spotify" }
            Text(
                text = stringResource("logged_in_as", userLabel, totalPlaylists),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        selectedPlaylistIds = if (selectedPlaylistIds.size == loadedPlaylists.size) {
                            emptySet()
                        } else {
                            loadedPlaylists.map { it.id }.toSet()
                        }
                    },
                    enabled = loadedPlaylists.isNotEmpty(),
                ) {
                    val allSelected = loadedPlaylists.isNotEmpty() &&
                        selectedPlaylistIds.size == loadedPlaylists.size
                    Text(stringResource(if (allSelected) "deselect_all" else "select_all"))
                }

                OutlinedButton(
                    onClick = {
                        tryRefreshAccessToken { token -> loadSpotifyData(token, append = false) }
                    },
                    enabled = !isRequesting,
                ) {
                    Text(stringResource("refresh"))
                }

                if (hasMore) {
                    OutlinedButton(
                        onClick = {
                            tryRefreshAccessToken { token -> loadSpotifyData(token, append = true) }
                        },
                        enabled = !isRequesting,
                    ) {
                        Text(stringResource("more"))
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeLikedSongs = !includeLikedSongs }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconAssets.favorite(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = stringResource("liked_songs"),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Checkbox(
                        checked = includeLikedSongs,
                        onCheckedChange = { includeLikedSongs = it },
                    )
                }
            }

            loadedPlaylists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlaylistIds = if (selected) {
                                    selectedPlaylistIds - playlist.id
                                } else {
                                    selectedPlaylistIds + playlist.id
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!playlist.imageUrl.isNullOrBlank()) {
                                RemoteImage(
                                    url = playlist.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(imageVector = IconAssets.spotify(), contentDescription = null)
                                }
                            }

                            Text(
                                text = playlist.name,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                selectedPlaylistIds = if (it) {
                                    selectedPlaylistIds + playlist.id
                                } else {
                                    selectedPlaylistIds - playlist.id
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { importSelected() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRequesting,
            ) {
                Text(stringResource("import_from_spotify"))
            }

            TextButton(
                onClick = {
                    preferences.setSpotifyAccessToken("")
                    preferences.setSpotifyRefreshToken("")
                    preferences.setSpotifyUserName("")
                    preferences.setSpotifyTokenExpiryEpochMillis(0L)
                    loadedPlaylists = emptyList()
                    selectedPlaylistIds = emptySet()
                    includeLikedSongs = false
                    totalPlaylists = 0
                    hasMore = false
                    nextOffset = 0
                    infoMessage = null
                },
            ) {
                Text(stringResource("logout"))
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!infoMessage.isNullOrBlank()) {
            Text(
                text = infoMessage ?: "",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
