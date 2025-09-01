package com.anitail.music.cast

import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anitail.music.BuildConfig
import com.anitail.music.R
import com.anitail.music.constants.DarkModeKey
import com.anitail.music.constants.MediaSessionConstants
import com.anitail.music.constants.PureBlackKey
import com.anitail.music.extensions.mediaItems
import com.anitail.music.ui.screens.settings.DarkMode
import com.anitail.music.ui.theme.AnitailTheme
import com.anitail.music.ui.theme.DefaultThemeColor
import com.anitail.music.ui.theme.extractThemeColor
import com.anitail.music.utils.dataStore
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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

        // Verificar si Cast está disponible antes de continuar
        if (!com.anitail.music.utils.GooglePlayServicesUtils.isCastAvailable(this)) {
            finish()
            return
        }
        
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
            // Cache de último artwork procesado para evitar reprocesar la misma imagen
            var lastArtworkProcessed by remember { mutableStateOf<String?>(null) }
            var lastPrefetchedNext by remember { mutableStateOf<String?>(null) }
            val imageLoader = remember { ImageLoader(ctx) }

            // Optimizado: Solo procesar artwork cuando cambie el media item
            LaunchedEffect(controller?.currentMediaItem?.mediaMetadata?.artworkUri) {
                val art = controller?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
                if (art != null && art != lastArtworkProcessed) {
                    runCatching {
                        val drawable = withContext(Dispatchers.IO) {
                            imageLoader.execute(
                                ImageRequest.Builder(ctx)
                                    .data(art)
                                    .allowHardware(false)
                                    .build()
                            ).drawable
                        }
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null) {
                            themeColor = bmp.extractThemeColor()
                            lastArtworkProcessed = art
                        }
                    }
                }
            }

            // Prefetch separado para evitar retraso en UI
            LaunchedEffect(controller?.currentMediaItemIndex) {
                val nextIndex = controller?.currentMediaItemIndex?.plus(1) ?: -1
                val items = runCatching { controller?.mediaItems }.getOrNull()
                val nextArt = items?.getOrNull(nextIndex)?.mediaMetadata?.artworkUri?.toString()
                if (!nextArt.isNullOrBlank() && nextArt != lastPrefetchedNext) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            imageLoader.execute(
                                ImageRequest.Builder(ctx)
                                    .data(nextArt)
                                    .allowHardware(false)
                                    .build()
                            )
                        }
                    }
                    lastPrefetchedNext = nextArt
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
@OptIn(ExperimentalMaterial3Api::class)
private fun CastExpandedContent(controllerProvider: () -> MediaController?, onClose: () -> Unit) {
    val controller = controllerProvider()
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf<String?>(null) }
    var repeatMode by remember { mutableIntStateOf(0) }
    var shuffleOn by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("") }
    var deviceVolume by remember { mutableFloatStateOf(0f) }
    var deviceVolumeMax by remember { mutableFloatStateOf(1f) }
    // liked derivado de DB vía PlayerConnection si disponible
    val activity = LocalContext.current as? CastComposeActivity
    val currentSong = activity?.playerConnection?.currentSong?.collectAsState(initial = null)?.value
    val liked = currentSong?.song?.liked == true

    // Listener en vivo para reflejar cambios sin interacción manual (evita depender solo de polling)
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                if (isPlaying != isPlayingChanged) isPlaying = isPlayingChanged
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Duración puede cambiar cuando se prepara
                runCatching { controller.duration.coerceAtLeast(0L) }.getOrNull()
                    ?.let { d -> if (d != duration) duration = d }
                // Posición puntual (si estaba parada y arranca)
                runCatching { controller.currentPosition }.getOrNull()?.let { p -> position = p }
                if (BuildConfig.DEBUG) {
                    Timber.d("CastQueue: PlaybackState changed to $playbackState")
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // Actualiza duración y metadata tan pronto la timeline esté disponible
                runCatching { controller.duration.coerceAtLeast(0L) }.getOrNull()
                    ?.let { d -> if (d != duration) duration = d }
                controller.currentMediaItem?.mediaMetadata?.let { md ->
                    val newTitle = (md.title ?: "").toString()
                    if (newTitle.isNotBlank() && newTitle != title) title = newTitle
                    val newArtist = (md.artist ?: md.subtitle ?: "").toString()
                    if (newArtist.isNotBlank() && newArtist != artist) artist = newArtist
                    md.artworkUri?.toString()?.let { art -> if (art != artwork) artwork = art }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let { md ->
                    val newTitle = (md.title ?: "").toString()
                    if (newTitle != title) title = newTitle
                    val newArtist = (md.artist ?: md.subtitle ?: "").toString()
                    if (newArtist != artist) artist = newArtist
                    val newArt = md.artworkUri?.toString()
                    if (newArt != artwork) artwork = newArt
                }
                runCatching { controller.duration.coerceAtLeast(0L) }.getOrNull()
                    ?.let { d -> duration = d }
            }

            override fun onRepeatModeChanged(repeatModeChanged: Int) {
                if (repeatMode != repeatModeChanged) repeatMode = repeatModeChanged
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleOn != shuffleModeEnabled) shuffleOn = shuffleModeEnabled
            }
        }
        controller.addListener(listener)
        onDispose { runCatching { controller.removeListener(listener) } }
    }

    val context = LocalContext.current
    // Obtener CastContext una sola vez (evita llamada repetitiva costosa)
    val rememberedCastContext = remember {
        try {
            CastContext.getSharedInstance(context)
        } catch (_: Exception) {
            null
        }
    }
    // Loop de metadatos adaptativo con fast-start agresivo para reducir latencia inicial
    LaunchedEffect(controller, rememberedCastContext) {
        if (controller == null) return@LaunchedEffect
        var nameLast = deviceName
        var lastMediaId: String? = null
        var consecutiveErrors = 0
        System.currentTimeMillis()

        fun refreshMetadata(): Boolean = runCatching {
            // Verificar estado del controller antes de cualquier acceso
            if (controller.playbackState == Player.STATE_IDLE) {
                return@runCatching false
            }

            try {
                controller.isPlaying.let { p -> if (p != isPlaying) isPlaying = p }
            } catch (e: Exception) {
                return@runCatching false
            }

            try {
                controller.duration.coerceAtLeast(0L).let { d -> if (d != duration) duration = d }
            } catch (e: Exception) {
                // Duración puede no estar disponible, no es crítico
            }

            val currentItem = try {
                controller.currentMediaItem
            } catch (e: Exception) {
                null
            }
            
            if (currentItem?.mediaId != null && currentItem.mediaId != lastMediaId) {
                lastMediaId = currentItem.mediaId
                currentItem.mediaMetadata?.let { md ->
                    val newTitle = (md.title ?: "").toString()
                    val newArtist = (md.artist ?: md.subtitle ?: "").toString()
                    val newArtwork = md.artworkUri?.toString()
                    if (newTitle.isNotBlank() && newTitle != title) title = newTitle
                    if (newArtist != artist) artist = newArtist
                    if (newArtwork != artwork) artwork = newArtwork
                }
            }

            try {
                controller.repeatMode.let { if (it != repeatMode) repeatMode = it }
            } catch (e: Exception) {
                // No crítico
            }

            try {
                controller.shuffleModeEnabled.let { if (it != shuffleOn) shuffleOn = it }
            } catch (e: Exception) {
                // No crítico
            }
            
            rememberedCastContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName?.let { fn ->
                if (fn != nameLast && fn != deviceName) {
                    deviceName = fn; nameLast = fn
                }
            }

            runCatching { controller.getDeviceVolume() }.getOrNull()?.let { v ->
                if (v >= 0 && v.toFloat() != deviceVolume) deviceVolume = v.toFloat()
            }

            try {
                controller.deviceInfo.let { info ->
                    val maxV = info.maxVolume.toFloat().coerceAtLeast(1f)
                    if (maxV != deviceVolumeMax) deviceVolumeMax = maxV
                }
            } catch (e: Exception) {
                // No crítico
            }
        }.isSuccess

        // Refresco inmediato
        refreshMetadata()

        while (isActive) {
            // Usar intervalos más largos para reducir sobrecarga y evitar frame drops
            val baseInterval = if (isPlaying) 2000L else 5000L // Intervalos más largos
            val interval = if (consecutiveErrors > 0) {
                (baseInterval * (1 + consecutiveErrors)).coerceAtMost(15000L)
            } else baseInterval

            if (refreshMetadata()) {
                consecutiveErrors = 0
            } else {
                consecutiveErrors = (consecutiveErrors + 1).coerceAtMost(3) // Limitar errores
            }
            delay(interval)
        }
    }

    // Telemetría y actualización de progreso optimizada
    var avgProgressInterval by remember { mutableLongStateOf(0L) }
    var lastProgressTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controller) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow { isPlaying }
            .collectLatest { playing ->
                if (!playing) return@collectLatest
                while (isActive && isPlaying) {
                    val now = System.currentTimeMillis()
                    val diff = now - lastProgressTick
                    if (diff > 0) {
                        avgProgressInterval =
                            if (avgProgressInterval == 0L) diff else (avgProgressInterval * 3 + diff) / 4
                        lastProgressTick = now
                    }
                    val nearEnd = duration > 0 && (duration - position) < 1500
                    // Intervalos aún más largos para reducir frame drops
                    val interval = if (nearEnd) 500L else 1000L // Intervalos más largos
                    // Actualizamos posición con menos frecuencia y protegido
                    runCatching {
                        if (controller.playbackState != Player.STATE_IDLE) {
                            controller.currentPosition.let { position = it }
                        }
                    }
                    runCatching {
                        if (controller.playbackState != Player.STATE_IDLE) {
                            controller.isPlaying
                        } else null
                    }.getOrNull()?.let { p -> if (p != isPlaying) isPlaying = p }
                    if (BuildConfig.DEBUG && now / 30000 != (now - diff) / 30000) {
                        // Reducir frecuencia de logs significativamente para evitar overhead
                        try {
                            Timber.d("CastProgress avgInterval=${avgProgressInterval}ms")
                        } catch (_: Exception) {
                            Timber.tag("CastProgress").d("avgInterval=${avgProgressInterval}ms")
                        }
                    }
                    delay(interval)
                }
            }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        // Fondo simplificado sin blur costoso
        Box(modifier = Modifier.fillMaxSize()) {
            if (artwork != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artwork)
                        .crossfade(false) // Remover crossfade para mejor rendimiento
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.2f), // Escala ligera en lugar de blur costoso
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f // Transparencia en lugar de blur
                )
                // Overlay más fuerte para compensar la falta de blur
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            var showDisconnectDialog by remember { mutableStateOf(false) }
            // Estado para mostrar la hoja de la cola (movido al fondo)
            var showQueueSheet by remember { mutableStateOf(false) }
            // Cabecera centrada similar al nuevo player
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val headerBtnSize = 42.dp
                val headerShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                val headerBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                Box(
                    modifier = Modifier
                        .size(headerBtnSize)
                        .clip(headerShape)
                        .background(headerBg)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(id = android.R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(3f)
                ) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                    )
                    Text(
                        text = title.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(headerBtnSize)
                        .clip(headerShape)
                        .background(headerBg)
                        .clickable { showDisconnectDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = stringResource(R.string.disconnect),
                        tint = MaterialTheme.colorScheme.onSurface
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
            Spacer(Modifier.height(8.dp))
            // Control de volumen del dispositivo
            if (deviceVolumeMax > 1f) {
                var localVolume by remember(deviceVolume) { mutableFloatStateOf(deviceVolume) }
                var volumeJob by remember { mutableStateOf<Job?>(null) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.volume),
                            style = MaterialTheme.typography.labelSmall
                        )
                        val percent = remember(
                            localVolume,
                            deviceVolumeMax
                        ) { (localVolume / deviceVolumeMax * 100).toInt() }
                        Text("${percent}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = localVolume,
                        onValueChange = { v ->
                            localVolume = v
                            volumeJob?.cancel()
                            volumeJob = activity?.lifecycleScope?.launch {
                                delay(300) // Incrementar debounce para reducir frecuencia
                                controller?.setDeviceVolume(v.toInt())
                            }
                        },
                        valueRange = 0f..deviceVolumeMax,
                        steps = (deviceVolumeMax.toInt() - 1).coerceAtLeast(0)
                    )
                }
            }
            // Arte principal (centrado, tamaño controlado) sobre el fondo difuminado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val artModifier = Modifier
                    .fillMaxWidth(0.80f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                if (artwork != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artwork)
                            .crossfade(false) // Remover crossfade para mejor rendimiento
                            .build(),
                        contentDescription = null,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = artModifier.background(
                            MaterialTheme.colorScheme.surfaceVariant
                        ), contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            alpha = 0.35f
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            // Info de título / artista bajo el artwork (similar al nuevo player)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            if (duration > 0) {
                ProgressSection(
                    mediaId = remember { controller?.currentMediaItem?.mediaId },
                    position = position,
                    duration = duration,
                    onSeek = { seekTo -> controller?.seekTo(seekTo) }
                )
                Spacer(Modifier.height(16.dp))
                // Fila de controles principal (repeat, previous, play, next, like)
                // Controles estilo "nuevo player": botón central grande y botones laterales + cluster secundario
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de Queue (cluster izquierda)
                        val queueShape = remember {
                            androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 50.dp,
                                bottomStart = 50.dp,
                                topEnd = 10.dp,
                                bottomEnd = 10.dp
                            )
                        }
                        val clusterBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        val queueItemCount = remember(controller) {
                            if (controller == null) {
                                0
                            } else {
                                runCatching {
                                    controller.mediaItemCount
                                }.getOrElse { 0 }.also { count ->
                                    if (BuildConfig.DEBUG) {
                                        Timber.d("CastQueue: Botón queue - count=$count")
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(queueShape)
                                .border(1.dp, clusterBorder, queueShape)
                                .clickable(enabled = controller != null && queueItemCount > 0) {
                                    if (controller != null) {
                                        showQueueSheet = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = stringResource(R.string.manage_queue),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        // Previous
                        FilledTonalIconButton(
                            enabled = controller?.hasPreviousMediaItem() == true,
                            onClick = { controller?.seekToPrevious() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // Play grande central
                        FilledIconButton(
                            onClick = {
                                if (isPlaying) controller?.pause() else controller?.play()
                                // Toggle optimista inmediato
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier.size(96.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // Next
                        FilledTonalIconButton(
                            enabled = controller?.hasNextMediaItem() == true,
                            onClick = { controller?.seekToNext() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        // Repeat / Like cluster derecha apilado
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val sideShape =
                                remember { androidx.compose.foundation.shape.RoundedCornerShape(14.dp) }
                            val borderCol = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            // Repeat
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(sideShape)
                                    .border(1.dp, borderCol, sideShape)
                                    .clickable {
                                        controller?.sendCustomCommand(
                                            androidx.media3.session.SessionCommand(
                                                MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE,
                                                Bundle()
                                            ), Bundle()
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val repeatIcon = when (repeatMode) {
                                    1 -> Icons.Default.RepeatOne
                                    2 -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                }
                                Icon(
                                    repeatIcon,
                                    contentDescription = null,
                                    tint = if (repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            // Like
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(sideShape)
                                    .border(1.dp, borderCol, sideShape)
                                    .clickable {
                                        controller?.sendCustomCommand(
                                            androidx.media3.session.SessionCommand(
                                                MediaSessionConstants.ACTION_TOGGLE_LIKE,
                                                Bundle()
                                            ), Bundle()
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                // Hoja modal de la cola (se muestra cuando se pulsa el nuevo botón inferior)
                if (showQueueSheet) {
                    val c = controller
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    // Estado reactivo para items de la cola que se actualiza progresivamente
                    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    var isLoadingQueue by remember { mutableStateOf(false) }
                    var queueVersion by remember { mutableStateOf(0) }
                    var loadingProgress by remember { mutableStateOf("") }

                    // Listener para observar cambios en tiempo real en la cola
                    DisposableEffect(c) {
                        val listener = if (c != null) {
                            object : Player.Listener {
                                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                                    if (showQueueSheet) {
                                        // Incrementar versión para forzar recarga
                                        queueVersion += 1
                                        if (BuildConfig.DEBUG) {
                                            Timber.d("CastQueue: Timeline cambió, nueva versión: $queueVersion, razón: $reason")
                                        }
                                    }
                                }

                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int
                                ) {
                                    if (showQueueSheet && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                                        queueVersion += 1
                                        if (BuildConfig.DEBUG) {
                                            Timber.d("CastQueue: Transición de item por cambio de playlist, nueva versión: $queueVersion")
                                        }
                                    }
                                }
                            }.also { c.addListener(it) }
                        } else {
                            null
                        }

                        onDispose {
                            listener?.let { c?.removeListener(it) }
                        }
                    }

                    // Observar cambios en la cola y cargar items progresivamente
                    LaunchedEffect(showQueueSheet, c, queueVersion) {
                        if (BuildConfig.DEBUG) {
                            Timber.d("CastQueue: LaunchedEffect iniciado - showQueueSheet: $showQueueSheet, controller: ${c != null}, queueVersion: $queueVersion")
                        }

                        if (!showQueueSheet || c == null) {
                            if (BuildConfig.DEBUG) {
                                Timber.d("CastQueue: Saliendo - showQueueSheet: $showQueueSheet, controller: ${c != null}")
                            }
                            items = emptyList()
                            loadingProgress = ""
                            return@LaunchedEffect
                        }

                        if (BuildConfig.DEBUG) {
                            Timber.d("CastQueue: Iniciando carga de cola...")
                        }

                        isLoadingQueue = true
                        items = emptyList() // Limpiar lista actual
                        loadingProgress = "Iniciando..."

                        try {
                            if (BuildConfig.DEBUG) {
                                Timber.d("CastQueue: Intentando obtener cola desde Cast MediaController...")
                                // Diagnosticar el estado del MediaController
                                Timber.d("CastQueue: MediaController estado - mediaItemCount: ${c.mediaItemCount}, currentIndex: ${c.currentMediaItemIndex}")
                            }

                            // Primero intentar obtener la cola directamente del Cast MediaController
                            val allMediaItems = runCatching { c.mediaItems }.getOrNull()
                            if (BuildConfig.DEBUG) {
                                Timber.d("CastQueue: mediaItems obtenidos - count: ${allMediaItems?.size ?: 0}")
                                allMediaItems?.forEachIndexed { index, item ->
                                    Timber.d("CastQueue: Item [$index]: ${item?.mediaMetadata?.title} (id: ${item?.mediaId})")
                                }
                            }

                            if (allMediaItems != null && allMediaItems.isNotEmpty()) {
                                val filteredItems = allMediaItems.filter { item ->
                                    item != null &&
                                            !item.mediaId.isNullOrBlank() &&
                                            item.mediaMetadata != null
                                }

                                if (BuildConfig.DEBUG) {
                                    Timber.d("CastQueue: Obtenidos ${filteredItems.size} items válidos desde Cast MediaController (total: ${allMediaItems.size})")
                                }

                                // Mostrar items progresivamente para mejor UX
                                val loadedItems = mutableListOf<MediaItem>()
                                filteredItems.forEachIndexed { index, mediaItem ->
                                    if (!isActive) return@forEachIndexed

                                    loadingProgress =
                                        "Cargando ${index + 1} de ${filteredItems.size}..."
                                    loadedItems.add(mediaItem)
                                    items = loadedItems.toList()

                                    if (BuildConfig.DEBUG) {
                                        Timber.d("CastQueue: Agregado item ${index + 1}/${filteredItems.size}: ${mediaItem.mediaMetadata?.title}")
                                    }

                                    // Pausa más corta ya que no hay acceso a red
                                    if (index < filteredItems.size - 1) {
                                        delay(30)
                                    }
                                }

                                loadingProgress =
                                    "Completado: ${filteredItems.size} canciones (cola Cast)"

                            } else {
                                if (BuildConfig.DEBUG) {
                                    Timber.d("CastQueue: Cast MediaController.mediaItems está vacío, intentando método alternativo...")
                                }


                                val mediaItemCount = c.mediaItemCount
                                if (BuildConfig.DEBUG) {
                                    Timber.d("CastQueue: MediaItemCount alternativo: $mediaItemCount")
                                }

                                if (mediaItemCount > 0) {
                                    val alternativeItems = mutableListOf<MediaItem>()
                                    for (i in 0 until mediaItemCount) {
                                        try {
                                            val item = c.getMediaItemAt(i)
                                            if (item != null && !item.mediaId.isNullOrBlank() && item.mediaMetadata != null) {
                                                alternativeItems.add(item)
                                                if (BuildConfig.DEBUG) {
                                                    Timber.d("CastQueue: Item alternativo [$i]: ${item.mediaMetadata?.title}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            if (BuildConfig.DEBUG) {
                                                Timber.w(
                                                    e,
                                                    "CastQueue: Error obteniendo item en posición $i"
                                                )
                                            }
                                        }
                                    }

                                    if (alternativeItems.isNotEmpty()) {
                                        if (BuildConfig.DEBUG) {
                                            Timber.d("CastQueue: Obtenidos ${alternativeItems.size} items con método alternativo")
                                        }

                                        // Mostrar items progresivamente
                                        val loadedItems = mutableListOf<MediaItem>()
                                        alternativeItems.forEachIndexed { index, mediaItem ->
                                            if (!isActive) return@forEachIndexed

                                            loadingProgress =
                                                "Cargando ${index + 1} de ${alternativeItems.size}..."
                                            loadedItems.add(mediaItem)
                                            items = loadedItems.toList()

                                            if (index < alternativeItems.size - 1) {
                                                delay(30)
                                            }
                                        }

                                        loadingProgress =
                                            "Completado: ${alternativeItems.size} canciones (método alternativo)"
                                    } else {
                                        // Último recurso: mostrar solo el item actual
                                        val currentMediaItem =
                                            runCatching { c.currentMediaItem }.getOrNull()
                                        if (currentMediaItem != null &&
                                            !currentMediaItem.mediaId.isNullOrBlank() &&
                                            currentMediaItem.mediaMetadata != null
                                        ) {

                                            loadingProgress = "Solo item actual disponible..."
                                            items = listOf(currentMediaItem)
                                            delay(500)
                                            loadingProgress =
                                                "Completado: 1 canción (solo actual Cast)"
                                        } else {
                                            loadingProgress = "No hay items disponibles en Cast"
                                        }
                                    }
                                } else {
                                    // Fallback final: mostrar solo el item actual del Cast
                                    val currentMediaItem =
                                        runCatching { c.currentMediaItem }.getOrNull()
                                    if (currentMediaItem != null &&
                                        !currentMediaItem.mediaId.isNullOrBlank() &&
                                        currentMediaItem.mediaMetadata != null
                                    ) {

                                        loadingProgress = "Solo item actual disponible..."
                                        items = listOf(currentMediaItem)
                                        delay(500)
                                        loadingProgress = "Completado: 1 canción (solo actual Cast)"
                                    } else {
                                        loadingProgress = "No hay items disponibles en Cast"
                                    }
                                }
                            }
                        } catch (exception: Exception) {
                            if (BuildConfig.DEBUG) {
                                Timber.e(exception, "CastQueue: Error en carga progresiva")
                            }
                            items = emptyList()
                            loadingProgress = "Error cargando cola"
                        } finally {
                            isLoadingQueue = false
                            // Limpiar mensaje de progreso después de un delay
                            delay(1000)
                            loadingProgress = ""
                        }
                    }
                    ModalBottomSheet(
                        onDismissRequest = { showQueueSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.manage_queue),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val headerShape =
                                remember { androidx.compose.foundation.shape.RoundedCornerShape(14.dp) }
                            val borderCol = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(headerShape)
                                    .border(1.dp, borderCol, headerShape)
                                    .clickable {
                                        controller?.sendCustomCommand(
                                            androidx.media3.session.SessionCommand(
                                                MediaSessionConstants.ACTION_TOGGLE_SHUFFLE,
                                                Bundle()
                                            ), Bundle()
                                        )
                                        shuffleOn = !shuffleOn
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()

                        // Mostrar indicador de carga si está cargando o hay mensaje de progreso
                        if (isLoadingQueue || loadingProgress.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isLoadingQueue) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (loadingProgress.isNotEmpty()) loadingProgress else "Cargando cola...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }

                        if (items.isEmpty() && !isLoadingQueue && loadingProgress.isEmpty()) {
                            Text(
                                stringResource(R.string.queue_empty),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (items.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                            ) {
                                itemsIndexed(
                                    items,
                                    key = { index, item ->
                                        // Asegurar que la key sea única incluso si mediaId es nulo o vacío
                                        val mediaId = item.mediaId
                                        if (mediaId.isNullOrBlank()) {
                                            "idx_$index"
                                        } else {
                                            // Combinar con el índice para garantizar unicidad total
                                            "${mediaId}_$index"
                                        }
                                    }) { idx, it ->
                                    // En este punto, todos los items ya están validados
                                    val mediaId =
                                        it.mediaId!! // Seguro que no es null por el filtro anterior
                                    val metadata = it.mediaMetadata!!
                                    
                                    val currentMediaId = c?.currentMediaItem?.mediaId
                                    val isCurrent =
                                        currentMediaId != null && mediaId == currentMediaId
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                                val targetId = mediaId
                                                if (targetId != currentMediaId && controller != null) {
                                                    val removed = runCatching {
                                                        val ctrl = controller
                                                        if (ctrl != null && ctrl.playbackState != Player.STATE_IDLE) {
                                                            val count = try {
                                                                ctrl.mediaItemCount
                                                            } catch (e: Exception) {
                                                                return@runCatching false
                                                            }
                                                            if (count <= 0) return@runCatching false
                                                            var found = -1
                                                            for (i in 0 until count) {
                                                                val mid = try {
                                                                    ctrl.getMediaItemAt(i).mediaId
                                                                } catch (e: Exception) {
                                                                    null
                                                                }
                                                                if (mid == targetId) {
                                                                    found = i; break
                                                                }
                                                            }
                                                            if (found >= 0 && found != ctrl.currentMediaItemIndex) {
                                                                try {
                                                                    ctrl.removeMediaItem(found)
                                                                    return@runCatching true
                                                                } catch (e: Exception) {
                                                                    return@runCatching false
                                                                }
                                                            }
                                                        }
                                                        return@runCatching false
                                                    }.getOrElse { false }
                                                    if (removed) {
                                                        // queueRevision eliminado para optimizar rendimiento
                                                        val remainingCount = try {
                                                            controller?.mediaItemCount ?: 0
                                                        } catch (e: Exception) {
                                                            0
                                                        }
                                                        if (remainingCount == 0) {
                                                            showQueueSheet = false
                                                        }
                                                    }
                                                }
                                                true
                                            } else false
                                        },
                                        positionalThreshold = { distance -> distance * 0.25f }
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {},
                                        enableDismissFromEndToStart = true,
                                        enableDismissFromStartToEnd = true
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val targetId = mediaId
                                                    if (BuildConfig.DEBUG) {
                                                        Timber.d("CastQueue: Click en item $idx, mediaId: $targetId")
                                                    }

                                                    if (controller != null) {
                                                        runCatching {
                                                            // Reproducir directamente en el Cast usando el MediaController
                                                            val targetId = mediaId

                                                            if (BuildConfig.DEBUG) {
                                                                Timber.d("CastQueue: Cambiando a canción en Cast - índice $idx: ${metadata.title}")
                                                            }

                                                            // Buscar el índice de la canción en la cola actual del Cast
                                                            var foundIndex = -1
                                                            val currentCount =
                                                                controller.mediaItemCount

                                                            for (i in 0 until currentCount) {
                                                                try {
                                                                    val castMediaId =
                                                                        controller.getMediaItemAt(i).mediaId
                                                                    if (castMediaId == targetId) {
                                                                        foundIndex = i
                                                                        break
                                                                    }
                                                                } catch (e: Exception) {
                                                                    if (BuildConfig.DEBUG) {
                                                                        Timber.w(
                                                                            e,
                                                                            "CastQueue: Error accediendo Cast mediaItem $i"
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            if (foundIndex >= 0) {
                                                                // La canción está en la cola del Cast, saltar a ella
                                                                if (BuildConfig.DEBUG) {
                                                                    Timber.d("CastQueue: Saltando a posición $foundIndex en Cast")
                                                                }
                                                                controller.seekToDefaultPosition(
                                                                    foundIndex
                                                                )
                                                                controller.play()
                                                            } else {
                                                                if (BuildConfig.DEBUG) {
                                                                    Timber.w("CastQueue: Canción no encontrada en cola del Cast")
                                                                }
                                                            }

                                                        }.onFailure { exception ->
                                                            if (BuildConfig.DEBUG) {
                                                                Timber.e(
                                                                    exception,
                                                                    "CastQueue: Error cambiando canción en Cast"
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        if (BuildConfig.DEBUG) {
                                                            Timber.w("CastQueue: Controller no disponible")
                                                        }
                                                    }
                                                    showQueueSheet = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = (idx + 1).toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            val thumb = metadata.artworkUri
                                            if (thumb != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(thumb)
                                                        .crossfade(false) // Remover crossfade para mejor rendimiento
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(MaterialTheme.shapes.small),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                ) {}
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = (metadata.title ?: "").toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                val artistLine =
                                                    (metadata.artist ?: metadata.subtitle
                                                    ?: "").toString()
                                                if (artistLine.isNotBlank()) {
                                                    Text(
                                                        artistLine,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            val isLiked = if (isCurrent) liked else false
                                            IconButton(onClick = {
                                                controller?.sendCustomCommand(
                                                    androidx.media3.session.SessionCommand(
                                                        MediaSessionConstants.ACTION_TOGGLE_LIKE,
                                                        Bundle()
                                                    ), Bundle()
                                                )
                                            }) {
                                                Icon(
                                                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (idx < items.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
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

@Composable
private fun ProgressSection(
    mediaId: String?,
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    var dragPosition by remember(mediaId) { mutableStateOf<Long?>(null) }
    val animPosition = remember(mediaId) { Animatable(position.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(position, dragPosition, mediaId) {
        if (dragPosition == null) {
            val target = position.toFloat()
            val diff = kotlin.math.abs(target - animPosition.value)
            if (diff > 2000) {
                animPosition.snapTo(target)
            } else if (diff > 50) {
                animPosition.animateTo(target, tween(300))
            } else {
                animPosition.snapTo(target)
            }
        }
    }
    val displayPos = dragPosition ?: animPosition.value.toLong()
    val formattedDisplay by remember(displayPos) { mutableStateOf(formatTime(displayPos)) }
    val formattedDuration by remember(duration) { mutableStateOf(formatTime(duration)) }
    Slider(
        value = displayPos.coerceAtMost(duration).toFloat(),
        onValueChange = { dragPosition = it.toLong() },
        onValueChangeFinished = {
            dragPosition?.let { seek ->
                scope.launch {
                    animPosition.snapTo(seek.toFloat())
                    onSeek(seek)
                }
            }
            dragPosition = null
        },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formattedDisplay, style = MaterialTheme.typography.labelSmall)
        Text(formattedDuration, style = MaterialTheme.typography.labelSmall)
    }
}

