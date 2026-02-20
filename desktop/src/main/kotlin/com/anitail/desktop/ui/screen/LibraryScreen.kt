package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.i18n.stringResource
import com.anitail.shared.model.LibraryItem

enum class LibraryFilter(val labelKey: String) {
    TODOS("filter_all"),
    CANCIONES("filter_songs"),
    ALBUMES("filter_albums"),
    ARTISTAS("filter_artists"),
    PLAYLISTS("filter_playlists"),
    DESCARGAS("filter_downloaded"),
}

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    filterState: MutableState<LibraryFilter>,
    onPlay: (LibraryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NavigationTitle(title = stringResource("filter_library"))
        ChipsRow(
            chips = LibraryFilter.values().map { it to stringResource(it.labelKey) },
            currentValue = filterState.value,
            onValueUpdate = { value -> filterState.value = value },
        )
        Spacer(modifier = Modifier.height(12.dp))

        val playlists = items.filter { it.playbackUrl.contains("playlist?list=") }
        val artists = items.filter { it.playbackUrl.contains("/channel/") }
        val songs = items.filterNot { it in playlists || it in artists }

        when (filterState.value) {
            LibraryFilter.TODOS -> {
                if (playlists.isNotEmpty()) {
                    NavigationTitle(title = stringResource("playlists"))
                    HorizontalItemRow(items = playlists, onPlay = onPlay)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (artists.isNotEmpty()) {
                    NavigationTitle(title = stringResource("artists"))
                    HorizontalItemRow(items = artists, onPlay = onPlay)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (songs.isNotEmpty()) {
                    NavigationTitle(title = stringResource("songs"))
                    ItemList(items = songs, primaryAction = stringResource("play"), onPrimaryAction = onPlay)
                } else if (playlists.isEmpty() && artists.isEmpty()) {
                    Text(stringResource("library_empty_title"))
                }
            }

            LibraryFilter.CANCIONES -> {
                if (songs.isEmpty()) {
                    Text(stringResource("library_songs_empty_title"))
                } else {
                    ItemList(items = songs, primaryAction = stringResource("play"), onPrimaryAction = onPlay)
                }
            }

            LibraryFilter.ALBUMES -> {
                Text(stringResource("library_albums_unavailable"))
            }

            LibraryFilter.ARTISTAS -> {
                if (artists.isEmpty()) {
                    Text(stringResource("library_artists_empty_title"))
                } else {
                    HorizontalItemRow(items = artists, onPlay = onPlay)
                }
            }

            LibraryFilter.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    Text(stringResource("library_playlists_empty_title"))
                } else {
                    HorizontalItemRow(items = playlists, onPlay = onPlay)
                }
            }

            LibraryFilter.DESCARGAS -> {
                Text(stringResource("library_downloads_unavailable"))
            }
        }
    }
}

@Composable
private fun HorizontalItemRow(
    items: List<LibraryItem>,
    onPlay: (LibraryItem) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clickable { onPlay(item) },
            ) {
                Box(
                    modifier = Modifier
                        .height(160.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
