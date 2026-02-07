package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.TopSearch
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerSectionRow
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.ExplorePage
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.SearchState

// Constantes de dimensiones
private val GridThumbnailHeight = 128.dp
private val ThumbnailCornerRadius = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    query: TextFieldValue,
    searchState: SearchState,
    explorePage: ExplorePage?,
    chartsPage: ChartsPage?,
    isLoading: Boolean,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onAddToLibrary: (LibraryItem) -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    pureBlack: Boolean,
    onChartsClick: () -> Unit = {},
    onMoodGreClick: () -> Unit = {},
    onNewReleaseClick: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            focusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopSearch(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            active = searchActive,
            onActiveChange = onSearchActiveChange,
            placeholder = { Text("Busca canciones o artistas") },
            leadingIcon = {
                IconButton(
                    onClick = {
                        if (searchActive) {
                            onSearchActiveChange(false)
                        } else {
                            onSearchActiveChange(true)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (searchActive) IconAssets.arrowBack() else IconAssets.search(),
                        contentDescription = null,
                    )
                }
            },
            trailingIcon = {
                if (searchActive && query.text.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange(TextFieldValue("")) },
                    ) {
                        Icon(
                            imageVector = IconAssets.close(),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .align(Alignment.CenterHorizontally),
            focusRequester = focusRequester,
            colors = if (pureBlack && searchActive) {
                SearchBarDefaults.colors(
                    containerColor = Color.Black,
                    dividerColor = Color.DarkGray,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            } else {
                SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (searchState.isLoading) {
                    Text("Buscando...")
                }
                searchState.errorMessage?.let { error ->
                    Text(error)
                }
                if (!searchState.isLoading && searchState.results.isNotEmpty()) {
                    NavigationTitle(title = "Resultados")
                    ItemList(
                        items = searchState.results,
                        primaryAction = "Reproducir",
                        onPrimaryAction = onPlay,
                        secondaryAction = "Agregar",
                        onSecondaryAction = onAddToLibrary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!searchActive) {
            if (isLoading) {
                // Shimmer placeholders para charts y explore
                NavigationTitle(title = "Charts")
                ShimmerSectionRow()
                Spacer(modifier = Modifier.height(12.dp))
                NavigationTitle(title = "Nuevos lanzamientos")
                ShimmerSectionRow()
            }
            chartsPage?.sections?.forEach { section ->
                NavigationTitle(title = section.title)
                ChartsRow(
                    items = section.items,
                    onPlay = onPlay,
                    onAddToLibrary = onAddToLibrary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            explorePage?.newReleaseAlbums?.let { albums ->
                NavigationTitle(title = "Nuevos lanzamientos")
                YtItemRow(items = albums, onItemSelected = { }, onAddToLibrary = { })
            }
            explorePage?.moodAndGenres?.let { moods ->
                NavigationTitle(title = "Mood y generos")
                MoodGenresRow(items = moods.map { it.title })
            }
        }

        if (!searchActive) {
            Spacer(modifier = Modifier.height(16.dp))
            NavigationTitle(title = "Mood y generos")
            SimpleChipsRow(
                chips = listOf(
                    "Pop",
                    "Rock",
                    "Hip-Hop",
                    "Electro",
                    "Focus",
                    "Relax",
                ),
            )
        }
    }
}

@Composable
private fun ChartsRow(
    items: List<YTItem>,
    onPlay: (LibraryItem) -> Unit,
    onAddToLibrary: (LibraryItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { item ->
            val libraryItem = item.toLibraryItem() ?: return@items
            Column(
                modifier = Modifier
                    .width(GridThumbnailHeight)
                    .padding(12.dp)
                    .clickable { onPlay(libraryItem) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(GridThumbnailHeight),
                ) {
                    RemoteImage(
                        url = libraryItem.artworkUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                    )
                    if (item is SongItem) {
                        Icon(
                            imageVector = IconAssets.play(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                )
                                .padding(6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    libraryItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    libraryItem.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun YtItemRow(
    items: List<YTItem>,
    onItemSelected: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { item ->
            val isArtist = item is ArtistItem
            val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
            val (title, subtitle) = when (item) {
                is SongItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
                is AlbumItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
                is ArtistItem -> item.title to "Artista"
                is PlaylistItem -> item.title to item.author?.name.orEmpty()
                else -> "" to ""
            }

            Column(
                modifier = Modifier
                    .width(GridThumbnailHeight)
                    .padding(12.dp)
                    .clickable { onItemSelected(item) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(GridThumbnailHeight),
                ) {
                    RemoteImage(
                        url = item.thumbnail,
                        modifier = Modifier.fillMaxSize(),
                        shape = shape,
                    )
                    if (item is SongItem) {
                        Icon(
                            imageVector = IconAssets.play(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                )
                                .padding(6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MoodGenresRow(
    items: List<String>,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { title ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimpleChipsRow(
    chips: List<String>,
    selectedIndex: Int = 0,
    onSelected: (Int) -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        chips.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelected(index) },
                label = { Text(label) },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

// Funciones auxiliares
private fun YTItem.toLibraryItem(): LibraryItem? {
    return when (this) {
        is SongItem -> LibraryItem(
            id = id,
            title = title,
            artist = artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/watch?v=${id}",
        )
        is AlbumItem -> LibraryItem(
            id = browseId,
            title = title,
            artist = artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${playlistId}",
        )
        is PlaylistItem -> LibraryItem(
            id = id,
            title = title,
            artist = author?.name.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${id}",
        )
        is ArtistItem -> LibraryItem(
            id = id,
            title = title,
            artist = title,
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/channel/${id}",
        )
        else -> null
    }
}

private val YTItem.thumbnail: String?
    get() = when (this) {
        is SongItem -> thumbnail
        is AlbumItem -> thumbnail
        is ArtistItem -> thumbnail
        is PlaylistItem -> thumbnail
        else -> null
    }
