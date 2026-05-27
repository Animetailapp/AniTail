/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.music.lyrics

import android.content.Context
import com.anitail.music.App
import com.anitail.paxsenix.Paxsenix
import com.anitail.music.constants.EnablePaxsenixKey
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import timber.log.Timber

object PaxsenixLyricsProvider : LyricsProvider {
    private const val TAG = "PaxsenixProvider"
    
    override val name = "Paxsenix"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        Timber.tag(TAG).d("getLyrics called: title='$title', artist='$artist', duration=$duration")
        
        try {
            Paxsenix.init(App.instance)
            val result = Paxsenix.getLyrics(title, artist, duration, album)
            
            result.onSuccess { lyrics ->
                Timber.tag(TAG).i("Success! Got ${lyrics.length} chars of lyrics")
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to get lyrics")
            }
            
            return result
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception in getLyrics")
            return Result.failure(e)
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Timber.tag(TAG).d("getAllLyrics called")
        try {
            Paxsenix.init(App.instance)
            Paxsenix.getAllLyrics(title, artist, duration, album, callback)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching lyrics from Paxsenix")
            callback("")
        }
    }
}
