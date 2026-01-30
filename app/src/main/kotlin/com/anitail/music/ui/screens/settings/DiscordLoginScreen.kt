package com.anitail.music.ui.screens.settings

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.DiscordTokenKey
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var webView: WebView? = null

    // Constants for extraction
    val TOKEN_EXTRACTION_DELAY = 2000L
    val MAX_RETRY_ATTEMPTS = 5
    val MIN_TOKEN_LENGTH = 50

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                WebView.setWebContentsDebuggingEnabled(true)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true

                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }

                WebStorage.getInstance().deleteAllData()

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url.isNullOrBlank()) return

                        // If we're already navigated away or token exists, skip
                        if (discordToken.isNotEmpty()) return

                        if (url.contains("/channels/@me") || url.contains("/app")) {
                            // Use a robust extraction strategy with retries
                            val attemptCounter = AtomicInteger(0)

                            fun tryExtract() {
                                if (discordToken.isNotEmpty()) return
                                val attempt = attemptCounter.get()
                                if (attempt >= MAX_RETRY_ATTEMPTS) {
                                    Log.e("DiscordWebView", "Max extraction attempts reached")
                                    return
                                }

                                postDelayed({
                                    evaluateJavascript(getTokenExtractionScript()) { result ->
                                        val token = result?.trim('"')?.replace("\\\"", "") ?: ""
                                        Log.d(
                                            "DiscordWebView",
                                            "Extraction attempt ${attempt + 1}, token length=${token.length}"
                                        )
                                        if (isValidToken(token, MIN_TOKEN_LENGTH)) {
                                            Log.i("DiscordWebView", "Valid token extracted")
                                            discordToken = token
                                            scope.launch(Dispatchers.Main) {
                                                loadUrl("about:blank")
                                                navController.navigateUp()
                                            }
                                        } else {
                                            attemptCounter.incrementAndGet()
                                            tryExtract()
                                        }
                                    }
                                }, TOKEN_EXTRACTION_DELAY)
                            }

                            tryExtract()
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = false
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult
                    ): Boolean {
                        // Some extraction fallbacks alert the token — validate it before using
                        if (message != "null" && message != "error" && isValidToken(
                                message,
                                MIN_TOKEN_LENGTH
                            )
                        ) {
                            discordToken = message
                            scope.launch(Dispatchers.Main) {
                                view.loadUrl("about:blank")
                                navController.navigateUp()
                            }
                        }
                        result.confirm()
                        return true
                    }
                }

                webView = this
                loadUrl("https://discord.com/login")
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

// Helper: JS extraction script (adapted from KizzyRPC / Saikou implementations)
private fun getTokenExtractionScript(): String {
    return """
            (function() {
                try {
                    // Method 1: Webpack chunks (modern Discord)
                    const w = (typeof webpackChunkdiscord_app !== 'undefined') ? webpackChunkdiscord_app.push([[""],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]) || m : null;
                    if (w) {
                        const req = m.find(m => m?.exports?.default?.getToken !== void 0)?.exports?.default;
                        if (req && req.getToken) {
                            const token = req.getToken();
                            if (token) return token;
                        }
                    }
                } catch(e){}

                try {
                    // Method 2: Alternative webpack approach
                    let token = null;
                    if (typeof webpackChunkdiscord_app !== 'undefined') {
                        webpackChunkdiscord_app.push([[Math.random()], {}, (r) => {
                            for (const m in r.c) {
                                const exp = r.c[m].exports;
                                if (!exp) continue;
                                if (exp.default && exp.default.getToken) token = exp.default.getToken();
                                if (exp.getToken) token = exp.getToken();
                            }
                        }]);
                        if (token) return token;
                    }
                } catch(e){}

                try {
                    // Method 3: scan localStorage
                    for (let i = 0; i < localStorage.length; i++) {
                        const key = localStorage.key(i);
                        const value = localStorage.getItem(key);
                        if (!value) continue;
                        const cleaned = value.replace(/['"]/g, '');
                        if (/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(cleaned)) return cleaned;
                    }
                } catch(e){}

                try {
                    const storageToken = localStorage.getItem('token');
                    if (storageToken) {
                        const cleaned = storageToken.replace(/['"]/g, '');
                        if (cleaned) return cleaned;
                    }
                } catch(e){}

                return 'NO_TOKEN';
            })();
        """.trimIndent()
}

private fun isValidToken(token: String, minLength: Int = 50): Boolean {
    return token.isNotEmpty() &&
            token != "NO_TOKEN" &&
            token != "null" &&
            token != "undefined" &&
            token.length > minLength &&
            Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$").matches(token)
}
