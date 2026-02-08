package com.anitail.desktop.player

import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.NextResult
import com.anitail.shared.model.LibraryItem

data class RadioQueuePlan(
    val items: List<LibraryItem>,
    val startIndex: Int,
    val currentSong: SongItem?,
)

fun buildRadioQueuePlan(currentItem: LibraryItem, nextResult: NextResult): RadioQueuePlan {
    val mappedItems = nextResult.items.map { it.toSongEntity().toLibraryItem() }
    val currentIndexFromNext = nextResult.currentIndex
        ?: mappedItems.indexOfFirst { it.id == currentItem.id }.takeIf { it >= 0 }
    val currentSong = when {
        currentIndexFromNext != null -> nextResult.items.getOrNull(currentIndexFromNext)
        else -> nextResult.items.firstOrNull { it.id == currentItem.id }
    }

    if (mappedItems.isEmpty()) {
        return RadioQueuePlan(
            items = listOf(currentItem),
            startIndex = 0,
            currentSong = currentSong,
        )
    }

    val containsCurrent = mappedItems.any { it.id == currentItem.id }
    val normalizedItems = if (containsCurrent) {
        mappedItems
    } else {
        listOf(currentItem) + mappedItems
    }
    val startIndex = normalizedItems.indexOfFirst { it.id == currentItem.id }.coerceAtLeast(0)

    return RadioQueuePlan(
        items = normalizedItems,
        startIndex = startIndex,
        currentSong = currentSong,
    )
}
