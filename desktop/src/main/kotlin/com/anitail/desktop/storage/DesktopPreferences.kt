package com.anitail.desktop.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

    // === Player Settings ===
    private val _audioQuality = MutableStateFlow(AudioQuality.AUTO)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(0) // in seconds, 0 = disabled
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    private val _persistentQueue = MutableStateFlow(true)
    val persistentQueue: StateFlow<Boolean> = _persistentQueue.asStateFlow()

    private val _autoStartRadio = MutableStateFlow(true)
    val autoStartRadio: StateFlow<Boolean> = _autoStartRadio.asStateFlow()

    // === Content Settings ===
    private val _contentLanguage = MutableStateFlow("es")
    val contentLanguage: StateFlow<String> = _contentLanguage.asStateFlow()

    private val _contentCountry = MutableStateFlow("ES")
    val contentCountry: StateFlow<String> = _contentCountry.asStateFlow()

    private val _hideExplicit = MutableStateFlow(false)
    val hideExplicit: StateFlow<Boolean> = _hideExplicit.asStateFlow()

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

    fun setAudioQuality(value: AudioQuality) {
        _audioQuality.value = value
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

    fun setPersistentQueue(value: Boolean) {
        _persistentQueue.value = value
        save()
    }

    fun setAutoStartRadio(value: Boolean) {
        _autoStartRadio.value = value
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

    fun setHideExplicit(value: Boolean) {
        _hideExplicit.value = value
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

            _audioQuality.value = AudioQuality.fromString(json.optString("audioQuality", "auto"))
            _skipSilence.value = json.optBoolean("skipSilence", false)
            _crossfadeDuration.value = json.optInt("crossfadeDuration", 0)
            _persistentQueue.value = json.optBoolean("persistentQueue", true)
            _autoStartRadio.value = json.optBoolean("autoStartRadio", true)

            _contentLanguage.value = json.optString("contentLanguage", "es")
            _contentCountry.value = json.optString("contentCountry", "ES")
            _hideExplicit.value = json.optBoolean("hideExplicit", false)

            _pauseListenHistory.value = json.optBoolean("pauseListenHistory", false)
            _pauseSearchHistory.value = json.optBoolean("pauseSearchHistory", false)

            _maxImageCacheSizeMB.value = json.optInt("maxImageCacheSizeMB", 500)
            _maxSongCacheSizeMB.value = json.optInt("maxSongCacheSizeMB", 1000)

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

                put("audioQuality", _audioQuality.value.name.lowercase())
                put("skipSilence", _skipSilence.value)
                put("crossfadeDuration", _crossfadeDuration.value)
                put("persistentQueue", _persistentQueue.value)
                put("autoStartRadio", _autoStartRadio.value)

                put("contentLanguage", _contentLanguage.value)
                put("contentCountry", _contentCountry.value)
                put("hideExplicit", _hideExplicit.value)

                put("pauseListenHistory", _pauseListenHistory.value)
                put("pauseSearchHistory", _pauseSearchHistory.value)

                put("maxImageCacheSizeMB", _maxImageCacheSizeMB.value)
                put("maxSongCacheSizeMB", _maxSongCacheSizeMB.value)

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

    val displayName: String
        get() = when (this) {
            LOW -> "Baja (128 kbps)"
            MEDIUM -> "Media (192 kbps)"
            HIGH -> "Alta (320 kbps)"
            AUTO -> "Automática"
        }
}
