package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AccountInfo
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.util.JavaFxManager
import com.anitail.innertube.YouTube
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.launch
import netscape.javascript.JSObject

/**
 * Pantalla de login para Desktop.
 * Usa JavaFX WebView para autenticación con Google.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authService: DesktopAuthService,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var extractedVisitorData by remember { mutableStateOf<String?>(null) }
    var extractedDataSyncId by remember { mutableStateOf<String?>(null) }
    var extractedCookie by remember { mutableStateOf<String?>(null) }

    // Callback cuando se completa el login
    LaunchedEffect(extractedDataSyncId) {
        if (extractedDataSyncId != null && extractedVisitorData != null) {
            authService.saveCredentials(
                AuthCredentials(
                    visitorData = extractedVisitorData,
                    dataSyncId = extractedDataSyncId,
                    cookie = extractedCookie,
                )
            )
            
            // Intentar obtener info de la cuenta
            authService.refreshAccountInfo()
            
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar sesión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(IconAssets.arrowBack(), contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            // WebView para login
            SwingPanel(
                factory = {
                    JavaFxManager.ensureInitialized()
                    JFXPanel().also { panel ->
                        Platform.runLater {
                            val webView = WebView()
                            
                            webView.engine.loadWorker.stateProperty().addListener { _, _, newState ->
                                Platform.runLater {
                                    isLoading = newState == Worker.State.RUNNING
                                    currentUrl = webView.engine.location ?: ""
                                    
                                    if (newState == Worker.State.SUCCEEDED) {
                                        // Extraer visitorData y dataSyncId del JavaScript
                                        try {
                                            val window = webView.engine.executeScript("window") as? JSObject
                                            val ytConfig = window?.getMember("yt") as? JSObject
                                            val config = ytConfig?.getMember("config_") as? JSObject
                                            
                                            config?.getMember("VISITOR_DATA")?.toString()?.let { vd ->
                                                if (vd != "undefined" && vd.isNotBlank()) {
                                                    extractedVisitorData = vd
                                                }
                                            }
                                            
                                            config?.getMember("DATASYNC_ID")?.toString()?.let { ds ->
                                                if (ds != "undefined" && ds.isNotBlank()) {
                                                    extractedDataSyncId = ds.substringBefore("||")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Ignorar errores de JS
                                        }
                                        
                                        // Capturar cookies si estamos en YouTube Music
                                        if (currentUrl.contains("music.youtube.com")) {
                                            try {
                                                val cookieMgr = java.net.CookieHandler.getDefault()
                                                // Las cookies se manejarán de otra forma en sistemas de producción
                                            } catch (e: Exception) {
                                                // Ignorar
                                            }
                                        }
                                    }
                                }
                            }
                            
                            webView.engine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            webView.engine.load(DesktopAuthService.LOGIN_URL)
                            
                            panel.scene = Scene(webView)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Pantalla de perfil/cuenta del usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authService: DesktopAuthService,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (authService.isLoggedIn) {
            isLoading = true
            accountInfo = authService.refreshAccountInfo()
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuenta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(IconAssets.arrowBack(), contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (authService.isLoggedIn) {
                // Mostrar información de la cuenta
                Icon(
                    imageVector = IconAssets.account(),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    accountInfo?.let { info ->
                        Text(
                            text = info.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        info.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        info.channelHandle?.let { handle ->
                            Text(
                                text = handle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } ?: run {
                        Text(
                            text = authService.credentials?.accountName ?: "Usuario conectado",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            authService.logout()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(IconAssets.logout(), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar sesión")
                }
            } else {
                // No hay sesión iniciada
                Icon(
                    imageVector = IconAssets.account(),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "No has iniciado sesión",
                    style = MaterialTheme.typography.titleMedium,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Inicia sesión para acceder a tu biblioteca, playlists y recomendaciones personalizadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onLoginClick) {
                    Text("Iniciar sesión con Google")
                }
            }
        }
    }
}
