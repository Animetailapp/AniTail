package com.anitail.music.ui.screens.library

import androidx.compose.ui.graphics.vector.ImageVector
import com.anitail.music.constants.LibraryFilter
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.Artist
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.PodcastEntity
import com.anitail.music.db.entities.Song
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class LibraryExpressiveFilter {
    TRACKS,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    PODCASTS,
}

fun LibraryExpressiveFilter?.toPreferenceFilter(): LibraryFilter =
    when (this) {
        LibraryExpressiveFilter.TRACKS -> LibraryFilter.SONGS
        LibraryExpressiveFilter.PLAYLISTS -> LibraryFilter.PLAYLISTS
        LibraryExpressiveFilter.ARTISTS -> LibraryFilter.ARTISTS
        LibraryExpressiveFilter.ALBUMS -> LibraryFilter.ALBUMS
        LibraryExpressiveFilter.PODCASTS -> LibraryFilter.PODCASTS
        null -> LibraryFilter.LIBRARY
    }

fun LibraryFilter.toExpressiveFilter(): LibraryExpressiveFilter? =
    when (this) {
        LibraryFilter.SONGS -> LibraryExpressiveFilter.TRACKS
        LibraryFilter.PLAYLISTS -> LibraryExpressiveFilter.PLAYLISTS
        LibraryFilter.ARTISTS -> LibraryExpressiveFilter.ARTISTS
        LibraryFilter.ALBUMS -> LibraryExpressiveFilter.ALBUMS
        LibraryFilter.PODCASTS -> LibraryExpressiveFilter.PODCASTS
        LibraryFilter.LIBRARY, LibraryFilter.LOCAL, LibraryFilter.DOWNLOADED -> null
    }

enum class LibraryExpressiveSortType {
    RECENTLY_ADDED,
    NAME,
    LAST_UPDATED,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAYTIME,
    MANUAL,
    ;

    val defaultAscending: Boolean
        get() = this !in setOf(RECENTLY_ADDED, LAST_UPDATED)

    fun isAvailableFor(filter: LibraryExpressiveFilter?): Boolean =
        when (this) {
            ARTIST, YEAR, LENGTH -> filter == LibraryExpressiveFilter.ALBUMS
            SONG_COUNT, PLAYTIME ->
                filter in setOf(
                    LibraryExpressiveFilter.ARTISTS,
                    LibraryExpressiveFilter.ALBUMS,
                    LibraryExpressiveFilter.PLAYLISTS,
                    null,
                )
            else -> true
        }
}

data class LibrarySortConfig(
    val sort: LibraryExpressiveSortType,
    val ascending: Boolean = sort.defaultAscending,
)

private fun filterStorageKey(filter: LibraryExpressiveFilter?): String = filter?.name ?: "ALL"

fun resolveLibrarySortConfig(
    sortingMap: Map<String, LibrarySortConfig>,
    filter: LibraryExpressiveFilter?,
): LibrarySortConfig {
    val stored = sortingMap[filterStorageKey(filter)] ?: LibrarySortConfig(LibraryExpressiveSortType.RECENTLY_ADDED)
    return if (stored.sort.isAvailableFor(filter)) {
        stored
    } else {
        LibrarySortConfig(LibraryExpressiveSortType.RECENTLY_ADDED)
    }
}

fun updateLibrarySortMap(
    sortingMap: Map<String, LibrarySortConfig>,
    filter: LibraryExpressiveFilter?,
    config: LibrarySortConfig,
): Map<String, LibrarySortConfig> =
    sortingMap.toMutableMap().apply {
        put(filterStorageKey(filter), config)
    }

fun encodeLibrarySortMap(sortingMap: Map<String, LibrarySortConfig>): String =
    sortingMap.entries.joinToString("\n") { (filterKey, config) ->
        "$filterKey=${config.sort.name}:${if (config.ascending) "ASC" else "DESC"}"
    }

fun decodeLibrarySortMap(raw: String): Map<String, LibrarySortConfig> {
    if (raw.isBlank()) return emptyMap()
    val result = mutableMapOf<String, LibrarySortConfig>()
    raw.lineSequence().forEach { line ->
        val eqIdx = line.indexOf('=')
        if (eqIdx <= 0) return@forEach
        val filterKey = line.substring(0, eqIdx)
        val spec = line.substring(eqIdx + 1)
        val colonIdx = spec.indexOf(':')
        if (colonIdx <= 0) return@forEach
        val sort = runCatching {
            LibraryExpressiveSortType.valueOf(spec.substring(0, colonIdx))
        }.getOrNull() ?: return@forEach
        val ascending = when (spec.substring(colonIdx + 1)) {
            "ASC" -> true
            "DESC" -> false
            else -> sort.defaultAscending
        }
        result[filterKey] = LibrarySortConfig(sort = sort, ascending = ascending)
    }
    return result
}

enum class HiddenLibraryItemType {
    AUTO,
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
    PODCAST,
}

data class HiddenLibraryItemKey(
    val type: HiddenLibraryItemType,
    val id: String,
) {
    val storageToken: String
        get() = "${type.name}:$id"
}

fun encodeHiddenLibraryItems(keys: Set<HiddenLibraryItemKey>): String =
    keys.joinToString("\n") { it.storageToken }

fun decodeHiddenLibraryItems(raw: String): Set<HiddenLibraryItemKey> {
    if (raw.isBlank()) return emptySet()
    val keys = linkedSetOf<HiddenLibraryItemKey>()
    raw.lineSequence().forEach { line ->
        parseHiddenLibraryItemKey(line)?.let(keys::add)
    }
    return keys
}

private fun parseHiddenLibraryItemKey(line: String): HiddenLibraryItemKey? {
    val separator = line.indexOf(':')
    if (separator <= 0) return null
    val type = runCatching { HiddenLibraryItemType.valueOf(line.substring(0, separator)) }.getOrNull() ?: return null
    val id = line.substring(separator + 1)
    if (id.isEmpty()) return null
    return HiddenLibraryItemKey(type = type, id = id)
}

internal val LibraryGridColumnsRange = 2..5
internal const val DEFAULT_LIBRARY_GRID_COLUMNS = 3

internal class LibraryGridPinch(
    initialColumns: Int,
) {
    var columns: Int = initialColumns.coerceIn(LibraryGridColumnsRange)
        private set

    private var zoom: Float = 1f

    fun zoomBy(factor: Float): Int {
        if (!factor.isFinite() || factor <= 0f) return columns
        zoom *= factor
        while (zoom >= 1.25f) {
            if (columns == LibraryGridColumnsRange.first) {
                zoom = 1f
                break
            }
            columns--
            zoom /= 1.25f
        }
        while (zoom <= 0.8f) {
            if (columns == LibraryGridColumnsRange.last) {
                zoom = 1f
                break
            }
            columns++
            zoom /= 0.8f
        }
        return columns
    }
}

data class LibraryAutoPlaylistItem(
    val type: String,
    val title: String,
    val subtitle: String,
    val coverRes: Int?,
    val fallbackIcon: ImageVector,
    val route: String,
    val badge: String? = null,
)

private fun LocalDateTime?.toSortEpochSeconds(): Long? =
    this?.toEpochSecond(ZoneOffset.UTC)

sealed interface LibraryEntry {
    val hiddenKey: HiddenLibraryItemKey
    val reorderKey: String
        get() = hiddenKey.storageToken
    val title: String
    val addedAtEpochSec: Long?
    val updatedAtEpochSec: Long?
    val artistSortName: String?
    val releaseYear: Int?
    val songCount: Int?
    val durationSeconds: Int?
    val playtimeValue: Long?
    val isDownloaded: Boolean

    fun matchesQuery(query: String): Boolean

    data class SongEntry(
        val song: Song,
        override val isDownloaded: Boolean = false,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.SONG, song.id)
        override val title: String = song.song.title
        override val addedAtEpochSec: Long? =
            (song.song.inLibrary ?: song.song.likedDate ?: song.song.dateModified).toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = addedAtEpochSec
        override val artistSortName: String? = null
        override val releaseYear: Int? = song.song.year
        override val songCount: Int? = null
        override val durationSeconds: Int? = song.song.duration.takeIf { it > 0 }
        override val playtimeValue: Long? = song.song.totalPlayTime.takeIf { it > 0L }

        val artistsDisplay: String
            get() = song.artists.joinToString(", ") { it.name }
                .ifBlank { song.song.artistName.orEmpty() }

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true) ||
                artistsDisplay.contains(query, ignoreCase = true) ||
                (song.album?.title ?: song.song.albumName).orEmpty().contains(query, ignoreCase = true)
    }

    data class AlbumEntry(
        val album: Album,
        override val isDownloaded: Boolean = false,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.ALBUM, album.id)
        override val title: String = album.album.title
        override val addedAtEpochSec: Long? =
            (album.album.bookmarkedAt ?: album.album.inLibrary ?: album.album.likedDate).toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = album.album.lastUpdateTime.toSortEpochSeconds()
        override val artistSortName: String? = album.artists.firstOrNull()?.name?.lowercase()
        override val releaseYear: Int? = album.album.year
        override val songCount: Int? = album.album.songCount.takeIf { it > 0 }
        override val durationSeconds: Int? = album.album.duration.takeIf { it > 0 }
        override val playtimeValue: Long? = album.timeListened?.toLong()

        val artistsDisplay: String
            get() = album.artists.joinToString(", ") { it.name }

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true) ||
                artistsDisplay.contains(query, ignoreCase = true)
    }

    data class ArtistEntry(
        val artist: Artist,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.ARTIST, artist.id)
        override val title: String = artist.artist.name
        override val addedAtEpochSec: Long? = artist.artist.bookmarkedAt.toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = artist.artist.lastUpdateTime.toSortEpochSeconds()
        override val artistSortName: String? = null
        override val releaseYear: Int? = null
        override val songCount: Int? = artist.songCount.takeIf { it > 0 }
        override val durationSeconds: Int? = null
        override val playtimeValue: Long? = artist.timeListened?.toLong()
        override val isDownloaded: Boolean = false

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true)
    }

    data class PlaylistEntry(
        val playlist: Playlist,
        override val isDownloaded: Boolean = false,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.PLAYLIST, playlist.id)
        override val title: String = playlist.playlist.name
        override val addedAtEpochSec: Long? =
            (playlist.playlist.bookmarkedAt ?: playlist.playlist.createdAt).toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = playlist.playlist.lastUpdateTime.toSortEpochSeconds()
        override val artistSortName: String? = null
        override val releaseYear: Int? = null
        override val songCount: Int? = playlist.songCount.takeIf { it >= 0 }
        override val durationSeconds: Int? = null
        override val playtimeValue: Long? = null

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true)
    }

    data class PodcastEntry(
        val podcast: PodcastEntity,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.PODCAST, podcast.id)
        override val title: String = podcast.title
        override val addedAtEpochSec: Long? = podcast.bookmarkedAt.toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = podcast.lastUpdateTime.toSortEpochSeconds()
        override val artistSortName: String? = null
        override val releaseYear: Int? = null
        override val songCount: Int? = null
        override val durationSeconds: Int? = null
        override val playtimeValue: Long? = null
        override val isDownloaded: Boolean = false

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true) ||
                podcast.author.orEmpty().contains(query, ignoreCase = true)
    }

    data class PodcastChannelEntry(
        val channelId: String,
        val artist: Artist,
    ) : LibraryEntry {
        override val hiddenKey: HiddenLibraryItemKey =
            HiddenLibraryItemKey(HiddenLibraryItemType.PODCAST, "channel:$channelId")
        override val title: String = artist.artist.name
        override val addedAtEpochSec: Long? = artist.artist.bookmarkedAt.toSortEpochSeconds()
        override val updatedAtEpochSec: Long? = artist.artist.lastUpdateTime.toSortEpochSeconds()
        override val artistSortName: String? = null
        override val releaseYear: Int? = null
        override val songCount: Int? = artist.songCount.takeIf { it > 0 }
        override val durationSeconds: Int? = null
        override val playtimeValue: Long? = artist.timeListened?.toLong()
        override val isDownloaded: Boolean = false

        override fun matchesQuery(query: String): Boolean =
            title.contains(query, ignoreCase = true)
    }
}

fun filterAutoPlaylists(
    autoPlaylists: List<LibraryAutoPlaylistItem>,
    filter: LibraryExpressiveFilter?,
    hiddenKeys: Set<HiddenLibraryItemKey>,
    searchQuery: String,
): List<LibraryAutoPlaylistItem> {
    if (filter != null && filter != LibraryExpressiveFilter.PLAYLISTS) return emptyList()
    val visible = autoPlaylists.filterNot {
        HiddenLibraryItemKey(HiddenLibraryItemType.AUTO, it.type) in hiddenKeys
    }
    val term = searchQuery.trim()
    return if (term.isEmpty()) {
        visible
    } else {
        visible.filter {
            it.title.contains(term, ignoreCase = true) ||
                it.subtitle.contains(term, ignoreCase = true)
        }
    }
}

fun orderLibraryEntries(
    entries: List<LibraryEntry>,
    storedOrder: String,
): List<LibraryEntry> {
    if (storedOrder.isBlank() || entries.size <= 1) return entries
    val availableKeys = entries.map { it.hiddenKey }
    val positions = normalizedLibraryOrder(availableKeys, storedOrder)
        .withIndex()
        .associate { it.value to it.index }
    return entries.withIndex()
        .sortedBy { positions[it.value.hiddenKey] ?: Int.MAX_VALUE }
        .map { it.value }
}

fun moveLibraryEntries(
    allEntries: List<LibraryEntry>,
    visibleEntries: List<LibraryEntry>,
    storedOrder: String,
    fromKey: HiddenLibraryItemKey,
    toKey: HiddenLibraryItemKey,
): String =
    moveLibraryOrder(
        available = allEntries.map { it.hiddenKey },
        visible = visibleEntries.map { it.hiddenKey }.toSet(),
        storedOrder = storedOrder,
        from = fromKey,
        to = toKey,
    )

internal fun moveLibraryOrder(
    available: List<HiddenLibraryItemKey>,
    visible: Set<HiddenLibraryItemKey>,
    storedOrder: String,
    from: HiddenLibraryItemKey,
    to: HiddenLibraryItemKey,
): String {
    val order = normalizedLibraryOrder(available, storedOrder)
    val moved = order.filter { it in visible }.toMutableList()
    val fromIndex = moved.indexOf(from)
    val toIndex = moved.indexOf(to)
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return storedOrder
    moved.add(toIndex, moved.removeAt(fromIndex))
    val reordered = moved.iterator()
    return order.joinToString("\n") { key ->
        val nextKey = if (key in visible) reordered.next() else key
        nextKey.storageToken
    }
}

internal fun normalizedLibraryOrder(
    available: List<HiddenLibraryItemKey>,
    storedOrder: String,
): List<HiddenLibraryItemKey> {
    val availableSet = available.toSet()
    val stored = storedOrder
        .lineSequence()
        .mapNotNull(::parseHiddenLibraryItemKey)
        .filterTo(mutableListOf()) { it in availableSet }
    val storedSet = stored.toSet()
    return (stored + available.filterNot(storedSet::contains)).distinct()
}

fun filterAndSortLibraryEntries(
    entries: List<LibraryEntry>,
    filter: LibraryExpressiveFilter?,
    hiddenKeys: Set<HiddenLibraryItemKey>,
    sortType: LibraryExpressiveSortType,
    ascending: Boolean,
    manualOrder: String,
    searchQuery: String,
): List<LibraryEntry> {
    val trimmedQuery = searchQuery.trim()
    val unhidden = entries.filterNot { it.hiddenKey in hiddenKeys }
    val filtered = when (filter) {
        LibraryExpressiveFilter.TRACKS -> unhidden.filterIsInstance<LibraryEntry.SongEntry>()
        LibraryExpressiveFilter.PLAYLISTS -> unhidden.filterIsInstance<LibraryEntry.PlaylistEntry>()
        LibraryExpressiveFilter.ARTISTS -> unhidden.filterIsInstance<LibraryEntry.ArtistEntry>()
        LibraryExpressiveFilter.ALBUMS -> unhidden.filterIsInstance<LibraryEntry.AlbumEntry>()
        LibraryExpressiveFilter.PODCASTS -> unhidden.filter {
            it is LibraryEntry.PodcastEntry || it is LibraryEntry.PodcastChannelEntry
        }
        null -> {
            if (trimmedQuery.isEmpty()) {
                unhidden.filterNot {
                    it is LibraryEntry.SongEntry || it is LibraryEntry.PodcastChannelEntry
                }
            } else {
                // In the unified All view, searching also surfaces matching library tracks.
                unhidden.filterNot { it is LibraryEntry.PodcastChannelEntry }
            }
        }
    }
    val searched = if (trimmedQuery.isEmpty()) {
        filtered
    } else {
        filtered.filter { it.matchesQuery(trimmedQuery) }
    }.distinctBy { it.hiddenKey }

    return sortLibraryEntries(
        entries = searched,
        sortType = sortType,
        ascending = ascending,
        manualOrder = manualOrder,
    )
}

fun sortLibraryEntries(
    entries: List<LibraryEntry>,
    sortType: LibraryExpressiveSortType,
    ascending: Boolean,
    manualOrder: String,
): List<LibraryEntry> =
    when (sortType) {
        LibraryExpressiveSortType.RECENTLY_ADDED ->
            entries.sortedByMetadata(ascending) { it.addedAtEpochSec }
        LibraryExpressiveSortType.NAME ->
            entries.sortedByMetadata(ascending) { it.title.lowercase() }
        LibraryExpressiveSortType.LAST_UPDATED ->
            entries.sortedByMetadata(ascending) { it.updatedAtEpochSec }
        LibraryExpressiveSortType.ARTIST ->
            entries.sortedByMetadata(ascending) { it.artistSortName }
        LibraryExpressiveSortType.YEAR ->
            entries.sortedByMetadata(ascending) { it.releaseYear }
        LibraryExpressiveSortType.SONG_COUNT ->
            entries.sortedByMetadata(ascending) { it.songCount }
        LibraryExpressiveSortType.LENGTH ->
            entries.sortedByMetadata(ascending) { it.durationSeconds }
        LibraryExpressiveSortType.PLAYTIME ->
            entries.sortedByMetadata(ascending) { it.playtimeValue }
        LibraryExpressiveSortType.MANUAL ->
            orderLibraryEntries(entries, manualOrder)
    }

private fun <T : Comparable<T>> List<LibraryEntry>.sortedByMetadata(
    ascending: Boolean,
    selector: (LibraryEntry) -> T?,
): List<LibraryEntry> =
    map { it to selector(it) }
        .sortedWith { first, second ->
            val left = first.second
            val right = second.second
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                ascending -> left.compareTo(right)
                else -> right.compareTo(left)
            }
        }
        .map { it.first }

