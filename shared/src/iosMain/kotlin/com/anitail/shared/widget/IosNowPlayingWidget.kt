package com.anitail.shared.widget

import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.next
import com.anitail.shared.generated.resources.now_playing
import com.anitail.shared.generated.resources.play_pause
import com.anitail.shared.generated.resources.previous
import com.anitail.shared.generated.resources.widget_description
import com.anitail.shared.playback.IosPlaybackController
import com.anitail.shared.playback.MusicPlayer
import com.anitail.shared.state.SongMetadataState
import com.anitail.shared.ui.playback.PlaybackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.math.abs
import kotlin.time.Clock

private const val APP_GROUP_INFO_KEY = "AnitailWidgetAppGroupIdentifier"
private const val SNAPSHOT_KEY = "AnitailWidgetSnapshot"
private const val DEBUG_KEY = "AnitailWidgetDebug"
private const val SEEK_TOLERANCE_MS = 2_000L

interface IosWidgetTimelineReloader {
    fun reloadTimelines()
}

// Decoded by NowPlayingSnapshot in iosApp/AniTailWidget/NowPlayingWidget.swift; the widget has no string catalog.
@Serializable
private data class WidgetLabels(
    val nowPlaying: String,
    val description: String,
    val playPause: String,
    val next: String,
    val previous: String,
)

@Serializable
private data class WidgetSnapshot(
    val title: String?,
    val artists: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val rate: Float,
    val updatedAtMs: Long,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val labels: WidgetLabels,
)

object IosNowPlayingWidget {
    private var reloader: IosWidgetTimelineReloader? = null
    private var controller: IosPlaybackController? = null
    private var player: MusicPlayer? = null
    private var job: Job? = null

    fun register(reloader: IosWidgetTimelineReloader) {
        this.reloader = reloader
    }

    // Called on the main actor by PlaybackControlIntent, which iOS runs in the app process.
    fun handle(command: String) {
        when (command) {
            "playPause" -> controller?.togglePlayPause()
            "next" -> player?.handleRemoteSkip(next = true)
            "previous" -> player?.handleRemoteSkip(next = false)
        }
    }

    internal fun bind(
        controller: IosPlaybackController,
        player: MusicPlayer,
        scope: CoroutineScope,
    ) {
        unbind()
        val group =
            (NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_INFO_KEY) as? String)
                ?.takeIf(String::isNotBlank) ?: return
        val defaults = NSUserDefaults(suiteName = group)
        this.controller = controller
        this.player = player
        job =
            scope.launch {
                try {
                    defaults.setObject("bound", forKey = DEBUG_KEY)
                    val labels =
                        WidgetLabels(
                            nowPlaying = getString(Res.string.now_playing),
                            description = getString(Res.string.widget_description),
                            playPause = getString(Res.string.play_pause),
                            next = getString(Res.string.next),
                            previous = getString(Res.string.previous),
                        )
                    var written: WidgetSnapshot? = null
                    // Polled rather than collected: guest lock and skip availability change without a state emission.
                    while (isActive) {
                        val snapshot = controller.state.value.toSnapshot(controller, labels)
                        if (written?.let(snapshot::matches) != true) {
                            defaults.setObject(Json.encodeToString(snapshot), forKey = SNAPSHOT_KEY)
                            defaults.setObject("written ${snapshot.updatedAtMs}", forKey = DEBUG_KEY)
                            written = snapshot
                            reloader?.reloadTimelines()
                        }
                        delay(1_000L)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    // Surfaced in the widget so a failed writer is visible without device logs.
                    defaults.setObject("error ${error::class.simpleName}: ${error.message}", forKey = DEBUG_KEY)
                }
            }
    }

    internal fun unbind() {
        job?.cancel()
        job = null
        controller = null
        player = null
    }
}

// The widget extrapolates progress itself, so position changes only matter after a seek.
private fun WidgetSnapshot.matches(previous: WidgetSnapshot): Boolean {
    if (copy(positionMs = 0L, updatedAtMs = 0L) != previous.copy(positionMs = 0L, updatedAtMs = 0L)) return false
    val expectedMs = previous.positionMs + ((updatedAtMs - previous.updatedAtMs) * previous.rate).toLong()
    return abs(positionMs - expectedMs) <= SEEK_TOLERANCE_MS
}

private fun PlaybackUiState.toSnapshot(
    controller: IosPlaybackController,
    labels: WidgetLabels,
): WidgetSnapshot {
    val song = currentSong?.let { SongMetadataState.apply(it) }
    return WidgetSnapshot(
        title = song?.title?.takeIf(String::isNotBlank),
        artists = song?.artists?.joinToString { it.name }?.takeIf(String::isNotBlank),
        isPlaying = isPlaying,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.takeIf { it > 0L } ?: song?.duration?.times(1_000L) ?: 0L,
        rate = if (isPlaying && !isLoading) playbackSpeed else 0f,
        updatedAtMs = Clock.System.now().toEpochMilliseconds(),
        // Listen Together guests cannot control playback.
        canPlayPause = song != null && !controller.guestLocked,
        canSkipNext = controller.canSkipNext,
        canSkipPrevious = controller.canSkipPrevious,
        labels = labels,
    )
}
