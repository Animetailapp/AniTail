package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.ui.component.RemoteImage

/**
 * Pantalla de descargas - muestra descargas activas y completadas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadService: DesktopDownloadService,
    onBack: () -> Unit,
    onPlaySong: (DownloadedSong) -> Unit,
) {
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    
    val activeDownloads = downloadStates.values.filter { 
        it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (downloadedSongs.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Eliminar todo")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Activas (${activeDownloads.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Completadas (${downloadedSongs.size})") },
                )
            }
            
            when (selectedTab) {
                0 -> ActiveDownloadsTab(
                    downloads = activeDownloads,
                    failedDownloads = downloadStates.values.filter { it.status == DownloadStatus.FAILED },
                    onCancel = { downloadService.cancelDownload(it) },
                    onPause = { downloadService.pauseDownload(it) },
                    onResume = { downloadService.resumeDownload(it) },
                    onRetry = { downloadService.retryDownload(it) },
                )
                1 -> CompletedDownloadsTab(
                    songs = downloadedSongs,
                    onPlay = onPlaySong,
                    onDelete = { downloadService.deleteDownload(it) },
                )
            }
        }
    }
    
    // Diálogo confirmar eliminar todo
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Eliminar todas las descargas") },
            text = { Text("¿Estás seguro de que deseas eliminar todas las canciones descargadas? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadService.clearAllDownloads()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ActiveDownloadsTab(
    downloads: List<DownloadState>,
    failedDownloads: List<DownloadState>,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    if (downloads.isEmpty() && failedDownloads.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay descargas activas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Descargas activas
            items(downloads, key = { it.songId }) { download ->
                DownloadProgressItem(
                    download = download,
                    onCancel = { onCancel(download.songId) },
                    onPause = { onPause(download.songId) },
                    onResume = { onResume(download.songId) },
                )
            }
            
            // Descargas fallidas
            if (failedDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Fallidas",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                
                items(failedDownloads, key = { "failed_${it.songId}" }) { download ->
                    FailedDownloadItem(
                        download = download,
                        onRetry = { onRetry(download.songId) },
                        onCancel = { onCancel(download.songId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressItem(
    download: DownloadState,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (download.thumbnailUrl != null) {
                    RemoteImage(
                        url = download.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = download.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Progress bar
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status text
                val statusText = when (download.status) {
                    DownloadStatus.QUEUED -> "En cola..."
                    DownloadStatus.DOWNLOADING -> {
                        val mb = download.downloadedBytes / (1024 * 1024f)
                        val totalMb = download.totalBytes / (1024 * 1024f)
                        if (download.totalBytes > 0) {
                            String.format("%.1f / %.1f MB (%.0f%%)", mb, totalMb, download.progress * 100)
                        } else {
                            String.format("%.1f MB descargados", mb)
                        }
                    }
                    DownloadStatus.PAUSED -> "Pausada"
                    else -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            // Actions
            if (download.status == DownloadStatus.PAUSED) {
                IconButton(onClick = onResume) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reanudar")
                }
            } else if (download.status == DownloadStatus.DOWNLOADING) {
                IconButton(onClick = onPause) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pausar")
                }
            }
            
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar")
            }
        }
    }
}

@Composable
private fun FailedDownloadItem(
    download: DownloadState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                download.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reintentar")
            }
            
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Eliminar")
            }
        }
    }
}

@Composable
private fun CompletedDownloadsTab(
    songs: List<DownloadedSong>,
    onPlay: (DownloadedSong) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay canciones descargadas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Las canciones descargadas se mostrarán aquí",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // Storage info
                val totalSize = songs.sumOf { it.fileSize }
                val totalMb = totalSize / (1024 * 1024f)
                Text(
                    text = String.format("%.1f MB usados • %d canciones", totalMb, songs.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            
            items(songs, key = { it.songId }) { song ->
                DownloadedSongItem(
                    song = song,
                    onPlay = { onPlay(song) },
                    onDelete = { onDelete(song.songId) },
                )
            }
        }
    }
}

@Composable
private fun DownloadedSongItem(
    song: DownloadedSong,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (song.thumbnailUrl != null) {
                    RemoteImage(
                        url = song.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                
                // Size and duration
                val sizeMb = song.fileSize / (1024 * 1024f)
                val durationMinutes = song.duration / 60
                val durationSeconds = song.duration % 60
                Text(
                    text = String.format("%.1f MB • %d:%02d", sizeMb, durationMinutes, durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            
            // Offline indicator
            Icon(
                Icons.Filled.OfflinePin,
                contentDescription = "Disponible sin conexión",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Delete button
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar descarga") },
            text = { Text("¿Eliminar \"${song.title}\" de las descargas?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
