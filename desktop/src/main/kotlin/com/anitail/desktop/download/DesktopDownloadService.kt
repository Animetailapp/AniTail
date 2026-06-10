package com.anitail.desktop.download

import com.anitail.desktop.YouTube
import com.anitail.innertube.NewPipeExtractor
import com.anitail.innertube.models.YouTubeClient
import com.anitail.desktop.player.StreamClientOrder
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DesktopPreferences
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Estado de una descarga individual.
 */
@Serializable
data class DownloadState(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String?,
    val errorMessage: String? = null,
)

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED,
}

/**
 * Información de canción descargada almacenada.
 */
@Serializable
data class DownloadedSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnailUrl: String?,
    val duration: Int,
    val filePath: String,
    val fileSize: Long,
    val bitrate: Int?,
    val downloadedAt: Long,
)

/**
 * Servicio de descargas para Desktop.
 * Descarga streams de audio de YouTube Music a archivos locales.
 */
class DesktopDownloadService {
    companion object {
        internal fun resolveMusicDownloadDir(homeDir: String = System.getProperty("user.home") ?: "."): File {
            val musicDir = File(homeDir, "Music")
            return File(musicDir, "Anitail")
        }

        private val downloadDir = resolveMusicDownloadDir()
        private val metadataFile = File(System.getProperty("user.home"), ".anitail/downloads.json")
        
        private val json = Json { 
            prettyPrint = true 
            ignoreUnknownKeys = true
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0 // Sin timeout para descargas largas
        }
    }
    private val preferences = DesktopPreferences.getInstance()
    
    // Estados de descargas activas
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    
    // Canciones ya descargadas
    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedSong>> = _downloadedSongs
    
    // Jobs activos (para cancelación)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    init {
        downloadDir.mkdirs()
        loadDownloadedSongs()
    }
    
    private fun loadDownloadedSongs() {
        if (metadataFile.exists()) {
            try {
                val content = metadataFile.readText()
                _downloadedSongs.value = json.decodeFromString(content)
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadedSongs.value = emptyList()
            }
        }
    }
    
    private fun saveDownloadedSongs() {
        try {
            metadataFile.parentFile?.mkdirs()
            metadataFile.writeText(json.encodeToString(_downloadedSongs.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Verifica si una canción ya está descargada.
     */
    fun isDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.songId == songId }
    }
    
    /**
     * Obtiene la canción descargada por su ID.
     */
    fun getDownloadedSong(songId: String): DownloadedSong? {
        return _downloadedSongs.value.find { it.songId == songId }
    }
    
    /**
     * Inicia la descarga de una canción.
     */
    fun downloadSong(
        songId: String,
        title: String,
        artist: String,
        album: String? = null,
        thumbnailUrl: String? = null,
        duration: Int = 0,
    ) {
        // Si ya está descargada, no hacer nada
        if (isDownloaded(songId)) return
        
        // Si ya está en cola o descargando, no hacer nada
        val currentState = _downloadStates.value[songId]
        if (currentState != null && 
            currentState.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) {
            return
        }
        
        // Añadir a cola
        updateDownloadState(
            DownloadState(
                songId = songId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = 0,
                filePath = null,
            )
        )
        
        // Iniciar descarga
        val job = scope.launch {
            try {
                performDownload(songId, title, artist, album, thumbnailUrl, duration)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                updateDownloadState(
                    _downloadStates.value[songId]?.copy(status = DownloadStatus.CANCELLED)
                        ?: return@launch
                )
            } catch (e: Exception) {
                e.printStackTrace()
                updateDownloadState(
                    _downloadStates.value[songId]?.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Error desconocido",
                    ) ?: return@launch
                )
            }
        }
        
        activeJobs[songId] = job
    }
    
    private suspend fun performDownload(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        thumbnailUrl: String?,
        duration: Int,
    ) {
        updateDownloadState(
            _downloadStates.value[songId]?.copy(status = DownloadStatus.DOWNLOADING) ?: return
        )
        
        // Obtener stream URL con fallback entre clientes
        val resolvedStream = resolveStreamForDownload(songId)
        val streamUrl = resolvedStream?.url
        if (streamUrl == null) {
            throw Exception("No se pudo obtener URL del stream")
        }

        val resolvedFormat = resolvedStream?.format ?: resolveBestAudioFormatFromRemix(songId)
        val totalBytes = resolvedFormat?.contentLength ?: 0L
        val extension = when {
            resolvedFormat?.mimeType?.contains("webm") == true -> "webm"
            resolvedFormat?.mimeType?.contains("mp4") == true -> "m4a"
            else -> "opus"
        }
        val maxDownloadBytes = resolveMaxDownloadBytes()
        val existingUsedBytes = getTotalDownloadSize()
        if (maxDownloadBytes != null && totalBytes > 0L && (existingUsedBytes + totalBytes) > maxDownloadBytes) {
            throw Exception("Se alcanzó el límite máximo de descargas configurado.")
        }
        
        // Crear nombre de archivo seguro
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${safeArtist} - ${safeTitle} [$songId].$extension"
        val outputFile = File(downloadDir, fileName)
        val downloadAsMp3 = preferences.downloadAsMp3.value
        
        updateDownloadState(
            _downloadStates.value[songId]?.copy(
                totalBytes = totalBytes,
                filePath = outputFile.absolutePath,
            ) ?: return
        )
        
        // Descargar archivo
        try {
            httpClient.prepareGet(streamUrl).execute { response ->
                val channel = response.bodyAsChannel()
                val file = RandomAccessFile(outputFile, "rw")

                try {
                    var downloadedBytes = 0L
                    val buffer = ByteArray(8192)

                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead <= 0) break

                        file.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (maxDownloadBytes != null && (existingUsedBytes + downloadedBytes) > maxDownloadBytes) {
                            throw Exception("Se alcanzó el límite máximo de descargas configurado.")
                        }

                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        updateDownloadState(
                            _downloadStates.value[songId]?.copy(
                                downloadedBytes = downloadedBytes,
                                progress = progress.coerceIn(0f, 1f),
                            ) ?: return@execute
                        )
                    }
                } finally {
                    file.close()
                }
            }
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
        
        val finalFile = if (downloadAsMp3) {
            val targetBitrateKbps = resolveMp3BitrateKbps(preferences.audioQuality.value)
            try {
                transcodeToMp3(outputFile, songId, safeTitle, safeArtist, targetBitrateKbps)
            } catch (e: Exception) {
                outputFile.delete()
                throw e
            }
        } else {
            outputFile
        }

        if (finalFile != outputFile) {
            outputFile.delete()
            updateDownloadState(
                _downloadStates.value[songId]?.copy(
                    filePath = finalFile.absolutePath,
                ) ?: return
            )
        }

        // Descarga completada
        val downloadedSong = DownloadedSong(
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            filePath = finalFile.absolutePath,
            fileSize = finalFile.length(),
            bitrate = resolvedFormat?.bitrate,
            downloadedAt = System.currentTimeMillis(),
        )
        
        _downloadedSongs.update { it + downloadedSong }
        saveDownloadedSongs()
        
        updateDownloadState(
            _downloadStates.value[songId]?.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                downloadedBytes = finalFile.length(),
            ) ?: return
        )
        
        activeJobs.remove(songId)
    }
    
    /**
     * Cancela una descarga en progreso.
     */
    fun cancelDownload(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
        
        // Eliminar archivo parcial
        _downloadStates.value[songId]?.filePath?.let { path ->
            File(path).delete()
        }
        
        _downloadStates.update { it - songId }
    }
    
    /**
     * Pausa una descarga (simplemente cancela, reanudar descargaría desde cero).
     */
    fun pauseDownload(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
        
        updateDownloadState(
            _downloadStates.value[songId]?.copy(status = DownloadStatus.PAUSED) ?: return
        )
    }
    
    /**
     * Reanuda una descarga pausada.
     */
    fun resumeDownload(songId: String) {
        val state = _downloadStates.value[songId] ?: return
        if (state.status != DownloadStatus.PAUSED && state.status != DownloadStatus.FAILED) return
        
        // Reiniciar descarga
        downloadSong(
            songId = state.songId,
            title = state.title,
            artist = state.artist,
            thumbnailUrl = state.thumbnailUrl,
        )
    }
    
    /**
     * Reintenta una descarga fallida.
     */
    fun retryDownload(songId: String) {
        val state = _downloadStates.value[songId] ?: return
        if (state.status != DownloadStatus.FAILED) return
        
        downloadSong(
            songId = state.songId,
            title = state.title,
            artist = state.artist,
            thumbnailUrl = state.thumbnailUrl,
        )
    }
    
    /**
     * Elimina una canción descargada.
     */
    fun deleteDownload(songId: String) {
        // Cancelar si está en progreso
        cancelDownload(songId)
        
        // Eliminar archivo
        val song = _downloadedSongs.value.find { it.songId == songId }
        song?.filePath?.let { path ->
            File(path).delete()
        }
        
        // Remover de la lista
        _downloadedSongs.update { list -> list.filter { it.songId != songId } }
        saveDownloadedSongs()
        _downloadStates.update { it - songId }
    }
    
    /**
     * Obtiene la ruta del archivo de una canción descargada.
     */
    fun getLocalFilePath(songId: String): String? {
        return _downloadedSongs.value.find { it.songId == songId }?.filePath
    }

    private data class ResolvedStream(
        val url: String,
        val format: com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format,
        val client: YouTubeClient,
    )

    private suspend fun resolveStreamForDownload(videoId: String): ResolvedStream? {
        val signatureTimestamp = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
        val isLoggedIn = YouTube.cookie != null
        val clientsToTry = StreamClientOrder.build()
        val audioQuality = preferences.audioQuality.value

        for (client in clientsToTry) {
            if (client.loginRequired && !isLoggedIn) continue
            val currentTimestamp = if (client.clientName.contains("TVHTML5")) null else signatureTimestamp
            val playerResponse = YouTube.player(videoId, null, client, currentTimestamp).getOrNull()
            val streamingData = playerResponse?.streamingData
            val rawFormats = streamingData?.adaptiveFormats
                ?.filter { it.isAudio }
                ?: emptyList()
            val formats = orderFormatsByQuality(rawFormats, audioQuality)
            if (formats.isEmpty()) continue

            for (format in formats) {
                val url = NewPipeExtractor.getStreamUrl(format, videoId)
                if (url != null) {
                    return ResolvedStream(url = url, format = format, client = client)
                }
            }
        }

        return null
    }

    private suspend fun resolveBestAudioFormatFromRemix(
        videoId: String
    ): com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format? {
        val playerResponse = YouTube.player(
            videoId = videoId,
            playlistId = null,
            client = YouTubeClient.WEB_REMIX,
        ).getOrNull() ?: return null

        val streamingData = playerResponse.streamingData ?: return null
        val rawFormats = streamingData.adaptiveFormats
            .filter { it.mimeType.startsWith("audio/") }
        val orderedFormats = orderFormatsByQuality(rawFormats, preferences.audioQuality.value)
        return orderedFormats.firstOrNull()
    }

    private fun orderFormatsByQuality(
        formats: List<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format>,
        audioQuality: AudioQuality
    ): List<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format> {
        if (formats.isEmpty()) return formats
        return when (audioQuality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> formats.sortedByDescending { it.bitrate }
            AudioQuality.LOW -> formats.sortedBy { it.bitrate }
            AudioQuality.MEDIUM -> formats.sortedWith(
                compareBy<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format> {
                    abs(it.bitrate - 192_000)
                }.thenByDescending { it.bitrate }
            )
        }
    }

    private fun resolveMp3BitrateKbps(audioQuality: AudioQuality): Int {
        return when (audioQuality) {
            AudioQuality.LOW -> 128
            AudioQuality.MEDIUM -> 192
            AudioQuality.HIGH, AudioQuality.AUTO -> 320
        }
    }

    private val ffmpegAvailable: Boolean by lazy { detectFfmpeg() }

    private fun detectFfmpeg(): Boolean {
        return runCatching {
            val process = ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching false
            }
            process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun transcodeToMp3(
        inputFile: File,
        songId: String,
        safeTitle: String,
        safeArtist: String,
        targetBitrateKbps: Int,
    ): File {
        if (!ffmpegAvailable) {
            throw Exception("FFmpeg no encontrado. Instala FFmpeg para descargar en MP3.")
        }

        val outputFile = File(downloadDir, "${safeArtist} - ${safeTitle} [$songId].mp3")
        val process = ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i",
            inputFile.absolutePath,
            "-vn",
            "-acodec",
            "libmp3lame",
            "-b:a",
            "${targetBitrateKbps}k",
            outputFile.absolutePath,
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(10, TimeUnit.MINUTES)
        if (!finished || process.exitValue() != 0) {
            throw Exception("FFmpeg falló al convertir a MP3: ${output.take(500)}")
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw Exception("FFmpeg no generó archivo MP3 válido.")
        }

        return outputFile
    }

    private fun updateDownloadState(state: DownloadState) {
        _downloadStates.update { map ->
            map.toMutableMap().apply {
                this[state.songId] = state
            }
        }
    }
    
    /**
     * Obtiene el espacio total usado por las descargas.
     */
    fun getTotalDownloadSize(): Long {
        return _downloadedSongs.value.sumOf { it.fileSize }
    }

    private fun resolveMaxDownloadBytes(): Long? {
        val limitMb = preferences.maxDownloadSizeMB.value
        return if (limitMb < 0) null else limitMb.toLong() * 1024L * 1024L
    }
    
    /**
     * Elimina todas las descargas.
     */
    fun clearAllDownloads() {
        // Cancelar todas las descargas activas
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        
        // Eliminar todos los archivos
        downloadDir.listFiles()?.forEach { it.delete() }
        
        _downloadedSongs.value = emptyList()
        _downloadStates.value = emptyMap()
        saveDownloadedSongs()
    }
    
    fun close() {
        scope.cancel()
        httpClient.close()
    }
}
