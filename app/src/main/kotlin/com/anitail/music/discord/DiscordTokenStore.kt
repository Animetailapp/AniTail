package com.anitail.music.discord

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber

object DiscordTokenStore {
    private const val PREFS_NAME = "discord_token"
    private const val TOKEN_KEY = "access_token"

    @Volatile
    private var prefs: EncryptedSharedPreferences? = null

    private val initDeferred = CompletableDeferred<Unit>()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            try {
                val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                prefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKey,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ) as EncryptedSharedPreferences
                initDeferred.complete(Unit)
            } catch (e: Exception) {
                Timber.e(e, "DiscordTokenStore init failed")
                initDeferred.completeExceptionally(e)
            }
        }
    }

    suspend fun retrieveSuspend(): String? {
        try {
            initDeferred.await()
        } catch (_: Exception) {
            return null
        }
        return retrieve()
    }

    fun store(token: String) {
        prefs?.edit()?.putString(TOKEN_KEY, token)?.apply()
    }

    fun retrieve(): String? = prefs?.getString(TOKEN_KEY, null)

    fun clear() {
        prefs?.edit()?.remove(TOKEN_KEY)?.apply()
    }
}
