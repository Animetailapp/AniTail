package com.anitail.desktop.rpc

import com.anitail.desktop.util.DesktopDiscordRPC
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DesktopPlayerRpcController(
    private val rpc: DesktopDiscordRPC,
) {
    suspend fun onTrackChanged(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,
        timeEnd: Long,
    ) {
        // Keep the previous behaviour of waiting a bit for thumbnails to be available
        delay(2000)

        withContext(Dispatchers.IO) {
            if (!rpc.isRpcRunning()) {
                rpc.connect()
            }
        }

        rpc.updateSong(
            item = item,
            artistThumbnailUrl = artistThumbnailUrl,
            albumName = albumName ?: item.artist,
            timeStart = timeStart,
            timeEnd = timeEnd,
        )
    }

    suspend fun clearActivity() {
        rpc.clearActivity()
    }

    fun close() {
        rpc.closeRPC()
    }
}
