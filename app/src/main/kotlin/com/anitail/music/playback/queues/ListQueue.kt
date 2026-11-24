package com.anitail.music.playback.queues

import androidx.media3.common.MediaItem
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.metadata
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ListQueue private constructor(
    val title: String?,
    private val eagerItems: List<MediaItem>?,
    private val lazyItemsBuilder: (() -> List<MediaItem>)?,
    private val explicitPreloadMediaItem: MediaItem?,
    val startIndex: Int,
    val position: Long,
) : Queue {
    constructor(
        title: String? = null,
        items: List<MediaItem>,
        startIndex: Int = 0,
        position: Long = 0L,
    ) : this(
        title = title,
        eagerItems = items,
        lazyItemsBuilder = null,
        explicitPreloadMediaItem = if (position == 0L) items.getOrNull(startIndex) else null,
        startIndex = startIndex,
        position = position,
    )

    override val preloadItem: MediaMetadata?
        get() = preloadMediaItem?.metadata

    override val preloadMediaItem: MediaItem?
        get() = explicitPreloadMediaItem

    override suspend fun getInitialStatus(): Queue.Status {
        val resolvedItems = eagerItems ?: withContext(Dispatchers.Default) {
            lazyItemsBuilder?.invoke().orEmpty()
        }
        return Queue.Status(title, resolvedItems, startIndex, position)
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()

    companion object {
        fun fromSongs(
            title: String? = null,
            songs: List<Song>,
            startIndex: Int = 0,
            position: Long = 0L,
        ): ListQueue {
            val snapshot = songs.toList()
            val safeIndex = if (snapshot.isEmpty()) 0 else startIndex.coerceIn(0, snapshot.lastIndex)
            val preload = if (position == 0L) snapshot.getOrNull(safeIndex)?.toMediaItem() else null
            return ListQueue(
                title = title,
                eagerItems = null,
                lazyItemsBuilder = {
                    snapshot.mapIndexed { idx, song ->
                        if (idx == safeIndex && preload != null) preload else song.toMediaItem()
                    }
                },
                explicitPreloadMediaItem = preload,
                startIndex = safeIndex,
                position = position,
            )
        }
    }
}
