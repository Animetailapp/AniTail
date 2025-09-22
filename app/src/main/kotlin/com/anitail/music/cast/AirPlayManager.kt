package com.anitail.music.cast

import android.content.Context
import android.provider.Settings
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
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    
    // Use enhanced device discovery for AirPlay devices
    private val deviceDiscovery = AirPlayDeviceDiscovery(context, scope)
    val discoveredDevices: StateFlow<Set<AirPlayDevice>> = deviceDiscovery.discoveredDevices
    
    fun start() {
        try {
            deviceDiscovery.startDiscovery()
            // Monitorizar desaparición del dispositivo seleccionado para auto-desconectar
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
            // Bloquear conexiones RAOP (RTSP) por ahora: sólo soportamos HTTP AirPlay
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
                    // Resetear estado para no bloquear botón si falla
                    _isConnected.value = false
                    _selectedDevice.value = null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing media on AirPlay device")
                // En fallos (incluido ProtocolException por RTSP), marcar desconexión
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

            val request = builder.build()
            httpClient.newCall(request).execute().use { response ->
                return@withContext handleResponse(device, url, response)
            }
        } catch (e: Exception) {
            // RAOP endpoints responden con RTSP y causan ProtocolException; tratar como fallo recuperable
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
                // Auth requerida: notificar y marcar desconectado
                _isConnected.value = false
                Timber.e("AirPlay authentication required for ${device.name} (${device.host}:${device.port}). Configure el dispositivo para permitir acceso o empareje en HomeKit.")
                onAirPlayAuthRequired?.invoke()
            }
        } else {
            Timber.d("AirPlay HTTP $code for $url")
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
}