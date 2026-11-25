package com.anitail.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.innertube.YouTube
import com.anitail.music.ui.screens.artist.artistCatalogTitles
import com.anitail.music.utils.LastFmArtistCache
import com.anitail.music.utils.LastFmArtistData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AboutArtistUiState(
    val seed: ArtistAboutSeed? = null,
    val lastFmData: LastFmArtistData? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

@HiltViewModel
class AboutArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "Missing artistId navigation argument"
    }

    private val cache = LastFmArtistCache.getInstance(context)
    private val initialSeed = ArtistAboutNavCache.get(artistId)
    private val initialCached = cache.get(artistId)

    private val _uiState = MutableStateFlow(
        AboutArtistUiState(
            seed = initialSeed,
            lastFmData = initialCached,
            isLoading = initialCached == null,
        )
    )
    val uiState: StateFlow<AboutArtistUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedLanguage: String? = null
    private var loadedCatalogTitles: List<String>? = null

    init {
        viewModelScope.launch {
            cache.artist(artistId).filterNotNull().collect { data ->
                _uiState.update { it.copy(lastFmData = data, isLoading = false, hasError = false) }
            }
        }
        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        val currentSeed = initialSeed
        if (currentSeed != null && currentSeed.artistName.isNotBlank()) {
            load(currentSeed.artistName, currentSeed.catalogTitles)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            val result = withContext(Dispatchers.IO) { YouTube.artist(artistId) }
            result
                .onSuccess { page ->
                    val catalogTitles = artistCatalogTitles(page.sections)
                    val fetchedSeed = ArtistAboutSeed(
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
                    ArtistAboutNavCache.put(fetchedSeed)
                    _uiState.update { it.copy(seed = fetchedSeed) }
                    if (fetchedSeed.artistName.isNotBlank()) {
                        load(fetchedSeed.artistName, fetchedSeed.catalogTitles)
                    } else {
                        _uiState.update { it.copy(isLoading = false, hasError = it.lastFmData == null) }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = it.lastFmData == null) }
                }
        }
    }

    fun load(
        artistName: String,
        catalogTitles: List<String>,
    ) {
        val language = cache.currentLanguage()
        if (loadJob?.isActive == true && loadedLanguage == language && loadedCatalogTitles == catalogTitles) return
        loadJob?.cancel()
        loadedLanguage = language
        loadedCatalogTitles = catalogTitles
        _uiState.update { it.copy(lastFmData = cache.get(artistId), isLoading = cache.get(artistId) == null) }
        loadJob = viewModelScope.launch {
            try {
                cache.refresh(artistId, artistName, catalogTitles)
                _uiState.update {
                    it.copy(
                        lastFmData = cache.get(artistId) ?: LastFmArtistData(),
                        isLoading = false,
                        hasError = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasError = state.lastFmData == null,
                    )
                }
            }
        }
    }
}

