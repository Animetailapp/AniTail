package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun StorageSettingsScreen(
    preferences: DesktopPreferences,
    downloadService: DesktopDownloadService,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val maxDownloadSizeMB by preferences.maxDownloadSizeMB.collectAsState()
    val maxSongCacheSizeMB by preferences.maxSongCacheSizeMB.collectAsState()
    val maxImageCacheSizeMB by preferences.maxImageCacheSizeMB.collectAsState()

    var downloadedSongsSize by remember { mutableStateOf(0L) }
    var songCacheSize by remember { mutableStateOf(0L) }
    var imageCacheSize by remember { mutableStateOf(0L) }

    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showClearSongCacheDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }

    fun refreshStorage() {
        scope.launch {
            downloadedSongsSize = withContext(Dispatchers.IO) {
                downloadService.getTotalDownloadSize()
            }
            songCacheSize = withContext(Dispatchers.IO) {
                directorySize(resolveSongCacheDir())
            }
            ImageCache.enforceCurrentLimit()
            imageCacheSize = ImageCache.estimatedSizeBytes()
        }
    }

    LaunchedEffect(Unit) {
        refreshStorage()
    }

    SettingsSubScreen(
        title = stringResource("storage"),
        onBack = onBack,
    ) {
        val unlimitedLabel = stringResource("unlimited")
        val downloadedMaxBytes = maxDownloadSizeMB.toBytesOrUnlimited()
        val songMaxBytes = maxSongCacheSizeMB.toBytesOrUnlimited()
        val imageMaxBytes = maxImageCacheSizeMB.toBytesOrUnlimited()

        SettingsSectionTitle(title = stringResource("storage_overview"))

        StorageUsageCard(
            title = stringResource("downloaded_songs"),
            usedBytes = downloadedSongsSize,
            maxBytes = downloadedMaxBytes,
        )
        StorageUsageCard(
            title = stringResource("song_cache"),
            usedBytes = songCacheSize,
            maxBytes = songMaxBytes,
        )
        StorageUsageCard(
            title = stringResource("image_cache"),
            usedBytes = imageCacheSize,
            maxBytes = imageMaxBytes,
        )

        SettingsSectionTitle(title = stringResource("downloaded_songs"))

        SettingsDropdown(
            title = stringResource("max_cache_size"),
            subtitle = cacheLimitLabel(maxDownloadSizeMB, unlimitedLabel),
            options = DOWNLOAD_LIMIT_OPTIONS.map { cacheLimitLabel(it, unlimitedLabel) },
            selectedIndex = DOWNLOAD_LIMIT_OPTIONS.indexOf(maxDownloadSizeMB).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setMaxDownloadSizeMB(DOWNLOAD_LIMIT_OPTIONS[index])
                refreshStorage()
            },
        )

        SettingsButton(
            title = stringResource("clear_all_downloads"),
            subtitle = stringResource("clear_downloads_confirmation"),
            onClick = { showClearDownloadsDialog = true },
            icon = IconAssets.delete(),
        )

        SettingsSectionTitle(title = stringResource("song_cache"))

        SettingsDropdown(
            title = stringResource("max_cache_size"),
            subtitle = cacheLimitLabel(maxSongCacheSizeMB, unlimitedLabel),
            options = SONG_CACHE_LIMIT_OPTIONS.map { cacheLimitLabel(it, unlimitedLabel) },
            selectedIndex = SONG_CACHE_LIMIT_OPTIONS.indexOf(maxSongCacheSizeMB).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setMaxSongCacheSizeMB(SONG_CACHE_LIMIT_OPTIONS[index])
                refreshStorage()
            },
        )

        SettingsButton(
            title = stringResource("clear_song_cache"),
            subtitle = stringResource("clear_song_cache_confirmation"),
            onClick = { showClearSongCacheDialog = true },
            icon = IconAssets.delete(),
        )

        SettingsSectionTitle(title = stringResource("image_cache"))

        SettingsDropdown(
            title = stringResource("max_cache_size"),
            subtitle = cacheLimitLabel(maxImageCacheSizeMB, unlimitedLabel),
            options = IMAGE_CACHE_LIMIT_OPTIONS.map { cacheLimitLabel(it, unlimitedLabel) },
            selectedIndex = IMAGE_CACHE_LIMIT_OPTIONS.indexOf(maxImageCacheSizeMB).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setMaxImageCacheSizeMB(IMAGE_CACHE_LIMIT_OPTIONS[index])
                ImageCache.enforceCurrentLimit()
                refreshStorage()
            },
        )

        SettingsButton(
            title = stringResource("clear_image_cache"),
            subtitle = stringResource("clear_image_cache_confirmation"),
            onClick = { showClearImageCacheDialog = true },
            icon = IconAssets.delete(),
        )
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource("clear_all_downloads")) },
            text = { Text(stringResource("clear_downloads_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDownloadsDialog = false
                        scope.launch(Dispatchers.IO) {
                            downloadService.clearAllDownloads()
                            refreshStorage()
                        }
                    },
                ) {
                    Text(stringResource("confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }

    if (showClearSongCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearSongCacheDialog = false },
            title = { Text(stringResource("clear_song_cache")) },
            text = { Text(stringResource("clear_song_cache_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSongCacheDialog = false
                        scope.launch(Dispatchers.IO) {
                            clearDirectoryContents(resolveSongCacheDir())
                            refreshStorage()
                        }
                    },
                ) {
                    Text(stringResource("confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSongCacheDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }

    if (showClearImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheDialog = false },
            title = { Text(stringResource("clear_image_cache")) },
            text = { Text(stringResource("clear_image_cache_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearImageCacheDialog = false
                        ImageCache.clear()
                        refreshStorage()
                    },
                ) {
                    Text(stringResource("confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }
}

@Composable
private fun StorageUsageCard(
    title: String,
    usedBytes: Long,
    maxBytes: Long?,
) {
    val progress = calculateProgress(usedBytes = usedBytes, maxBytes = maxBytes)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource("size_used", formatFileSize(usedBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = maxBytes?.let { formatFileSize(it) } ?: stringResource("unlimited"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

private fun calculateProgress(usedBytes: Long, maxBytes: Long?): Float {
    if (maxBytes == null || maxBytes <= 0L) return 0f
    return (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
}

private fun Int.toBytesOrUnlimited(): Long? {
    if (this < 0) return null
    return this.toLong() * 1024L * 1024L
}

private fun cacheLimitLabel(limitMb: Int, unlimitedLabel: String): String {
    return if (limitMb < 0) {
        unlimitedLabel
    } else {
        formatFileSize(limitMb.toLong() * 1024L * 1024L)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun resolveSongCacheDir(): File {
    return File(System.getProperty("user.home") ?: ".", ".anitail/cache/song")
}

private fun clearDirectoryContents(directory: File) {
    if (!directory.exists()) return
    directory.listFiles()?.forEach { item ->
        if (item.isDirectory) item.deleteRecursively() else item.delete()
    }
}

private fun directorySize(directory: File): Long {
    if (!directory.exists()) return 0L
    return directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}

private val DOWNLOAD_LIMIT_OPTIONS = listOf(512, 1024, 2048, 4096, 8192, -1)
private val SONG_CACHE_LIMIT_OPTIONS = listOf(128, 256, 512, 1024, 2048, 4096, 8192, -1)
private val IMAGE_CACHE_LIMIT_OPTIONS = listOf(128, 256, 512, 1024, 2048, 4096, 8192)
