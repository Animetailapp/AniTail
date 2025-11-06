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

data class DlnaDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String = "DLNA"
)

class DlnaManager(
    private val context: Context,
    private val onDlnaSessionStarted: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: kotlinx.coroutines.Job? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()
    
    // Use enhanced device discovery
    private val deviceDiscovery = DlnaDeviceDiscovery(context, scope)
    val discoveredDevices: StateFlow<Set<DlnaDevice>> = deviceDiscovery.discoveredDevices
    
    fun start() {
        try {
            deviceDiscovery.startDiscovery()
            // Monitor disappearance of selected device
            monitorJob?.cancel()
            monitorJob = scope.launch {
                discoveredDevices.collect { devices ->
                    val selected = _selectedDevice.value
                    if (selected != null && devices.none { it.id == selected.id }) {
                        Timber.w("Selected DLNA device disappeared, disconnecting: ${selected.name}")
                        disconnect()
                    }
                }
            }
            Timber.d("DLNA service started and searching for devices")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start DLNA service")
        }
    }
    
    fun stop() {
        try {
            monitorJob?.cancel()
            deviceDiscovery.stopDiscovery()
            disconnect()
            Timber.d("DLNA service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping DLNA service")
        }
    }
    
    fun connectToDevice(device: DlnaDevice) {
        try {
            _selectedDevice.value = device
            _isConnected.value = true
            onDlnaSessionStarted?.invoke()
            Timber.d("Connected to DLNA device: ${device.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to DLNA device: ${device.name}")
        }
    }
    
    fun disconnect() {
        _selectedDevice.value = null
        _isConnected.value = false
        Timber.d("Disconnected from DLNA device")
    }
    
    fun playMedia(mediaUrl: String, title: String, artist: String, albumArt: String? = null, mimeType: String = "audio/mpeg") {
        val device = _selectedDevice.value
        if (device == null) {
            Timber.w("Cannot play media: no DLNA device selected")
            return
        }
        
        scope.launch {
            try {
                // For a real implementation, you would send UPnP SOAP requests
                // This is a simplified version that demonstrates the concept
                val didlMetadata = buildDidlMetadata(title, artist, albumArt, mimeType)
                
                // Send SetAVTransportURI request
                val setUriSuccess = sendSoapRequest(
                    device = device,
                    action = "SetAVTransportURI",
                    arguments = mapOf(
                        "InstanceID" to "0",
                        "CurrentURI" to mediaUrl,
                        "CurrentURIMetaData" to didlMetadata
                    )
                )
                
                if (setUriSuccess) {
                    // Send Play request
                    sendSoapRequest(
                        device = device,
                        action = "Play",
                        arguments = mapOf(
                            "InstanceID" to "0",
                            "Speed" to "1"
                        )
                    )
                    Timber.d("Successfully started DLNA playback for: $title")
                } else {
                    Timber.e("Failed to set AV Transport URI for DLNA device")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing media on DLNA device")
            }
        }
    }
    
    fun pauseMedia() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            sendSoapRequest(
                device = device,
                action = "Pause",
                arguments = mapOf("InstanceID" to "0")
            )
        }
    }
    
    fun stopMedia() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            sendSoapRequest(
                device = device,
                action = "Stop",
                arguments = mapOf("InstanceID" to "0")
            )
        }
    }
    
    private suspend fun sendSoapRequest(
        device: DlnaDevice,
        action: String,
        arguments: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val soapBody = buildSoapRequest(action, arguments)
            val request = Request.Builder()
                .url("http://${device.host}:${device.port}/AVTransport/control")
                .post(soapBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
                .header("Content-Type", "text/xml; charset=utf-8")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful
            
            if (success) {
                Timber.d("DLNA $action request successful")
            } else {
                Timber.e("DLNA $action request failed with code: ${response.code}")
            }
            
            response.close()
            success
        } catch (e: Exception) {
            Timber.e(e, "Error sending DLNA SOAP request for action: $action")
            false
        }
    }
    
    private fun buildSoapRequest(action: String, arguments: Map<String, String>): String {
        val args = arguments.entries.joinToString("") { (key, value) ->
            "<$key>$value</$key>"
        }
        
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        $args
                    </u:$action>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
    
    private fun buildDidlMetadata(title: String, artist: String, albumArt: String?, mimeType: String): String {
        val artTag = if (albumArt != null) {
            "<upnp:albumArtURI>$albumArt</upnp:albumArtURI>"
        } else {
            ""
        }
        
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" 
                       xmlns:dc="http://purl.org/dc/elements/1.1/" 
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="1" parentID="0" restricted="1">
                    <dc:title>$title</dc:title>
                    <dc:creator>$artist</dc:creator>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    $artTag
                    <res protocolInfo="http-get:*:$mimeType:*"></res>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }
}