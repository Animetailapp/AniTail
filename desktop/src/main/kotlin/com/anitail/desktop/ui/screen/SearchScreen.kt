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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.SearchSummaryPage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Pantalla de búsqueda para Desktop - Idéntica a Android.
 * Incluye: barra de búsqueda, sugerencias, historial, resultados con filtros.
 */
@Composable
fun SearchScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onSongClick: (LibraryItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Estados para sugerencias y resultados
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionItems by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var searchSummary by remember { mutableStateOf<SearchSummaryPage?>(null) }
    var currentFilter by remember { mutableStateOf<SearchFilter?>(null) }
    var filteredResults by remember { mutableStateOf<List<YTItem>>(emptyList()) }

    // Historial de búsqueda (en memoria, puede persistirse después)
    val searchHistory = remember { mutableStateListOf<String>() }

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Cargar sugerencias cuando cambia el query
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            suggestions = emptyList()
            suggestionItems = emptyList()
            return@LaunchedEffect
        }

        snapshotFlow { query }
            .debounce(300)
            .collect { q ->
                if (q.isNotEmpty() && !isSearching) {
                    YouTube.searchSuggestions(q).onSuccess { result ->
                        suggestions = result.queries
                        suggestionItems = result.recommendedItems
                    }
                }
            }
    }

    // Función de búsqueda
    fun performSearch(searchQuery: String) {
        if (searchQuery.isBlank()) return

        // Agregar al historial
        searchHistory.remove(searchQuery)
        searchHistory.add(0, searchQuery)
        if (searchHistory.size > 10) {
            searchHistory.removeAt(searchHistory.lastIndex)
        }

        isSearching = true
        isLoading = true
        currentFilter = null

        coroutineScope.launch {
            YouTube.searchSummary(searchQuery).onSuccess { summary ->
                searchSummary = summary
            }.onFailure {
                searchSummary = null
            }
            isLoading = false
        }
    }

    // Función para buscar con filtro
    fun searchWithFilter(filter: SearchFilter) {
        if (query.isBlank()) return

        currentFilter = filter
        isLoading = true

        coroutineScope.launch {
            YouTube.search(query, YouTube.SearchFilter(filter.value)).onSuccess { result ->
                filteredResults = result.items
            }.onFailure {
                filteredResults = emptyList()
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de búsqueda
        SearchBar(
            query = query,
            onQueryChange = {
                query = it
                if (it.isEmpty()) {
                    isSearching = false
                    searchSummary = null
                    filteredResults = emptyList()
                    currentFilter = null
                }
            },
            onSearch = { performSearch(query) },
            onBack = {
                if (isSearching) {
                    isSearching = false
                    searchSummary = null
                    filteredResults = emptyList()
                    currentFilter = null
                } else {
                    onBack()
                }
            },
            focusRequester = focusRequester,
        )

        // Chips de filtro (solo cuando hay resultados)
        if (isSearching && searchSummary != null) {
            ChipsRow(
                currentFilter = currentFilter,
                onFilterChange = { filter ->
                    if (filter == null) {
                        currentFilter = null
                        // Volver a mostrar resumen
                    } else {
                        searchWithFilter(filter)
                    }
                }
            )
        }

        // Contenido principal
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Mostrar historial y sugerencias cuando NO está buscando
            if (!isSearching) {
                // Historial
                if (query.isEmpty() && searchHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = "Búsquedas recientes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(searchHistory) { historyItem ->
                        SuggestionItem(
                            text = historyItem,
                            isHistory = true,
                            onClick = {
                                query = historyItem
                                performSearch(historyItem)
                            },
                            onFillTextField = { query = historyItem },
                            onDelete = { searchHistory.remove(historyItem) }
                        )
                    }
                }

                // Sugerencias de texto
                if (query.isNotEmpty() && suggestions.isNotEmpty()) {
                    items(suggestions) { suggestion ->
                        SuggestionItem(
                            text = suggestion,
                            isHistory = false,
                            onClick = {
                                query = suggestion
                                performSearch(suggestion)
                            },
                            onFillTextField = { query = suggestion },
                        )
                    }
                }

                // Items sugeridos (canciones, álbumes, etc.)
                if (query.isNotEmpty() && suggestionItems.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    items(suggestionItems, key = { it.id }) { item ->
                        SearchResultItem(
                            item = item,
                            isActive = playerState.currentItem?.id == item.id,
                            isPlaying = playerState.isPlaying,
                            onClick = {
                                when (item) {
                                    is SongItem -> onSongClick(songItemToLibraryItem(item))
                                    is AlbumItem -> onAlbumClick(item.browseId, item.title)
                                    is ArtistItem -> onArtistClick(item.id, item.title)
                                    is PlaylistItem -> onPlaylistClick(item.id, item.title)
                                }
                            },
                        )
                    }
                }
            }

            // Mostrar resultados de búsqueda
            if (isSearching) {
                if (isLoading) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            repeat(8) {
                                ShimmerListItem()
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                } else if (currentFilter != null) {
                    // Resultados filtrados
                    if (filteredResults.isEmpty()) {
                        item {
                            EmptySearchResults()
                        }
                    } else {
                        items(filteredResults, key = { it.id }) { item ->
                            SearchResultItem(
                                item = item,
                                isActive = playerState.currentItem?.id == item.id,
                                isPlaying = playerState.isPlaying,
                                onClick = {
                                    when (item) {
                                        is SongItem -> onSongClick(songItemToLibraryItem(item))
                                        is AlbumItem -> onAlbumClick(item.browseId, item.title)
                                        is ArtistItem -> onArtistClick(item.id, item.title)
                                        is PlaylistItem -> onPlaylistClick(item.id, item.title)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    // Resumen de búsqueda (todas las categorías)
                    searchSummary?.summaries?.forEach { summary ->
                        item {
                            Text(
                                text = summary.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        items(summary.items, key = { "${summary.title}_${it.id}" }) { item ->
                            SearchResultItem(
                                item = item,
                                isActive = playerState.currentItem?.id == item.id,
                                isPlaying = playerState.isPlaying,
                                onClick = {
                                    when (item) {
                                        is SongItem -> onSongClick(songItemToLibraryItem(item))
                                        is AlbumItem -> onAlbumClick(item.browseId, item.title)
                                        is ArtistItem -> onArtistClick(item.id, item.title)
                                        is PlaylistItem -> onPlaylistClick(item.id, item.title)
                                    }
                                },
                            )
                        }
                    }

                    if (searchSummary?.summaries?.isEmpty() == true) {
                        item {
                            EmptySearchResults()
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Auto-focus en la barra de búsqueda
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar canciones, artistas, álbumes...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.key == Key.Enter) {
                        onSearch()
                        true
                    } else false
                }
        )
    }
}

enum class SearchFilter(val value: String, val label: String) {
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D", "Canciones"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D", "Videos"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D", "Álbumes"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D", "Artistas"),
    PLAYLISTS("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D", "Playlists"),
}

@Composable
private fun ChipsRow(
    currentFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = currentFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text("Todo") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
        items(SearchFilter.entries.toList()) { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    }
}

@Composable
private fun SuggestionItem(
    text: String,
    isHistory: Boolean,
    onClick: () -> Unit,
    onFillTextField: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = if (isHistory) Icons.Default.History else Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.alpha(0.5f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isHistory && onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Eliminar")
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f)
        ) {
            Icon(Icons.Default.NorthWest, contentDescription = "Usar sugerencia")
        }
    }
}

@Composable
private fun SearchResultItem(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Thumbnail
        Box(modifier = Modifier.size(56.dp)) {
            RemoteImage(
                url = item.thumbnail,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        when (item) {
                            is ArtistItem -> CircleShape
                            else -> RoundedCornerShape(6.dp)
                        }
                    ),
            )
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitle = when (item) {
                is SongItem -> {
                    val artistsText = item.artists.joinToString { it.name }
                    val albumText = item.album?.name?.let { " • $it" } ?: ""
                    "Canción • $artistsText$albumText"
                }
                is AlbumItem -> {
                    val artistsText = item.artists?.joinToString { it.name } ?: ""
                    val yearText = item.year?.let { " • $it" } ?: ""
                    "Álbum • $artistsText$yearText"
                }
                is ArtistItem -> "Artista"
                is PlaylistItem -> {
                    val authorText = item.author?.name ?: ""
                    "Playlist • $authorText"
                }
                else -> ""
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { /* TODO: Context menu */ }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .alpha(0.5f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No se encontraron resultados",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun songItemToLibraryItem(song: SongItem): LibraryItem {
    return LibraryItem(
        id = song.id,
        title = song.title,
        artist = song.artists.joinToString { it.name },
        artworkUrl = song.thumbnail,
        playbackUrl = "https://music.youtube.com/watch?v=${song.id}",
        durationMs = song.duration?.times(1000)?.toLong(),
    )
}
