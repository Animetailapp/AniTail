package com.anitail.desktop.auth

import com.anitail.desktop.security.DesktopPaths
import com.anitail.desktop.security.DesktopSecureStore
import com.anitail.desktop.security.SecureSecretsStore
import com.anitail.desktop.security.SensitiveKeys
import com.anitail.desktop.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Servicio de autenticacion para Desktop.
 * Gestiona credenciales de YouTube Music y migra datos sensibles al almacén seguro.
 */
class DesktopAuthService(
    private val credentialsFile: Path = defaultCredentialsPath(),
    private val secureStore: SecureSecretsStore = DesktopSecureStore.instance,
) {
    private var _credentials: AuthCredentials? = null

    val credentials: AuthCredentials?
        get() = _credentials

    val isLoggedIn: Boolean
        get() = _credentials?.cookie?.isNotBlank() == true

    init {
        loadCredentials()
    }

    /**
     * Carga credenciales guardadas del disco y del almacén seguro.
     */
    fun loadCredentials() {
        runCatching {
            val json = readCredentialsJson()

            val legacyVisitorData = json.optString("visitorData").takeIf { it.isNotBlank() }
            val legacyDataSyncId = json.optString("dataSyncId").takeIf { it.isNotBlank() }
            val legacyCookie = json.optString("cookie").takeIf { it.isNotBlank() }

            val visitorData = readSensitiveValue(
                key = SensitiveKeys.YOUTUBE_VISITOR_DATA,
                legacyValue = legacyVisitorData,
            )
            val dataSyncId = readSensitiveValue(
                key = SensitiveKeys.YOUTUBE_DATA_SYNC_ID,
                legacyValue = legacyDataSyncId,
            )
            val cookie = readSensitiveValue(
                key = SensitiveKeys.YOUTUBE_COOKIE,
                legacyValue = legacyCookie,
            )

            val loaded = AuthCredentials(
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                cookie = cookie,
                accountName = json.optString("accountName").takeIf { it.isNotBlank() },
                accountEmail = json.optString("accountEmail").takeIf { it.isNotBlank() },
                channelHandle = json.optString("channelHandle").takeIf { it.isNotBlank() },
                accountImageUrl = json.optString("accountImageUrl").takeIf { it.isNotBlank() },
            )

            _credentials = loaded.takeIf { it.hasAnyValue() }

            _credentials?.let { creds ->
                YouTube.visitorData = creds.visitorData
                YouTube.dataSyncId = normalizeDataSyncId(creds.dataSyncId)
                YouTube.cookie = creds.cookie
            } ?: run {
                YouTube.visitorData = null
                YouTube.dataSyncId = null
                YouTube.cookie = null
            }

            // Si hubo migracion desde JSON antiguo, sanear archivo público.
            if (legacyVisitorData != null || legacyDataSyncId != null || legacyCookie != null) {
                persistPublicAccountInfo(_credentials)
            }
        }.onFailure {
            _credentials = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            YouTube.cookie = null
        }
    }

    /**
     * Guarda credenciales: sensibles en almacén seguro, datos de perfil en JSON.
     */
    suspend fun saveCredentials(credentials: AuthCredentials) = withContext(Dispatchers.IO) {
        _credentials = credentials.takeIf { it.hasAnyValue() }

        YouTube.visitorData = credentials.visitorData
        YouTube.dataSyncId = normalizeDataSyncId(credentials.dataSyncId)
        YouTube.cookie = credentials.cookie

        persistSensitiveValue(SensitiveKeys.YOUTUBE_VISITOR_DATA, credentials.visitorData)
        persistSensitiveValue(SensitiveKeys.YOUTUBE_DATA_SYNC_ID, credentials.dataSyncId)
        persistSensitiveValue(SensitiveKeys.YOUTUBE_COOKIE, credentials.cookie)

        persistPublicAccountInfo(_credentials)
    }

    /**
     * Elimina datos sensibles de YouTube pero conserva datos públicos opcionalmente.
     */
    suspend fun clearSensitiveData(preservePublicProfile: Boolean = true) = withContext(Dispatchers.IO) {
        persistSensitiveValue(SensitiveKeys.YOUTUBE_VISITOR_DATA, null)
        persistSensitiveValue(SensitiveKeys.YOUTUBE_DATA_SYNC_ID, null)
        persistSensitiveValue(SensitiveKeys.YOUTUBE_COOKIE, null)

        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.cookie = null

        val current = _credentials
        val updated = if (preservePublicProfile && current != null) {
            current.copy(visitorData = null, dataSyncId = null, cookie = null)
        } else {
            null
        }

        _credentials = updated?.takeIf { it.hasAnyValue() }
        persistPublicAccountInfo(_credentials)
    }

    /**
     * Cierra sesion: elimina todo rastro de credenciales.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        clearSensitiveData(preservePublicProfile = false)
    }

    /**
     * Actualiza visitorData.
     */
    suspend fun updateVisitorData(visitorData: String) {
        val updated = (_credentials ?: AuthCredentials()).copy(visitorData = visitorData)
        saveCredentials(updated)
    }

    /**
     * Actualiza dataSyncId.
     */
    suspend fun updateDataSyncId(dataSyncId: String) {
        val updated = (_credentials ?: AuthCredentials()).copy(dataSyncId = dataSyncId)
        saveCredentials(updated)
    }

    /**
     * Actualiza cookie de YouTube.
     */
    suspend fun updateCookie(cookie: String) {
        val updated = (_credentials ?: AuthCredentials()).copy(cookie = cookie)
        saveCredentials(updated)
    }

    /**
     * Actualiza informacion de la cuenta.
     */
    suspend fun updateAccountInfo(
        name: String?,
        email: String?,
        channelHandle: String?,
        accountImageUrl: String? = null,
    ) {
        val updated = (_credentials ?: AuthCredentials()).copy(
            accountName = name,
            accountEmail = email,
            channelHandle = channelHandle,
            accountImageUrl = accountImageUrl ?: _credentials?.accountImageUrl,
        )
        saveCredentials(updated)
    }

    /**
     * Obtiene o genera visitorData nuevo.
     */
    suspend fun ensureVisitorData(): String? = withContext(Dispatchers.IO) {
        if (_credentials?.visitorData != null) {
            return@withContext _credentials?.visitorData
        }

        YouTube.visitorData().onSuccess { newVisitorData ->
            updateVisitorData(newVisitorData)
            return@withContext newVisitorData
        }

        return@withContext null
    }

    /**
     * Obtiene informacion de la cuenta desde YouTube.
     */
    suspend fun refreshAccountInfo(): AccountInfo? = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext null

        YouTube.accountInfo().onSuccess { info ->
            updateAccountInfo(
                name = info.name,
                email = info.email,
                channelHandle = info.channelHandle,
                accountImageUrl = info.thumbnailUrl,
            )
            return@withContext AccountInfo(
                name = info.name,
                email = info.email,
                channelHandle = info.channelHandle,
                thumbnailUrl = info.thumbnailUrl,
            )
        }

        return@withContext null
    }

    private fun readSensitiveValue(key: String, legacyValue: String?): String? {
        val secured = secureStore.get(key)?.takeIf { it.isNotBlank() }
        if (secured != null) return secured

        if (!legacyValue.isNullOrBlank()) {
            secureStore.put(key, legacyValue)
            return legacyValue
        }

        return null
    }

    private fun persistSensitiveValue(key: String, value: String?) {
        val safeValue = value.orEmpty()
        if (safeValue.isEmpty()) {
            secureStore.remove(key)
        } else {
            secureStore.put(key, safeValue)
        }
    }

    private fun readCredentialsJson(): JSONObject {
        if (!Files.exists(credentialsFile)) return JSONObject()
        val raw = Files.readString(credentialsFile, StandardCharsets.UTF_8)
        if (raw.isBlank()) return JSONObject()
        return JSONObject(raw)
    }

    private fun persistPublicAccountInfo(credentials: AuthCredentials?) {
        val safeCredentials = credentials ?: run {
            if (Files.exists(credentialsFile)) {
                Files.delete(credentialsFile)
            }
            return
        }

        val hasPublicData = safeCredentials.accountName.orEmpty().isNotBlank() ||
            safeCredentials.accountEmail.orEmpty().isNotBlank() ||
            safeCredentials.channelHandle.orEmpty().isNotBlank() ||
            safeCredentials.accountImageUrl.orEmpty().isNotBlank()

        if (!hasPublicData) {
            if (Files.exists(credentialsFile)) {
                Files.delete(credentialsFile)
            }
            return
        }

        ensureParent()
        val json = JSONObject().apply {
            put("accountName", safeCredentials.accountName.orEmpty())
            put("accountEmail", safeCredentials.accountEmail.orEmpty())
            put("channelHandle", safeCredentials.channelHandle.orEmpty())
            put("accountImageUrl", safeCredentials.accountImageUrl.orEmpty())
        }
        Files.writeString(credentialsFile, json.toString(2), StandardCharsets.UTF_8)
    }

    private fun ensureParent() {
        credentialsFile.parent?.let { parent ->
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }

    companion object {
        private fun defaultCredentialsPath(): Path = DesktopPaths.credentialsFile()

        /**
         * URL de login de Google para YouTube Music.
         */
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
    }
}

/**
 * Credenciales de autenticacion.
 */
data class AuthCredentials(
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val cookie: String? = null,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val channelHandle: String? = null,
    val accountImageUrl: String? = null,
) {
    fun hasAnyValue(): Boolean {
        return visitorData.orEmpty().isNotBlank() ||
            dataSyncId.orEmpty().isNotBlank() ||
            cookie.orEmpty().isNotBlank() ||
            accountName.orEmpty().isNotBlank() ||
            accountEmail.orEmpty().isNotBlank() ||
            channelHandle.orEmpty().isNotBlank() ||
            accountImageUrl.orEmpty().isNotBlank()
    }
}

/**
 * Informacion de la cuenta.
 */
data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)
