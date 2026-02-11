package com.anitail.desktop.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.anitail.desktop.ui.screen.library.AlbumFilter
import com.anitail.desktop.ui.screen.library.AlbumSortType
import com.anitail.desktop.ui.screen.library.ArtistFilter
import com.anitail.desktop.ui.screen.library.ArtistSortType
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.LibraryFilter
import com.anitail.desktop.ui.screen.library.LibraryViewType
import com.anitail.desktop.ui.screen.library.MixSortType
import com.anitail.desktop.ui.screen.library.PlaylistSortType
import com.anitail.desktop.ui.screen.library.SongFilter
import com.anitail.desktop.ui.screen.library.SongSortType
import com.anitail.desktop.i18n.SYSTEM_DEFAULT

/**
 * Desktop preferences storage system.
 * Provides persistent settings storage using JSON file.
 * Mirrors Android's DataStore/SharedPreferences functionality.
 */
class DesktopPreferences private constructor(
    private val filePath: Path = defaultPreferencesPath(),
) {
    // === Appearance Settings ===
    private val _darkMode = MutableStateFlow(DarkModePreference.AUTO)
    val darkMode: StateFlow<DarkModePreference> = _darkMode.asStateFlow()

    private val _pureBlack = MutableStateFlow(false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _playerBackgroundStyle = MutableStateFlow(PlayerBackgroundStyle.DEFAULT)
    val playerBackgroundStyle: StateFlow<PlayerBackgroundStyle> = _playerBackgroundStyle.asStateFlow()

    private val _playerButtonsStyle = MutableStateFlow(PlayerButtonsStyle.DEFAULT)
    val playerButtonsStyle: StateFlow<PlayerButtonsStyle> = _playerButtonsStyle.asStateFlow()

    private val _sliderStyle = MutableStateFlow(SliderStyle.DEFAULT)
    val sliderStyle: StateFlow<SliderStyle> = _sliderStyle.asStateFlow()

    private val _densityScale = MutableStateFlow(1.0f)
    val densityScale: StateFlow<Float> = _densityScale.asStateFlow()

    private val _customDensityValue = MutableStateFlow(0.85f)
    val customDensityValue: StateFlow<Float> = _customDensityValue.asStateFlow()

    private val _defaultOpenTab = MutableStateFlow(NavigationTabPreference.HOME)
    val defaultOpenTab: StateFlow<NavigationTabPreference> = _defaultOpenTab.asStateFlow()

    private val _defaultLibChip = MutableStateFlow(LibraryFilter.LIBRARY)
    val defaultLibChip: StateFlow<LibraryFilter> = _defaultLibChip.asStateFlow()

    private val _slimNavBar = MutableStateFlow(false)
    val slimNavBar: StateFlow<Boolean> = _slimNavBar.asStateFlow()

    private val _swipeToSong = MutableStateFlow(false)
    val swipeToSong: StateFlow<Boolean> = _swipeToSong.asStateFlow()

    private val _swipeThumbnail = MutableStateFlow(true)
    val swipeThumbnail: StateFlow<Boolean> = _swipeThumbnail.asStateFlow()

    private val _swipeSensitivity = MutableStateFlow(0.73f)
    val swipeSensitivity: StateFlow<Float> = _swipeSensitivity.asStateFlow()

    private val _lyricsTextPosition = MutableStateFlow(LyricsPositionPreference.CENTER)
    val lyricsTextPosition: StateFlow<LyricsPositionPreference> = _lyricsTextPosition.asStateFlow()

    private val _lyricsClick = MutableStateFlow(true)
    val lyricsClick: StateFlow<Boolean> = _lyricsClick.asStateFlow()

    private val _lyricsScroll = MutableStateFlow(true)
    val lyricsScroll: StateFlow<Boolean> = _lyricsScroll.asStateFlow()

    private val _lyricsFontSize = MutableStateFlow(20f)
    val lyricsFontSize: StateFlow<Float> = _lyricsFontSize.asStateFlow()

    private val _lyricsCustomFontPath = MutableStateFlow("")
    val lyricsCustomFontPath: StateFlow<String> = _lyricsCustomFontPath.asStateFlow()

    private val _lyricsSmoothScroll = MutableStateFlow(true)
    val lyricsSmoothScroll: StateFlow<Boolean> = _lyricsSmoothScroll.asStateFlow()

    private val _lyricsAnimationStyle = MutableStateFlow(LyricsAnimationStylePreference.APPLE)
    val lyricsAnimationStyle: StateFlow<LyricsAnimationStylePreference> = _lyricsAnimationStyle.asStateFlow()

    // === Player Settings ===
    private val _audioQuality = MutableStateFlow(AudioQuality.AUTO)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _normalizeAudio = MutableStateFlow(true)
    val normalizeAudio: StateFlow<Boolean> = _normalizeAudio.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(0) // in seconds, 0 = disabled
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    private val _historyDuration = MutableStateFlow(30f) // seconds
    val historyDuration: StateFlow<Float> = _historyDuration.asStateFlow()

    private val _persistentQueue = MutableStateFlow(true)
    val persistentQueue: StateFlow<Boolean> = _persistentQueue.asStateFlow()

    private val _autoStartRadio = MutableStateFlow(true)
    val autoStartRadio: StateFlow<Boolean> = _autoStartRadio.asStateFlow()

    private val _queueEditLocked = MutableStateFlow(true)
    val queueEditLocked: StateFlow<Boolean> = _queueEditLocked.asStateFlow()

    // === Content Settings ===
    private val _contentLanguage = MutableStateFlow("es")
    val contentLanguage: StateFlow<String> = _contentLanguage.asStateFlow()

    private val _contentCountry = MutableStateFlow("ES")
    val contentCountry: StateFlow<String> = _contentCountry.asStateFlow()

    private val _appLanguage = MutableStateFlow(SYSTEM_DEFAULT)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _hideExplicit = MutableStateFlow(false)
    val hideExplicit: StateFlow<Boolean> = _hideExplicit.asStateFlow()

    private val _quickPicks = MutableStateFlow(QuickPicks.QUICK_PICKS)
    val quickPicks: StateFlow<QuickPicks> = _quickPicks.asStateFlow()

    private val _enableBetterLyrics = MutableStateFlow(true)
    val enableBetterLyrics: StateFlow<Boolean> = _enableBetterLyrics.asStateFlow()

    private val _enableSimpMusic = MutableStateFlow(true)
    val enableSimpMusic: StateFlow<Boolean> = _enableSimpMusic.asStateFlow()

    private val _enableLrcLib = MutableStateFlow(true)
    val enableLrcLib: StateFlow<Boolean> = _enableLrcLib.asStateFlow()

    private val _enableKuGou = MutableStateFlow(true)
    val enableKuGou: StateFlow<Boolean> = _enableKuGou.asStateFlow()

    private val _preferredLyricsProvider = MutableStateFlow(PreferredLyricsProvider.BETTER_LYRICS)
    val preferredLyricsProvider: StateFlow<PreferredLyricsProvider> = _preferredLyricsProvider.asStateFlow()

    // === Library Settings ===
    private val _libraryFilter = MutableStateFlow(LibraryFilter.LIBRARY)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    private val _mixViewType = MutableStateFlow(LibraryViewType.GRID)
    val mixViewType: StateFlow<LibraryViewType> = _mixViewType.asStateFlow()

    private val _playlistViewType = MutableStateFlow(LibraryViewType.GRID)
    val playlistViewType: StateFlow<LibraryViewType> = _playlistViewType.asStateFlow()

    private val _albumViewType = MutableStateFlow(LibraryViewType.GRID)
    val albumViewType: StateFlow<LibraryViewType> = _albumViewType.asStateFlow()

    private val _artistViewType = MutableStateFlow(LibraryViewType.GRID)
    val artistViewType: StateFlow<LibraryViewType> = _artistViewType.asStateFlow()

    private val _gridItemSize = MutableStateFlow(GridItemSize.BIG)
    val gridItemSize: StateFlow<GridItemSize> = _gridItemSize.asStateFlow()

    private val _mixSortType = MutableStateFlow(MixSortType.CREATE_DATE)
    val mixSortType: StateFlow<MixSortType> = _mixSortType.asStateFlow()

    private val _mixSortDescending = MutableStateFlow(true)
    val mixSortDescending: StateFlow<Boolean> = _mixSortDescending.asStateFlow()

    private val _playlistSortType = MutableStateFlow(PlaylistSortType.CREATE_DATE)
    val playlistSortType: StateFlow<PlaylistSortType> = _playlistSortType.asStateFlow()

    private val _playlistSortDescending = MutableStateFlow(true)
    val playlistSortDescending: StateFlow<Boolean> = _playlistSortDescending.asStateFlow()

    private val _albumSortType = MutableStateFlow(AlbumSortType.CREATE_DATE)
    val albumSortType: StateFlow<AlbumSortType> = _albumSortType.asStateFlow()

    private val _albumSortDescending = MutableStateFlow(true)
    val albumSortDescending: StateFlow<Boolean> = _albumSortDescending.asStateFlow()

    private val _artistSortType = MutableStateFlow(ArtistSortType.CREATE_DATE)
    val artistSortType: StateFlow<ArtistSortType> = _artistSortType.asStateFlow()

    private val _artistSortDescending = MutableStateFlow(true)
    val artistSortDescending: StateFlow<Boolean> = _artistSortDescending.asStateFlow()

    private val _songSortType = MutableStateFlow(SongSortType.CREATE_DATE)
    val songSortType: StateFlow<SongSortType> = _songSortType.asStateFlow()

    private val _songSortDescending = MutableStateFlow(true)
    val songSortDescending: StateFlow<Boolean> = _songSortDescending.asStateFlow()

    private val _songFilter = MutableStateFlow(SongFilter.LIKED)
    val songFilter: StateFlow<SongFilter> = _songFilter.asStateFlow()

    private val _albumFilter = MutableStateFlow(AlbumFilter.LIKED)
    val albumFilter: StateFlow<AlbumFilter> = _albumFilter.asStateFlow()

    private val _artistFilter = MutableStateFlow(ArtistFilter.LIKED)
    val artistFilter: StateFlow<ArtistFilter> = _artistFilter.asStateFlow()

    private val _showLikedPlaylist = MutableStateFlow(true)
    val showLikedPlaylist: StateFlow<Boolean> = _showLikedPlaylist.asStateFlow()

    private val _showDownloadedPlaylist = MutableStateFlow(true)
    val showDownloadedPlaylist: StateFlow<Boolean> = _showDownloadedPlaylist.asStateFlow()

    private val _showTopPlaylist = MutableStateFlow(true)
    val showTopPlaylist: StateFlow<Boolean> = _showTopPlaylist.asStateFlow()

    private val _showCachedPlaylist = MutableStateFlow(true)
    val showCachedPlaylist: StateFlow<Boolean> = _showCachedPlaylist.asStateFlow()

    // === Account Settings ===
    private val _useLoginForBrowse = MutableStateFlow(true)
    val useLoginForBrowse: StateFlow<Boolean> = _useLoginForBrowse.asStateFlow()

    private val _ytmSync = MutableStateFlow(true)
    val ytmSync: StateFlow<Boolean> = _ytmSync.asStateFlow()

    private val _discordToken = MutableStateFlow("")
    val discordToken: StateFlow<String> = _discordToken.asStateFlow()

    private val _discordUsername = MutableStateFlow("")
    val discordUsername: StateFlow<String> = _discordUsername.asStateFlow()

    private val _discordAvatarUrl = MutableStateFlow("")
    val discordAvatarUrl: StateFlow<String> = _discordAvatarUrl.asStateFlow()

    private val _preferredAvatarSource = MutableStateFlow(AvatarSourcePreference.YOUTUBE)
    val preferredAvatarSource: StateFlow<AvatarSourcePreference> = _preferredAvatarSource.asStateFlow()

    // === Privacy Settings ===
    private val _pauseListenHistory = MutableStateFlow(false)
    val pauseListenHistory: StateFlow<Boolean> = _pauseListenHistory.asStateFlow()

    private val _pauseSearchHistory = MutableStateFlow(false)
    val pauseSearchHistory: StateFlow<Boolean> = _pauseSearchHistory.asStateFlow()

    // === Storage Settings ===
    private val _maxImageCacheSizeMB = MutableStateFlow(500)
    val maxImageCacheSizeMB: StateFlow<Int> = _maxImageCacheSizeMB.asStateFlow()

    private val _maxSongCacheSizeMB = MutableStateFlow(1000)
    val maxSongCacheSizeMB: StateFlow<Int> = _maxSongCacheSizeMB.asStateFlow()

    private val _downloadAsMp3 = MutableStateFlow(true)
    val downloadAsMp3: StateFlow<Boolean> = _downloadAsMp3.asStateFlow()

    // === Lyrics Settings ===
    private val _showLyrics = MutableStateFlow(true)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _romanizeLyrics = MutableStateFlow(false)
    val romanizeLyrics: StateFlow<Boolean> = _romanizeLyrics.asStateFlow()

    private val _youtubeCookie = MutableStateFlow<String?>(null)
    val youtubeCookie: StateFlow<String?> = _youtubeCookie.asStateFlow()

    // === Setters ===

    fun setDarkMode(value: DarkModePreference) {
        _darkMode.value = value
        save()
    }

    fun setPureBlack(value: Boolean) {
        _pureBlack.value = value
        save()
    }

    fun setDynamicColor(value: Boolean) {
        _dynamicColor.value = value
        save()
    }

    fun setPlayerBackgroundStyle(value: PlayerBackgroundStyle) {
        _playerBackgroundStyle.value = value
        save()
    }

    fun setPlayerButtonsStyle(value: PlayerButtonsStyle) {
        _playerButtonsStyle.value = value
        save()
    }

    fun setSliderStyle(value: SliderStyle) {
        _sliderStyle.value = value
        save()
    }

    fun setDensityScale(value: Float) {
        _densityScale.value = value.coerceIn(0.5f, 1.2f)
        save()
    }

    fun setCustomDensityValue(value: Float) {
        _customDensityValue.value = value.coerceIn(0.5f, 1.2f)
        save()
    }

    fun setDefaultOpenTab(value: NavigationTabPreference) {
        _defaultOpenTab.value = value
        save()
    }

    fun setDefaultLibChip(value: LibraryFilter) {
        _defaultLibChip.value = value
        save()
    }

    fun setSlimNavBar(value: Boolean) {
        _slimNavBar.value = value
        save()
    }

    fun setSwipeToSong(value: Boolean) {
        _swipeToSong.value = value
        save()
    }

    fun setSwipeThumbnail(value: Boolean) {
        _swipeThumbnail.value = value
        save()
    }

    fun setSwipeSensitivity(value: Float) {
        _swipeSensitivity.value = value.coerceIn(0f, 1f)
        save()
    }

    fun setLyricsTextPosition(value: LyricsPositionPreference) {
        _lyricsTextPosition.value = value
        save()
    }

    fun setLyricsClick(value: Boolean) {
        _lyricsClick.value = value
        save()
    }

    fun setLyricsScroll(value: Boolean) {
        _lyricsScroll.value = value
        save()
    }

    fun setLyricsFontSize(value: Float) {
        _lyricsFontSize.value = value.coerceIn(12f, 36f)
        save()
    }

    fun setLyricsCustomFontPath(value: String) {
        _lyricsCustomFontPath.value = value
        save()
    }

    fun setLyricsSmoothScroll(value: Boolean) {
        _lyricsSmoothScroll.value = value
        save()
    }

    fun setLyricsAnimationStyle(value: LyricsAnimationStylePreference) {
        _lyricsAnimationStyle.value = value
        save()
    }

    fun setAudioQuality(value: AudioQuality) {
        _audioQuality.value = value
        save()
    }

    fun setNormalizeAudio(value: Boolean) {
        _normalizeAudio.value = value
        save()
    }

    fun setSkipSilence(value: Boolean) {
        _skipSilence.value = value
        save()
    }

    fun setCrossfadeDuration(value: Int) {
        _crossfadeDuration.value = value.coerceIn(0, 12)
        save()
    }

    fun setHistoryDuration(value: Float) {
        _historyDuration.value = value.coerceIn(0f, 120f)
        save()
    }

    fun setPersistentQueue(value: Boolean) {
        _persistentQueue.value = value
        save()
    }

    fun setAutoStartRadio(value: Boolean) {
        _autoStartRadio.value = value
        save()
    }

    fun setQueueEditLocked(value: Boolean) {
        _queueEditLocked.value = value
        save()
    }

    fun setContentLanguage(value: String) {
        _contentLanguage.value = value
        save()
    }

    fun setContentCountry(value: String) {
        _contentCountry.value = value
        save()
    }

    fun setAppLanguage(value: String) {
        _appLanguage.value = value
        save()
    }

    fun setHideExplicit(value: Boolean) {
        _hideExplicit.value = value
        save()
    }

    fun setQuickPicks(value: QuickPicks) {
        _quickPicks.value = value
        save()
    }

    fun setEnableBetterLyrics(value: Boolean) {
        _enableBetterLyrics.value = value
        save()
    }

    fun setEnableSimpMusic(value: Boolean) {
        _enableSimpMusic.value = value
        save()
    }

    fun setEnableLrcLib(value: Boolean) {
        _enableLrcLib.value = value
        save()
    }

    fun setEnableKuGou(value: Boolean) {
        _enableKuGou.value = value
        save()
    }

    fun setPreferredLyricsProvider(value: PreferredLyricsProvider) {
        _preferredLyricsProvider.value = value
        save()
    }

    fun setLibraryFilter(value: LibraryFilter) {
        _libraryFilter.value = value
        save()
    }

    fun setMixViewType(value: LibraryViewType) {
        _mixViewType.value = value
        save()
    }

    fun setPlaylistViewType(value: LibraryViewType) {
        _playlistViewType.value = value
        save()
    }

    fun setAlbumViewType(value: LibraryViewType) {
        _albumViewType.value = value
        save()
    }

    fun setArtistViewType(value: LibraryViewType) {
        _artistViewType.value = value
        save()
    }

    fun setGridItemSize(value: GridItemSize) {
        _gridItemSize.value = value
        save()
    }

    fun setMixSortType(value: MixSortType) {
        _mixSortType.value = value
        save()
    }

    fun setMixSortDescending(value: Boolean) {
        _mixSortDescending.value = value
        save()
    }

    fun setPlaylistSortType(value: PlaylistSortType) {
        _playlistSortType.value = value
        save()
    }

    fun setPlaylistSortDescending(value: Boolean) {
        _playlistSortDescending.value = value
        save()
    }

    fun setAlbumSortType(value: AlbumSortType) {
        _albumSortType.value = value
        save()
    }

    fun setAlbumSortDescending(value: Boolean) {
        _albumSortDescending.value = value
        save()
    }

    fun setArtistSortType(value: ArtistSortType) {
        _artistSortType.value = value
        save()
    }

    fun setArtistSortDescending(value: Boolean) {
        _artistSortDescending.value = value
        save()
    }

    fun setSongSortType(value: SongSortType) {
        _songSortType.value = value
        save()
    }

    fun setSongSortDescending(value: Boolean) {
        _songSortDescending.value = value
        save()
    }

    fun setSongFilter(value: SongFilter) {
        _songFilter.value = value
        save()
    }

    fun setAlbumFilter(value: AlbumFilter) {
        _albumFilter.value = value
        save()
    }

    fun setArtistFilter(value: ArtistFilter) {
        _artistFilter.value = value
        save()
    }

    fun setShowLikedPlaylist(value: Boolean) {
        _showLikedPlaylist.value = value
        save()
    }

    fun setShowDownloadedPlaylist(value: Boolean) {
        _showDownloadedPlaylist.value = value
        save()
    }

    fun setShowTopPlaylist(value: Boolean) {
        _showTopPlaylist.value = value
        save()
    }

    fun setShowCachedPlaylist(value: Boolean) {
        _showCachedPlaylist.value = value
        save()
    }

    fun setUseLoginForBrowse(value: Boolean) {
        _useLoginForBrowse.value = value
        save()
    }

    fun setYtmSync(value: Boolean) {
        _ytmSync.value = value
        save()
    }

    fun setDiscordToken(value: String) {
        _discordToken.value = value
        save()
    }

    fun setDiscordUsername(value: String) {
        _discordUsername.value = value
        save()
    }

    fun setDiscordAvatarUrl(value: String) {
        _discordAvatarUrl.value = value
        save()
    }

    fun setPreferredAvatarSource(value: AvatarSourcePreference) {
        _preferredAvatarSource.value = value
        save()
    }

    fun setPauseListenHistory(value: Boolean) {
        _pauseListenHistory.value = value
        save()
    }

    fun setPauseSearchHistory(value: Boolean) {
        _pauseSearchHistory.value = value
        save()
    }

    fun setMaxImageCacheSizeMB(value: Int) {
        _maxImageCacheSizeMB.value = value.coerceIn(100, 2000)
        save()
    }

    fun setMaxSongCacheSizeMB(value: Int) {
        _maxSongCacheSizeMB.value = value.coerceIn(500, 10000)
        save()
    }

    fun setDownloadAsMp3(value: Boolean) {
        _downloadAsMp3.value = value
        save()
    }

    fun setShowLyrics(value: Boolean) {
        _showLyrics.value = value
        save()
    }

    fun setRomanizeLyrics(value: Boolean) {
        _romanizeLyrics.value = value
        save()
    }

    fun setYoutubeCookie(value: String?) {
        _youtubeCookie.value = value
        save()
    }

    // === Persistence ===

    fun load() {
        if (!Files.exists(filePath)) return
        runCatching {
            val raw = Files.readString(filePath, StandardCharsets.UTF_8)
            if (raw.isBlank()) return
            val json = JSONObject(raw)

            _darkMode.value = DarkModePreference.fromString(json.optString("darkMode", "auto"))
            _pureBlack.value = json.optBoolean("pureBlack", false)
            _dynamicColor.value = json.optBoolean("dynamicColor", true)
            _playerBackgroundStyle.value = PlayerBackgroundStyle.fromString(
                json.optString("playerBackgroundStyle", "default")
            )
            _playerButtonsStyle.value = PlayerButtonsStyle.fromString(
                json.optString("playerButtonsStyle", "default")
            )
            _sliderStyle.value = SliderStyle.fromString(json.optString("sliderStyle", "default"))
            _densityScale.value = json.optDouble("density_scale_factor", 1.0).toFloat().coerceIn(0.5f, 1.2f)
            _customDensityValue.value = json.optDouble("custom_density_scale_value", 0.85).toFloat().coerceIn(0.5f, 1.2f)
            _defaultOpenTab.value = NavigationTabPreference.fromString(json.optString("defaultOpenTab", "home"))
            _defaultLibChip.value = json.optString("chipSortType", "library")
                .let { value -> runCatching { LibraryFilter.valueOf(value.uppercase()) }.getOrDefault(LibraryFilter.LIBRARY) }
            _slimNavBar.value = json.optBoolean("slimNavBar", false)
            _swipeToSong.value = json.optBoolean("SwipeToSong", false)
            _swipeThumbnail.value = json.optBoolean("swipeThumbnail", true)
            _swipeSensitivity.value = json.optDouble("swipeSensitivity", 0.73).toFloat().coerceIn(0f, 1f)
            _lyricsTextPosition.value = LyricsPositionPreference.fromString(
                json.optString("lyricsTextPosition", "center")
            )
            _lyricsClick.value = json.optBoolean("lyricsClick", true)
            _lyricsScroll.value = json.optBoolean("lyricsScrollKey", json.optBoolean("lyricsScroll", true))
            _lyricsFontSize.value = json.optDouble("lyricsFontSize", 20.0).toFloat().coerceIn(12f, 36f)
            _lyricsCustomFontPath.value =
                json.optString("lyricsCustomFontPath", "").takeIf { it != "null" } ?: ""
            _lyricsSmoothScroll.value = json.optBoolean("lyricsSmoothScroll", true)
            _lyricsAnimationStyle.value = LyricsAnimationStylePreference.fromString(
                json.optString("lyricsAnimationStyle", "apple")
            )

            _audioQuality.value = AudioQuality.fromString(json.optString("audioQuality", "auto"))
            _normalizeAudio.value = json.optBoolean("normalizeAudio", true)
            _skipSilence.value = json.optBoolean("skipSilence", false)
            _crossfadeDuration.value = json.optInt("crossfadeDuration", 0)
            _historyDuration.value = json.optDouble("historyDuration", 30.0).toFloat()
            _persistentQueue.value = json.optBoolean("persistentQueue", true)
            _autoStartRadio.value = json.optBoolean("autoStartRadio", true)
            _queueEditLocked.value = json.optBoolean("queueEditLocked", true)

            _contentLanguage.value = json.optString("contentLanguage", "es")
            _contentCountry.value = json.optString("contentCountry", "ES")
            _appLanguage.value = json.optString("appLanguage", SYSTEM_DEFAULT)
            _hideExplicit.value = json.optBoolean("hideExplicit", false)
            _quickPicks.value = QuickPicks.fromString(json.optString("quickPicks", "quick_picks"))
            _enableBetterLyrics.value = json.optBoolean("enableBetterLyrics", true)
            _enableSimpMusic.value = json.optBoolean("enableSimpMusic", true)
            _enableLrcLib.value = json.optBoolean("enableLrclib", true)
            _enableKuGou.value = json.optBoolean("enableKugou", true)
            _preferredLyricsProvider.value = PreferredLyricsProvider.fromString(
                json.optString("lyricsProvider", "better_lyrics")
            )
            _useLoginForBrowse.value = json.optBoolean("useLoginForBrowse", true)
            _ytmSync.value = json.optBoolean("ytmSync", true)
            _discordToken.value = json.optString("discordToken", "")
            _discordUsername.value = json.optString("discordUsername", "")
            _discordAvatarUrl.value =
                json.optString("discordAvatarUrl", "").takeIf { it != "null" } ?: ""
            _preferredAvatarSource.value = AvatarSourcePreference.fromString(
                json.optString("preferredAvatarSource", "youtube")
            )

            _libraryFilter.value = json.optString("libraryFilter", "library")
                .let { value -> runCatching { LibraryFilter.valueOf(value.uppercase()) }.getOrDefault(LibraryFilter.LIBRARY) }
            _mixViewType.value = json.optString("mixViewType", "grid")
                .let { value -> runCatching { LibraryViewType.valueOf(value.uppercase()) }.getOrDefault(LibraryViewType.GRID) }
            _playlistViewType.value = json.optString("playlistViewType", "grid")
                .let { value -> runCatching { LibraryViewType.valueOf(value.uppercase()) }.getOrDefault(LibraryViewType.GRID) }
            _albumViewType.value = json.optString("albumViewType", "grid")
                .let { value -> runCatching { LibraryViewType.valueOf(value.uppercase()) }.getOrDefault(LibraryViewType.GRID) }
            _artistViewType.value = json.optString("artistViewType", "grid")
                .let { value -> runCatching { LibraryViewType.valueOf(value.uppercase()) }.getOrDefault(LibraryViewType.GRID) }
            _gridItemSize.value = json.optString("gridItemSize", "big")
                .let { value -> runCatching { GridItemSize.valueOf(value.uppercase()) }.getOrDefault(GridItemSize.BIG) }

            _mixSortType.value = json.optString("mixSortType", "create_date")
                .let { value -> runCatching { MixSortType.valueOf(value.uppercase()) }.getOrDefault(MixSortType.CREATE_DATE) }
            _mixSortDescending.value = json.optBoolean("mixSortDescending", true)
            _playlistSortType.value = json.optString("playlistSortType", "create_date")
                .let { value -> runCatching { PlaylistSortType.valueOf(value.uppercase()) }.getOrDefault(PlaylistSortType.CREATE_DATE) }
            _playlistSortDescending.value = json.optBoolean("playlistSortDescending", true)
            _albumSortType.value = json.optString("albumSortType", "create_date")
                .let { value -> runCatching { AlbumSortType.valueOf(value.uppercase()) }.getOrDefault(AlbumSortType.CREATE_DATE) }
            _albumSortDescending.value = json.optBoolean("albumSortDescending", true)
            _artistSortType.value = json.optString("artistSortType", "create_date")
                .let { value -> runCatching { ArtistSortType.valueOf(value.uppercase()) }.getOrDefault(ArtistSortType.CREATE_DATE) }
            _artistSortDescending.value = json.optBoolean("artistSortDescending", true)
            _songSortType.value = json.optString("songSortType", "create_date")
                .let { value -> runCatching { SongSortType.valueOf(value.uppercase()) }.getOrDefault(SongSortType.CREATE_DATE) }
            _songSortDescending.value = json.optBoolean("songSortDescending", true)
            _songFilter.value = json.optString("songFilter", "liked")
                .let { value -> runCatching { SongFilter.valueOf(value.uppercase()) }.getOrDefault(SongFilter.LIKED) }
            _albumFilter.value = json.optString("albumFilter", "liked")
                .let { value -> runCatching { AlbumFilter.valueOf(value.uppercase()) }.getOrDefault(AlbumFilter.LIKED) }
            _artistFilter.value = json.optString("artistFilter", "liked")
                .let { value -> runCatching { ArtistFilter.valueOf(value.uppercase()) }.getOrDefault(ArtistFilter.LIKED) }

            _showLikedPlaylist.value = json.optBoolean("showLikedPlaylist", true)
            _showDownloadedPlaylist.value = json.optBoolean("showDownloadedPlaylist", true)
            _showTopPlaylist.value = json.optBoolean("showTopPlaylist", true)
            _showCachedPlaylist.value = json.optBoolean("showCachedPlaylist", true)

            _pauseListenHistory.value = json.optBoolean("pauseListenHistory", false)
            _pauseSearchHistory.value = json.optBoolean("pauseSearchHistory", false)

            _maxImageCacheSizeMB.value = json.optInt("maxImageCacheSizeMB", 500)
            _maxSongCacheSizeMB.value = json.optInt("maxSongCacheSizeMB", 1000)
            _downloadAsMp3.value = json.optBoolean("downloadAsMp3", true)

            _showLyrics.value = json.optBoolean("showLyrics", true)
            _romanizeLyrics.value = json.optBoolean("romanizeLyrics", false)
            _youtubeCookie.value = json.optString("youtubeCookie", null).takeIf { it != "null" }
        }
    }

    private fun save() {
        runCatching {
            ensureParent()
            val json = JSONObject().apply {
                put("darkMode", _darkMode.value.name.lowercase())
                put("pureBlack", _pureBlack.value)
                put("dynamicColor", _dynamicColor.value)
                put("playerBackgroundStyle", _playerBackgroundStyle.value.name.lowercase())
                put("playerButtonsStyle", _playerButtonsStyle.value.name.lowercase())
                put("sliderStyle", _sliderStyle.value.name.lowercase())
                put("density_scale_factor", _densityScale.value)
                put("custom_density_scale_value", _customDensityValue.value)
                put("defaultOpenTab", _defaultOpenTab.value.name.lowercase())
                put("chipSortType", _defaultLibChip.value.name.lowercase())
                put("slimNavBar", _slimNavBar.value)
                put("SwipeToSong", _swipeToSong.value)
                put("swipeThumbnail", _swipeThumbnail.value)
                put("swipeSensitivity", _swipeSensitivity.value)
                put("lyricsTextPosition", _lyricsTextPosition.value.name.lowercase())
                put("lyricsClick", _lyricsClick.value)
                put("lyricsScrollKey", _lyricsScroll.value)
                put("lyricsFontSize", _lyricsFontSize.value)
                put("lyricsCustomFontPath", _lyricsCustomFontPath.value)
                put("lyricsSmoothScroll", _lyricsSmoothScroll.value)
                put("lyricsAnimationStyle", _lyricsAnimationStyle.value.name.lowercase())

                put("audioQuality", _audioQuality.value.name.lowercase())
                put("normalizeAudio", _normalizeAudio.value)
                put("skipSilence", _skipSilence.value)
                put("crossfadeDuration", _crossfadeDuration.value)
                put("historyDuration", _historyDuration.value)
                put("persistentQueue", _persistentQueue.value)
                put("autoStartRadio", _autoStartRadio.value)
                put("queueEditLocked", _queueEditLocked.value)

                put("contentLanguage", _contentLanguage.value)
                put("contentCountry", _contentCountry.value)
                put("appLanguage", _appLanguage.value)
                put("hideExplicit", _hideExplicit.value)
                put("quickPicks", _quickPicks.value.name.lowercase())
                put("enableBetterLyrics", _enableBetterLyrics.value)
                put("enableSimpMusic", _enableSimpMusic.value)
                put("enableLrclib", _enableLrcLib.value)
                put("enableKugou", _enableKuGou.value)
                put("lyricsProvider", _preferredLyricsProvider.value.name.lowercase())
                put("useLoginForBrowse", _useLoginForBrowse.value)
                put("ytmSync", _ytmSync.value)
                put("discordToken", _discordToken.value)
                put("discordUsername", _discordUsername.value)
                put("discordAvatarUrl", _discordAvatarUrl.value)
                put("preferredAvatarSource", _preferredAvatarSource.value.name.lowercase())

                put("libraryFilter", _libraryFilter.value.name.lowercase())
                put("mixViewType", _mixViewType.value.name.lowercase())
                put("playlistViewType", _playlistViewType.value.name.lowercase())
                put("albumViewType", _albumViewType.value.name.lowercase())
                put("artistViewType", _artistViewType.value.name.lowercase())
                put("gridItemSize", _gridItemSize.value.name.lowercase())
                put("mixSortType", _mixSortType.value.name.lowercase())
                put("mixSortDescending", _mixSortDescending.value)
                put("playlistSortType", _playlistSortType.value.name.lowercase())
                put("playlistSortDescending", _playlistSortDescending.value)
                put("albumSortType", _albumSortType.value.name.lowercase())
                put("albumSortDescending", _albumSortDescending.value)
                put("artistSortType", _artistSortType.value.name.lowercase())
                put("artistSortDescending", _artistSortDescending.value)
                put("songSortType", _songSortType.value.name.lowercase())
                put("songSortDescending", _songSortDescending.value)
                put("songFilter", _songFilter.value.name.lowercase())
                put("albumFilter", _albumFilter.value.name.lowercase())
                put("artistFilter", _artistFilter.value.name.lowercase())
                put("showLikedPlaylist", _showLikedPlaylist.value)
                put("showDownloadedPlaylist", _showDownloadedPlaylist.value)
                put("showTopPlaylist", _showTopPlaylist.value)
                put("showCachedPlaylist", _showCachedPlaylist.value)

                put("pauseListenHistory", _pauseListenHistory.value)
                put("pauseSearchHistory", _pauseSearchHistory.value)

                put("maxImageCacheSizeMB", _maxImageCacheSizeMB.value)
                put("maxSongCacheSizeMB", _maxSongCacheSizeMB.value)
                put("downloadAsMp3", _downloadAsMp3.value)

                put("showLyrics", _showLyrics.value)
                put("romanizeLyrics", _romanizeLyrics.value)
                put("youtubeCookie", _youtubeCookie.value)
            }
            Files.writeString(filePath, json.toString(2), StandardCharsets.UTF_8)
        }
    }

    private fun ensureParent() {
        filePath.parent?.let { parent ->
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DesktopPreferences? = null

        fun getInstance(): DesktopPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DesktopPreferences().also {
                    it.load()
                    INSTANCE = it
                }
            }
        }

        private fun defaultPreferencesPath(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".anitail", "preferences.json")
        }
    }
}

enum class DarkModePreference {
    ON,
    OFF,
    AUTO,
    TIME_BASED;

    companion object {
        fun fromString(value: String): DarkModePreference = when (value.lowercase()) {
            "on", "dark" -> ON
            "off", "light" -> OFF
            "time_based", "timebased", "time" -> TIME_BASED
            "auto", "system" -> AUTO
            else -> AUTO
        }
    }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN;

    companion object {
        fun fromString(value: String): QuickPicks = when (value.lowercase()) {
            "last_listen", "last_listened", "last" -> LAST_LISTEN
            else -> QUICK_PICKS
        }
    }
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC;

    companion object {
        fun fromString(value: String): PreferredLyricsProvider = when (value.lowercase()) {
            "lrclib" -> LRCLIB
            "kugou" -> KUGOU
            "simpmusic" -> SIMPMUSIC
            "better_lyrics", "betterlyrics", "better" -> BETTER_LYRICS
            else -> BETTER_LYRICS
        }
    }
}

enum class AvatarSourcePreference {
    YOUTUBE,
    DISCORD;

    companion object {
        fun fromString(value: String): AvatarSourcePreference = when (value.lowercase()) {
            "discord" -> DISCORD
            else -> YOUTUBE
        }
    }
}

enum class NavigationTabPreference {
    HOME,
    EXPLORE,
    LIBRARY;

    companion object {
        fun fromString(value: String): NavigationTabPreference = when (value.lowercase()) {
            "explore" -> EXPLORE
            "library" -> LIBRARY
            else -> HOME
        }
    }
}

enum class LyricsPositionPreference {
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        fun fromString(value: String): LyricsPositionPreference = when (value.lowercase()) {
            "left" -> LEFT
            "right" -> RIGHT
            else -> CENTER
        }
    }
}

enum class LyricsAnimationStylePreference {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE;

    companion object {
        fun fromString(value: String): LyricsAnimationStylePreference = when (value.lowercase()) {
            "none" -> NONE
            "fade" -> FADE
            "glow" -> GLOW
            "slide" -> SLIDE
            "karaoke" -> KARAOKE
            else -> APPLE
        }
    }

    val labelKey: String
        get() = when (this) {
            NONE -> "lyrics_animation_none"
            FADE -> "lyrics_animation_fade"
            GLOW -> "lyrics_animation_glow"
            SLIDE -> "lyrics_animation_slide"
            KARAOKE -> "lyrics_animation_karaoke"
            APPLE -> "lyrics_animation_apple"
        }
}

enum class AudioQuality {
    LOW,
    MEDIUM,
    HIGH,
    AUTO;

    companion object {
        fun fromString(value: String): AudioQuality = when (value.lowercase()) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            else -> AUTO
        }
    }

    val labelKey: String
        get() = when (this) {
            LOW -> "audio_quality_low"
            MEDIUM -> "audio_quality_medium"
            HIGH -> "audio_quality_high"
            AUTO -> "audio_quality_auto"
        }
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR;

    companion object {
        fun fromString(value: String): PlayerBackgroundStyle = when (value.lowercase()) {
            "gradient" -> GRADIENT
            "blur" -> BLUR
            else -> DEFAULT
        }
    }

    val labelKey: String
        get() = when (this) {
            DEFAULT -> "follow_theme"
            GRADIENT -> "gradient"
            BLUR -> "player_background_blur"
        }
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY;

    companion object {
        fun fromString(value: String): PlayerButtonsStyle = when (value.lowercase()) {
            "secondary" -> SECONDARY
            else -> DEFAULT
        }
    }

    val labelKey: String
        get() = when (this) {
            DEFAULT -> "default_style"
            SECONDARY -> "secondary_color_style"
        }
}

enum class SliderStyle {
    DEFAULT,
    SQUIGGLY,
    SLIM;

    companion object {
        fun fromString(value: String): SliderStyle = when (value.lowercase()) {
            "squiggly" -> SQUIGGLY
            "slim" -> SLIM
            else -> DEFAULT
        }
    }

    val labelKey: String
        get() = when (this) {
            DEFAULT -> "default_"
            SQUIGGLY -> "squiggly"
            SLIM -> "slim"
        }
}
