package com.anitail.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado global del reproductor para Desktop.
 * Usa NativeAudioPlayer (FFmpeg primario + VLCJ fallback) y VideoPlayer (FFmpeg + Skia).
 */
class PlayerState {
    var currentItem by mutableStateOf<LibraryItem?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var position by mutableStateOf(0L)
        private set

    var duration by mutableStateOf(0L)
        private set

    var shuffleEnabled by mutableStateOf(false)

    var repeatMode by mutableStateOf(RepeatMode.OFF)
        private set

    var isBuffering by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var volume by mutableStateOf(1.0)
        private set

    var tempo by mutableStateOf(1f)
        private set

    var pitchSemitone by mutableStateOf(0)
        private set

    var skipSilence by mutableStateOf(false)

    var crossfadeDurationSec by mutableStateOf(0)

    @set:JvmName("assignVideoModeState")
    var isVideoMode by mutableStateOf(false)
        private set

    var showMiniPlayer by mutableStateOf(false)
    var miniplayerFocusMode by mutableStateOf(false)
    var showVideoViewer by mutableStateOf(false)
    var isFullscreenOverlay by mutableStateOf(false)
    var showCastDialog by mutableStateOf(false)
    var isCasting by mutableStateOf(false)
    var isPlayerExpanded by mutableStateOf(false)

    fun toggleMiniPlayer() {
        showMiniPlayer = !showMiniPlayer
    }

    fun toggleVideoViewer() {
        showVideoViewer = !showVideoViewer
        if (showVideoViewer && !isVideoMode) {
            setVideoMode(true)
        }
    }

    fun toggleFullscreenOverlay() {
        isFullscreenOverlay = !isFullscreenOverlay
    }

    var onPlaybackEvent: ((LibraryItem, Long) -> Unit)? = null
    var onQueueEnded: ((LibraryItem) -> Unit)? = null
    var onPlaybackErrorEvent: ((LibraryItem, String) -> Unit)? = null

    private var lastReportedItemId: String? = null
    private var lastReportedPosition: Long = 0L

    private var sleepTimerState by mutableStateOf(inactiveSleepTimerState())
    private var sleepTimerJob: Job? = null
    private var sleepTimerTick by mutableStateOf(0L)
    private var positionTrackerJob: Job? = null
    private var playJob: Job? = null
    private var videoLoadJob: Job? = null
    private var audioRestoreJob: Job? = null
    private var videoLoadGeneration = 0L
    private var currentAudioSwitchedForVideo = false
    private var crossfadeTriggeredForCurrentTrack = false
    private var prematureEndRecoveryCount = 0
    private var videoStreamRefreshCount = 0
    private var lastVideoStartTimeMs = 0L

    // Cola de reproducción
    private val _queue = mutableStateListOf<LibraryItem>()
    val queue: List<LibraryItem> get() = _queue

    var currentQueueIndex by mutableStateOf(-1)
        private set

    // Reproductor nativo de audio y video
    private val preferences = DesktopPreferences.getInstance()
    private val nativePlayer = NativeAudioPlayer()
    val videoPlayer: VideoPlayer = VideoPlayer().apply {
        audioPtsProvider = { nativePlayer.getCurrentAudioPtsMs() }
        onStreamExpired = { status ->
            scope.launch {
                val item = currentItem ?: return@launch
                val videoId = extractVideoId(item.playbackUrl) ?: return@launch
                if (!isVideoMode || videoStreamRefreshCount >= 5) return@launch
                val elapsedSinceStart = System.currentTimeMillis() - lastVideoStartTimeMs
                if (videoStreamRefreshCount > 0 && elapsedSinceStart < 5_000L) {
                    DesktopInnerTubeXPlayer.markStreamClientFailed(videoId, "WEB_EMBEDDED_PLAYER")
                }
                videoStreamRefreshCount++
                println("Anitail VideoPlayer: stream expired (HTTP $status), refreshing video (attempt $videoStreamRefreshCount)")
                startVideoForCurrentItem(item, videoId, preserveExistingAudio = true)
            }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        skipSilence = preferences.skipSilence.value
        nativePlayer.setSkipSilence(skipSilence)
        crossfadeDurationSec = preferences.crossfadeDuration.value.coerceIn(0, 12)
        nativePlayer.setCrossfadeDuration(crossfadeDurationSec)
        setupNativePlayerCallbacks()
        applyPlaybackRate()
        observePlaybackPreferences()
    }

    private fun observePlaybackPreferences() {
        scope.launch {
            preferences.skipSilence.collect { enabled ->
                if (skipSilence != enabled) {
                    skipSilence = enabled
                }
                nativePlayer.setSkipSilence(enabled)
            }
        }
        scope.launch {
            preferences.crossfadeDuration.collect { seconds ->
                val clamped = seconds.coerceIn(0, 12)
                if (crossfadeDurationSec != clamped) {
                    crossfadeDurationSec = clamped
                }
                nativePlayer.setCrossfadeDuration(clamped)
            }
        }
    }

    private fun setupNativePlayerCallbacks() {
        nativePlayer.onStatusChanged = { status ->
            when (status) {
                NativeAudioPlayer.PlaybackStatus.PLAYING -> {
                    isPlaying = true
                    isBuffering = false
                    errorMessage = null
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, true)
                    }
                    startPositionTracker()
                }
                NativeAudioPlayer.PlaybackStatus.PAUSED -> {
                    isPlaying = false
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, false)
                    }
                    stopPositionTracker()
                }
                NativeAudioPlayer.PlaybackStatus.BUFFERING -> {
                    isBuffering = true
                }
                NativeAudioPlayer.PlaybackStatus.ENDED -> {
                    isPlaying = false
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, false)
                    }
                    stopPositionTracker()
                    handlePlaybackEnded()
                }
                NativeAudioPlayer.PlaybackStatus.ERROR -> {
                    isPlaying = false
                    isBuffering = false
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, false)
                    }
                    stopPositionTracker()
                }
                NativeAudioPlayer.PlaybackStatus.STOPPED -> {
                    isPlaying = false
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, false)
                    }
                    stopPositionTracker()
                }
                else -> { /* Ignorar otros estados */ }
            }
        }

        nativePlayer.onTimeChanged = { currentMs, totalMs ->
            position = currentMs
            if (totalMs > 0) {
                duration = totalMs
            }
        }

        nativePlayer.onError = { error ->
            handlePlaybackError(IllegalStateException(error))
        }

        nativePlayer.onBuffering = { buffering ->
            isBuffering = buffering
        }
    }

    private fun handlePlaybackEnded() {
        val item = currentItem
        val videoId = item?.let { extractVideoId(it.playbackUrl) }
        val endedPrematurely =
            item != null &&
                videoId != null &&
                duration > 20_000L &&
                position in 1_000L until (duration - 10_000L) &&
                prematureEndRecoveryCount < 2

        if (endedPrematurely) {
            prematureEndRecoveryCount++
            val resumePosition = position
            println("Anitail WARN: Stream ended prematurely at ${resumePosition}ms / ${duration}ms for ${item.title}; recovering...")
            playJob?.cancel()
            playJob = scope.launch {
                isBuffering = true
                val result = nativePlayer.playAtPosition(videoId, resumePosition, true)
                if (result.isSuccess && isVideoMode && currentItem?.id == item.id) {
                    videoPlayer.syncPosition(resumePosition, true)
                }
                isBuffering = false
            }
            return
        }

        val shouldRecord = repeatMode == RepeatMode.ONE || !canSkipNext
        if (shouldRecord) {
            reportPlaybackIfNeeded(force = true)
        }
        when (repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                nativePlayer.resume()
                if (isVideoMode) {
                    videoPlayer.syncPosition(0L, true)
                }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                if (canSkipNext) {
                    skipToNext()
                } else {
                    position = 0L
                    currentItem?.let { endedItem ->
                        onQueueEnded?.invoke(endedItem)
                    }
                }
            }
        }
    }

    val canSkipPrevious: Boolean
        get() = currentQueueIndex > 0

    val canSkipNext: Boolean
        get() = currentQueueIndex < _queue.size - 1 || repeatMode == RepeatMode.ALL

    val hasVideoStream: Boolean
        get() = currentItem?.let { extractVideoId(it.playbackUrl) != null } == true

    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val isSleepTimerActive: Boolean
        get() = sleepTimerState.isActive

    val sleepTimerTimeLeftMs: Long
        get() = computeSleepTimerTimeLeftMs(sleepTimerState, sleepTimerTick)

    fun play(item: LibraryItem) {
        if (currentItem?.id != item.id) {
            reportPlaybackIfNeeded()
        }
        playJob?.cancel()
        audioRestoreJob?.cancel()
        audioRestoreJob = null
        videoLoadJob?.cancel()
        videoLoadJob = null
        videoLoadGeneration++
        currentAudioSwitchedForVideo = false
        videoPlayer.stop()
        crossfadeTriggeredForCurrentTrack = false
        prematureEndRecoveryCount = 0

        currentItem = item
        position = 0L
        duration = item.durationMs ?: 180_000L
        isBuffering = true
        errorMessage = null

        // Agregar a cola si no existe
        if (_queue.none { it.id == item.id }) {
            _queue.add(item)
        }
        currentQueueIndex = _queue.indexOfFirst { it.id == item.id }

        // Extraer videoId para intentar reproducción nativa vía extracción de NativeAudioPlayer
        val videoId = extractVideoId(item.playbackUrl)
        if (videoId == null && isVideoMode) {
            isVideoMode = false
        }
        println("Anitail DEBUG: Play called for item: ${item.title}, videoId: $videoId")

        playJob = scope.launch {
            val result = if (videoId != null) {
                isBuffering = true
                nativePlayer.play(videoId)
            } else {
                nativePlayer.playStream(item.playbackUrl)
            }
            if (!isActive || currentItem?.id != item.id) return@launch
            result.onSuccess {
                if (isVideoMode && videoId != null && currentItem?.id == item.id) {
                    startVideoForCurrentItem(item, videoId)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                handlePlaybackError(error)
            }
        }
    }

    private fun handlePlaybackError(error: Throwable) {
        errorMessage = error.message ?: "Error al reproducir"
        isPlaying = false
        isBuffering = false
        stopPositionTracker()
        currentItem?.let { failedItem ->
            onPlaybackErrorEvent?.invoke(failedItem, errorMessage.orEmpty())
        }
        println("Anitail ERROR: Playback failed: ${error.message}")
    }

    private fun extractVideoId(url: String): String? {
        // Patrones comunes de YouTube
        val patterns = listOf(
            Regex("watch\\?v=([^&]+)"),
            Regex("youtu.be/([^?]+)"),
            Regex("/watch/([^?]+)"),
            Regex("v=([^&]+)"),
        )
        
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        
        // Si la URL parece ser solo un ID de video
        if (url.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return url
        }
        
        return null
    }

    fun playQueue(
        items: List<LibraryItem>,
        startIndex: Int = 0,
        preserveCurrentPlayback: Boolean = false,
    ) {
        _queue.clear()
        _queue.addAll(items)
        if (items.isNotEmpty() && startIndex in items.indices) {
            currentQueueIndex = startIndex
            val startItem = items[startIndex]
            val keepCurrent = preserveCurrentPlayback && currentItem?.id == startItem.id
            if (!keepCurrent) {
                play(startItem)
            }
        }
    }

    fun addToQueue(item: LibraryItem, playNext: Boolean = false) {
        if (_queue.none { it.id == item.id }) {
            if (playNext) {
                val insertIndex = (currentQueueIndex + 1).coerceAtMost(_queue.size)
                _queue.add(insertIndex, item)
            } else {
                _queue.add(item)
            }
        }
    }

    fun removeFromQueue(item: LibraryItem) {
        val index = _queue.indexOfFirst { it.id == item.id }
        if (index != -1) {
            _queue.removeAt(index)
            if (index < currentQueueIndex) {
                currentQueueIndex--
            } else if (index == currentQueueIndex && _queue.isNotEmpty()) {
                // Si borramos el actual, reproducimos el siguiente o paramos si es el último
                if (currentQueueIndex < _queue.size) {
                    play(_queue[currentQueueIndex])
                } else {
                    stop()
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in _queue.indices) {
            _queue.removeAt(index)
            if (index < currentQueueIndex) {
                currentQueueIndex--
            } else if (index == currentQueueIndex && _queue.isNotEmpty()) {
                if (currentQueueIndex < _queue.size) {
                    play(_queue[currentQueueIndex])
                } else {
                    stop()
                }
            }
        }
    }

    fun clearQueue() {
        _queue.clear()
        currentQueueIndex = -1
    }

    fun insertIntoQueue(index: Int, item: LibraryItem) {
        val safeIndex = index.coerceIn(0, _queue.size)
        if (_queue.any { it.id == item.id }) {
            return
        }
        _queue.add(safeIndex, item)
        if (currentQueueIndex >= safeIndex && currentQueueIndex != -1) {
            currentQueueIndex++
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.indices || toIndex !in _queue.indices || fromIndex == toIndex) {
            return
        }

        val item = _queue.removeAt(fromIndex)
        _queue.add(toIndex, item)

        currentQueueIndex = when {
            currentQueueIndex == fromIndex -> toIndex
            fromIndex < currentQueueIndex && toIndex >= currentQueueIndex -> currentQueueIndex - 1
            fromIndex > currentQueueIndex && toIndex <= currentQueueIndex -> currentQueueIndex + 1
            else -> currentQueueIndex
        }
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
        nativePlayer.togglePlayPause()
        if (isVideoMode) {
            videoPlayer.syncPosition(position, isPlaying)
        }
    }

    fun pause() {
        isPlaying = false
        nativePlayer.pause()
        if (isVideoMode) {
            videoPlayer.syncPosition(position, false)
        }
    }

    fun resume() {
        if (currentItem != null) {
            isPlaying = true
            nativePlayer.resume()
            if (isVideoMode) {
                videoPlayer.syncPosition(position, true)
            }
        }
    }

    fun stop() {
        reportPlaybackIfNeeded()
        playJob?.cancel()
        playJob = null
        audioRestoreJob?.cancel()
        audioRestoreJob = null
        videoLoadJob?.cancel()
        videoLoadJob = null
        videoLoadGeneration++
        currentAudioSwitchedForVideo = false
        isVideoMode = false
        crossfadeTriggeredForCurrentTrack = false
        isPlaying = false
        stopPositionTracker()
        cancelSleepTimer()
        position = 0L
        currentItem = null
        videoPlayer.stop()
        nativePlayer.stop()
    }

    fun seekTo(positionMs: Long) {
        position = positionMs.coerceIn(0L, duration)
        nativePlayer.seekTo(position)
        if (isVideoMode) {
            videoPlayer.seekTo(position)
        }
    }

    fun seekToProgress(progress: Float) {
        val newPosition = (duration * progress.coerceIn(0f, 1f)).toLong()
        seekTo(newPosition)
    }

    fun skipToNext() {
        if (canSkipNext) {
            val nextIndex = if (currentQueueIndex >= _queue.size - 1 && repeatMode == RepeatMode.ALL) {
                0
            } else {
                currentQueueIndex + 1
            }
            if (nextIndex in _queue.indices) {
                currentQueueIndex = nextIndex
                play(_queue[nextIndex])
            }
        }
    }

    fun skipToPrevious() {
        if (position > 3000L) {
            seekTo(0L)
        } else if (canSkipPrevious) {
            currentQueueIndex--
            play(_queue[currentQueueIndex])
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled && _queue.size > 1) {
            val current = currentItem
            val remaining = _queue.filter { it.id != current?.id }.shuffled()
            _queue.clear()
            if (current != null) {
                _queue.add(current)
                currentQueueIndex = 0
            }
            _queue.addAll(remaining)
        }
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private var lastNonZeroVolume: Double = 1.0

    fun updateVolume(newVolume: Double) {
        val clamped = newVolume.coerceIn(0.0, 1.0)
        if (clamped > 0.01) {
            lastNonZeroVolume = clamped
        }
        volume = clamped
        nativePlayer.setVolume(volume)
    }

    fun stepVolume(delta: Double) {
        updateVolume(volume + delta)
    }

    fun toggleMute() {
        if (volume > 0.01) {
            updateVolume(0.0)
        } else {
            updateVolume(if (lastNonZeroVolume > 0.01) lastNonZeroVolume else 1.0)
        }
    }

    fun updateTempo(newTempo: Float) {
        tempo = newTempo.coerceIn(0.5f, 2.0f)
        applyPlaybackRate()
    }

    fun updatePitchSemitone(newPitchSemitone: Int) {
        pitchSemitone = newPitchSemitone.coerceIn(-12, 12)
        applyPlaybackRate()
    }

    fun resetTempoPitch() {
        tempo = 1f
        pitchSemitone = 0
        applyPlaybackRate()
    }

    fun updateSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        nativePlayer.setSkipSilence(enabled)
        preferences.setSkipSilence(enabled)
    }

    fun updateCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        crossfadeDurationSec = clamped
        nativePlayer.setCrossfadeDuration(clamped)
        preferences.setCrossfadeDuration(clamped)
    }

    fun toggleVideoMode() {
        setVideoMode(!isVideoMode)
    }

    fun setVideoMode(enabled: Boolean) {
        audioRestoreJob?.cancel()
        audioRestoreJob = null
        videoLoadJob?.cancel()
        videoLoadJob = null
        videoLoadGeneration++
        if (!enabled) {
            isVideoMode = false
            isBuffering = false
            currentAudioSwitchedForVideo = false
            videoPlayer.stop()
            return
        }
        val item = currentItem
        val videoId = item?.let { extractVideoId(it.playbackUrl) }
        if (item == null || videoId == null) {
            isVideoMode = false
            videoPlayer.stop()
            return
        }
        isVideoMode = true
        startVideoForCurrentItem(item, videoId)
    }

    private fun startVideoForCurrentItem(
        item: LibraryItem,
        videoId: String,
        preserveExistingAudio: Boolean = false,
    ) {
        if (!preserveExistingAudio) {
            videoStreamRefreshCount = 0
        }
        lastVideoStartTimeMs = System.currentTimeMillis()
        val generation = ++videoLoadGeneration
        videoLoadJob?.cancel()
        videoLoadJob = scope.launch {
            val result = nativePlayer.extractVideoStream(videoId, item.title, item.artist)
            if (!isActive || generation != videoLoadGeneration || !isVideoMode || currentItem?.id != item.id) {
                return@launch
            }
            result.onSuccess { info ->
                val targetPositionMs = nativePlayer.getCurrentPosition().takeIf { it > 0L } ?: position
                val shouldPlay = isPlaying
                if (!preserveExistingAudio && info.targetVideoId.isNotBlank() && info.targetVideoId != videoId && !info.audioUrl.isNullOrBlank()) {
                    val switched = nativePlayer.switchAudioStreamPreservingPosition(
                        url = info.audioUrl,
                        headers = info.audioHeaders,
                        contentLengthBytes = info.audioContentLengthBytes,
                        targetPositionMs = targetPositionMs,
                        playWhenReady = shouldPlay,
                    )
                    if (!isActive || generation != videoLoadGeneration || !isVideoMode || currentItem?.id != item.id) {
                        return@launch
                    }
                    if (switched) {
                        currentAudioSwitchedForVideo = true
                    }
                }
                val startPositionMs = nativePlayer.getCurrentPosition().takeIf { it > 0L } ?: targetPositionMs
                videoPlayer.setPlaybackSpeed(tempo)
                videoPlayer.start(
                    url = info.url,
                    headers = info.headers,
                    positionMs = startPositionMs,
                    contentLengthBytes = info.contentLengthBytes,
                    positionProvider = { nativePlayer.getCurrentPosition() },
                )
                videoPlayer.syncPosition(startPositionMs, isPlaying)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                println("Anitail ERROR: Video stream extraction failed for $videoId: ${error.message}")
                isVideoMode = false
                videoPlayer.stop()
            }
        }
    }

    private fun applyPlaybackRate() {
        nativePlayer.setTempoAndPitch(tempo, pitchSemitone)
        videoPlayer.setPlaybackSpeed(tempo)
    }

    private fun reportPlaybackIfNeeded(force: Boolean = false) {
        val item = currentItem ?: return
        val playTimeMs = position.coerceAtLeast(0L)
        if (playTimeMs <= 0L) return
        if (!force && lastReportedItemId == item.id && lastReportedPosition == playTimeMs) return
        lastReportedItemId = item.id
        lastReportedPosition = playTimeMs
        onPlaybackEvent?.invoke(item, playTimeMs)
    }

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()
        val nowMs = System.currentTimeMillis()
        val newState = startSleepTimerState(nowMs, durationMs)
        sleepTimerState = newState
        sleepTimerTick = nowMs

        if (!newState.isActive) return

        sleepTimerJob = scope.launch {
            while (isActive && sleepTimerState.isActive) {
                delay(1_000L)
                val tick = System.currentTimeMillis()
                sleepTimerTick = tick
                if (isSleepTimerExpired(sleepTimerState, tick)) {
                    pause()
                    sleepTimerState = cancelSleepTimerState()
                    break
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerState = cancelSleepTimerState()
        sleepTimerTick = 0L
    }

    fun updatePosition(positionMs: Long) {
        // Este método ahora es principalmente para compatibilidad
        // El reproductor nativo actualiza la posición automáticamente
        position = positionMs

        if (position >= duration && duration > 0) {
            handlePlaybackEnded()
        }
    }

    val audioEqualizer: AudioEqualizer?
        get() = nativePlayer.audioEqualizer

    fun applyEqualizer() {
        nativePlayer.applyEqualizer()
    }

    fun release() {
        playJob?.cancel()
        playJob = null
        videoLoadJob?.cancel()
        videoLoadJob = null
        stopPositionTracker()
        cancelSleepTimer()
        videoPlayer.dispose()
        nativePlayer.release()
        scope.cancel()
    }

    private fun startPositionTracker() {
        if (positionTrackerJob?.isActive == true) return
        positionTrackerJob = scope.launch {
            while (isActive) {
                if (isPlaying) {
                    val currentMs = nativePlayer.getCurrentPosition()
                    if (currentMs >= 0L) {
                        position = currentMs
                    }
                    val totalMs = nativePlayer.getDuration()
                    if (totalMs > 0L) {
                        duration = totalMs
                    }
                    if (isVideoMode) {
                        videoPlayer.syncPosition(position, true)
                    }
                    if (
                        crossfadeDurationSec > 0 &&
                        !crossfadeTriggeredForCurrentTrack &&
                        canSkipNext &&
                        repeatMode != RepeatMode.ONE
                    ) {
                        val fadeMs = crossfadeDurationSec * 1000L
                        if (duration > maxOf(fadeMs * 2L, 8_000L) && position > 1_000L) {
                            val remaining = duration - position
                            if (remaining in 1L..fadeMs) {
                                crossfadeTriggeredForCurrentTrack = true
                                skipToNext()
                            }
                        }
                    }
                }
                delay(PositionTrackerIntervalMs)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Composable
fun rememberPlayerState(): PlayerState {
    return remember { PlayerState() }
}

private const val PositionTrackerIntervalMs = 16L
