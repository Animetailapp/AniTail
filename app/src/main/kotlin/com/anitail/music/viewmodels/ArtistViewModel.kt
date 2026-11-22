package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.pages.ArtistPage
import com.anitail.innertube.pages.ArtistSection
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.ui.screens.artist.artistCatalogTitles
import com.anitail.music.ui.screens.artist.classifyArtistSections
import com.anitail.music.utils.LastFmArtistCache
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject

data class ArtistUiState(
    val artistPage: ArtistPage? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastFmPhotoUrl: String? = null,
    val lastFmBio: String? = null,
    val subscribeSuccessCount: Int = 0,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "Missing artistId navigation argument"
    }

    private val lastFmCache = LastFmArtistCache.getInstance(context)
    private var loadGeneration = 0L
    private var lastFmCollectJob: Job? = null

    private val _uiState = MutableStateFlow(ArtistUiState(isLoading = true))
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = database.artistAlbumsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val likedSongIds: StateFlow<Set<String>> = database.likedSongsByCreateDateAsc()
        .map { songs -> songs.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    init {
        fetchArtistsFromYTM()
    }

    fun refresh() {
        fetchArtistsFromYTM()
    }

    fun fetchArtistsFromYTM() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) { YouTube.artist(artistId) }
            if (generation != loadGeneration) return@launch
            result
                .onSuccess { rawPage ->
                    val sectionsWithLatest = ArtistPage.artistSectionsWithLatestRelease(
                        artist = rawPage.artist,
                        sections = rawPage.sections,
                    )
                    val page = rawPage.copy(sections = sectionsWithLatest)
                    val catalogTitles = artistCatalogTitles(rawPage.sections)
                    ArtistAboutNavCache.put(
                        ArtistAboutSeed(
                            artistId = artistId,
                            artistName = page.artist.title,
                            thumbnailUrl = page.artist.thumbnail,
                            bannerUrl = page.banner,
                            description = page.description,
                            subscriberCountText = page.subscriberCountText,
                            monthlyListenerCount = page.monthlyListenerCount,
                            viewCount = page.viewCount,
                            catalogTitles = catalogTitles,
                        )
                    )
                    _uiState.update {
                        it.copy(
                            artistPage = page,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    syncLibraryArtistMetadata(page)
                    fetchLastFmMetadata(page.artist.title, catalogTitles)
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: throwable.message ?: "Error",
                        )
                    }
                }
        }
    }

    private fun syncLibraryArtistMetadata(page: ArtistPage) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = libraryArtist.value?.artist ?: database.artist(artistId).first()?.artist
            if (existing != null) {
                database.update(existing, page)
            }
        }
    }

    private fun fetchLastFmMetadata(
        artistName: String,
        catalogTitles: List<String>,
    ) {
        if (artistName.isBlank()) return
        lastFmCollectJob?.cancel()
        lastFmCollectJob = viewModelScope.launch {
            lastFmCache.artist(artistId).filterNotNull().collect { data ->
                _uiState.update { state ->
                    state.copy(
                        lastFmPhotoUrl = data.photos.firstOrNull()?.url ?: state.lastFmPhotoUrl,
                        lastFmBio = data.summary,
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                lastFmCache.refresh(artistId, artistName, catalogTitles)
            }
        }
    }

    fun toggleSubscription() {
        val currentArtist = libraryArtist.value?.artist
        val page = _uiState.value.artistPage
        val subscribingNow = currentArtist?.bookmarkedAt == null
        database.transaction {
            if (currentArtist != null) {
                update(currentArtist.toggleLike())
            } else {
                page?.artist?.let { item ->
                    insert(
                        ArtistEntity(
                            id = item.id,
                            name = item.title,
                            channelId = item.channelId,
                            thumbnailUrl = item.thumbnail,
                        ).toggleLike()
                    )
                }
            }
        }
        if (subscribingNow) {
            _uiState.update { it.copy(subscribeSuccessCount = it.subscribeSuccessCount + 1) }
        }
    }

    fun toggleSongLike(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.song(song.id).first()
            database.transaction {
                val updated: SongEntity
                if (existing == null) {
                    insert(song.toMediaMetadata(), SongEntity::toggleLike)
                    updated = song
                        .toMediaMetadata()
                        .toSongEntity()
                        .copy(
                            isEpisode = song.isEpisode,
                            liked = true,
                            likedDate = LocalDateTime.now(),
                            inLibrary = LocalDateTime.now(),
                        )
                } else {
                    updated = existing.song.toggleLike()
                    update(updated)
                }
                if (!updated.isEpisode) {
                    syncUtils.likeSong(updated)
                }
            }
        }
    }

    suspend fun resolveArtistSongs(): List<SongItem> = withContext(Dispatchers.IO) {
        val page = _uiState.value.artistPage ?: return@withContext emptyList()
        val classification = classifyArtistSections(page.sections)
        val section = page.sections.firstOrNull { it.title == classification.topTracksSection }
            ?: page.sections.firstOrNull { section ->
                section.items.filterIsInstance<SongItem>().any { !it.isVideoSong && !it.isEpisode && it.album != null }
            }
            ?: page.sections.firstOrNull { section ->
                section.items.isNotEmpty() && section.items.all { it is SongItem && !it.isVideoSong && !it.isEpisode }
            }
            ?: return@withContext emptyList()

        val loaded = section.moreEndpoint?.let { endpoint ->
            val fromArtistItems = YouTube.artistItems(endpoint).getOrNull()?.items.orEmpty()
            if (fromArtistItems.isNotEmpty()) {
                fromArtistItems
            } else if (endpoint.browseId.startsWith("VL")) {
                YouTube.playlist(endpoint.browseId.removePrefix("VL")).getOrNull()?.songs.orEmpty()
            } else {
                emptyList()
            }
        }.orEmpty().ifEmpty { section.items }

        loaded
            .filterIsInstance<SongItem>()
            .filterNot { it.isVideoSong || it.isEpisode }
            .distinctBy { it.id }
    }

    suspend fun resolveShelfQueue(
        song: SongItem,
        section: ArtistSection,
    ): List<SongItem> {
        val loaded = section.items.filterIsInstance<SongItem>().ifEmpty { listOf(song) }
        val endpoint = section.moreEndpoint?.takeIf { it.browseId.startsWith("VL") } ?: return loaded
        val full = try {
            withTimeoutOrNull(SHELF_PLAYLIST_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    YouTube.playlist(endpoint.browseId.removePrefix("VL"))
                        .getOrNull()
                        ?.songs
                        .orEmpty()
                        .ifEmpty {
                            YouTube.artistItems(endpoint).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        return full?.takeIf { songs -> songs.size > loaded.size && songs.any { it.id == song.id } } ?: loaded
    }

    private companion object {
        const val SHELF_PLAYLIST_TIMEOUT_MS = 3_000L
    }
}
