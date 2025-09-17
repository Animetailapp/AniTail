package com.anitail.music.cast

import android.content.Context
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
import timber.log.Timber
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
    private val onAirPlaySessionStarted: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
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
            Timber.d("AirPlay service started and searching for devices")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AirPlay service")
        }
    }
    
    fun stop() {
        try {
            deviceDiscovery.stopDiscovery()
            disconnect()
            Timber.d("AirPlay service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AirPlay service")
        }
    }
    
    fun connectToDevice(device: AirPlayDevice) {
        try {
            _selectedDevice.value = device
            _isConnected.value = true
            onAirPlaySessionStarted?.invoke()
            Timber.d("Connected to AirPlay device: ${device.name}")
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
                // AirPlay uses HTTP POST to /play endpoint with media information
                val playRequest = buildPlayRequest(mediaUrl, title, artist, albumArt, mimeType)
                
                val success = sendAirPlayRequest(
                    device = device,
                    endpoint = "play",
                    body = playRequest,
                    method = "POST"
                )
                
                if (success) {
                    Timber.d("Successfully started AirPlay playback for: $title")
                } else {
                    Timber.e("Failed to start AirPlay playback")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing media on AirPlay device")
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
    
    private suspend fun sendAirPlayRequest(
        device: AirPlayDevice,
        endpoint: String,
        body: String? = null,
        method: String = "GET"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("http://${device.host}:${device.port}/$endpoint")
                .header("User-Agent", "AniTail/1.0")
                .header("Content-Type", "text/parameters")
            
            when (method) {
                "POST" -> {
                    val requestBody = (body ?: "").toRequestBody("text/parameters".toMediaType())
                    requestBuilder.post(requestBody)
                }
                "GET" -> requestBuilder.get()
            }
            
            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful
            
            if (success) {
                Timber.d("AirPlay $endpoint request successful")
            } else {
                Timber.e("AirPlay $endpoint request failed with code: ${response.code}")
            }
            
            response.close()
            success
        } catch (e: Exception) {
            Timber.e(e, "Error sending AirPlay request to endpoint: $endpoint")
            false
        }
    }
    
    private fun buildPlayRequest(mediaUrl: String, title: String, artist: String, albumArt: String?, mimeType: String): String {
        val parameters = mutableListOf<String>()
        
        parameters.add("Content-Location: $mediaUrl")
        parameters.add("Start-Position: 0")
        
        // Add metadata if available
        if (title.isNotEmpty()) {
            parameters.add("X-Apple-AssetKey: $title")
        }
        
        if (artist.isNotEmpty()) {
            parameters.add("X-Apple-Artist: $artist")
        }
        
        albumArt?.let {
            parameters.add("X-Apple-Artwork-URL: $it")
        }
        
        return parameters.joinToString("\n")
    }
}