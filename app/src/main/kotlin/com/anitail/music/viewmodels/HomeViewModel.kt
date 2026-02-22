package com.anitail.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.models.filterExplicit
import com.anitail.innertube.pages.ExplorePage
import com.anitail.innertube.pages.HomePage
import com.anitail.innertube.utils.completed
import com.anitail.music.constants.AccountImageUrlKey
import com.anitail.music.constants.AccountNameKey
import com.anitail.music.constants.DiscordAvatarUrlKey
import com.anitail.music.constants.DiscordTokenKey
import com.anitail.music.constants.DiscordUsernameKey
import com.anitail.music.constants.HideExplicitKey
import com.anitail.music.constants.QuickPicks
import com.anitail.music.constants.QuickPicksKey
import com.anitail.music.constants.YtmSyncKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Album
import com.anitail.music.db.entities.LocalItem
import com.anitail.music.db.entities.Playlist
import com.anitail.music.db.entities.Song
import com.anitail.music.extensions.toEnum
import com.anitail.music.models.SimilarRecommendation
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.utils.reportException
import com.my.kizzy.rpc.KizzyRPC
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: SongItem,
    val relatedEndpoint: BrowseEndpoint?,
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
)

data class HomeContentUiState(
    val quickPicks: List<Song> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val keepListening: List<LocalItem>? = null,
    val similarRecommendations: List<SimilarRecommendation>? = null,
    val accountPlaylists: List<PlaylistItem>? = null,
    val dailyDiscover: List<DailyDiscoverItem>? = null,
    val communityPlaylists: List<CommunityPlaylistItem>? = null,
    val speedDialItems: List<YTItem> = emptyList(),
    val homePage: HomePage? = null,
    val explorePage: ExplorePage? = null,
    val allLocalItems: List<LocalItem> = emptyList(),
    val allYtItems: List<YTItem> = emptyList(),
    val selectedChip: HomePage.Chip? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val accountName: String = "Guest",
    val accountImageUrl: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isSyncing = MutableStateFlow(false)
    val syncStatus = MutableStateFlow<String?>(null)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val speedDialItems = MutableStateFlow<List<YTItem>>(emptyList())
    val homePage = MutableStateFlow<HomePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)

    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())
    private var deferredHomeContentJob: Job? = null

        // Account display info
    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    // Discord avatar URL state
    val discordAvatarUrl = MutableStateFlow<String?>(null)
    // Discord username state
    val discordUsername = MutableStateFlow<String?>(null)

    private data class ContentListSnapshot(
        val quickPicks: List<Song>,
        val forgottenFavorites: List<Song>,
        val keepListening: List<LocalItem>?,
        val similarRecommendations: List<SimilarRecommendation>?,
        val accountPlaylists: List<PlaylistItem>?,
    )

    private data class ContentPageSnapshot(
        val dailyDiscover: List<DailyDiscoverItem>?,
        val communityPlaylists: List<CommunityPlaylistItem>?,
        val speedDialItems: List<YTItem>,
        val homePage: HomePage?,
        val explorePage: ExplorePage?,
    )

    private data class ContentMetaSnapshot(
        val allLocalItems: List<LocalItem>,
        val allYtItems: List<YTItem>,
        val selectedChip: HomePage.Chip?,
        val isLoading: Boolean,
        val isRefreshing: Boolean,
    )

    private data class ContentAccountSnapshot(
        val accountName: String,
        val accountImageUrl: String?,
    )

    private fun observeResolvedSongs(
        source: StateFlow<List<Song>?>,
    ): StateFlow<List<Song>> {
        return source
            .flatMapLatest { baseSongs ->
                if (baseSongs.isNullOrEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        baseSongs.map { fallbackSong ->
                            database.song(fallbackSong.id).map { latestSong ->
                                latestSong ?: fallbackSong
                            }
                        }
                    ) { songsArray ->
                        songsArray.toList()
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
    }

    private val resolvedQuickPicks = observeResolvedSongs(quickPicks)
    private val resolvedForgottenFavorites = observeResolvedSongs(forgottenFavorites)

    private val contentListSnapshot =
        combine(
            resolvedQuickPicks,
            resolvedForgottenFavorites,
            keepListening,
            similarRecommendations,
            accountPlaylists,
        ) { quickPicks, forgottenFavorites, keepListening, similarRecommendations, accountPlaylists ->
            ContentListSnapshot(
                quickPicks = quickPicks,
                forgottenFavorites = forgottenFavorites,
                keepListening = keepListening,
                similarRecommendations = similarRecommendations,
                accountPlaylists = accountPlaylists,
            )
        }

    private val contentPageSnapshot =
        combine(
            dailyDiscover,
            communityPlaylists,
            speedDialItems,
            homePage,
            explorePage,
        ) { dailyDiscover, communityPlaylists, speedDialItems, homePage, explorePage ->
            ContentPageSnapshot(
                dailyDiscover = dailyDiscover,
                communityPlaylists = communityPlaylists,
                speedDialItems = speedDialItems,
                homePage = homePage,
                explorePage = explorePage,
            )
        }

    private val contentMetaSnapshot =
        combine(
            allLocalItems,
            allYtItems,
            selectedChip,
            isLoading,
            isRefreshing,
        ) { allLocalItems, allYtItems, selectedChip, isLoading, isRefreshing ->
            ContentMetaSnapshot(
                allLocalItems = allLocalItems,
                allYtItems = allYtItems,
                selectedChip = selectedChip,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
            )
        }

    private val contentAccountSnapshot =
        combine(accountName, accountImageUrl) { accountName, accountImageUrl ->
            ContentAccountSnapshot(
                accountName = accountName,
                accountImageUrl = accountImageUrl,
            )
        }

    val contentUiState: StateFlow<HomeContentUiState> =
        combine(
            contentListSnapshot,
            contentPageSnapshot,
            contentMetaSnapshot,
            contentAccountSnapshot,
        ) { lists, pages, meta, account ->
            HomeContentUiState(
                quickPicks = lists.quickPicks,
                forgottenFavorites = lists.forgottenFavorites,
                keepListening = lists.keepListening,
                similarRecommendations = lists.similarRecommendations,
                accountPlaylists = lists.accountPlaylists,
                dailyDiscover = pages.dailyDiscover,
                communityPlaylists = pages.communityPlaylists,
                speedDialItems = pages.speedDialItems,
                homePage = pages.homePage,
                explorePage = pages.explorePage,
                allLocalItems = meta.allLocalItems,
                allYtItems = meta.allYtItems,
                selectedChip = meta.selectedChip,
                isLoading = meta.isLoading,
                isRefreshing = meta.isRefreshing,
                accountName = account.accountName,
                accountImageUrl = account.accountImageUrl,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeContentUiState(),
        )

    init {
        try {
            val cachedName = context.dataStore[AccountNameKey]
            if (!cachedName.isNullOrBlank()) accountName.value = cachedName
            val cachedAccountImage = context.dataStore[AccountImageUrlKey]
            if (!cachedAccountImage.isNullOrBlank()) accountImageUrl.value = cachedAccountImage
            val cachedDiscordName = context.dataStore[DiscordUsernameKey]
            if (!cachedDiscordName.isNullOrBlank()) discordUsername.value = cachedDiscordName
            val cachedDiscordAvatar = context.dataStore[DiscordAvatarUrlKey]
            if (!cachedDiscordAvatar.isNullOrBlank()) discordAvatarUrl.value = cachedDiscordAvatar
        } catch (_: Exception) {
        }
    }

    private suspend fun getQuickPicks(){
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> quickPicks.value = database.quickPicks().first().shuffled().take(20)
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    private fun Song.toSongItemOrNull(): SongItem? {
        val thumb = thumbnailUrl ?: return null
        return SongItem(
            id = id,
            title = title,
            artists = artists.map { com.anitail.innertube.models.Artist(name = it.name, id = it.id) },
            album = album?.let { com.anitail.innertube.models.Album(name = it.title, id = it.id) },
            duration = song.duration,
            thumbnail = thumb,
            endpoint = WatchEndpoint(videoId = id),
        )
    }

    private fun rebuildSpeedDialItems() {
        val localSongs = buildList<YTItem> {
            addAll(quickPicks.value.orEmpty().mapNotNull { it.toSongItemOrNull() })
            addAll(keepListening.value.orEmpty().mapNotNull { (it as? Song)?.toSongItemOrNull() })
        }
        val homeItems = homePage.value?.sections?.flatMap { it.items }.orEmpty()
        val similarItems = similarRecommendations.value?.flatMap { it.items }.orEmpty()
        val dailyItems = dailyDiscover.value?.map { it.recommendation }.orEmpty()
        val communityItems = communityPlaylists.value?.map { it.playlist }.orEmpty()

        speedDialItems.value =
            (localSongs + homeItems + similarItems + dailyItems + communityItems)
                .distinctBy { it.id }
                .take(26)
    }

    private fun rebuildAllYtItems() {
        allYtItems.value =
            similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty() +
                dailyDiscover.value?.map { it.recommendation }.orEmpty() +
                communityPlaylists.value?.map { it.playlist }.orEmpty()
    }

    private suspend fun getDailyDiscover() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) {
            dailyDiscover.value = null
            return
        }

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(3)
        val results = mutableListOf<DailyDiscoverItem>()

        seeds.forEach { seed ->
            val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint ?: return@forEach
            val recommendation = YouTube.related(endpoint).getOrNull()
                ?.songs
                ?.firstOrNull { song -> (!hideExplicit || !song.explicit) && song.id != seed.id }

            if (recommendation != null) {
                results += DailyDiscoverItem(
                    seed = seed,
                    recommendation = recommendation,
                    relatedEndpoint = endpoint,
                )
            }
        }

        dailyDiscover.value = results.distinctBy { it.recommendation.id }.ifEmpty { null }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled()
            .take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled()
            .take(2)

        val candidatePlaylists = mutableListOf<PlaylistItem>()
        val blockedAuthors = setOf("YouTube Music", "YouTube", "Playlist")

        artistSeeds.forEach { seed ->
            YouTube.artist(seed.id).onSuccess { page ->
                page.sections.forEach { section ->
                    candidatePlaylists += section.items
                        .filterIsInstance<PlaylistItem>()
                        .filter { playlist ->
                            val authorName = playlist.author?.name ?: ""
                            authorName.isNotBlank() && authorName !in blockedAuthors
                        }
                }
            }
        }

        songSeeds.forEach { seed ->
            val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint ?: return@forEach
            YouTube.related(endpoint).onSuccess { page ->
                candidatePlaylists += page.playlists
                    .filter { playlist ->
                        val authorName = playlist.author?.name ?: ""
                        authorName.isNotBlank() && authorName !in blockedAuthors
                    }
            }
        }

        val playlists = mutableListOf<CommunityPlaylistItem>()
        candidatePlaylists.distinctBy { it.id }.shuffled().take(5).forEach { playlist ->
            YouTube.playlist(playlist.id).onSuccess { page ->
                val songs = page.songs.take(10)
                if (songs.isNotEmpty()) {
                    playlists += CommunityPlaylistItem(
                        playlist = playlist.copy(songCountText = page.playlist.songCountText ?: playlist.songCountText),
                        songs = songs,
                    )
                }
            }
        }

        communityPlaylists.value = playlists.shuffled().ifEmpty { null }
    }

    private suspend fun updateAccountSnapshot() {
        if (YouTube.cookie == null) return

        YouTube.accountInfo().onSuccess { info ->
            val name = info.name
            val image = info.thumbnailUrl
            accountName.value = name
            accountImageUrl.value = image
            try {
                context.dataStore.edit { prefs ->
                    prefs[AccountNameKey] = name
                    if (!image.isNullOrBlank()) prefs[AccountImageUrlKey] = image else prefs.remove(AccountImageUrlKey)
                }
            } catch (_: Exception) {
            }
        }.onFailure {
            reportException(it)
        }

        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            val lists = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
            accountPlaylists.value = lists
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun getSimilarRecommendations(
        hideExplicit: Boolean,
        fromTimeStamp: Long,
    ): List<SimilarRecommendation> {
        val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
            .mapNotNull {
                val items = mutableListOf<YTItem>()
                YouTube.artist(it.id).onSuccess { page ->
                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                    items += page.sections.lastOrNull()?.items.orEmpty()
                }
                SimilarRecommendation(
                    title = it,
                    items = items.filterExplicit(hideExplicit).shuffled().ifEmpty { return@mapNotNull null }
                )
            }

        val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
            .filter { it.album != null }
            .shuffled().take(2)
            .mapNotNull { song ->
                val endpoint =
                    YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                SimilarRecommendation(
                    title = song,
                    items = (page.songs.shuffled().take(8) +
                            page.albums.shuffled().take(4) +
                            page.artists.shuffled().take(4) +
                            page.playlists.shuffled().take(4))
                        .filterExplicit(hideExplicit)
                        .shuffled()
                        .ifEmpty { return@mapNotNull null }
                )
            }

        return (artistRecommendations + songRecommendations).shuffled()
    }

    private fun startDeferredHomeContentLoad(
        hideExplicit: Boolean,
        fromTimeStamp: Long,
    ) {
        deferredHomeContentJob?.cancel()
        deferredHomeContentJob = viewModelScope.launch(Dispatchers.IO) {
            supervisorScope {
                launch {
                    updateAccountSnapshot()
                }
                launch {
                    getDailyDiscover()
                    rebuildAllYtItems()
                    rebuildSpeedDialItems()
                }
                launch {
                    getCommunityPlaylists()
                    rebuildAllYtItems()
                    rebuildSpeedDialItems()
                }
                launch {
                    similarRecommendations.value = getSimilarRecommendations(
                        hideExplicit = hideExplicit,
                        fromTimeStamp = fromTimeStamp,
                    )
                    rebuildAllYtItems()
                    rebuildSpeedDialItems()
                }
            }
        }
    }

    private suspend fun load() {
        deferredHomeContentJob?.cancel()
        consumedContinuations.clear()
        isLoading.value = true

        try {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

            supervisorScope {
                val quickPicksTask = async {
                    getQuickPicks()
                }
                val forgottenFavoritesTask = async {
                    database.forgottenFavorites().first().shuffled().take(20)
                }
                val keepListeningTask = async {
                    val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)
                    val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                        .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
                        .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)
                    (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                }
                val homePageTask = async {
                    val page = YouTube.home().onFailure {
                        reportException(it)
                    }.getOrNull()
                    page?.copy(
                        sections = page.sections.map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit))
                        }
                    )
                }
                val explorePageTask = async {
                    YouTube.explore().onFailure {
                        reportException(it)
                    }.getOrNull()
                }

                quickPicksTask.await()
                forgottenFavorites.value = forgottenFavoritesTask.await()
                keepListening.value = keepListeningTask.await()

                allLocalItems.value =
                    (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                        .filter { it is Song || it is Album }

                isLoading.value = false
                startDeferredHomeContentLoad(
                    hideExplicit = hideExplicit,
                    fromTimeStamp = fromTimeStamp,
                )

                homePage.value = homePageTask.await()
                explorePage.value = explorePageTask.await()

                rebuildAllYtItems()
                rebuildSpeedDialItems()
            }
        } finally {
            if (isLoading.value) isLoading.value = false
        }
    }

    private suspend fun songLoad(){
        val song = database.events().first().firstOrNull()?.song
        if (song != null) {
            println(song.song.title)
            if (database.hasRelatedSongs(song.id)){
                val relatedSongs = database.getRelatedSongs(song.id).first().shuffled().take(20)
                quickPicks.value = relatedSongs
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    private val consumedContinuations = Collections.synchronizedSet(mutableSetOf<String>())

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation.isNullOrBlank()) return

        synchronized(consumedContinuations) {
            if (_isLoadingMore.value || continuation in consumedContinuations) return
            // Reserve continuation immediately to avoid concurrent requests during fast scroll.
            consumedContinuations += continuation
        }
        _isLoadingMore.value = true

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextSections = YouTube.home(continuation).getOrNull()
                if (nextSections == null) {
                    synchronized(consumedContinuations) {
                        consumedContinuations.remove(continuation)
                    }
                    return@launch
                }

                homePage.value = nextSections.copy(
                    chips = homePage.value?.chips,
                    sections = (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                        section.copy(items = section.items.filterExplicit(hideExplicit))
                    }
                )
                rebuildAllYtItems()
                rebuildSpeedDialItems()
            } catch (e: Exception) {
                // Allow retry if this continuation fails before being applied.
                synchronized(consumedContinuations) {
                    consumedContinuations.remove(continuation)
                }
                reportException(e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            consumedContinuations.clear()
            rebuildAllYtItems()
            rebuildSpeedDialItems()
            return
        }

        if (selectedChip.value == null) {
            // store the actual homepage for deselecting chips
            previousHomePage.value = homePage.value
        }
        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit))
                }
            )
            selectedChip.value = chip
            consumedContinuations.clear()
            rebuildAllYtItems()
            rebuildSpeedDialItems()
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        // Cloud Sync (Smart Merge) - with throttling built into syncCloud()
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("HomeViewModel: Starting cloud sync...")
            isSyncing.value = true
            syncStatus.value = "Sincronizando..."
            try {
                val result = syncUtils.syncCloud()
                if (result != null) {
                    Timber.d("HomeViewModel: Cloud sync result: $result")
                    syncStatus.value = result
                } else {
                    syncStatus.value = null
                }
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: Cloud sync failed")
                syncStatus.value = "Error de sincronización"
            } finally {
                isSyncing.value = false
                // Clear status after 3 seconds
                kotlinx.coroutines.delay(3000)
                syncStatus.value = null
            }
        }

        // YouTube Music sync
        viewModelScope.launch(Dispatchers.IO) {
            val isSyncEnabled = context.dataStore.data
                .map { it[YtmSyncKey] ?: true }
                .distinctUntilChanged()
                .first()

            if (isSyncEnabled) {
                supervisorScope {
                    launch { syncUtils.syncLikedSongs() }
                    launch { syncUtils.syncLibrarySongs() }
                    launch { syncUtils.syncSavedPlaylists() }
                    launch { syncUtils.syncLikedAlbums() }
                    launch { syncUtils.syncArtistsSubscriptions() }
                    launch { syncUtils.syncWatchHistory() }
                }
            }
        }

        // Listen for Discord token changes and fetch Discord avatar
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data.map { it[DiscordTokenKey] ?: "" }.distinctUntilChanged().collect { token ->
                if (token.isNotEmpty()) {
                    runCatching {
                            KizzyRPC.getUserInfo(token)
                        }.onSuccess { result ->
                            val info = result.getOrNull()
                            if (info != null) {
                                val dAvatar = info.avatarUrl
                                val dUser = info.name.takeIf { it.isNotEmpty() } ?: info.username.takeIf { it.isNotEmpty() }
                                discordAvatarUrl.value = dAvatar
                                discordUsername.value = dUser
                                try {
                                    context.dataStore.edit { prefs ->
                                        if (!dUser.isNullOrBlank()) prefs[DiscordUsernameKey] = dUser else prefs.remove(DiscordUsernameKey)
                                        if (!dAvatar.isNullOrBlank()) prefs[DiscordAvatarUrlKey] = dAvatar else prefs.remove(DiscordAvatarUrlKey)
                                    }
                                } catch (_: Exception) {}
                            } else {
                                discordAvatarUrl.value = null
                                discordUsername.value = null
                            }
                        }.onFailure {
                            discordAvatarUrl.value = null
                            discordUsername.value = null
                        }
                } else {
                    discordAvatarUrl.value = null
                    discordUsername.value = null
                }
            }
        }
    }
}
