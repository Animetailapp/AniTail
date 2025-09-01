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
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.cast.CastDevicePickerDialog
import com.anitail.music.cast.CastManager
import com.anitail.music.utils.GooglePlayServicesUtils
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

@Composable
fun CastMiniPlayerButton(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    // Verificar si Cast está disponible antes de renderizar
    if (!GooglePlayServicesUtils.isCastAvailable(context)) {
        return
    }
    
    // Usar singleton para evitar crear múltiples instancias
    val castManager = remember(context.applicationContext, playerConnection) {
        CastManager(
            context.applicationContext,
            onCastSessionStarted = {
                // Sincronizar automáticamente cuando se detecta una nueva sesión de Cast
                playerConnection?.service?.castCurrentToDevice()
            }
        )
    }

    // Inicializar solo una vez y manegar ciclo de vida
    LaunchedEffect(castManager) {
        runCatching {
            CastContext.getSharedInstance(context)
            castManager.start()
        }
    }

    DisposableEffect(castManager) {
        onDispose {
            castManager.stop()
        }
    }
    
    val isCasting by castManager.isCasting.collectAsState()
    val isPreparingState = playerConnection?.service?.isCastPreparing?.collectAsState()
    val isPreparing = isPreparingState?.value ?: false
    val routeButtonRef: MutableState<MediaRouteButton?> = remember { mutableStateOf(null) }
    var showPicker by remember { mutableStateOf(false) }
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val primary = MaterialTheme.colorScheme.primary
    val tintColor = when {
        isCasting -> Color.White // icono blanco cuando está casteando
        pureBlack -> Color.White.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    val bgColor = Color.Transparent

    // Transferir playback cuando cambia el estado de casting
    LaunchedEffect(isCasting) {
        val service = playerConnection?.service
        if (service != null) {
            runCatching {
                if (isCasting) service.castCurrentToDevice() else service.returnToLocalPlayback()
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, if (isCasting) primary.copy(alpha = 0.6f) else outline, CircleShape)
            .background(bgColor, CircleShape)
            .clickable(enabled = !isPreparing) {
                if (isCasting) {
                    // Nueva actividad Compose personalizada
                    runCatching {
                        context.startActivity(
                            Intent(
                                context,
                                com.anitail.music.cast.CastComposeActivity::class.java
                            )
                        )
                    }
                } else {
                    showPicker = true
                }
            }
    ) {
        Icon(
            painter = painterResource(if (isCasting) R.drawable.ic_cast_filled else R.drawable.ic_cast),
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
        AndroidView(
            modifier = Modifier.size(0.dp),
            factory = { ctx ->
                MediaRouteButton(ctx).also { btn ->
                    CastButtonFactory.setUpMediaRouteButton(ctx, btn)
                    routeButtonRef.value = btn
                }
            }
        )
        if (showPicker) {
            CastDevicePickerDialog(onDismiss = { showPicker = false })
        }
    }
}
