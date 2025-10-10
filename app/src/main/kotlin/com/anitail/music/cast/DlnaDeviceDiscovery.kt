package com.anitail.music.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Enhanced DLNA device discovery that searches for multiple service types
 * to improve compatibility with different DLNA implementations
 */
class DlnaDeviceDiscovery(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredDevices = MutableStateFlow<Set<DlnaDevice>>(emptySet())
    val discoveredDevices: StateFlow<Set<DlnaDevice>> = _discoveredDevices.asStateFlow()
    
    private var nsdManager: NsdManager? = null
    private val activeDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    
    // Common DLNA/UPnP service types to search for
    private val dlnaServiceTypes = listOf(
        "_http._tcp",      // General HTTP services (many DLNA devices)
        "_upnp._tcp",      // UPnP services
        "_dlna._tcp",      // Specific DLNA services
        "_mediaserver._tcp", // Media server services
        "_renderer._tcp"   // Media renderer services
    )
    
    fun startDiscovery() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            
            // Start discovery for each service type
            dlnaServiceTypes.forEach { serviceType ->
                startDiscoveryForServiceType(serviceType)
            }
            
            Timber.d("Started DLNA discovery for ${dlnaServiceTypes.size} service types")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start DLNA device discovery")
        }
    }
    
    fun stopDiscovery() {
        try {
            activeDiscoveryListeners.forEach { listener ->
                nsdManager?.stopServiceDiscovery(listener)
            }
            activeDiscoveryListeners.clear()
            _discoveredDevices.value = emptySet()
            Timber.d("Stopped DLNA device discovery")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping DLNA device discovery")
        }
    }
    
    private fun startDiscoveryForServiceType(serviceType: String) {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.w("DLNA discovery start failed for $serviceType: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.w("DLNA discovery stop failed for $serviceType: $errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                Timber.d("DLNA discovery started for: $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Timber.d("DLNA discovery stopped for: $serviceType")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    if (isPotentialDlnaDevice(info)) {
                        Timber.d("Potential DLNA service found: ${info.serviceName} (${info.serviceType})")
                        resolveService(info)
                    }
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    removeDevice(info.serviceName)
                }
            }
        }
        
        activeDiscoveryListeners.add(discoveryListener)
        nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
    
    private fun isPotentialDlnaDevice(serviceInfo: NsdServiceInfo): Boolean {
        val name = serviceInfo.serviceName.lowercase()
        val type = serviceInfo.serviceType.lowercase()
        
        // Enhanced heuristics for DLNA device detection
        val nameIndicators = listOf(
            "dlna", "upnp", "media", "renderer", "tv", "samsung", "lg", "sony", 
            "philips", "panasonic", "roku", "player", "stream", "cast"
        )
        
        val typeIndicators = listOf(
            "_http._tcp", "_upnp._tcp", "_dlna._tcp", "_mediaserver._tcp", "_renderer._tcp"
        )
        
        return nameIndicators.any { name.contains(it) } || 
               typeIndicators.any { type.contains(it) } ||
               serviceInfo.port in 8080..8200 // Common DLNA port range
    }
    
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                if (errorCode != NsdManager.FAILURE_ALREADY_ACTIVE) {
                    Timber.w("Failed to resolve DLNA service: ${serviceInfo?.serviceName}, error: $errorCode")
                }
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    val host = info.host?.hostAddress
                    if (host != null && isValidDlnaDevice(info)) {
                        val device = DlnaDevice(
                            id = "${host}:${info.port}",
                            name = cleanDeviceName(info.serviceName),
                            host = host,
                            port = info.port,
                            serviceType = info.serviceType
                        )
                        
                        addDevice(device)
                        Timber.d("DLNA device resolved: ${device.name} at ${device.host}:${device.port}")
                    }
                }
            }
        }
        
        nsdManager?.resolveService(serviceInfo, resolveListener)
    }
    
    private fun isValidDlnaDevice(serviceInfo: NsdServiceInfo): Boolean {
        // Additional validation for resolved services
        val port = serviceInfo.port
        val host = serviceInfo.host?.hostAddress
        
        return host != null && 
               port > 0 && 
               port < 65536 &&
               !host.startsWith("127.") // Exclude localhost
    }
    
    private fun cleanDeviceName(serviceName: String): String {
        // Clean up device names for better display
        return serviceName
            .replace(Regex("\\[.*?\\]"), "") // Remove bracketed text
            .replace(Regex("_+"), " ") // Replace underscores with spaces
            .trim()
    }
    
    private fun addDevice(device: DlnaDevice) {
        val currentDevices = _discoveredDevices.value.toMutableSet()
        if (currentDevices.add(device)) {
            _discoveredDevices.value = currentDevices
        }
    }
    
    private fun removeDevice(serviceName: String) {
        val currentDevices = _discoveredDevices.value.toMutableSet()
        val removed = currentDevices.removeAll { it.name == serviceName }
        if (removed) {
            _discoveredDevices.value = currentDevices
            Timber.d("DLNA device removed: $serviceName")
        }
    }
}