package com.anitail.music.discord

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber

object DiscordTokenStore {
    private const val PREFS_NAME = "discord_token"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val EXPIRES_AT_KEY = "expires_at"

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

    suspend fun awaitInit(): Boolean {
        return try {
            initDeferred.await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun retrieveSuspend(): String? {
        if (!awaitInit()) return null
        return retrieve()
    }

    fun store(token: String) {
        prefs?.edit()?.putString(TOKEN_KEY, token)?.apply()
    }

    fun storeTokens(accessToken: String, refreshToken: String?, expiresAtMillis: Long) {
        prefs?.edit()?.apply {
            putString(TOKEN_KEY, accessToken)
            if (!refreshToken.isNullOrBlank()) {
                putString(REFRESH_TOKEN_KEY, refreshToken)
            }
            if (expiresAtMillis > 0L) {
                putLong(EXPIRES_AT_KEY, expiresAtMillis)
            }
        }?.apply()
    }

    fun storeRefreshToken(refreshToken: String) {
        if (refreshToken.isBlank()) return
        prefs?.edit()?.putString(REFRESH_TOKEN_KEY, refreshToken)?.apply()
    }

    fun storeExpiresAt(expiresAtMillis: Long) {
        if (expiresAtMillis <= 0L) return
        prefs?.edit()?.putLong(EXPIRES_AT_KEY, expiresAtMillis)?.apply()
    }

    fun retrieve(): String? = prefs?.getString(TOKEN_KEY, null)

    fun retrieveRefreshToken(): String? =
        prefs?.getString(REFRESH_TOKEN_KEY, null)?.takeIf { it.isNotBlank() }

    suspend fun retrieveRefreshTokenSuspend(): String? =
        if (awaitInit()) retrieveRefreshToken() else null

    fun retrieveExpiresAt(): Long = prefs?.getLong(EXPIRES_AT_KEY, 0L) ?: 0L

    fun clear() {
        prefs?.edit()?.apply {
            remove(TOKEN_KEY)
            remove(REFRESH_TOKEN_KEY)
            remove(EXPIRES_AT_KEY)
        }?.apply()
    }
}
