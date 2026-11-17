package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.util.JavaFxManager
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val DISCORD_LOGIN_URL = "https://discord.com/login"

private const val EXTRACT_DISCORD_TOKEN_JS = """
    (function() {
        try {
            var iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            document.body.appendChild(iframe);
            var token = iframe.contentWindow.localStorage.getItem('token');
            iframe.remove();
            if (token && token !== 'null' && token !== 'undefined') {
                return token.replace(/^"|"$/g, '');
            }
        } catch (e) {}
        try {
            var direct = window.localStorage.getItem('token');
            if (direct && direct !== 'null' && direct !== 'undefined') {
                return direct.replace(/^"|"$/g, '');
            }
        } catch (e) {}
        return '';
    })();
"""

@Composable
internal fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val tokenCapturedOnce = remember { AtomicBoolean(false) }

    fun tryExtractToken(view: WebView) {
        if (tokenCapturedOnce.get()) return
        val location = view.engine.location.orEmpty()
        if (!location.contains("discord.com")) return
        val result = runCatching {
            view.engine.executeScript(EXTRACT_DISCORD_TOKEN_JS)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
        if (result.isNotBlank() && result != "null" && result != "undefined" && tokenCapturedOnce.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                onTokenCaptured(result)
            }
        }
    }

    LaunchedEffect(webViewRef) {
        val view = webViewRef ?: return@LaunchedEffect
        while (isActive && !tokenCapturedOnce.get()) {
            delay(750L)
            Platform.runLater {
                val loc = view.engine.location.orEmpty()
                if (loc.contains("/channels/") || loc.contains("/app")) {
                    tryExtractToken(view)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val view = webViewRef
            if (view != null) {
                Platform.runLater {
                    val _ = runCatching { view.engine.load(null) }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .height(680.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IconAssets.discord(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource("discord_integration"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val view = webViewRef
                                if (view != null) {
                                    Platform.runLater { tryExtractToken(view) }
                                }
                            },
                        ) {
                            Text(stringResource("refresh"))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = IconAssets.close(),
                                contentDescription = stringResource("close"),
                            )
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SwingPanel(
                        factory = {
                            JavaFxManager.ensureInitialized()
                            JFXPanel().also { panel ->
                                Platform.runLater {
                                    val webView = WebView()
                                    webViewRef = webView
                                    webView.engine.isJavaScriptEnabled = true
                                    webView.engine.userAgent =
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

                                    webView.engine.loadWorker.stateProperty().addListener { _, _, newState ->
                                        SwingUtilities.invokeLater {
                                            isLoading = newState == Worker.State.RUNNING || newState == Worker.State.SCHEDULED
                                        }
                                        if (newState == Worker.State.SUCCEEDED) {
                                            val loc = webView.engine.location.orEmpty()
                                            if (loc.contains("/channels/") || loc.contains("/app")) {
                                                tryExtractToken(webView)
                                            }
                                        }
                                    }

                                    webView.engine.locationProperty().addListener { _, _, newLocation ->
                                        if (!newLocation.isNullOrBlank() &&
                                            (newLocation.contains("/channels/") || newLocation.contains("/app"))
                                        ) {
                                            tryExtractToken(webView)
                                        }
                                    }

                                    webView.engine.load(DISCORD_LOGIN_URL)
                                    panel.scene = Scene(webView)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

