package com.anitail.music.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * AirPlay device discovery that searches for AirPlay-compatible devices
 * using Bonjour/mDNS service discovery
 */
class AirPlayDeviceDiscovery(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredDevices = MutableStateFlow<Set<AirPlayDevice>>(emptySet())
    val discoveredDevices: StateFlow<Set<AirPlayDevice>> = _discoveredDevices.asStateFlow()
    
    private var nsdManager: NsdManager? = null
    private val activeDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    
    // AirPlay service types to search for
    private val airPlayServiceTypes = listOf(
        "_airplay._tcp",        // Standard AirPlay service
        "_raop._tcp",          // Remote Audio Output Protocol (older AirPlay audio)
        "_airplay._tcp.local", // Local domain AirPlay
        "_homekit._tcp"        // HomeKit devices often support AirPlay
    )
    
    fun startDiscovery() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            
            // Start discovery for each service type
            airPlayServiceTypes.forEach { serviceType ->
                startDiscoveryForServiceType(serviceType)
            }
            
            Timber.d("Started AirPlay discovery for ${airPlayServiceTypes.size} service types")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AirPlay device discovery")
        }
    }
    
    fun stopDiscovery() {
        var hadError = false
        activeDiscoveryListeners.forEach { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (iae: IllegalArgumentException) {
                // Listener was never registered or already unregistered; ignore
                Timber.d("Ignoring stopServiceDiscovery for unregistered listener")
            } catch (e: Exception) {
                hadError = true
                Timber.e(e, "Error stopping a discovery listener")
            }
        }
        activeDiscoveryListeners.clear()
        _discoveredDevices.value = emptySet()
        if (!hadError) {
            Timber.d("Stopped AirPlay device discovery")
        }
    }
    
    private fun startDiscoveryForServiceType(serviceType: String) {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.w("AirPlay discovery start failed for $serviceType: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.w("AirPlay discovery stop failed for $serviceType: $errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                Timber.d("AirPlay discovery started for: $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Timber.d("AirPlay discovery stopped for: $serviceType")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    if (isPotentialAirPlayDevice(info)) {
                        Timber.d("Potential AirPlay service found: ${info.serviceName} (${info.serviceType})")
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
    
    private fun isPotentialAirPlayDevice(serviceInfo: NsdServiceInfo): Boolean {
        val name = serviceInfo.serviceName.lowercase()
        val type = serviceInfo.serviceType.lowercase()
        
        // Enhanced heuristics for AirPlay device detection
        val nameIndicators = listOf(
            "airplay", "apple", "tv", "homepod", "airport", "mac", "iphone", "ipad",
            "lg", "samsung", "sony", "vizio", "roku", "speaker", "soundbar"
        )
        
        val typeIndicators = listOf(
            "_airplay._tcp", "_raop._tcp", "_homekit._tcp"
        )
        
        // Check for known AirPlay service types
        val isAirPlayServiceType = typeIndicators.any { type.contains(it.lowercase()) }
        
        // Check for device names that commonly support AirPlay
        val hasAirPlayName = nameIndicators.any { name.contains(it) }
        
        // AirPlay typically uses specific port ranges
        val isAirPlayPort = serviceInfo.port in 5000..7000 || serviceInfo.port in 49152..65535
        
        return isAirPlayServiceType || (hasAirPlayName && isAirPlayPort)
    }
    
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                if (errorCode != NsdManager.FAILURE_ALREADY_ACTIVE) {
                    Timber.w("Failed to resolve AirPlay service: ${serviceInfo?.serviceName}, error: $errorCode")
                }
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    val host = info.host?.hostAddress
                    if (host != null && isValidAirPlayDevice(info)) {
                        val device = AirPlayDevice(
                            id = "${info.serviceName}@$host:${info.port}",
                            name = cleanDeviceName(info.serviceName),
                            host = host,
                            port = info.port,
                            serviceType = info.serviceType
                        )
                        
                        addDevice(device)
                        Timber.d("AirPlay device resolved: ${device.name} at ${device.host}:${device.port}")
                    }
                }
            }
        }
        
        nsdManager?.resolveService(serviceInfo, resolveListener)
    }
    
    private fun isValidAirPlayDevice(serviceInfo: NsdServiceInfo): Boolean {
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
            .replace(" s ", "'s ") // Fix possessive forms
            .trim()
    }
    
    private fun addDevice(device: AirPlayDevice) {
        val current = _discoveredDevices.value.toMutableSet()
        // Con IDs únicos basados en serviceName@host:port, simplemente agregamos o actualizamos
        val removed = current.removeAll { it.id == device.id }
        if (removed) {
            Timber.d("Updated AirPlay device: ${device.name} (id=${device.id})")
        }
        current.add(device)
        _discoveredDevices.value = current
        Timber.d("Added AirPlay device: ${device.name} (id=${device.id})")
    }
    
    private fun removeDevice(serviceName: String) {
        val current = _discoveredDevices.value.toMutableSet()
        // Buscar dispositivos que empiecen con el serviceName (ya que ID incluye serviceName)
        val removed = current.removeAll { it.id.startsWith("$serviceName@") }
        if (removed) {
            _discoveredDevices.value = current
            Timber.d("AirPlay device removed: $serviceName")
        }
    }
}