package com.anitail.music.discord

import androidx.media3.common.Player
import com.anitail.music.extensions.mediaItems
import com.anitail.music.extensions.metadata
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.models.MediaMetadata
import com.anitail.music.playback.MusicService
import com.anitail.music.playback.queues.ListQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
data class DiscordGroupState(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val isPlaying: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class DiscordListenAlongManager(private val service: MusicService) :
    DiscordRpcManager.LobbyListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _activeLobbyId = MutableStateFlow<Long?>(null)
    val activeLobbyId: StateFlow<Long?> = _activeLobbyId.asStateFlow()

    private val _joinSecret = MutableStateFlow<String?>(null)
    val joinSecret: StateFlow<String?> = _joinSecret.asStateFlow()

    private var lastReceivedState: DiscordGroupState? = null
    private var hostPublishJob: Job? = null

    init {
        DiscordRpcManager.setLobbyListener(this)
    }

    fun startHostSession() {
        _isHost.value = true
        val secret = java.util.UUID.randomUUID().toString()
        Timber.tag("ListenAlong").i("Starting host session with secret: $secret")
        DiscordRpcManager.createLobby(secret)
    }

    fun joinSession(lobbyId: Long, secret: String) {
        _isHost.value = false
        Timber.tag("ListenAlong").i("Joining session lobbyId: $lobbyId, secret: $secret")
        DiscordRpcManager.joinLobby(lobbyId, secret)
    }

    fun leaveSession() {
        val lobbyId = _activeLobbyId.value
        if (lobbyId != null) {
            Timber.tag("ListenAlong").i("Leaving session lobbyId: $lobbyId")
            DiscordRpcManager.leaveLobby(lobbyId)
        }
        _activeLobbyId.value = null
        _joinSecret.value = null
        _isHost.value = false
        stopHostPublishing()
    }

    override fun onLobbyJoined(lobbyId: Long, secret: String) {
        _activeLobbyId.value = lobbyId
        _joinSecret.value = secret
        Timber.tag("ListenAlong")
            .i("Lobby joined successfully. lobbyId=$lobbyId, isHost=${_isHost.value}")

        // Trigger RPC update to propagate join secret/party info
        scope.launch {
            service.currentMediaMetadata.value?.let {
                // Triggering currentSong update or updateDiscordRPC directly
            }
        }

        if (_isHost.value) {
            startHostPublishing()
        }
    }

    override fun onLobbyMessage(lobbyId: Long, authorId: Long, message: String) {
        if (isHost.value) return // Host ignores incoming messages

        try {
            val state = Json.decodeFromString<DiscordGroupState>(message)
            Timber.tag("ListenAlong").d("Received state update from lobby: $state")
            applyHostState(state)
        } catch (e: Exception) {
            Timber.tag("ListenAlong").e(e, "Error parsing lobby message JSON")
        }
    }

    private fun startHostPublishing() {
        stopHostPublishing()
        hostPublishJob = scope.launch {
            while (isActive) {
                publishHostState()
                delay(4000) // Publish every 4 seconds
            }
        }
    }

    private fun stopHostPublishing() {
        hostPublishJob?.cancel()
        hostPublishJob = null
    }

    private suspend fun publishHostState() {
        val lobbyId = _activeLobbyId.value ?: return
        if (!_isHost.value) return

        try {
            val state = withContext(Dispatchers.Main) {
                val player = service.player
                val items = player.mediaItems.mapNotNull { it.metadata }
                DiscordGroupState(
                    title = service.queueTitle,
                    items = items,
                    mediaItemIndex = player.currentMediaItemIndex,
                    position = player.currentPosition,
                    isPlaying = player.isPlaying,
                    repeatMode = player.repeatMode,
                    shuffleModeEnabled = player.shuffleModeEnabled
                )
            }
            val json = Json.encodeToString(state)
            DiscordRpcManager.sendLobbyMessage(lobbyId, json)
        } catch (e: Exception) {
            Timber.tag("ListenAlong").e(e, "Error publishing host state")
        }
    }

    private fun applyHostState(state: DiscordGroupState) {
        val lastState = lastReceivedState
        lastReceivedState = state

        // Extract metadata items to check difference
        val currentItems = service.player.mediaItems.mapNotNull { it.metadata }
        val itemsChanged = currentItems.map { it.id } != state.items.map { it.id }

        if (itemsChanged || service.player.currentMediaItemIndex != state.mediaItemIndex) {
            // Re-create queue/load queue
            val validItems = state.items.filter { it.id.isNotBlank() }
            if (validItems.isNotEmpty()) {
                val safeStartIndex = state.mediaItemIndex.coerceIn(0, validItems.size - 1)
                val queue = ListQueue(
                    title = state.title ?: "Discord Group Session",
                    items = validItems.map { it.toMediaItem() },
                    startIndex = safeStartIndex,
                    position = state.position.coerceAtLeast(0L)
                )
                service.playQueue(queue, playWhenReady = state.isPlaying, skipJamBroadcast = true)
            }
        } else {
            // Apply play/pause and seek
            val player = service.player
            if (player.isPlaying != state.isPlaying) {
                if (state.isPlaying) {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    player.play()
                } else {
                    player.pause()
                }
            }

            val posDiff = Math.abs(player.currentPosition - state.position)
            if (posDiff > 3000) { // Seek if difference is > 3 seconds
                player.seekTo(state.position)
            }

            if (player.repeatMode != state.repeatMode) {
                player.repeatMode = state.repeatMode
            }

            if (player.shuffleModeEnabled != state.shuffleModeEnabled) {
                player.shuffleModeEnabled = state.shuffleModeEnabled
            }
        }
    }

    fun onDestroy() {
        scope.cancel()
        leaveSession()
    }
}
