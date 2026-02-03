package com.anitail.music.lyrics

import android.content.Context
import android.util.LruCache
import com.anitail.music.constants.PreferredLyricsProvider
import com.anitail.music.constants.PreferredLyricsProviderKey
import com.anitail.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.anitail.music.extensions.toEnum
import com.anitail.music.models.MediaMetadata
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private var lyricsProviders =
        listOf(
            BetterLyricsProvider,
            SimpMusicLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider
        )

    val preferred =
        context.dataStore.data
            .map {
                it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.BETTER_LYRICS)
            }.distinctUntilChanged()
            .map {
                lyricsProviders =
                    when (it) {
                        PreferredLyricsProvider.LRCLIB ->
                            listOf(
                                BetterLyricsProvider,
                                LrcLibLyricsProvider,
                                SimpMusicLyricsProvider,
                                KuGouLyricsProvider,
                                YouTubeSubtitleLyricsProvider,
                                YouTubeLyricsProvider
                            )

                        PreferredLyricsProvider.KUGOU ->
                            listOf(
                                BetterLyricsProvider,
                                KuGouLyricsProvider,
                                SimpMusicLyricsProvider,
                                LrcLibLyricsProvider,
                                YouTubeSubtitleLyricsProvider,
                                YouTubeLyricsProvider
                            )

                        PreferredLyricsProvider.BETTER_LYRICS ->
                            listOf(
                                BetterLyricsProvider,
                                SimpMusicLyricsProvider,
                                LrcLibLyricsProvider,
                                KuGouLyricsProvider,
                                YouTubeSubtitleLyricsProvider,
                                YouTubeLyricsProvider
                            )

                        PreferredLyricsProvider.SIMPMUSIC ->
                            listOf(
                                BetterLyricsProvider,
                                SimpMusicLyricsProvider,
                                LrcLibLyricsProvider,
                                KuGouLyricsProvider,
                                YouTubeSubtitleLyricsProvider,
                                YouTubeLyricsProvider
                            )
                    }
            }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val scope = CoroutineScope(SupervisorJob())
        val deferred = scope.async {
            // Prefer setVideoId for YouTube-based providers (SimpMusic, YouTubeSubtitle, YouTube)
            val videoId = mediaMetadata.setVideoId ?: mediaMetadata.id
            val artistsStr = mediaMetadata.artists.joinToString { it.name }
            
            for (provider in lyricsProviders) {
                if (provider.isEnabled(context)) {
                    try {
                        android.util.Log.d("LyricsHelper", "Trying ${provider.name}: id=$videoId, title=${mediaMetadata.title}, artist=$artistsStr, duration=${mediaMetadata.duration}")
                        
                        val result = provider.getLyrics(
                            videoId,
                            mediaMetadata.title,
                            artistsStr,
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                        result.onSuccess { lyrics ->
                            android.util.Log.d("LyricsHelper", "${provider.name} succeeded with ${lyrics.length} chars")
                            return@async LyricsWithProvider(lyrics, provider.name)
                        }.onFailure {
                            android.util.Log.w("LyricsHelper", "${provider.name} failed: ${it.message}")
                            reportException(it)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        android.util.Log.e("LyricsHelper", "${provider.name} exception: ${e.message}", e)
                        reportException(e)
                    }
                }
            }
            return@async LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val result = deferred.await()
        scope.cancel()
        return result
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            lyricsProviders.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)