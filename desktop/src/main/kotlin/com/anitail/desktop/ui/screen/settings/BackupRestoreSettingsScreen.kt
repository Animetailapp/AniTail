package com.anitail.desktop.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopBackupRestoreService
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
internal fun BackupRestoreSettingsScreen(
    database: DesktopDatabase,
    preferences: DesktopPreferences,
    authService: DesktopAuthService,
    onAuthChanged: (AuthCredentials?) -> Unit,
    onOpenAutoBackup: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backupService = remember(authService, preferences) {
        DesktopBackupRestoreService(
            preferences = preferences,
            authService = authService,
        )
    }

    val backupLabel = stringResource("backup")
    val backupDescription = stringResource("backup_description")
    val backupSuccessLabel = stringResource("backup_create_success")
    val backupFailedLabel = stringResource("backup_create_failed")
    val restoreLabel = stringResource("restore")
    val restoreDescription = stringResource("restore_description")
    val restoreFailedLabel = stringResource("restore_failed")
    val confirmLabel = stringResource("confirm")
    val cancelLabel = stringResource("cancel")
    val pleaseWaitLabel = stringResource("please_wait")
    val importOnlineLabel = stringResource("import_online")
    val importCsvLabel = stringResource("import_csv")
    val importOnlineDescription = stringResource("import_online_description")
    val importCsvDescription = stringResource("import_csv_description")

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePath by remember { mutableStateOf<Path?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf("") }
    var operationError by remember { mutableStateOf<String?>(null) }
    var operationSuccess by remember { mutableStateOf<String?>(null) }

    var uploadStatus by remember { mutableStateOf<UploadStatus?>(null) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    val enableBackupUpload by preferences.enableBackupUpload.collectAsState()

    SettingsSubScreen(
        title = stringResource("backup_restore"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("backup_restore"))
        BackupInfoCard()

        SettingsSectionTitle(title = stringResource("options"))
        SettingsButton(
            title = stringResource("auto_backup_settings"),
            subtitle = stringResource("auto_backup_settings_desc"),
            onClick = onOpenAutoBackup,
            icon = IconAssets.refresh(),
        )
        SettingsSwitch(
            title = stringResource("enable_backup_upload"),
            subtitle = stringResource("enable_backup_upload_desc"),
            checked = enableBackupUpload,
            onCheckedChange = { checked ->
                preferences.setEnableBackupUpload(checked)
            },
        )

        SettingsSectionTitle(title = stringResource("actions"))
        SettingsButton(
            title = backupLabel,
            subtitle = backupDescription,
            onClick = {
                val output = chooseBackupSavePath() ?: return@SettingsButton
                scope.launch {
                    operationInProgress = true
                    operationLabel = backupLabel
                    operationError = null
                    operationSuccess = null
                    uploadStatus = null
                    try {
                        val report = withContext(Dispatchers.IO) {
                            backupService.backupTo(output)
                        }
                        operationSuccess = if (report.includesAccounts) {
                            "$backupSuccessLabel (${report.backupFile.fileName})"
                        } else {
                            "$backupSuccessLabel (${report.backupFile.fileName}, no accounts.json)"
                        }

                        if (enableBackupUpload) {
                            uploadStatus = UploadStatus.Uploading
                            uploadProgress = 0f
                            val uploadedUrl = withContext(Dispatchers.IO) {
                                uploadBackupToFilebin(output) { progress ->
                                    scope.launch {
                                        uploadProgress = progress.coerceIn(0f, 1f)
                                    }
                                }
                            }
                            uploadStatus = if (uploadedUrl != null) {
                                UploadStatus.Success(uploadedUrl)
                            } else {
                                UploadStatus.Failure
                            }
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Throwable) {
                        operationError = "$backupFailedLabel: ${error.message.orEmpty()}"
                    } finally {
                        operationInProgress = false
                    }
                }
            },
            icon = IconAssets.download(),
        )
        SettingsButton(
            title = restoreLabel,
            subtitle = restoreDescription,
            onClick = {
                val selected = chooseBackupOpenPath() ?: return@SettingsButton
                restorePath = selected
                showRestoreDialog = true
            },
            icon = IconAssets.windowRestore(),
        )
        SettingsButton(
            title = importOnlineLabel,
            subtitle = importOnlineDescription,
            onClick = {
                val selected = chooseImportPlaylistPath("m3u") ?: return@SettingsButton
                scope.launch {
                    operationInProgress = true
                    operationLabel = importOnlineLabel
                    operationError = null
                    operationSuccess = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            importPlaylistFromFile(
                                sourceFile = selected,
                                database = database,
                                preferredExtension = "m3u",
                            )
                        }
                    }.onSuccess { message ->
                        operationSuccess = message
                    }.onFailure { error ->
                        operationError = "${error.message.orEmpty()}."
                    }
                    operationInProgress = false
                }
            },
            icon = IconAssets.add(),
        )
        SettingsButton(
            title = importCsvLabel,
            subtitle = importCsvDescription,
            onClick = {
                val selected = chooseImportPlaylistPath("csv") ?: return@SettingsButton
                scope.launch {
                    operationInProgress = true
                    operationLabel = importCsvLabel
                    operationError = null
                    operationSuccess = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            importPlaylistFromFile(
                                sourceFile = selected,
                                database = database,
                                preferredExtension = "csv",
                            )
                        }
                    }.onSuccess { message ->
                        operationSuccess = message
                    }.onFailure { error ->
                        operationError = "${error.message.orEmpty()}."
                    }
                    operationInProgress = false
                }
            },
            icon = IconAssets.add(),
        )

        if (uploadStatus != null) {
            SettingsSectionTitle(title = stringResource("backup_link_title"))
            UploadStatusSection(
                uploadStatus = uploadStatus,
                uploadProgress = uploadProgress,
                onCopyClick = {
                    val fileUrl = (uploadStatus as? UploadStatus.Success)?.fileUrl ?: return@UploadStatusSection
                    copyToClipboard(fileUrl)
                },
            )
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(restoreLabel) },
            text = {
                Text(
                    "$restoreDescription\n\n${restorePath?.fileName?.toString().orEmpty()}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = restorePath
                        showRestoreDialog = false
                        if (selected == null) return@TextButton
                        scope.launch {
                            operationInProgress = true
                            operationLabel = restoreLabel
                            operationError = null
                            operationSuccess = null
                            try {
                                val report = withContext(Dispatchers.IO) {
                                    backupService.restoreFrom(selected)
                                }
                                onAuthChanged(authService.credentials)
                                operationSuccess = buildString {
                                    append("Restore completed successfully.")
                                    if (report.warnings.isNotEmpty()) {
                                        append("\n")
                                        append(report.warnings.joinToString(" "))
                                    }
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Throwable) {
                                operationError = "$restoreFailedLabel: ${error.message.orEmpty()}"
                            } finally {
                                operationInProgress = false
                            }
                        }
                    },
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(cancelLabel)
                }
            },
        )
    }

    if (operationInProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(operationLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(pleaseWaitLabel)
                }
            },
            confirmButton = {},
        )
    }

    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            title = { Text("Error") },
            text = { Text(operationError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { operationError = null }) {
                    Text(confirmLabel)
                }
            },
        )
    }

    if (operationSuccess != null) {
        AlertDialog(
            onDismissRequest = { operationSuccess = null },
            title = { Text("Success") },
            text = { Text(operationSuccess.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { operationSuccess = null }) {
                    Text(confirmLabel)
                }
            },
        )
    }
}

@Composable
private fun BackupInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource("backup_restore"),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource("backup_info_description"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadStatusSection(
    uploadStatus: UploadStatus?,
    uploadProgress: Float,
    onCopyClick: () -> Unit,
) {
    when (uploadStatus) {
        UploadStatus.Uploading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(stringResource("uploading_backup"))
                    }
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource("upload_progress_percentage", (uploadProgress * 100).toInt()))
                }
            }
        }

        is UploadStatus.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource("backup_link_description"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = uploadStatus.fileUrl,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = onCopyClick) {
                                Icon(IconAssets.link(), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(stringResource("copy_link"))
                            }
                        }
                    }
                }
            }
        }

        UploadStatus.Failure -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = IconAssets.error(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource("upload_error"),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        null -> Unit
    }
}

private fun chooseBackupSavePath(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select backup destination"
        fileSelectionMode = JFileChooser.FILES_ONLY
        selectedFile = File(System.getProperty("user.home") ?: ".", "AniTail_Backup.backup")
        fileFilter = FileNameExtensionFilter("AniTail backup (*.backup, *.zip)", "backup", "zip")
    }
    val result = chooser.showSaveDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    var selected = chooser.selectedFile ?: return null
    if (!selected.name.contains('.')) {
        selected = File(selected.parentFile ?: File("."), "${selected.name}.backup")
    }
    return selected.toPath()
}

private fun chooseBackupOpenPath(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select backup file"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("AniTail backup (*.backup, *.zip)", "backup", "zip")
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.toPath()
}

private fun chooseImportPlaylistPath(preferredExtension: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select playlist file"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = if (preferredExtension.equals("csv", ignoreCase = true)) {
            FileNameExtensionFilter("Playlist CSV (*.csv)", "csv")
        } else {
            FileNameExtensionFilter("Playlist M3U (*.m3u)", "m3u")
        }
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.toPath()
}

private data class ParsedImportTrack(
    val title: String,
    val artist: String,
)

private suspend fun importPlaylistFromFile(
    sourceFile: Path,
    database: DesktopDatabase,
    preferredExtension: String,
): String {
    if (!Files.exists(sourceFile)) {
        error("Playlist file not found.")
    }

    val extension = sourceFile.fileName.toString().substringAfterLast('.', "").lowercase()
    val normalized = if (extension.isBlank()) preferredExtension.lowercase() else extension
    val tracks = when (normalized) {
        "m3u" -> parseM3uTracks(sourceFile)
        "csv" -> parseCsvTracks(sourceFile)
        else -> error("Unsupported playlist format: .$normalized")
    }

    if (tracks.isEmpty()) {
        error("No songs found in file.")
    }

    val playlistName = sourceFile.fileName.toString().substringBeforeLast('.').ifBlank {
        "Imported Playlist"
    }
    val playlist = PlaylistEntity(name = playlistName)
    database.insertPlaylist(playlist)

    var importedSongs = 0
    tracks.forEach { track ->
        val query = buildString {
            append(track.title)
            if (track.artist.isNotBlank()) {
                append(" ")
                append(track.artist)
            }
        }
        val item = YouTube.search(query, YouTube.SearchFilter("songs"))
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.firstOrNull()
            ?: return@forEach

        database.insertSong(item.toSongEntity(inLibrary = true), item.toSongArtistMaps())
        database.addSongToPlaylist(playlist.id, item.id)
        importedSongs++
    }

    if (importedSongs == 0) {
        error("No song matches were found online.")
    }

    return "Imported $importedSongs/${tracks.size} songs into \"$playlistName\"."
}

private fun parseM3uTracks(filePath: Path): List<ParsedImportTrack> {
    val lines = Files.readAllLines(filePath)
    if (lines.isEmpty()) return emptyList()
    return lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (!trimmed.startsWith("#EXTINF:", ignoreCase = true)) return@mapNotNull null
        val metadata = trimmed.substringAfter(',', "").trim()
        if (metadata.isBlank()) return@mapNotNull null

        val separatorIndex = metadata.indexOf(" - ")
        if (separatorIndex > 0) {
            ParsedImportTrack(
                title = metadata.substring(separatorIndex + 3).trim(),
                artist = metadata.substring(0, separatorIndex).trim(),
            )
        } else {
            ParsedImportTrack(
                title = metadata,
                artist = "",
            )
        }
    }
}

private fun parseCsvTracks(filePath: Path): List<ParsedImportTrack> {
    val lines = Files.readAllLines(filePath)
    if (lines.isEmpty()) return emptyList()
    return lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        if (trimmed.startsWith("title,", ignoreCase = true)) return@mapNotNull null

        val parts = trimmed.split(',').map { it.trim() }
        if (parts.isEmpty()) return@mapNotNull null
        val title = parts.firstOrNull().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val artist = parts.getOrNull(1).orEmpty()
        ParsedImportTrack(
            title = title,
            artist = artist,
        )
    }
}

private fun uploadBackupToFilebin(
    backupPath: Path,
    onProgress: (Float) -> Unit,
): String? {
    if (!Files.exists(backupPath)) return null
    val fileSize = Files.size(backupPath)
    if (fileSize <= 0L) return null

    val binId = UUID.randomUUID().toString().substring(0, 8)
    val fileName = backupPath.fileName.toString()
    val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
    val endpoint = URI("https://filebin.net/$binId/$encodedFileName").toURL()

    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "PUT"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 60_000
        setRequestProperty("Content-Type", "application/octet-stream")
        setFixedLengthStreamingMode(fileSize)
    }

    return runCatching {
        connection.outputStream.use { output ->
            Files.newInputStream(backupPath).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                var bytesRead = input.read(buffer)
                while (bytesRead >= 0) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress(totalRead.toFloat() / fileSize.toFloat())
                    bytesRead = input.read(buffer)
                }
                output.flush()
            }
        }

        val code = connection.responseCode
        if (code in 200..299) {
            "https://filebin.net/$binId/$fileName"
        } else {
            null
        }
    }.getOrNull().also {
        connection.disconnect()
    }
}

private fun copyToClipboard(text: String) {
    runCatching {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}

private sealed interface UploadStatus {
    data object Uploading : UploadStatus

    data class Success(val fileUrl: String) : UploadStatus

    data object Failure : UploadStatus
}
