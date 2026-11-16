package com.anitail.desktop.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

enum class CastProtocol {
    GOOGLE_CAST,
    DLNA,
    FCAST,
}

data class CastDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val protocol: CastProtocol,
    val modelName: String? = null,
)

class DesktopCastManager private constructor() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastDevice?>(null)
    val connectedDevice: StateFlow<CastDevice?> = _connectedDevice.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var discoveryJob: Job? = null
    private var jmDnsInstances = mutableListOf<JmDNS>()
    private val discoveredMap = mutableMapOf<String, CastDevice>()

    companion object {
        val instance by lazy { DesktopCastManager() }
        private const val SSDP_PORT = 1900
        private const val SSDP_MULTICAST = "239.255.255.250"
    }

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true
        discoveredMap.clear()
        _devices.value = emptyList()

        discoveryJob = scope.launch {
            launch { startMdnsDiscovery() }
            launch { startDlnaSsdpDiscovery() }
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        discoveryJob?.cancel()
        discoveryJob = null
        scope.launch {
            stopMdnsDiscovery()
        }
    }

    fun connect(device: CastDevice) {
        _connectedDevice.value = device
    }

    fun disconnect() {
        _connectedDevice.value = null
    }

    private fun addOrUpdateDevice(device: CastDevice) {
        synchronized(discoveredMap) {
            discoveredMap[device.id] = device
            _devices.value = discoveredMap.values.toList()
        }
    }

    private fun removeDevice(id: String) {
        synchronized(discoveredMap) {
            if (discoveredMap.remove(id) != null) {
                _devices.value = discoveredMap.values.toList()
            }
        }
    }

    private suspend fun startMdnsDiscovery() = withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = iface.inetAddresses.toList().filterIsInstance<java.net.Inet4Address>()
                for (addr in addrs) {
                    try {
                        val jmdns = JmDNS.create(addr, "AniTail-Cast")
                        jmDnsInstances.add(jmdns)

                        val listener = object : ServiceListener {
                            override fun serviceAdded(event: ServiceEvent) {
                                jmdns.requestServiceInfo(event.type, event.name, 3000)
                            }

                            override fun serviceRemoved(event: ServiceEvent) {
                                removeDevice(event.name)
                            }

                            override fun serviceResolved(event: ServiceEvent) {
                                val info = event.info ?: return
                                val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                                val fn = info.getPropertyString("fn") ?: event.name
                                val md = info.getPropertyString("md")
                                val isCast = event.type.contains("googlecast", ignoreCase = true)
                                val protocol = if (isCast) CastProtocol.GOOGLE_CAST else CastProtocol.FCAST

                                addOrUpdateDevice(
                                    CastDevice(
                                        id = "${event.type}:${event.name}",
                                        name = fn,
                                        address = host,
                                        port = info.port,
                                        protocol = protocol,
                                        modelName = md,
                                    ),
                                )
                            }
                        }

                        jmdns.addServiceListener("_googlecast._tcp.local.", listener)
                        jmdns.addServiceListener("_fcast._tcp.local.", listener)
                    } catch (_: Throwable) {
                        // Ignore bind errors on specific virtual adapters
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun stopMdnsDiscovery() {
        for (jmdns in jmDnsInstances) {
            try {
                jmdns.close()
            } catch (_: Throwable) {}
        }
        jmDnsInstances.clear()
    }

    private suspend fun startDlnaSsdpDiscovery() = withContext(Dispatchers.IO) {
        val query = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 3
            ST: urn:schemas-upnp-org:device:MediaRenderer:1

        """.trimIndent().replace("\n", "\r\n")

        val data = query.toByteArray(StandardCharsets.UTF_8)
        val group = InetAddress.getByName(SSDP_MULTICAST)

        while (isActive && _isDiscovering.value) {
            try {
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(0))
                    socket.soTimeout = 2000

                    val packet = DatagramPacket(data, data.size, group, SSDP_PORT)
                    socket.send(packet)

                    val buffer = ByteArray(4096)
                    val receivePacket = DatagramPacket(buffer, buffer.size)

                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 3000L && isActive) {
                        try {
                            socket.receive(receivePacket)
                            val text = String(receivePacket.data, 0, receivePacket.length, StandardCharsets.UTF_8)
                            val host = receivePacket.address.hostAddress

                            // Parse location or friendly name
                            val location = Regex("(?i)LOCATION:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()
                            val server = Regex("(?i)SERVER:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()
                            val usn = Regex("(?i)USN:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim() ?: host

                            val displayName = server?.split('/')?.firstOrNull() ?: "Smart TV ($host)"

                            addOrUpdateDevice(
                                CastDevice(
                                    id = usn,
                                    name = displayName,
                                    address = host,
                                    port = receivePacket.port,
                                    protocol = CastProtocol.DLNA,
                                    modelName = location,
                                ),
                            )
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}

            delay(6000L)
        }
    }
}

