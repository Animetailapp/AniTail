package com.anitail.desktop.auth

import com.anitail.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Servicio de autenticación para Desktop.
 * Gestiona credenciales de YouTube Music (visitorData, dataSyncId, cookies).
 */
class DesktopAuthService(
    private val credentialsFile: Path = defaultCredentialsPath(),
) {
    private var _credentials: AuthCredentials? = null
    
    val credentials: AuthCredentials?
        get() = _credentials
    
    val isLoggedIn: Boolean
        get() = _credentials?.dataSyncId?.isNotBlank() == true
    
    init {
        loadCredentials()
    }
    
    /**
     * Carga credenciales guardadas del disco.
     */
    fun loadCredentials() {
        if (!Files.exists(credentialsFile)) {
            _credentials = null
            return
        }
        
        runCatching {
            val raw = Files.readString(credentialsFile, StandardCharsets.UTF_8)
            if (raw.isBlank()) {
                _credentials = null
                return
            }
            
            val json = JSONObject(raw)
            _credentials = AuthCredentials(
                visitorData = json.optString("visitorData").takeIf { it.isNotBlank() },
                dataSyncId = json.optString("dataSyncId").takeIf { it.isNotBlank() },
                cookie = json.optString("cookie").takeIf { it.isNotBlank() },
                accountName = json.optString("accountName").takeIf { it.isNotBlank() },
                accountEmail = json.optString("accountEmail").takeIf { it.isNotBlank() },
                channelHandle = json.optString("channelHandle").takeIf { it.isNotBlank() },
            )
            
            // Aplicar credenciales a YouTube API
            _credentials?.let { creds ->
                YouTube.visitorData = creds.visitorData
                YouTube.dataSyncId = creds.dataSyncId
                YouTube.cookie = creds.cookie
            }
        }.onFailure {
            _credentials = null
        }
    }
    
    /**
     * Guarda credenciales en disco.
     */
    suspend fun saveCredentials(credentials: AuthCredentials) = withContext(Dispatchers.IO) {
        _credentials = credentials
        
        // Aplicar credenciales a YouTube API
        YouTube.visitorData = credentials.visitorData
        YouTube.dataSyncId = credentials.dataSyncId
        YouTube.cookie = credentials.cookie
        
        ensureParent()
        
        val json = JSONObject().apply {
            put("visitorData", credentials.visitorData ?: "")
            put("dataSyncId", credentials.dataSyncId ?: "")
            put("cookie", credentials.cookie ?: "")
            put("accountName", credentials.accountName ?: "")
            put("accountEmail", credentials.accountEmail ?: "")
            put("channelHandle", credentials.channelHandle ?: "")
        }
        
        Files.writeString(credentialsFile, json.toString(2), StandardCharsets.UTF_8)
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
     * Actualiza información de la cuenta.
     */
    suspend fun updateAccountInfo(name: String?, email: String?, channelHandle: String?) {
        val updated = (_credentials ?: AuthCredentials()).copy(
            accountName = name,
            accountEmail = email,
            channelHandle = channelHandle,
        )
        saveCredentials(updated)
    }
    
    /**
     * Cierra sesión - elimina credenciales.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        _credentials = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.cookie = null
        
        if (Files.exists(credentialsFile)) {
            Files.delete(credentialsFile)
        }
    }
    
    /**
     * Obtiene o genera visitorData nuevo.
     */
    suspend fun ensureVisitorData(): String? = withContext(Dispatchers.IO) {
        if (_credentials?.visitorData != null) {
            return@withContext _credentials?.visitorData
        }
        
        // Intentar obtener nuevo visitorData de YouTube
        YouTube.visitorData().onSuccess { newVisitorData ->
            updateVisitorData(newVisitorData)
            return@withContext newVisitorData
        }
        
        return@withContext null
    }
    
    /**
     * Obtiene información de la cuenta desde YouTube.
     */
    suspend fun refreshAccountInfo(): AccountInfo? = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext null
        
        YouTube.accountInfo().onSuccess { info ->
            updateAccountInfo(
                name = info.name,
                email = info.email,
                channelHandle = info.channelHandle,
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
    
    private fun ensureParent() {
        credentialsFile.parent?.let { parent ->
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }
    
    companion object {
        private fun defaultCredentialsPath(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".anitail", "credentials.json")
        }
        
        /**
         * URL de login de Google para YouTube Music.
         */
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
    }
}

/**
 * Credenciales de autenticación.
 */
data class AuthCredentials(
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val cookie: String? = null,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val channelHandle: String? = null,
)

/**
 * Información de la cuenta.
 */
data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)
