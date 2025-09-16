package com.anitail.music.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import java.net.InetAddress
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
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DlnaDevice>> = _discoveredDevices.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()
    
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    fun start() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            startDiscovery()
            Timber.d("DLNA service started and searching for devices")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start DLNA service")
        }
    }
    
    fun stop() {
        try {
            stopDiscovery()
            _discoveredDevices.value = emptyList()
            disconnect()
            Timber.d("DLNA service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping DLNA service")
        }
    }
    
    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("DLNA discovery start failed: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("DLNA discovery stop failed: $errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                Timber.d("DLNA discovery started for: $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Timber.d("DLNA discovery stopped for: $serviceType")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    Timber.d("DLNA service found: ${info.serviceName}")
                    resolveService(info)
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    val currentDevices = _discoveredDevices.value.toMutableList()
                    val removed = currentDevices.removeAll { it.name == info.serviceName }
                    if (removed) {
                        _discoveredDevices.value = currentDevices
                        Timber.d("DLNA device lost: ${info.serviceName}")
                        
                        // If the currently selected device was lost, disconnect
                        if (_selectedDevice.value?.name == info.serviceName) {
                            disconnect()
                        }
                    }
                }
            }
        }
        
        // Discover DLNA/UPnP media renderers
        nsdManager?.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
    
    private fun stopDiscovery() {
        discoveryListener?.let { listener ->
            nsdManager?.stopServiceDiscovery(listener)
        }
        discoveryListener = null
    }
    
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Timber.w("Failed to resolve service: ${serviceInfo?.serviceName}, error: $errorCode")
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    val host = info.host?.hostAddress ?: return
                    val port = info.port
                    
                    // Check if this looks like a DLNA device (basic heuristic)
                    if (isDlnaDevice(info)) {
                        val dlnaDevice = DlnaDevice(
                            id = "${host}:${port}",
                            name = info.serviceName,
                            host = host,
                            port = port
                        )
                        
                        val currentDevices = _discoveredDevices.value.toMutableList()
                        if (currentDevices.none { it.id == dlnaDevice.id }) {
                            currentDevices.add(dlnaDevice)
                            _discoveredDevices.value = currentDevices
                            Timber.d("DLNA device resolved: ${dlnaDevice.name} at ${dlnaDevice.host}:${dlnaDevice.port}")
                        }
                    }
                }
            }
        }
        
        nsdManager?.resolveService(serviceInfo, resolveListener)
    }
    
    private fun isDlnaDevice(serviceInfo: NsdServiceInfo): Boolean {
        // Basic heuristics to identify DLNA devices
        val name = serviceInfo.serviceName.lowercase()
        return name.contains("dlna") || 
               name.contains("media") || 
               name.contains("renderer") ||
               name.contains("tv") ||
               serviceInfo.serviceType.contains("_http._tcp")
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