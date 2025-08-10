package com.anitail.music.cast

import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anitail.music.R
import com.anitail.music.constants.DarkModeKey
import com.anitail.music.constants.PureBlackKey
import com.anitail.music.ui.screens.settings.DarkMode
import com.anitail.music.ui.theme.AnitailTheme
import com.anitail.music.ui.theme.DefaultThemeColor
import com.anitail.music.ui.theme.extractThemeColor
import com.anitail.music.utils.dataStore
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla expandida personalizada para sesiones Cast usando Compose.
 * Controla reproducción vía MediaController (Media3) apuntando al MusicService.
 */
@dagger.hilt.android.AndroidEntryPoint
class CastComposeActivity : ComponentActivity() {
    private var controller: MediaController? = null
    @javax.inject.Inject
    lateinit var database: com.anitail.music.db.MusicDatabase
    var playerConnection by mutableStateOf<com.anitail.music.playback.PlayerConnection?>(null)
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: android.os.IBinder?
        ) {
            if (service is com.anitail.music.playback.MusicService.MusicBinder) {
                playerConnection = com.anitail.music.playback.PlayerConnection(
                    this@CastComposeActivity,
                    service,
                    database,
                    lifecycleScope
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose(); playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(
            android.content.Intent(
                this,
                com.anitail.music.playback.MusicService::class.java
            )
        )
        bindService(
            android.content.Intent(
                this,
                com.anitail.music.playback.MusicService::class.java
            ), serviceConnection, BIND_AUTO_CREATE
        )
        lifecycleScope.launch {
            runCatching {
                val token = SessionToken(
                    this@CastComposeActivity,
                    ComponentName(
                        this@CastComposeActivity,
                        com.anitail.music.playback.MusicService::class.java
                    )
                )
                val future = MediaController.Builder(this@CastComposeActivity, token).buildAsync()
                controller = future.await()
            }
        }

        setContent {
            val prefs by produceState(initialValue = Pair(DarkMode.AUTO, false)) {
                val data = applicationContext.dataStore.data.first()
                val dark = when (data[DarkModeKey]) {
                    "ON" -> DarkMode.ON
                    "OFF" -> DarkMode.OFF
                    "TIME_BASED" -> DarkMode.TIME_BASED
                    else -> DarkMode.AUTO
                }
                val pure = data[PureBlackKey] ?: false
                value = dark to pure
            }

            var themeColor by remember { mutableStateOf(DefaultThemeColor) }
            val ctx = LocalContext.current
            LaunchedEffect(Unit) {
                while (true) {
                    val c = controller
                    val mediaItem = c?.currentMediaItem
                    val artwork = mediaItem?.mediaMetadata?.artworkUri
                    if (artwork != null) {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                ImageLoader(ctx).execute(
                                    ImageRequest.Builder(ctx).data(artwork).allowHardware(false)
                                        .build()
                                ).drawable
                            }
                            val bmp = (result as? BitmapDrawable)?.bitmap
                            if (bmp != null) themeColor = bmp.extractThemeColor()
                        } catch (_: Exception) {
                        }
                    }
                    delay(5000)
                }
            }

            AnitailTheme(
                darkMode = prefs.first,
                pureBlack = prefs.second,
                themeColor = themeColor
            ) {
                CastExpandedContent(controllerProvider = { controller }) { finish() }
            }
        }
    }

    override fun onDestroy() {
        try {
            unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        controller?.release()
        controller = null
        super.onDestroy()
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
private fun CastExpandedContent(controllerProvider: () -> MediaController?, onClose: () -> Unit) {
    val controller = controllerProvider()
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf<String?>(null) }
    var repeatMode by remember { mutableStateOf(0) }
    var shuffleOn by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("") }
    var deviceVolume by remember { mutableStateOf(0f) }
    var deviceVolumeMax by remember { mutableStateOf(1f) }
    // liked derivado de DB vía PlayerConnection si disponible
    val activity = LocalContext.current as? CastComposeActivity
    val currentSong = activity?.playerConnection?.currentSong?.collectAsState(initial = null)?.value
    val liked = currentSong?.song?.liked == true

    val context = LocalContext.current
    // Obtener CastContext una sola vez (evita llamada repetitiva costosa)
    val rememberedCastContext = remember {
        try {
            CastContext.getSharedInstance(context)
        } catch (_: Exception) {
            null
        }
    }
    LaunchedEffect(controller, rememberedCastContext) {
        if (controller == null) return@LaunchedEffect
        var nameLast = deviceName
        while (true) {
            // Estado primario de reproducción
            isPlaying = controller.isPlaying
            position = controller.currentPosition
            duration = controller.duration.coerceAtLeast(0L)
            controller.currentMediaItem?.mediaMetadata?.let { md ->
                title = (md.title ?: "").toString()
                artist = (md.artist ?: md.subtitle ?: "").toString()
                artwork = md.artworkUri?.toString()
            }
            repeatMode = controller.repeatMode
            shuffleOn = controller.shuffleModeEnabled
            // Nombre de dispositivo (solo si cambia)
            rememberedCastContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName?.let { fn ->
                if (fn != nameLast) {
                    deviceName = fn
                    nameLast = fn
                }
            }
            // Volumen
            runCatching { controller.getDeviceVolume() }.getOrNull()
                ?.let { v -> if (v >= 0 && v.toFloat() != deviceVolume) deviceVolume = v.toFloat() }
            controller.deviceInfo.let { info ->
                if (info.maxVolume > 0 && info.maxVolume.toFloat() != deviceVolumeMax) deviceVolumeMax =
                    info.maxVolume.toFloat().coerceAtLeast(1f)
            }
            delay(750) // ritmo menor para reducir carga
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            var showDisconnectDialog by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(id = android.R.string.cancel)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    showDisconnectDialog = true
                }) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = stringResource(R.string.disconnect)
                    )
                }
            }
            if (showDisconnectDialog) {
                AlertDialog(
                    onDismissRequest = { showDisconnectDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDisconnectDialog = false
                            controller?.pause()
                            controller?.release()
                            val act = activity
                            runCatching {
                                act?.let {
                                    CastContext.getSharedInstance(it).sessionManager.endCurrentSession(
                                        true
                                    )
                                }
                            }
                            // Forzar liberar la ruta seleccionada para reactivar discovery visible
                            runCatching {
                                act?.let { ctx ->
                                    val mr = androidx.mediarouter.media.MediaRouter.getInstance(ctx)
                                    if (mr.selectedRoute != mr.defaultRoute) {
                                        // UNSELECT_REASON_STOPPED = 1 (mantener si la constante cambia)
                                        runCatching { mr.unselect(androidx.mediarouter.media.MediaRouter.UNSELECT_REASON_STOPPED) }
                                    }
                                }
                            }
                            act?.playerConnection?.service?.returnToLocalPlayback()
                            onClose()
                        }) { Text(stringResource(R.string.disconnect)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDisconnectDialog = false
                        }) { Text(stringResource(R.string.cancel)) }
                    },
                    title = { Text(stringResource(R.string.disconnect_cast_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.disconnect_cast_message,
                                deviceName.ifBlank { "Cast" })
                        )
                    }
                )
            }
            if (deviceName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.casting_to, deviceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            // Control de volumen del dispositivo
            if (deviceVolumeMax > 1f) {
                var localVolume by remember(deviceVolume) { mutableFloatStateOf(deviceVolume) }
                Column(Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.volume),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "${(localVolume / deviceVolumeMax * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Slider(
                        value = localVolume,
                        onValueChange = {
                            localVolume = it; controller?.setDeviceVolume(it.toInt())
                        },
                        valueRange = 0f..deviceVolumeMax,
                        steps = (deviceVolumeMax.toInt() - 1).coerceAtLeast(0)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            alpha = 0.4f
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            if (duration > 0) {
                var dragPosition by remember { mutableStateOf<Long?>(null) }
                val displayPos = dragPosition ?: position
                Slider(
                    value = displayPos.coerceAtLeast(0L).toFloat(),
                    onValueChange = { dragPosition = it.toLong() },
                    onValueChangeFinished = {
                        val seekTo = dragPosition ?: return@Slider
                        controller?.seekTo(seekTo)
                        dragPosition = null
                    },
                    valueRange = 0f..duration.toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(displayPos), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    enabled = controller?.hasPreviousMediaItem() == true,
                    onClick = { controller?.seekToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }
                FilledIconButton(onClick = { if (isPlaying) controller?.pause() else controller?.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }
                IconButton(
                    enabled = controller?.hasNextMediaItem() == true,
                    onClick = { controller?.seekToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
            // Acciones secundarias reubicadas debajo de los controles principales
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    controller?.sendCustomCommand(
                        androidx.media3.session.SessionCommand(
                            com.anitail.music.constants.MediaSessionConstants.ACTION_TOGGLE_LIKE,
							Bundle()
                        ), Bundle()
                    )
                }) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = {
                    controller?.sendCustomCommand(
                        androidx.media3.session.SessionCommand(
                            com.anitail.music.constants.MediaSessionConstants.ACTION_TOGGLE_SHUFFLE,
							Bundle()
                        ), Bundle()
                    )
                    shuffleOn = !shuffleOn
                }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = {
                    controller?.sendCustomCommand(
                        androidx.media3.session.SessionCommand(
                            com.anitail.music.constants.MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE,
							Bundle()
                        ), Bundle()
                    )
                }) {
                    val repeatIcon = when (repeatMode) {
                        1 -> Icons.Default.RepeatOne; 2 -> Icons.Default.Repeat; else -> Icons.Default.Repeat
                    }
                    val tint =
                        if (repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Icon(repeatIcon, contentDescription = null, tint = tint)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(
        minutes,
        seconds
    )
}

