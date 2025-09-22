package com.anitail.music.ui.component

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.cast.CastingType
import com.anitail.music.cast.UniversalCastManager
import com.anitail.music.cast.UniversalDevicePickerDialog
import timber.log.Timber

@Composable
fun UniversalCastButton(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    // Usar el UniversalCastManager compartido del MusicService si está disponible; si no, crear fallback local
    val sharedCastManager = playerConnection?.service?.getUniversalCastManager()
    val universalCastManager =
        remember(sharedCastManager, context.applicationContext, playerConnection) {
            sharedCastManager ?: UniversalCastManager(
            context.applicationContext,
            onCastSessionStarted = {
                // Sincronizar con Cast cuando se usa el manager local
                playerConnection?.service?.castCurrentToDevice()
            },
            onDlnaSessionStarted = {
                // Disparar casting DLNA cuando se usa el manager local
                playerConnection?.service?.castCurrentToDlnaDevice()
            },
            onAirPlaySessionStarted = {
                // Disparar casting AirPlay cuando se usa el manager local
                playerConnection?.service?.castCurrentToAirPlayDevice()
            }
        )
    }

    // Estado de casting
    val castingState by universalCastManager.castingState.collectAsState()
    val isPreparing by playerConnection?.service?.isCastPreparing?.collectAsState() ?: remember { mutableStateOf(false) }
    
    var showPicker by remember { mutableStateOf(false) }

    // Iniciar/detener el manager solo si usamos el fallback local; si es compartido, lo maneja el servicio
    DisposableEffect(universalCastManager, sharedCastManager) {
        if (sharedCastManager == null) {
            universalCastManager.start()
            onDispose { universalCastManager.stop() }
        } else {
            onDispose { }
        }
    }

    // Colores UI
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val tintColor = when {
        pureBlack -> if (castingState.isActive) primary else Color.White
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    val bgColor = Color.Transparent

    // Transferir reproducción cuando cambia el estado de casting (solo necesario con fallback local)
    LaunchedEffect(castingState) {
        val service = playerConnection?.service
        if (service != null) {
            runCatching {
                when (castingState.type) {
                    CastingType.CAST -> service.castCurrentToDevice()
                    CastingType.DLNA -> {
                        if (sharedCastManager == null) service.castCurrentToDlnaDevice() else Timber.d(
                            "DLNA casting activated"
                        )
                    }
                    CastingType.AIRPLAY -> {
                        if (sharedCastManager == null) service.castCurrentToAirPlayDevice() else Timber.d(
                            "AirPlay casting activated"
                        )
                    }
                    CastingType.NONE -> service.returnToLocalPlayback()
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                1.dp,
                if (castingState.isActive) primary.copy(alpha = 0.6f) else outline,
                CircleShape
            )
            .background(bgColor, CircleShape)
            .clickable(enabled = !isPreparing) {
                if (castingState.isActive) {
                    // Mostrar controlador según tipo
                    when (castingState.type) {
                        CastingType.CAST -> {
                            // Open Cast compose activity
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        context,
                                        com.anitail.music.cast.CastComposeActivity::class.java
                                    )
                                )
                            }
                        }

                        CastingType.DLNA -> {
                            Timber.d("DLNA device selected: ${castingState.deviceName}")
                        }

                        CastingType.AIRPLAY -> {
                            Timber.d("AirPlay device selected: ${castingState.deviceName}")
                        }

                        CastingType.NONE -> {
                            // No-op
                        }
                    }
                } else {
                    showPicker = true
                }
            }
    ) {
        Icon(
            painter = painterResource(
                if (castingState.isActive) R.drawable.ic_cast_filled else R.drawable.ic_cast
            ),
            contentDescription = null,
            tint = if (isPreparing) tintColor.copy(alpha = 0.35f) else tintColor,
            modifier = Modifier.size(20.dp)
        )
        
        if (isPreparing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = tintColor.copy(alpha = 0.9f)
            )
        }
        
        if (showPicker) {
            UniversalDevicePickerDialog(
                dlnaManager = universalCastManager.getDlnaManager(),
                airPlayManager = universalCastManager.getAirPlayManager(),
                onDismiss = { showPicker = false },
                onCastDeviceSelected = { route ->
                    // Selección Cast
                    route.select()
                    Timber.d("Cast device selected: ${route.name}")
                },
                onDlnaDeviceSelected = { dlnaDevice ->
                    // Selección DLNA
                    universalCastManager.getDlnaManager().connectToDevice(dlnaDevice)
                    Timber.d("DLNA device selected: ${dlnaDevice.name}")
                },
                onAirPlayDeviceSelected = { airPlayDevice ->
                    // Selección AirPlay
                    universalCastManager.getAirPlayManager().connectToDevice(airPlayDevice)
                    Timber.d("AirPlay device selected: ${airPlayDevice.name}")
                }
            )
        }
    }
}

@Composable 
fun UniversalCastMiniPlayerButton(pureBlack: Boolean, modifier: Modifier = Modifier) {
    UniversalCastButton(pureBlack = pureBlack, modifier = modifier)
}