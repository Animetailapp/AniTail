package com.anitail.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.YouTubeMusicPlayer
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.ExplorePage
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.net.URI
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTopBar(
    title: String,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onHistory) {
                Icon(IconsHistory, contentDescription = "Historial")
            }
            IconButton(onClick = onStats) {
                Icon(IconsStats, contentDescription = "Estadisticas")
            }
            IconButton(onClick = onSearch) {
                Icon(IconsSearch, contentDescription = "Buscar")
            }
            IconButton(onClick = onSettings) {
                Icon(IconsSettings, contentDescription = "Ajustes")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
fun HomeScreen(
    homePage: HomePage?,
    selectedChip: HomePage.Chip?,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    quickPicks: List<LibraryItem>,
    keepListening: List<LibraryItem>,
    forgottenFavorites: List<LibraryItem>,
    onChipSelected: (HomePage.Chip?) -> Unit,
    onLoadMore: () -> Unit,
    onItemSelected: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle(title = "Para ti")
        }

        homePage?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
            item {
                HomeChipsRow(
                    chips = chips,
                    selectedChip = selectedChip,
                    onSelected = onChipSelected,
                )
            }
        }

        if (isLoading && homePage == null) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (quickPicks.isNotEmpty()) {
            item { SectionHeader(title = "Quick picks", label = null, usePrimary = true) }
            item {
                QuickPicksGrid(
                    items = quickPicks,
                    onPrimary = onItemSelected,
                    onSecondary = onAddToLibrary,
                )
            }
        }

        if (keepListening.isNotEmpty()) {
            item { SectionHeader(title = "Keep listening", label = null, usePrimary = true) }
            item {
                KeepListeningRow(items = keepListening, onOpen = onItemSelected)
            }
        }

        if (forgottenFavorites.isNotEmpty()) {
            item { SectionHeader(title = "Favoritos olvidados", label = null, usePrimary = true) }
            item {
                QuickPicksGrid(
                    items = forgottenFavorites,
                    onPrimary = onItemSelected,
                    onSecondary = onAddToLibrary,
                )
            }
        }

        homePage?.sections?.forEach { section ->
            item {
                SectionHeader(title = section.title, label = section.label)
            }
            item {
                HomeSectionRow(
                    items = section.items,
                    onItemSelected = onItemSelected,
                    onAddToLibrary = onAddToLibrary,
                )
            }
        }

        if (isLoadingMore) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    query: String,
    searchState: SearchState,
    explorePage: ExplorePage?,
    chartsPage: ChartsPage?,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onAddToLibrary: (LibraryItem) -> Unit,
    requestFocus: Boolean,
    onRequestFocusHandled: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onRequestFocusHandled()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Buscar",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Busca canciones o artistas") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSearch) {
                Text("Buscar")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (searchState.isLoading) {
            Text("Buscando...")
        }
        searchState.errorMessage?.let { error ->
            Text(error)
        }

        if (!searchState.isLoading && searchState.results.isNotEmpty()) {
            SectionTitle(title = "Resultados")
            ItemList(
                items = searchState.results,
                primaryAction = "Reproducir",
                onPrimaryAction = onPlay,
                secondaryAction = "Agregar",
                onSecondaryAction = onAddToLibrary,
            )
        } else if (!searchState.isLoading) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            chartsPage?.sections?.forEach { section ->
                SectionHeader(title = section.title, label = null)
                ChartsRow(
                    items = section.items,
                    onPlay = onPlay,
                    onAddToLibrary = onAddToLibrary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            explorePage?.newReleaseAlbums?.let { albums ->
                SectionHeader(title = "Nuevos lanzamientos", label = null)
                YtItemRow(items = albums, onItemSelected = { }, onAddToLibrary = { })
            }
            explorePage?.moodAndGenres?.let { moods ->
                SectionHeader(title = "Mood y generos", label = null)
                MoodGenresRow(items = moods.map { it.title })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle(title = "Mood y generos")
        ChipsRow(
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

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    filterState: MutableState<LibraryFilter>,
    onPlay: (LibraryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Biblioteca",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ChipsRow(
            chips = LibraryFilter.values().map { it.label },
            selectedIndex = filterState.value.ordinal,
            onSelected = { index ->
                filterState.value = LibraryFilter.values()[index]
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        val playlists = items.filter { it.playbackUrl.contains("playlist?list=") }
        val artists = items.filter { it.playbackUrl.contains("/channel/") }
        val songs = items.filterNot { it in playlists || it in artists }

        when (filterState.value) {
            LibraryFilter.TODOS -> {
                if (playlists.isNotEmpty()) {
                    SectionHeader(title = "Playlists", label = null)
                    HorizontalItemRow(items = playlists, onPlay = onPlay)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (artists.isNotEmpty()) {
                    SectionHeader(title = "Artistas", label = null)
                    HorizontalItemRow(items = artists, onPlay = onPlay)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (songs.isNotEmpty()) {
                    SectionHeader(title = "Canciones", label = null)
                    ItemList(items = songs, primaryAction = "Reproducir", onPrimaryAction = onPlay)
                } else if (playlists.isEmpty() && artists.isEmpty()) {
                    Text("Aun no hay elementos en tu biblioteca.")
                }
            }

            LibraryFilter.CANCIONES -> {
                if (songs.isEmpty()) {
                    Text("No hay canciones guardadas.")
                } else {
                    ItemList(items = songs, primaryAction = "Reproducir", onPrimaryAction = onPlay)
                }
            }

            LibraryFilter.ALBUMES -> {
                Text("Los albumes apareceran aqui cuando esten disponibles en Desktop.")
            }

            LibraryFilter.ARTISTAS -> {
                if (artists.isEmpty()) {
                    Text("No hay artistas guardados.")
                } else {
                    HorizontalItemRow(items = artists, onPlay = onPlay)
                }
            }

            LibraryFilter.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    Text("No hay playlists guardadas.")
                } else {
                    HorizontalItemRow(items = playlists, onPlay = onPlay)
                }
            }

            LibraryFilter.DESCARGAS -> {
                Text("Descargas locales no disponibles en Desktop por ahora.")
            }
        }
    }
}

@Composable
fun PlayerScreen(
    item: LibraryItem?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Reproductor",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (item == null) {
            Text("Selecciona una cancion desde la busqueda o la biblioteca.")
            return
        }

        NowPlayingHeader(title = item.title, artist = item.artist)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                PlayerControls()
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { openInBrowser(item.playbackUrl) },
                ) {
                    Text("Abrir en navegador")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxSize(),
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            YouTubeMusicPlayer(
                url = item.playbackUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun HistoryScreen(
    items: List<LibraryItem>,
    onPlay: (LibraryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Historial",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("No hay reproducciones recientes.")
        } else {
            ItemList(items = items, primaryAction = "Reproducir", onPrimaryAction = onPlay)
        }
    }
}

@Composable
fun StatsScreen(
    items: List<LibraryItem>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Estadisticas",
            style = MaterialTheme.typography.headlineSmall,
        )
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Canciones en biblioteca", style = MaterialTheme.typography.titleMedium)
                Text(items.size.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reproducciones recientes", style = MaterialTheme.typography.titleMedium)
                Text(items.take(5).size.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(
            text = "Mas estadisticas llegaran pronto en Desktop.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineSmall,
        )
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Text("Predeterminado (Desktop)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reproduccion", style = MaterialTheme.typography.titleMedium)
                Text("WebView YouTube Music", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "Mas opciones se agregaran a medida que portemos ajustes Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NowPlayingHeader(title: String, artist: String) {
    Column {
        Text(
            text = "Reproduciendo ahora",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerControls() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { }) {
            Icon(IconsRepeat, contentDescription = "Repetir")
        }
        IconButton(onClick = { }) {
            Icon(IconsPrev, contentDescription = "Anterior")
        }
        Button(onClick = { }) {
            Text("Play/Pause")
        }
        IconButton(onClick = { }) {
            Icon(IconsNext, contentDescription = "Siguiente")
        }
        IconButton(onClick = { }) {
            Icon(IconsShuffle, contentDescription = "Shuffle")
        }
    }
}

@Composable
fun ItemList(
    items: List<LibraryItem>,
    primaryAction: String,
    onPrimaryAction: (LibraryItem) -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: ((LibraryItem) -> Unit)? = null,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items) { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = item.artist, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = { onPrimaryAction(item) }) {
                    Text(primaryAction)
                }
                if (secondaryAction != null && onSecondaryAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onSecondaryAction(item) }) {
                        Text(secondaryAction)
                    }
                }
            }
            HorizontalDivider()
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

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    label: String?,
    usePrimary: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = if (usePrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<YTItem>,
    onItemSelected: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            HomeItemCard(
                item = item,
                onPrimary = { onItemSelected(item) },
                onSecondary = { onAddToLibrary(item) },
            )
        }
    }
}

@Composable
private fun HomeItemCard(
    item: YTItem,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val (title, subtitle) = when (item) {
        is SongItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is AlbumItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is ArtistItem -> item.title to "Artista"
        is PlaylistItem -> item.title to item.author?.name.orEmpty()
        else -> "" to ""
    }

    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable { onPrimary() },
    ) {
        RemoteImage(
            url = itemThumbnail(item),
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
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
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary) { Text("Reproducir") }
            OutlinedButton(onClick = onSecondary) { Text("Agregar") }
        }
    }
}

@Composable
private fun QuickPicksGrid(
    items: List<LibraryItem>,
    onPrimary: (YTItem) -> Unit,
    onSecondary: (YTItem) -> Unit,
) {
    val rows = 4
    val itemHeight = ListItemHeight
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * rows),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            QuickPickRowItem(
                title = item.title,
                subtitle = item.artist,
                thumbnail = item.artworkUrl,
                onPrimary = { onPrimary(ytItemFromLibrary(item)) },
                onSecondary = { onSecondary(ytItemFromLibrary(item)) },
            )
        }
    }
}

@Composable
private fun QuickPickRowItem(
    title: String,
    subtitle: String,
    thumbnail: String?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(320.dp)
            .height(ListItemHeight)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onPrimary() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = thumbnail,
            modifier = Modifier.size(ListThumbnailSize),
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onSecondary) {
            Icon(IconsMore, contentDescription = "Menu")
        }
    }
}

@Composable
private fun KeepListeningRow(
    items: List<LibraryItem>,
    onOpen: (YTItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items) { item ->
            val isArtist = item.playbackUrl.contains("/channel/")
            val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clickable { onOpen(ytItemFromLibrary(item)) },
            ) {
                Box {
                    RemoteImage(
                        url = item.artworkUrl,
                        modifier = Modifier
                            .size(GridThumbnailHeight)
                            .clip(shape),
                        shape = shape,
                    )
                    if (!isArtist) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                )
                                .padding(4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
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
                    .width(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                Text(
                    libraryItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    libraryItem.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(libraryItem) }) { Text("Reproducir") }
                    OutlinedButton(onClick = { onAddToLibrary(libraryItem) }) { Text("Agregar") }
                }
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
            HomeItemCard(
                item = item,
                onPrimary = { onItemSelected(item) },
                onSecondary = { onAddToLibrary(item) },
            )
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

private fun ytItemFromLibrary(item: LibraryItem): YTItem {
    return SongItem(
        id = item.id,
        title = item.title,
        artists = emptyList(),
        thumbnail = item.artworkUrl.orEmpty(),
        explicit = false,
    )
}

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

@Composable
private fun RemoteImage(
    url: String?,
    modifier: Modifier,
    shape: Shape,
) {
    val cached = remember(url) { ImageCache.get(url) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(cached) }

    LaunchedEffect(url) {
        if (image != null || url.isNullOrBlank()) return@LaunchedEffect
        val bytes = withContext(Dispatchers.IO) {
            runCatching { URL(url).readBytes() }.getOrNull()
        } ?: return@LaunchedEffect
        val bitmap = runCatching { Image.makeFromEncoded(bytes).asImageBitmap() }.getOrNull()
        if (bitmap != null) {
            ImageCache.put(url, bitmap)
            image = bitmap
        }
    }

    if (image == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        androidx.compose.foundation.Image(
            bitmap = image!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    }
}

private fun itemThumbnail(item: YTItem): String? {
    return when (item) {
        is SongItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        is ArtistItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
        else -> null
    }
}

private val IconsMore
    @Composable get() = androidx.compose.material.icons.Icons.Filled.MoreVert

private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val GridThumbnailHeight = 128.dp
private val ThumbnailCornerRadius = 6.dp

private object ImageCache {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun get(url: String?): ImageBitmap? = if (url == null) null else cache[url]

    fun put(url: String, bitmap: ImageBitmap) {
        cache[url] = bitmap
    }
}

@Composable
private fun HomeChipsRow(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onSelected: (HomePage.Chip?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips) { chip ->
            val selected = chip == selectedChip
            val background =
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val content =
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelected(chip) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = chip.title,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipsRow(
    chips: List<String>,
    selectedIndex: Int = 0,
    onSelected: (Int) -> Unit = {},
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips.size) { index ->
            val selected = index == selectedIndex
            val background =
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val content =
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelected(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = chips[index],
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

enum class LibraryFilter(val label: String) {
    TODOS("Todo"),
    CANCIONES("Canciones"),
    ALBUMES("Albumes"),
    ARTISTAS("Artistas"),
    PLAYLISTS("Playlists"),
    DESCARGAS("Descargas"),
}

private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

private val IconsHistory
    @Composable get() = IconAssets.history()

private val IconsStats
    @Composable get() = IconAssets.stats()

private val IconsSearch
    @Composable get() = IconAssets.search()

private val IconsSettings
    @Composable get() = IconAssets.settings()

private val IconsRepeat
    @Composable get() = IconAssets.repeat()

private val IconsPrev
    @Composable get() = IconAssets.previous()

private val IconsNext
    @Composable get() = IconAssets.next()

private val IconsShuffle
    @Composable get() = IconAssets.shuffle()
