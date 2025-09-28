package com.anitail.music.cast

import android.content.Context
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class AirPlayDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String = "AirPlay"
)

class AirPlayManager(
    private val context: Context,
    private val onAirPlaySessionStarted: (() -> Unit)? = null,
    private val onAirPlayAuthRequired: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: kotlinx.coroutines.Job? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Stable device ID header expected by some AirPlay receivers
    private val deviceId: String by lazy {
        runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        }
            .getOrNull()
            ?.uppercase()
            ?: UUID.randomUUID().toString().replace("-", "").uppercase()
    }

    private val sessionId: String by lazy { UUID.randomUUID().toString() }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<AirPlayDevice?>(null)
    val selectedDevice: StateFlow<AirPlayDevice?> = _selectedDevice.asStateFlow()

    // Auth state (PIN/password)
    enum class AuthScheme { BASIC, DIGEST }
    data class AuthChallenge(
        val deviceId: String,
        val deviceName: String,
        val scheme: AuthScheme,
        val realm: String? = null,
        val nonce: String? = null,
        val qop: String? = null,
        val opaque: String? = null,
        val algorithm: String? = null
    )

    data class Credentials(
        val username: String,
        val password: String,
        var nc: Int = 0 // nonce count for digest
    )

    private val _authChallenge = MutableStateFlow<AuthChallenge?>(null)
    val authChallenge: StateFlow<AuthChallenge?> = _authChallenge.asStateFlow()
    private val deviceCredentials = mutableMapOf<String, Credentials>()
    private var lastAuthRequired: Boolean = false

    private data class PendingRequest(val endpoint: String, val method: String, val body: String?)

    private var lastPendingRequest: PendingRequest? = null
    
    // Use enhanced device discovery for AirPlay devices
    private val deviceDiscovery = AirPlayDeviceDiscovery(context, scope)
    val discoveredDevices: StateFlow<Set<AirPlayDevice>> = deviceDiscovery.discoveredDevices
    
    fun start() {
        try {
            deviceDiscovery.startDiscovery()
            // Monitor disappearance of selected device for auto-disconnect
            monitorJob?.cancel()
            monitorJob = scope.launch {
                discoveredDevices.collect { devices ->
                    val selected = _selectedDevice.value
                    if (selected != null && devices.none { it.id == selected.id }) {
                        Timber.w("Selected AirPlay device disappeared, disconnecting: ${selected.name}")
                        disconnect()
                    }
                }
            }
            Timber.d("AirPlay service started and searching for devices")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AirPlay service")
        }
    }
    
    fun stop() {
        try {
            monitorJob?.cancel()
            deviceDiscovery.stopDiscovery()
            disconnect()
            Timber.d("AirPlay service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AirPlay service")
        }
    }
    
    fun connectToDevice(device: AirPlayDevice) {
        try {
            // Block RAOP (RTSP) connections for now: we only support HTTP AirPlay
            if (device.serviceType.contains("_raop._tcp", ignoreCase = true)) {
                Timber.w("Skipping RAOP device (unsupported for HTTP /play): ${device.name} ${device.host}:${device.port}")
                onAirPlayAuthRequired?.invoke()
                return
            }
            _selectedDevice.value = device
            _isConnected.value = true
            onAirPlaySessionStarted?.invoke()
            Timber.d("Connected to AirPlay device: ${device.name} at http://${device.host}:${device.port}")
            // Probe server-info for diagnostics (non-fatal)
            scope.launch { probeServerInfo(device) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to AirPlay device: ${device.name}")
        }
    }
    
    fun disconnect() {
        _selectedDevice.value = null
        _isConnected.value = false
        _authChallenge.value = null
        Timber.d("Disconnected from AirPlay device")
    }
    
    fun playMedia(mediaUrl: String, title: String, artist: String, albumArt: String? = null, mimeType: String = "audio/mpeg") {
        val device = _selectedDevice.value
        if (device == null) {
            Timber.w("Cannot play media: no AirPlay device selected")
            return
        }
        
        scope.launch {
            try {
                // Minimal, compatible /play body
                val playRequest = buildPlayRequest(mediaUrl)
                val success = sendAirPlayRequest(
                    device = device,
                    endpoint = "play",
                    body = playRequest,
                    method = "POST"
                )
                
                if (success) {
                    Timber.d("AirPlay playback started: $title - $artist")
                } else {
                    Timber.e("AirPlay playback request failed")
                    // If it was due to authentication, keep device to retry after credentials
                    if (!lastAuthRequired) {
                        _isConnected.value = false
                        _selectedDevice.value = null
                    } else {
                        Timber.w("AirPlay auth required; keeping device selected to retry after credentials")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing media on AirPlay device")
                // On failures (including ProtocolException from RTSP), mark disconnection
                _isConnected.value = false
                _selectedDevice.value = null
            }
        }
    }
    
    fun pauseMedia() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            sendAirPlayRequest(
                device = device,
                endpoint = "rate?value=0.0",
                method = "POST"
            )
        }
    }
    
    fun resumeMedia() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            sendAirPlayRequest(
                device = device,
                endpoint = "rate?value=1.0",
                method = "POST"
            )
        }
    }
    
    fun stopMedia() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            sendAirPlayRequest(
                device = device,
                endpoint = "stop",
                method = "POST"
            )
        }
    }

    private suspend fun probeServerInfo(device: AirPlayDevice) = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("http://${device.host}:${device.port}/server-info")
                .header("User-Agent", "AirPlay/1.0")
                .header("X-Apple-ProtocolVersion", "1.0")
                .header("X-Apple-DeviceID", deviceId)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string()?.take(500)
                Timber.d("AirPlay server-info: code=$code body=$body")
            }
        }.onFailure { Timber.w(it, "AirPlay server-info probe failed") }
    }

    private suspend fun sendAirPlayRequest(
        device: AirPlayDevice,
        endpoint: String,
        body: String? = null,
        method: String = "GET"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://${device.host}:${device.port}/$endpoint"
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "AirPlay/1.0")
                .header("X-Apple-ProtocolVersion", "1.0")
                .header("X-Apple-DeviceID", deviceId)
                .header("X-Apple-Client-Name", "Anitail Music on Android")
                .header("X-Apple-Session-ID", sessionId)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")

            when (method) {
                "POST" -> {
                    val mt = "text/parameters; charset=utf-8".toMediaType()
                    val rb = (body ?: "").toRequestBody(mt)
                    builder.post(rb)
                }

                else -> builder.get()
            }

            lastPendingRequest = PendingRequest(endpoint = endpoint, method = method, body = body)
            // Attach Authorization if we have credentials and/or a pending challenge
            attachAuthorizationHeaderIfNeeded(device, method, "/$endpoint", builder)

            val request = builder.build()
            httpClient.newCall(request).execute().use { response ->
                return@withContext handleResponse(device, url, response)
            }
        } catch (e: Exception) {
            // RAOP endpoints respond with RTSP and cause ProtocolException; treat as recoverable failure
            Timber.e(e, "AirPlay request error for endpoint=/$endpoint")
            false
        }
    }

    private fun handleResponse(device: AirPlayDevice, url: String, response: Response): Boolean {
        val code = response.code
        val isOk = response.isSuccessful
        val bodyStr = runCatching { response.body?.string() ?: "" }.getOrElse { "" }
        if (!isOk) {
            Timber.e("AirPlay HTTP error $code for $url, body=${bodyStr.take(500)}")
            if (code == 401 || code == 403) {
                // Intentar parsear desafío de autenticación
                val wa = response.header("WWW-Authenticate").orEmpty()
                val challenge = parseAuthChallenge(device, wa)
                if (challenge != null) {
                    _authChallenge.value = challenge
                }
                // Notify UI
                _isConnected.value = false
                Timber.e("AirPlay authentication required for ${device.name} (${device.host}:${device.port}). WWW-Authenticate='$wa'")
                onAirPlayAuthRequired?.invoke()
                lastAuthRequired = true
            }
        } else {
            Timber.d("AirPlay HTTP $code for $url")
            // Clear challenge if it existed
            _authChallenge.value = null
            lastAuthRequired = false
        }
        return isOk
    }

    private fun buildPlayRequest(mediaUrl: String): String {
        // AirPlay expects LF or CRLF separated parameters; use CRLF for compatibility
        val lines = listOf(
            "Content-Location: $mediaUrl",
            "Start-Position: 0.0"
        )
        return lines.joinToString(separator = "\r\n", postfix = "\r\n")
    }

    // region Auth helpers
    private fun parseAuthChallenge(device: AirPlayDevice, header: String): AuthChallenge? {
        if (header.isBlank()) return null
        val lower = header.lowercase()
        val scheme = when {
            lower.startsWith("basic") -> AuthScheme.BASIC
            lower.startsWith("digest") -> AuthScheme.DIGEST
            else -> return null
        }

        fun param(name: String): String? {
            val regex = Regex("(?i)${name}\\s*=\\s*\"?([^,\"]+)\"?")
            return regex.find(header)?.groupValues?.getOrNull(1)
        }
        return AuthChallenge(
            deviceId = device.id,
            deviceName = device.name,
            scheme = scheme,
            realm = param("realm"),
            nonce = param("nonce"),
            qop = param("qop"),
            opaque = param("opaque"),
            algorithm = param("algorithm")
        )
    }

    private fun attachAuthorizationHeaderIfNeeded(
        device: AirPlayDevice,
        method: String,
        uri: String,
        builder: Request.Builder
    ) {
        val creds = deviceCredentials[device.id] ?: return
        val challenge = _authChallenge.value
        when (challenge?.scheme) {
            AuthScheme.BASIC -> {
                val token = Base64.encodeToString(
                    "${creds.username}:${creds.password}".toByteArray(),
                    Base64.NO_WRAP
                )
                builder.header("Authorization", "Basic $token")
            }

            AuthScheme.DIGEST -> {
                val header = buildDigestAuthorizationHeader(method, uri, challenge, creds)
                if (header != null) builder.header("Authorization", header)
            }

            else -> {}
        }
    }

    private fun buildDigestAuthorizationHeader(
        method: String,
        uri: String,
        ch: AuthChallenge,
        creds: Credentials
    ): String? {
        val realm = ch.realm ?: return null
        val nonce = ch.nonce ?: return null
        val qop = ch.qop?.split(",")?.map { it.trim() }?.firstOrNull { it.equals("auth", true) }
        val algorithm = (ch.algorithm ?: "MD5").uppercase()

        fun md5Hex(s: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(s.toByteArray())
            return bytes.joinToString("") { String.format("%02x", it) }
        }

        val ha1 = if (algorithm == "MD5" || algorithm.isBlank()) {
            md5Hex("${creds.username}:$realm:${creds.password}")
        } else {
            // Fallback to MD5
            md5Hex("${creds.username}:$realm:${creds.password}")
        }
        val ha2 = md5Hex("$method:$uri")

        val header = StringBuilder("Digest ")
        val cnonce = Random.nextBytes(8).joinToString("") { String.format("%02x", it) }
        val nc = (++creds.nc).toString(16).padStart(8, '0')
        val response = if (qop != null) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }

        header.append("username=\"${creds.username}\",")
        header.append("realm=\"$realm\",")
        header.append("nonce=\"$nonce\",")
        header.append("uri=\"$uri\",")
        header.append("response=\"$response\",")
        if (qop != null) {
            header.append("qop=$qop,")
            header.append("nc=$nc,")
            header.append("cnonce=\"$cnonce\",")
        }
        ch.opaque?.let { header.append("opaque=\"$it\",") }
        header.append("algorithm=$algorithm")
        return header.toString()
    }

    fun providePinOrPassword(input: String, username: String = "AirPlay") {
        val device = _selectedDevice.value ?: return
        // Save credentials and retry next request with Authorization
        deviceCredentials[device.id] = Credentials(username = username, password = input)
        Timber.d("Credentials provided for AirPlay device ${device.name} (scheme=${_authChallenge.value?.scheme})")
        // Retry last pending request automatically
        val pending = lastPendingRequest
        if (pending != null) {
            scope.launch {
                val ok = sendAirPlayRequest(device, pending.endpoint, pending.body, pending.method)
                Timber.d("Retry after auth result: $ok for ${pending.endpoint}")
            }
        }
    }

    fun cancelAuthentication() {
        _authChallenge.value?.let { chal ->
            Timber.w("Authentication canceled for device ${chal.deviceName}")
        }
        _authChallenge.value = null
    }
    // endregion
}