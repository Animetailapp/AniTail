package com.anitail.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.anitail.music.db.MusicDatabase
import com.anitail.music.extensions.currentMetadata
import com.anitail.music.extensions.getCurrentQueueIndex
import com.anitail.music.extensions.getQueueWindows
import com.anitail.music.extensions.metadata
import com.anitail.music.playback.MusicService.MusicBinder
import com.anitail.music.playback.queues.Queue
import com.anitail.music.utils.LanJamCommands
import com.anitail.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    private val coroutineScope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    private var attachedPlayer: ExoPlayer = service.player
    val player: ExoPlayer
        get() = attachedPlayer

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val isPlaying =
        combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
            playWhenReady && playbackState != STATE_ENDED
        }.stateIn(
            coroutineScope,
            SharingStarted.Lazily,
            player.playWhenReady && player.playbackState != STATE_ENDED
        )
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val waitingForNetworkConnection = service.waitingForNetworkConnection
    val discordPresence = service.discordPresence

    init {
        attachPlayer(attachedPlayer)
        coroutineScope.launch {
            service.playerFlow.collect { nextPlayer ->
                if (nextPlayer != null && nextPlayer !== attachedPlayer) {
                    attachPlayer(nextPlayer)
                }
            }
        }
    }

    private fun attachPlayer(newPlayer: ExoPlayer) {
        attachedPlayer.removeListener(this)
        attachedPlayer = newPlayer
        attachedPlayer.addListener(this)

        playbackState.value = attachedPlayer.playbackState
        playWhenReady.value = attachedPlayer.playWhenReady
        mediaMetadata.value = attachedPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = attachedPlayer.getQueueWindows()
        currentWindowIndex.value = attachedPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = attachedPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = attachedPlayer.shuffleModeEnabled
        repeatMode.value = attachedPlayer.repeatMode
        updateCanSkipPreviousAndNext()
    }

    /**
     * Closes the player completely, stopping the service
     * This function is called from the miniplayer's X button and from the notification X button
     * through the MediaSessionConstants.ACTION_CLOSE_PLAYER command
     */
    fun closePlayer() {
        coroutineScope.launch {
            delay(300)
            MusicService.instance?.closePlayer() ?: service.closePlayer()
        }
    }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        service.addToQueue(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }    fun seekToNext() {
        player.seekToNext()
        player.prepare()
        player.playWhenReady = true

        if (service.isJamEnabled && service.isJamHost) {
            service.sendJamCommand(LanJamCommands.CommandType.NEXT)
        }
    }

    fun seekToPrevious() {
        player.seekToPrevious()
        player.prepare()
        player.playWhenReady = true

        if (service.isJamEnabled && service.isJamHost) {
            service.sendJamCommand(LanJamCommands.CommandType.PREVIOUS)
        }
    }

    /**
     * Elimina un elemento de la cola y sincroniza el cambio con los clientes JAM
     */
    fun removeQueueItem(index: Int) {
        player.removeMediaItem(index)

        if (service.isJamEnabled && service.isJamHost) {
            coroutineScope.launch {
                delay(100)
                service.syncQueueWithClients()
            }
        }
    }
    
    /**
     * Elimina varios elementos de la cola y sincroniza el cambio con los clientes JAM
     */
    fun removeQueueItems(indices: List<Int>) {
        indices.sortedDescending().forEach { 
            player.removeMediaItem(it)
        }

        if (service.isJamEnabled && service.isJamHost) {
            coroutineScope.launch {
                delay(100)
                service.syncQueueWithClients()
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()

        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && 
            service.isJamEnabled && service.isJamHost) {
            coroutineScope.launch {
                delay(100)
                service.syncQueueWithClients()
            }
        }
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        attachedPlayer.removeListener(this)
    }
}
