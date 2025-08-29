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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
            LaunchedEffect(controller) {
                while (isActive) {
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
                            // Prefetch del siguiente artwork si existe
                            val nextIndex = controller?.currentMediaItemIndex?.plus(1) ?: -1
                            val items = runCatching { controller?.mediaItems }.getOrNull()
                            val nextArt =
                                items?.getOrNull(nextIndex)?.mediaMetadata?.artworkUri?.toString()
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
        val startTime = System.currentTimeMillis()

        fun refreshMetadata(): Boolean = runCatching {
            controller.isPlaying.let { p -> if (p != isPlaying) isPlaying = p }
            controller.duration.coerceAtLeast(0L).let { d -> if (d != duration) duration = d }
            val currentItem = controller.currentMediaItem
            if (currentItem?.mediaId != null && currentItem.mediaId != lastMediaId) {
                lastMediaId = currentItem.mediaId
                currentItem.mediaMetadata.let { md ->
                    val newTitle = (md.title ?: "").toString()
                    val newArtist = (md.artist ?: md.subtitle ?: "").toString()
                    val newArtwork = md.artworkUri?.toString()
                    if (newTitle.isNotBlank() && newTitle != title) title = newTitle
                    if (newArtist != artist) artist = newArtist
                    if (newArtwork != artwork) artwork = newArtwork
                }
            }
            controller.repeatMode.let { if (it != repeatMode) repeatMode = it }
            controller.shuffleModeEnabled.let { if (it != shuffleOn) shuffleOn = it }
            rememberedCastContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName?.let { fn ->
                if (fn != nameLast && fn != deviceName) {
                    deviceName = fn; nameLast = fn
                }
            }
            runCatching { controller.getDeviceVolume() }.getOrNull()?.let { v ->
                if (v >= 0 && v.toFloat() != deviceVolume) deviceVolume = v.toFloat()
            }
            controller.deviceInfo.let { info ->
                val maxV = info.maxVolume.toFloat().coerceAtLeast(1f)
                if (maxV != deviceVolumeMax) deviceVolumeMax = maxV
            }
        }.isSuccess

        // Refresco inmediato antes del primer delay
        refreshMetadata()

        while (isActive) {
            val success = refreshMetadata()
            if (!success) {
                consecutiveErrors++
                if (consecutiveErrors > 5) break
            } else if (consecutiveErrors > 0) consecutiveErrors = 0

            val elapsed = System.currentTimeMillis() - startTime
            val needFast = (title.isBlank() || duration == 0L || artwork == null)
            val ultraFast = elapsed < 1600 && needFast
            val fast = !ultraFast && elapsed < 3500 && needFast
            val medium = !ultraFast && !fast && elapsed < 7000 && needFast
            val interval = when {
                ultraFast -> 120L
                fast -> 240L
                medium -> 500L
                else -> 1000L
            }
            delay(interval)
        }
    }

    // Telemetría y actualización de progreso usando snapshotFlow para suspender cuando no está reproduciendo
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
                    val interval = if (nearEnd) 150L else 250L
                    // Actualizamos posición y refrescamos isPlaying rápido (optimista)
                    runCatching { controller.currentPosition.let { position = it } }
                    runCatching { controller.isPlaying }.getOrNull()
                        ?.let { p -> if (p != isPlaying) isPlaying = p }
                    if (BuildConfig.DEBUG && now / 5000 != (now - diff) / 5000) {
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
        // Fondo con blur usando el artwork (imitando nuevo player)
        Box(modifier = Modifier.fillMaxSize()) {
            if (artwork != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artwork)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp),
                    contentScale = ContentScale.Crop
                )
                // Overlay oscura para contraste
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
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
            var queueRevision by remember { mutableIntStateOf(0) }
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
                                delay(160) // debounce
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
                            .crossfade(true)
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
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(queueShape)
                                .border(1.dp, clusterBorder, queueShape)
                                .clickable { showQueueSheet = true },
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
                    // Snapshot estable de la cola: iteramos usando mediaItemCount para evitar inconsistencias durante scroll
                    val items: List<MediaItem> =
                        remember(queueRevision, c?.mediaItemCount, c?.currentMediaItemIndex) {
                            val count = c?.mediaItemCount ?: 0
                            buildList(count) {
                                for (i in 0 until count) {
                                    val mi = runCatching { c?.getMediaItemAt(i) }.getOrNull()
                                    if (mi != null) add(mi)
                                }
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
                        if (items.isEmpty()) {
                            Text(
                                stringResource(R.string.queue_empty),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                            ) {
                                itemsIndexed(
                                    items,
                                    key = { index, item ->
                                        (item.mediaId ?: "idx_$index")
                                    }) { idx, it ->
                                    val currentMediaId = c?.currentMediaItem?.mediaId
                                    val isCurrent =
                                        currentMediaId != null && it.mediaId == currentMediaId
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                                val targetId = it.mediaId
                                                if (targetId != null && targetId != currentMediaId) {
                                                    val removed = runCatching {
                                                        controller?.let { ctrl ->
                                                            val count = ctrl.mediaItemCount
                                                            if (count <= 0) return@let
                                                            var found = -1
                                                            for (i in 0 until count) {
                                                                val mid = runCatching {
                                                                    ctrl.getMediaItemAt(i).mediaId
                                                                }.getOrNull()
                                                                if (mid == targetId) {
                                                                    found = i; break
                                                                }
                                                            }
                                                            if (found >= 0 && found != ctrl.currentMediaItemIndex) {
                                                                ctrl.removeMediaItem(found)
                                                            }
                                                        }
                                                    }.isSuccess
                                                    if (removed) {
                                                        queueRevision++
                                                        if ((controller?.mediaItemCount
                                                                ?: 0) == 0
                                                        ) showQueueSheet = false
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
                                                    val targetId = it.mediaId
                                                    if (targetId != null) {
                                                        val seekIndex = run {
                                                            val ctrl = controller
                                                            if (ctrl != null) {
                                                                val count = ctrl.mediaItemCount
                                                                var found = -1
                                                                for (i in 0 until count) {
                                                                    val mid = runCatching {
                                                                        ctrl.getMediaItemAt(i).mediaId
                                                                    }.getOrNull()
                                                                    if (mid == targetId) {
                                                                        found = i; break
                                                                    }
                                                                }
                                                                if (found >= 0) found else idx
                                                            } else idx
                                                        }
                                                        runCatching {
                                                            controller?.seekToDefaultPosition(
                                                                seekIndex
                                                            ); controller?.play()
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
                                            val thumb = it.mediaMetadata.artworkUri
                                            if (thumb != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(thumb)
                                                        .crossfade(true)
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
                                                    text = (it.mediaMetadata.title
                                                        ?: "").toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                val artistLine = (it.mediaMetadata.artist
                                                    ?: it.mediaMetadata.subtitle ?: "").toString()
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

