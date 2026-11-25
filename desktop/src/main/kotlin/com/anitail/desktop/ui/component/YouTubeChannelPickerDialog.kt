package com.anitail.desktop.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.innertube.models.YouTubeChannelAccount
import kotlinx.coroutines.launch

@Composable
fun YouTubeChannelPickerDialog(
    visible: Boolean,
    authService: DesktopAuthService,
    onDismiss: () -> Unit,
    onChannelSwitched: () -> Unit,
    onSignInAgain: () -> Unit,
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSwitching by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<YouTubeChannelAccount>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible) {
        isLoading = true
        errorText = null
        authService.fetchChannels()
            .onSuccess { list ->
                channels = list
                selectedIndex = list.indexOfFirst { it.isSelected }.coerceAtLeast(0)
            }
            .onFailure { err ->
                errorText = err.message
            }
        isLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isSwitching) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .width(560.dp)
                .heightIn(min = 340.dp, max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Elige un canal de YouTube",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { if (!isSwitching) onDismiss() },
                    ) {
                        Icon(
                            imageVector = IconAssets.close(),
                            contentDescription = stringResource("close"),
                        )
                    }
                }

                Text(
                    text = "Esta cuenta de Google tiene varios canales. Elige el que deseas usar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 380.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator()
                        }
                        errorText != null -> {
                            Text(
                                text = errorText.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        channels.isEmpty() -> {
                            Text(
                                text = stringResource("no_account_logged_in"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(channels) { index, channel ->
                                    val selected = index == selectedIndex
                                    val containerColor = if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                    val contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    val secondaryColor = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = !isSwitching) { selectedIndex = index },
                                        shape = RoundedCornerShape(16.dp),
                                        color = containerColor,
                                        contentColor = contentColor,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ExpressiveFramedAvatar(
                                                url = channel.thumbnailUrl,
                                                contentDescription = channel.name,
                                                shapeKey = channel.dataSyncId ?: channel.name,
                                                ringWidth = 2.dp,
                                                modifier = Modifier.size(48.dp),
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = channel.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (!channel.handle.isNullOrBlank()) {
                                                    Text(
                                                        text = channel.handle.orEmpty(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = secondaryColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (!channel.byline.isNullOrBlank() && channel.byline != channel.handle) {
                                                    Text(
                                                        text = channel.byline.orEmpty(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = secondaryColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            RadioButton(
                                                selected = selected,
                                                onClick = { if (!isSwitching) selectedIndex = index },
                                                enabled = !isSwitching,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSwitching,
                    ) {
                        Text(stringResource("cancel"))
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onSignInAgain()
                        },
                        enabled = !isSwitching,
                    ) {
                        Text("Sign in again")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        enabled = !isLoading && !isSwitching && channels.isNotEmpty(),
                        onClick = {
                            val chosen = channels.getOrNull(selectedIndex) ?: return@Button
                            scope.launch {
                                isSwitching = true
                                authService.switchChannel(chosen.dataSyncId)
                                isSwitching = false
                                onChannelSwitched()
                                onDismiss()
                            }
                        },
                    ) {
                        Text("Continuar")
                    }
                }
            }
        }
    }
}

