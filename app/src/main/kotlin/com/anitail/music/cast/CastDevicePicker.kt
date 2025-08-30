package com.anitail.music.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.anitail.music.R
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay

private data class CastRouteUi(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val isConnecting: Boolean
)

@Composable
fun CastDevicePickerDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val castContext =
        remember { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
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

    var routes by remember { mutableStateOf<List<CastRouteUi>>(emptyList()) }
    var discovering by remember { mutableStateOf(true) }
    var connectingRouteId by remember { mutableStateOf<String?>(null) }

    val callback = remember {
        object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refresh(router)

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refresh(router)

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refresh(router)

            private fun refresh(router: MediaRouter) {
                val selected = router.selectedRoute
                routes = router.routes
                    .filter { it.isEnabled && !it.isDefault }
                    .filter {
                        it.supportsControlCategory(
                            CastMediaControlIntent.categoryForCast(
                                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                            )
                        )
                    }
                    .map {
                        CastRouteUi(
                            id = it.id,
                            name = it.name,
                            isSelected = it == selected && selected != router.defaultRoute,
                            isConnecting = connectingRouteId == it.id
                        )
                    }
                discovering = false
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(500) // Incrementar delay para reducir carga inicial
        discovering = false
    }

    DisposableEffect(mediaRouter, selector) {
        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        onDispose { mediaRouter.removeCallback(callback) }
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
            if (discovering && routes.isEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (routes.isEmpty()) {
                    Text(
                        stringResource(id = R.string.cast_no_devices),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(routes, key = { it.id }) { route ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (route.isSelected) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.15f
                                        ) else Color.Transparent
                                    )
                                    .clickable(enabled = !route.isConnecting) {
                                        val found =
                                            mediaRouter.routes.firstOrNull { r -> r.id == route.id }
                                                ?: return@clickable
                                        if (route.isSelected) {
                                            castContext?.sessionManager?.endCurrentSession(true)
                                        } else {
                                            connectingRouteId = route.id
                                            found.select()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        route.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (route.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (route.isSelected) {
                                        Text(
                                            stringResource(id = R.string.cast_connected),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (route.isConnecting) {
                                        Text(
                                            stringResource(id = R.string.cast_connecting),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
