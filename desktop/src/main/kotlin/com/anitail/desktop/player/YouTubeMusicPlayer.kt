package com.anitail.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.anitail.desktop.util.JavaFxManager
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView

@Composable
fun YouTubeMusicPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val currentUrl = rememberUpdatedState(url)
    val panel = remember {
        JavaFxManager.ensureInitialized()
        JFXPanel()
    }

    LaunchedEffect(currentUrl.value) {
        Platform.runLater {
            val view = (panel.scene?.root as? WebView) ?: WebView().also { newView ->
                panel.scene = Scene(newView)
            }
            if (view.engine.location != currentUrl.value) {
                view.engine.load(currentUrl.value)
            }
        }
    }

    SwingPanel(
        factory = { panel },
        modifier = modifier,
        update = { /* No-op */ },
    )
}
