package com.anitail.music.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.anitail.music.R
import com.anitail.music.utils.GooglePlayServicesUtils
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import timber.log.Timber

// Combined device data class for both Cast and DLNA devices
data class UniversalDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isSelected: Boolean,
    val isConnecting: Boolean,
    val castRoute: MediaRouter.RouteInfo? = null,
    val dlnaDevice: DlnaDevice? = null,
    val airPlayDevice: AirPlayDevice? = null
)

enum class DeviceType {
    CAST, DLNA, AIRPLAY
}

@Composable
fun UniversalDevicePickerDialog(
    dlnaManager: DlnaManager,
    airPlayManager: AirPlayManager,
    onDismiss: () -> Unit,
    onCastDeviceSelected: (MediaRouter.RouteInfo) -> Unit,
    onDlnaDeviceSelected: (DlnaDevice) -> Unit,
    onAirPlayDeviceSelected: (AirPlayDevice) -> Unit
) {
    Timber.d("UniversalDevicePickerDialog opened")
    val context = LocalContext.current
    
    // Cast setup
    val castAvailable = GooglePlayServicesUtils.isCastAvailable(context)
    remember {
        if (castAvailable) {
            runCatching { CastContext.getSharedInstance(context) }.getOrNull()
        } else null
    }
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()
    }

    var castRoutes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
    var discovering by remember { mutableStateOf(true) }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }

    // DLNA setup
    val dlnaDevices by dlnaManager.discoveredDevices.collectAsState()
    val selectedDlnaDevice by dlnaManager.selectedDevice.collectAsState()
    val isDlnaConnected by dlnaManager.isConnected.collectAsState()
    
    // AirPlay setup
    val airPlayDevices by airPlayManager.discoveredDevices.collectAsState()
    val selectedAirPlayDevice by airPlayManager.selectedDevice.collectAsState()
    val isAirPlayConnected by airPlayManager.isConnected.collectAsState()

    // Debug: Log de dispositivos cuando cambian
    LaunchedEffect(airPlayDevices) {
        Timber.d("AirPlay devices updated: ${airPlayDevices.size} devices")
        airPlayDevices.forEach { device ->
            Timber.d("AirPlay device: ${device.name} (${device.serviceType}) id=${device.id}")
        }
    }

    val callback = remember {
        object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refreshCastRoutes(router)

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refreshCastRoutes(router)

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refreshCastRoutes(router)

            private fun refreshCastRoutes(router: MediaRouter) {
                router.selectedRoute
                castRoutes = router.routes
                    .filter { it.isEnabled && !it.isDefault }
                    .filter {
                        it.supportsControlCategory(
                            CastMediaControlIntent.categoryForCast(
                                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                            )
                        )
                    }
                discovering = false
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        discovering = false
    }

    DisposableEffect(mediaRouter, selector) {
        if (castAvailable) {
            mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        }
        onDispose { 
            if (castAvailable) {
                mediaRouter.removeCallback(callback) 
            }
        }
    }

    // Combine Cast, DLNA, and AirPlay devices into a unified list
    val allDevices = remember(castRoutes, dlnaDevices, airPlayDevices, selectedDlnaDevice, isDlnaConnected, selectedAirPlayDevice, isAirPlayConnected, connectingDeviceId) {
        val devices = mutableListOf<UniversalDevice>()

        Timber.d("Building device list - Cast: ${castRoutes.size}, DLNA: ${dlnaDevices.size}, AirPlay: ${airPlayDevices.size}")
        
        // Add Cast devices
        val selectedCastRoute = mediaRouter.selectedRoute
        castRoutes.forEach { route ->
            val universalId = "cast_${route.id}"
            Timber.d("Adding Cast device: ${route.name} -> universalId=$universalId")
            devices.add(
                UniversalDevice(
                    id = universalId,
                    name = route.name,
                    type = DeviceType.CAST,
                    isSelected = route == selectedCastRoute && selectedCastRoute != mediaRouter.defaultRoute,
                    isConnecting = connectingDeviceId == route.id,
                    castRoute = route
                )
            )
        }
        
        // Add DLNA devices
        dlnaDevices.forEach { dlnaDevice ->
            val universalId = "dlna_${dlnaDevice.id}"
            Timber.d("Adding DLNA device: ${dlnaDevice.name} -> universalId=$universalId")
            devices.add(
                UniversalDevice(
                    id = universalId,
                    name = dlnaDevice.name,
                    type = DeviceType.DLNA,
                    isSelected = selectedDlnaDevice?.id == dlnaDevice.id && isDlnaConnected,
                    isConnecting = false,
                    dlnaDevice = dlnaDevice
                )
            )
        }

        // Add AirPlay devices (all supported service types)
        val filteredAirPlayDevices = airPlayDevices
            .filter { device ->
                // Por ahora mostramos solo servicios AirPlay HTTP; RAOP (RTSP) se excluye para evitar errores
                device.serviceType.contains("_airplay._tcp", ignoreCase = true)
            }

        Timber.d("Filtered AirPlay devices: ${filteredAirPlayDevices.size} out of ${airPlayDevices.size}")
        filteredAirPlayDevices.forEach { airPlayDevice ->
            val universalId = "airplay_${airPlayDevice.id}"
            Timber.d("Adding AirPlay device: ${airPlayDevice.name} (${airPlayDevice.serviceType}) id=${airPlayDevice.id} -> universalId=$universalId")
            devices.add(
                UniversalDevice(
                    id = universalId,
                    name = airPlayDevice.name,
                    type = DeviceType.AIRPLAY,
                    isSelected = selectedAirPlayDevice?.id == airPlayDevice.id && isAirPlayConnected,
                    isConnecting = false,
                    airPlayDevice = airPlayDevice
                )
            )
        }

        // Deduplicate by unique id to ensure stable, unique keys in LazyColumn
        Timber.d("Before deduplication: ${devices.size} devices")
        devices.forEach { device ->
            Timber.d("Device before dedup: ${device.name} (${device.type}) id=${device.id}")
        }

        val finalDevices = devices.distinctBy { it.id }
        Timber.d("Final device count after deduplication: ${finalDevices.size} (was ${devices.size})")

        finalDevices.forEach { device ->
            Timber.d("Final device: ${device.name} (${device.type}) id=${device.id}")
        }

        finalDevices
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text(
                stringResource(id = R.string.cast_devices),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (discovering && allDevices.isEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (allDevices.isEmpty()) {
                    Column {
                        Text(
                            stringResource(id = R.string.cast_no_devices),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Make sure your devices support Chromecast, DLNA/UPnP, or AirPlay",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Timber.d("Showing ${allDevices.size} devices in picker")
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(allDevices, key = { it.id }) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (device.isSelected) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.15f
                                        ) else Color.Transparent
                                    )
                                    .clickable(enabled = !device.isConnecting) {
                                        when (device.type) {
                                            DeviceType.CAST -> {
                                                device.castRoute?.let { route ->
                                                    connectingDeviceId = route.id
                                                    onCastDeviceSelected(route)
                                                }
                                            }

                                            DeviceType.DLNA -> {
                                                device.dlnaDevice?.let { dlnaDevice ->
                                                    onDlnaDeviceSelected(dlnaDevice)
                                                }
                                            }

                                            DeviceType.AIRPLAY -> {
                                                device.airPlayDevice?.let { airPlayDevice ->
                                                    onAirPlayDeviceSelected(airPlayDevice)
                                                }
                                            }
                                        }
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (device.type) {
                                            DeviceType.CAST -> R.drawable.ic_cast
                                            DeviceType.DLNA -> R.drawable.ic_cast // You may want to use a different icon for DLNA
                                            DeviceType.AIRPLAY -> R.drawable.ic_cast // You may want to use a different icon for AirPlay
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                    tint = if (device.isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (device.isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = when (device.type) {
                                            DeviceType.CAST -> "Chromecast"
                                            DeviceType.DLNA -> "DLNA/UPnP"
                                            DeviceType.AIRPLAY -> "AirPlay"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (device.isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}