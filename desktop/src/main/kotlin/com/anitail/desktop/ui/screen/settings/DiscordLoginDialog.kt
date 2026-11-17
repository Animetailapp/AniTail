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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anitail.desktop.auth.DesktopDiscordService
import com.anitail.desktop.auth.DiscordAuthResult
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.util.JavaFxManager
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

@Composable
internal fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: (DiscordAuthResult) -> Unit,
    onAuthError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val authSession = remember { DesktopDiscordService.createAuthSession() }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val handledCallback = remember { AtomicBoolean(false) }

    fun handleRedirectUrl(url: String) {
        if (!url.startsWith(DesktopDiscordService.DISCORD_REDIRECT_URI)) return
        if (!handledCallback.compareAndSet(false, true)) return

        val query = url.substringAfter("?", "")
        val params = parseQueryParams(query)
        val error = params["error"]
        val code = params["code"]
        val state = params["state"].orEmpty()

        if (!error.isNullOrBlank()) {
            SwingUtilities.invokeLater {
                onAuthError(params["error_description"] ?: error)
            }
            return
        }

        if (code.isNullOrBlank()) {
            SwingUtilities.invokeLater {
                onAuthError("Missing authorization code")
            }
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopDiscordService.exchangeCode(
                    code = code,
                    verifier = authSession.pkceVerifier,
                    state = state,
                    expectedState = authSession.state,
                )
            }
            result.onSuccess { authResult ->
                onAuthSuccess(authResult)
            }.onFailure { err ->
                onAuthError(err.message ?: "Discord OAuth error")
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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = IconAssets.close(),
                            contentDescription = stringResource("close"),
                        )
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
                                        val loc = webView.engine.location.orEmpty()
                                        if (loc.startsWith(DesktopDiscordService.DISCORD_REDIRECT_URI)) {
                                            handleRedirectUrl(loc)
                                        }
                                    }

                                    webView.engine.locationProperty().addListener { _, _, newLocation ->
                                        if (!newLocation.isNullOrBlank() &&
                                            newLocation.startsWith(DesktopDiscordService.DISCORD_REDIRECT_URI)
                                        ) {
                                            handleRedirectUrl(newLocation)
                                        }
                                    }

                                    webView.engine.load(authSession.authorizeUrl)
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

private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) null
        else {
            val key = URLDecoder.decode(pair.substring(0, idx), Charsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8)
            key to value
        }
    }.toMap()
}
