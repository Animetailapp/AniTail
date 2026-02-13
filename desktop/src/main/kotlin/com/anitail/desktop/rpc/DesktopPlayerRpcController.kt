package com.anitail.desktop.rpc

import com.anitail.desktop.util.DesktopDiscordRPC
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.delay

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

        if (!rpc.isRpcRunning()) {
            rpc.connect()
        }

        rpc.updateSong(
            item = item,
            artistThumbnailUrl = artistThumbnailUrl,
            albumName = albumName ?: item.artist,
            timeStart = timeStart,
            timeEnd = timeEnd,
        )
    }

    fun close() {
        rpc.closeRPC()
    }
}
