package com.anitail.shared.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.shared.cast.CastDevice
import com.anitail.shared.cast.CastError
import com.anitail.shared.cast.CastProtocol
import com.anitail.shared.cast.CastState
import com.anitail.shared.deviceconnect.DcSyncState
import com.anitail.shared.deviceconnect.DeviceConnectManager
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.cast_connection_failed
import com.anitail.shared.generated.resources.cast_discovery_failed
import com.anitail.shared.generated.resources.cast_no_media
import com.anitail.shared.generated.resources.cast_playback_failed
import com.anitail.shared.generated.resources.cast_unsupported_stream
import com.anitail.shared.generated.resources.dlna
import com.anitail.shared.generated.resources.fcast
import com.anitail.shared.generated.resources.google_cast
import com.anitail.shared.generated.resources.sonos
import com.anitail.shared.settings.Settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CastDeviceDialog(
    devices: List<CastDevice>,
    state: CastState,
    onDismiss: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onDeviceClick: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    deviceState: DeviceConnectManager.State? = null,
    syncState: DcSyncState = DcSyncState(),
    onEnableDeviceConnect: (() -> Unit)? = null,
    onTransferTo: (String) -> Unit = {},
) {
    if (!Settings.shared.castEnabled) return
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose(onStopDiscovery)
    }
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            ConnectedDevicesContent(
                devices = devices,
                castState = state,
                onDeviceClick = onDeviceClick,
                onDisconnectCast = onDisconnect,
                onClose = onDismiss,
                deviceState = deviceState,
                syncState = syncState,
                onEnableDeviceConnect = onEnableDeviceConnect,
                onTransferTo = onTransferTo,
                modifier = Modifier.heightIn(max = 640.dp),
            )
        }
    }
}

@Composable
internal fun protocolName(protocol: CastProtocol): String =
    stringResource(
        when (protocol) {
            CastProtocol.FCAST -> Res.string.fcast
            CastProtocol.GOOGLE_CAST -> Res.string.google_cast
            CastProtocol.DLNA -> Res.string.dlna
            CastProtocol.SONOS -> Res.string.sonos
        },
    )

internal val CastError.stringResource: StringResource
    get() =
        when (this) {
            CastError.DISCOVERY_FAILED -> Res.string.cast_discovery_failed
            CastError.NO_MEDIA -> Res.string.cast_no_media
            CastError.UNSUPPORTED_STREAM -> Res.string.cast_unsupported_stream
            CastError.CONNECTION_FAILED -> Res.string.cast_connection_failed
            CastError.PLAYBACK_FAILED -> Res.string.cast_playback_failed
        }
