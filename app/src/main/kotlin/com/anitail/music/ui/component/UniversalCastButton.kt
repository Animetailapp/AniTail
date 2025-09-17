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
import androidx.mediarouter.media.MediaRouter
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.cast.CastingType
import com.anitail.music.cast.AirPlayDevice
import com.anitail.music.cast.DlnaDevice
import com.anitail.music.cast.UniversalCastManager
import com.anitail.music.cast.UniversalDevicePickerDialog
import timber.log.Timber

@Composable
fun UniversalCastButton(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    // Create universal cast manager
    val universalCastManager = remember(context.applicationContext, playerConnection) {
        UniversalCastManager(
            context.applicationContext,
            onCastSessionStarted = {
                // Synchronize automatically when a Cast session is detected
                playerConnection?.service?.castCurrentToDevice()
            },
            onDlnaSessionStarted = {
                // Handle DLNA session start
                Timber.d("DLNA session started")
            },
            onAirPlaySessionStarted = {
                // Handle AirPlay session start
                Timber.d("AirPlay session started")
            }
        )
    }

    // Collect casting state
    val castingState by universalCastManager.castingState.collectAsState()
    val isPreparing by playerConnection?.service?.isCastPreparing?.collectAsState() ?: remember { mutableStateOf(false) }
    
    var showPicker by remember { mutableStateOf(false) }

    // Start/stop the manager
    DisposableEffect(universalCastManager) {
        universalCastManager.start()
        onDispose { universalCastManager.stop() }
    }

    // UI colors based on casting state and theme
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val tintColor = when {
        pureBlack -> if (castingState.isActive) primary else Color.White
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    val bgColor = Color.Transparent

    // Transfer playback when casting state changes
    LaunchedEffect(castingState) {
        val service = playerConnection?.service
        if (service != null) {
            runCatching {
                when (castingState.type) {
                    CastingType.CAST -> service.castCurrentToDevice()
                    CastingType.DLNA -> {
                        // For DLNA, we need to handle playback differently
                        // The current media will be sent to DLNA device via UniversalCastManager
                        Timber.d("DLNA casting activated")
                    }
                    CastingType.AIRPLAY -> {
                        // For AirPlay, we need to handle playback differently
                        // The current media will be sent to AirPlay device via UniversalCastManager
                        Timber.d("AirPlay casting activated")
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
                    // Show expanded controller based on casting type
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
                            // For DLNA, you might want to show a simple control dialog
                            // or navigate to a DLNA-specific activity
                            Timber.d("DLNA device selected: ${castingState.deviceName}")
                        }
                        CastingType.AIRPLAY -> {
                            // For AirPlay, you might want to show a simple control dialog
                            // or navigate to an AirPlay-specific activity
                            Timber.d("AirPlay device selected: ${castingState.deviceName}")
                        }
                        CastingType.NONE -> {
                            // This shouldn't happen but handle gracefully
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
                    // Handle Cast device selection
                    route.select()
                    Timber.d("Cast device selected: ${route.name}")
                },
                onDlnaDeviceSelected = { dlnaDevice ->
                    // Handle DLNA device selection  
                    universalCastManager.getDlnaManager().connectToDevice(dlnaDevice)
                    Timber.d("DLNA device selected: ${dlnaDevice.name}")
                },
                onAirPlayDeviceSelected = { airPlayDevice ->
                    // Handle AirPlay device selection  
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