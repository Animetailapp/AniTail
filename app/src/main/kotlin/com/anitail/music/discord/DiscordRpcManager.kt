package com.anitail.music.discord

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.discord.socialsdk.NativeCalls
import com.discord.socialsdk.AuthenticationClientCallback
import com.anitail.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import timber.log.Timber

data class DiscordUser(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String?,
)

object DiscordRpcManager {
    private val APP_ID = BuildConfig.DISCORD_APP_ID
    private const val SCOPES = "identify openid sdk.social_layer_presence"
    private val REDIRECT_URI = "discord-$APP_ID:///authorize/callback"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val AUTH_URL = "https://discord.com/oauth2/authorize"

    private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var _authorized = false
    @Volatile private var _ready = false
    @Volatile private var accessToken: String? = null

    private val _accessTokenFlow = MutableStateFlow<String?>(null)
    val accessTokenFlow: StateFlow<String?> = _accessTokenFlow

    private var callbackJob: Job? = null

    private val _connectionStatus = MutableStateFlow(Status.Disconnected)
    val connectionStatus: StateFlow<Status> = _connectionStatus

    private val _settingsChanged = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = _settingsChanged

    fun notifySettingsChanged() {
        _settingsChanged.value++
    }

    enum class Status { Disconnected, Authorizing, Connected }

    enum class StatusType(val value: Int) {
        Online(0),
        Idle(3),
        Dnd(4),
    }

    @JvmStatic
    fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
        synchronized(this) {
            Timber.i("onNativeStatusChanged: statusCode=%d ready=%s authorized=%s", statusCode, ready, authorized)
            _ready = ready
            _authorized = authorized
            _connectionStatus.value = when {
                ready && authorized -> Status.Connected
                statusCode == 0 || (!ready && !authorized) -> Status.Disconnected
                else -> Status.Authorizing
            }
        }
    }

    fun getAccessToken(): String? = accessToken

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeSetActivity(
        activityType: Int,
        name: String?, state: String?, details: String?,
        startSecs: Long, endSecs: Long,
        largeImage: String?, largeText: String?,
        smallImage: String?, smallText: String?,
        button1Label: String?, button1Url: String?,
        button2Label: String?, button2Url: String?,
    )
    private external fun nativeSetOnlineStatus(statusType: Int)
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDestroy()
    private external fun nativeDisconnect()

    fun isInitialized(): Boolean = initialized.get()
    fun isAuthorized(): Boolean = _authorized
    fun isReady(): Boolean = _ready

    fun init(context: Context) {
        DiscordTokenStore.init(context.applicationContext)
        if (!initialized.compareAndSet(false, true)) {
            Timber.i("init: already initialized, skipping")
            return
        }
        Timber.i("init: loading native library 'anitail_discord'")
        try {
            System.loadLibrary("anitail_discord")
            Timber.i("init: native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "init: Failed to load native library")
            initialized.set(false)
            return
        }
        Timber.i("init: calling nativeInit(appId=%d)", APP_ID)
        if (!nativeInit(APP_ID)) {
            Timber.w("init: nativeInit returned false")
            initialized.set(false)
            return
        }
        Timber.i("init: nativeInit succeeded")
        _connectionStatus.value = Status.Disconnected

        // Retrieve stored token to immediately populate flow
        val storedToken = DiscordTokenStore.retrieve()
        if (storedToken != null) {
            accessToken = storedToken
            _accessTokenFlow.value = storedToken
            Timber.i("init: populated accessTokenFlow with stored token (length=%d)", storedToken.length)
        }

        Timber.i("init: starting callback processing coroutine")
        callbackJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    nativeRunCallbacks()
                } catch (e: Exception) {
                    Timber.w(e, "coroutine: error in callback processing iteration")
                }
                delay(1000)
            }
        }
        Timber.i("init: coroutine launched, returning")
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun authorize(onComplete: (Boolean) -> Unit) {
        if (!initialized.get()) {
            Timber.w("authorize: skipping — not initialized")
            onComplete(false)
            return
        }

        Timber.i("authorize: setting status=Authorizing, starting PKCE flow")
        _connectionStatus.value = Status.Authorizing
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val oauthUrl = "$AUTH_URL" +
            "?client_id=$APP_ID" +
            "&response_type=code" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge"

        Timber.i("authorize: calling NativeCalls.authorize with oauthUrl")

        val callback = object : AuthenticationClientCallback(0) {
            private val codeVerifier = verifier
            private var callbackFired = false

            override fun onAuthorizationComplete(error: String?, authCode: String?, state: String?) {
                if (callbackFired) {
                    Timber.w("authorize: callback already fired, ignoring duplicate")
                    return
                }
                callbackFired = true

                if (!error.isNullOrEmpty()) {
                    Timber.e("authorize: onAuthorizationComplete returned error=%s", error)
                    onComplete(false)
                    return
                }

                if (authCode.isNullOrEmpty()) {
                    Timber.w("authorize: onAuthorizationComplete returned null/empty authCode")
                    onComplete(false)
                    return
                }

                Timber.i("authorize: got authCode (length=%d), exchanging for token", authCode.length)
                exchangeCodeForToken(authCode, codeVerifier, onComplete)
            }
        }

        try {
            NativeCalls.authorize(oauthUrl, callback)
            Timber.i("authorize: NativeCalls.authorize returned successfully")
        } catch (e: Exception) {
            Timber.e(e, "authorize: NativeCalls.authorize threw exception")
            onComplete(false)
        }
    }

    private fun exchangeCodeForToken(
        authCode: String,
        codeVerifier: String,
        onComplete: (Boolean) -> Unit,
    ) {
        Thread {
            try {
                val body = "client_id=$APP_ID" +
                    "&grant_type=authorization_code" +
                    "&code=${java.net.URLEncoder.encode(authCode, "UTF-8")}" +
                    "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                    "&code_verifier=$codeVerifier"

                Timber.i("exchange: POSTing to %s", TOKEN_URL)
                val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                Timber.i("exchange: responseCode=%d body.length=%d", responseCode, responseBody.length)

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("access_token")
                    if (accessToken.isNotEmpty()) {
                        Timber.i("exchange: got access_token (length=%d), calling nativeSetTokenAndConnect", accessToken.length)
                        this@DiscordRpcManager.accessToken = accessToken
                        _accessTokenFlow.value = accessToken
                        DiscordTokenStore.store(accessToken)
                        nativeSetTokenAndConnect(accessToken)
                        _connectionStatus.value = Status.Authorizing
                        Handler(Looper.getMainLooper()).post {
                            Timber.i("exchange: posting nativeConnect to main thread")
                            nativeConnect()
                            onComplete(true)
                        }
                        return@Thread
                    } else {
                        Timber.w("exchange: response 200 but no access_token in body: %s", responseBody.take(200))
                    }
                } else {
                    Timber.w("exchange: HTTP %d body=%s", responseCode, responseBody.take(500))
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "exchange: exception during token exchange")
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            }
        }.apply { name = "DiscordTokenExchange" }.start()
    }

    fun fetchCurrentUser(token: String): DiscordUser? {
        return try {
            val url = URL("https://discord.com/api/v10/users/@me")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                Timber.w("fetchCurrentUser: HTTP $responseCode body=$responseBody")
                return null
            }

            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val username = json.getString("username")
            val name = json.optString("global_name", username)
            val avatarHash = json.optString("avatar")
            val avatar = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
            } else null

            DiscordUser(id, username, name, avatar)
        } catch (e: Exception) {
            Timber.e(e, "fetchCurrentUser: exception")
            null
        }
    }

    fun setActivity(activity: DiscordActivity) {
        synchronized(this) {
            if (!_ready) {
                Timber.w("setActivity: skipping — _ready=false, activity name=%s", activity.name)
                return
            }
        }
        Timber.i("setActivity: type=%d name=%s state=%s details=%s start=%d end=%d largeImage=%s smallImage=%s btn1=%s btn2=%s",
            activity.activityType, activity.name, activity.state, activity.details,
            activity.startTimestamp, activity.endTimestamp ?: 0L,
            activity.largeImage, activity.smallImage,
            activity.button1Label, activity.button2Label)
        nativeSetActivity(
            activity.activityType,
            activity.name, activity.state, activity.details,
            activity.startTimestamp, activity.endTimestamp ?: 0L,
            activity.largeImage, activity.largeText,
            activity.smallImage, activity.smallText,
            activity.button1Label, activity.button1Url,
            activity.button2Label, activity.button2Url,
        )
        Timber.i("setActivity: nativeSetActivity call completed")
    }

    fun setOnlineStatus(status: StatusType) {
        synchronized(this) {
            if (!_ready) {
                Timber.w("setOnlineStatus: skipping — _ready=false")
                return
            }
        }
        Timber.i("setOnlineStatus: status=%s", status)
        nativeSetOnlineStatus(status.value)
    }

    fun clear() {
        synchronized(this) {
            if (!_ready) {
                Timber.w("clear: skipping — _ready=false")
                return
            }
        }
        Timber.i("clear: calling nativeClear")
        nativeClear()
    }

    fun reconnectWithToken(token: String) {
        synchronized(this) {
            if (!initialized.get()) {
                Timber.w("reconnectWithToken: skipping — not initialized")
                return
            }
            Timber.i("reconnectWithToken: calling nativeSetTokenAndConnect (token length=%d)", token.length)
            accessToken = token
            _accessTokenFlow.value = token
            DiscordTokenStore.store(token)
            _connectionStatus.value = Status.Authorizing
        }
        Timber.i("reconnectWithToken: set status=Authorizing, posting nativeConnect")
        nativeSetTokenAndConnect(token)
        Handler(Looper.getMainLooper()).post {
            Timber.i("reconnectWithToken: executing nativeConnect on main thread")
            nativeConnect()
        }
    }

    fun destroy() = synchronized(this) {
        Timber.i("destroy: entering (_ready=%s, _authorized=%s, initialized=%s)", _ready, _authorized, initialized)
        _ready = false
        _authorized = false
        initialized.set(false)
        callbackJob?.cancel()
        callbackJob = null
        nativeDestroy()
        Timber.i("destroy: complete")
    }

    fun disconnect() {
        synchronized(this) {
            Timber.i("disconnect: entering (_ready=%s, _authorized=%s)", _ready, _authorized)
            _connectionStatus.value = Status.Disconnected
            _ready = false
            _authorized = false
        }
        nativeDisconnect()
        Timber.i("disconnect: complete")
    }

    fun logout() {
        Timber.i("logout: entering")
        disconnect()
        accessToken = null
        _accessTokenFlow.value = null
        DiscordTokenStore.clear()
        Timber.i("logout: complete, accessToken cleared")
    }
}
