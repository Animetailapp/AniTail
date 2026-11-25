package com.anitail.music.discord

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.anitail.music.BuildConfig
import com.anitail.music.constants.DiscordAvatarKey
import com.anitail.music.constants.DiscordAvatarUrlKey
import com.anitail.music.constants.DiscordNameKey
import com.anitail.music.constants.DiscordUsernameKey
import com.anitail.music.utils.dataStore
import com.discord.socialsdk.AuthenticationClientCallback
import com.discord.socialsdk.NativeCalls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

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
    @Volatile private var appContext: Context? = null
    @Volatile private var _authorized = false
    @Volatile private var _ready = false
    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null
    @Volatile private var expiresAtMillis: Long = 0L
    @Volatile private var lastRefreshFailureAt: Long = 0L
    private val refreshLock = Any()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    sealed class RefreshOutcome {
        data class Refreshed(val accessToken: String) : RefreshOutcome()
        data object InvalidToken : RefreshOutcome()
        data object TransientFailure : RefreshOutcome()
    }

    sealed class UserFetchOutcome {
        data class Success(val user: DiscordUser, val accessToken: String) : UserFetchOutcome()
        data object Unauthorized : UserFetchOutcome()
        data object TransientError : UserFetchOutcome()
    }

    @JvmStatic
    fun onNativeStatusChanged(
        statusCode: Int,
        errorCode: Int,
        errorDetail: Int,
        ready: Boolean,
        authorized: Boolean,
    ) {
        synchronized(this) {
            Timber.i(
                "onNativeStatusChanged: statusCode=%d errorCode=%d errorDetail=%d ready=%s authorized=%s",
                statusCode, errorCode, errorDetail, ready, authorized,
            )
            _ready = ready
            _authorized = authorized
            _connectionStatus.value = when {
                ready && authorized -> Status.Connected
                statusCode == 0 || (!ready && !authorized) -> Status.Disconnected
                else -> Status.Authorizing
            }
        }
        if (DiscordTokenPolicy.isGatewayAuthFailure(statusCode, errorCode, errorDetail)) {
            Timber.w("onNativeStatusChanged: gateway auth failure detected (detail=%d), attempting token refresh", errorDetail)
            val failedToken = accessToken
            managerScope.launch {
                val outcome = refreshAccessTokenBlocking(failedToken)
                if (outcome is RefreshOutcome.InvalidToken) {
                    invalidateExpiredSession()
                }
            }
        }
    }

    @JvmStatic
    fun onNativeTokenExpired() {
        Timber.w("onNativeTokenExpired: SDK signaled token expiration, refreshing token")
        val failedToken = accessToken
        managerScope.launch {
            val outcome = refreshAccessTokenBlocking(failedToken)
            if (outcome is RefreshOutcome.InvalidToken) {
                invalidateExpiredSession()
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
        val appCtx = context.applicationContext
        appContext = appCtx
        DiscordTokenStore.init(appCtx)
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

        val storedToken = DiscordTokenStore.retrieve()
        val storedRefresh = DiscordTokenStore.retrieveRefreshToken()
        val storedExpiresAt = DiscordTokenStore.retrieveExpiresAt()
        refreshToken = storedRefresh
        expiresAtMillis = storedExpiresAt
        if (storedToken != null) {
            accessToken = storedToken
            _accessTokenFlow.value = storedToken
            Timber.i(
                "init: populated accessTokenFlow with stored token (length=%d, hasRefresh=%s, expiresAt=%d)",
                storedToken.length,
                !storedRefresh.isNullOrBlank(),
                storedExpiresAt,
            )
        }

        Timber.i("init: starting callback processing coroutine")
        callbackJob = managerScope.launch {
            while (isActive) {
                try {
                    nativeRunCallbacks()
                } catch (e: Exception) {
                    Timber.w(e, "coroutine: error in callback processing iteration")
                }
                delay(1000)
            }
        }

        if (!storedToken.isNullOrBlank() &&
            !storedRefresh.isNullOrBlank() &&
            DiscordTokenPolicy.shouldRefreshProactively(System.currentTimeMillis(), storedExpiresAt)
        ) {
            managerScope.launch {
                refreshAccessTokenBlocking(null)
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
            "&scope=${URLEncoder.encode(SCOPES, "UTF-8")}" +
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
                    _connectionStatus.value = Status.Disconnected
                    onComplete(false)
                    return
                }

                if (authCode.isNullOrEmpty()) {
                    Timber.w("authorize: onAuthorizationComplete returned null/empty authCode")
                    _connectionStatus.value = Status.Disconnected
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
            _connectionStatus.value = Status.Disconnected
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
                    "&code=${URLEncoder.encode(authCode, "UTF-8")}" +
                    "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
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
                    val parsed = DiscordTokenPolicy.parseTokenResponse(responseBody)
                    if (parsed != null) {
                        val nextExpiresAt = DiscordTokenPolicy.computeExpiresAtMillis(
                            System.currentTimeMillis(),
                            parsed.expiresInSec,
                        )
                        Timber.i(
                            "exchange: got access_token (length=%d, hasRefresh=%s, expiresIn=%d), calling nativeSetTokenAndConnect",
                            parsed.accessToken.length,
                            !parsed.refreshToken.isNullOrBlank(),
                            parsed.expiresInSec,
                        )
                        accessToken = parsed.accessToken
                        refreshToken = parsed.refreshToken
                        expiresAtMillis = nextExpiresAt
                        lastRefreshFailureAt = 0L
                        DiscordTokenStore.storeTokens(parsed.accessToken, parsed.refreshToken, nextExpiresAt)
                        _accessTokenFlow.value = parsed.accessToken
                        _connectionStatus.value = Status.Authorizing
                        nativeSetTokenAndConnect(parsed.accessToken)
                        Handler(Looper.getMainLooper()).post {
                            onComplete(true)
                        }
                        return@Thread
                    } else {
                        Timber.w("exchange: response 200 but no access_token in body: %s", responseBody.take(200))
                    }
                } else {
                    Timber.w("exchange: HTTP %d body=%s", responseCode, responseBody.take(500))
                }
                _connectionStatus.value = Status.Disconnected
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "exchange: exception during token exchange")
                _connectionStatus.value = Status.Disconnected
                Handler(Looper.getMainLooper()).post {
                    onComplete(false)
                }
            }
        }.apply { name = "DiscordTokenExchange" }.start()
    }

    fun refreshAccessTokenBlocking(failedAccessToken: String? = null): RefreshOutcome {
        synchronized(refreshLock) {
            val now = System.currentTimeMillis()
            val currentAccess = accessToken ?: DiscordTokenStore.retrieve()
            val currentRefresh = refreshToken ?: DiscordTokenStore.retrieveRefreshToken()
            val currentExpiry = if (expiresAtMillis > 0L) expiresAtMillis else DiscordTokenStore.retrieveExpiresAt()

            if (failedAccessToken != null &&
                !currentAccess.isNullOrBlank() &&
                currentAccess != failedAccessToken &&
                !DiscordTokenPolicy.shouldRefreshProactively(now, currentExpiry)
            ) {
                Timber.i("refresh: token already refreshed by another caller")
                return RefreshOutcome.Refreshed(currentAccess)
            }

            if (currentRefresh.isNullOrBlank()) {
                Timber.w("refresh: no refresh_token stored")
                return RefreshOutcome.InvalidToken
            }

            if (failedAccessToken == null &&
                now - lastRefreshFailureAt < DiscordTokenPolicy.REFRESH_FAILURE_COOLDOWN_MS
            ) {
                Timber.i("refresh: skipping proactive refresh during failure cooldown")
                return RefreshOutcome.TransientFailure
            }

            return try {
                val body = "client_id=$APP_ID" +
                    "&grant_type=refresh_token" +
                    "&refresh_token=${URLEncoder.encode(currentRefresh, "UTF-8")}"

                Timber.i("refresh: POSTing refresh_token grant to %s", TOKEN_URL)
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

                Timber.i("refresh: responseCode=%d body.length=%d", responseCode, responseBody.length)

                if (responseCode in 200..299) {
                    val parsed = DiscordTokenPolicy.parseTokenResponse(responseBody)
                    if (parsed == null) {
                        Timber.w("refresh: HTTP %d but invalid token payload", responseCode)
                        lastRefreshFailureAt = System.currentTimeMillis()
                        return RefreshOutcome.TransientFailure
                    }
                    val nextRefresh = parsed.refreshToken ?: currentRefresh
                    val nextExpiresAt = DiscordTokenPolicy.computeExpiresAtMillis(
                        System.currentTimeMillis(),
                        parsed.expiresInSec,
                    )
                    accessToken = parsed.accessToken
                    refreshToken = nextRefresh
                    expiresAtMillis = nextExpiresAt
                    lastRefreshFailureAt = 0L
                    DiscordTokenStore.storeTokens(parsed.accessToken, nextRefresh, nextExpiresAt)
                    _accessTokenFlow.value = parsed.accessToken
                    if (initialized.get()) {
                        _connectionStatus.value = Status.Authorizing
                        nativeSetTokenAndConnect(parsed.accessToken)
                    }
                    Timber.i("refresh: succeeded (expiresIn=%d)", parsed.expiresInSec)
                    RefreshOutcome.Refreshed(parsed.accessToken)
                } else if (DiscordTokenPolicy.isPermanentTokenError(responseCode, responseBody)) {
                    Timber.w("refresh: permanent auth error HTTP %d body=%s", responseCode, responseBody.take(300))
                    invalidateExpiredSession()
                    RefreshOutcome.InvalidToken
                } else {
                    Timber.w("refresh: transient HTTP %d body=%s", responseCode, responseBody.take(300))
                    lastRefreshFailureAt = System.currentTimeMillis()
                    RefreshOutcome.TransientFailure
                }
            } catch (e: Exception) {
                Timber.w(e, "refresh: transient exception during refresh")
                lastRefreshFailureAt = System.currentTimeMillis()
                RefreshOutcome.TransientFailure
            }
        }
    }

    private fun invalidateExpiredSession() {
        Timber.w("invalidateExpiredSession: clearing invalid Discord session")
        logout()
        val ctx = appContext ?: return
        managerScope.launch {
            runCatching {
                ctx.dataStore.edit { prefs ->
                    prefs.remove(DiscordUsernameKey)
                    prefs.remove(DiscordNameKey)
                    prefs.remove(DiscordAvatarKey)
                    prefs.remove(DiscordAvatarUrlKey)
                }
            }.onFailure { e ->
                Timber.w(e, "invalidateExpiredSession: failed to clear DataStore")
            }
        }
    }

    fun fetchCurrentUserWithOutcome(initialToken: String? = getAccessToken()): UserFetchOutcome {
        var token = initialToken ?: DiscordTokenStore.retrieve() ?: return UserFetchOutcome.Unauthorized
        val exp = if (expiresAtMillis > 0L) expiresAtMillis else DiscordTokenStore.retrieveExpiresAt()
        val ref = refreshToken ?: DiscordTokenStore.retrieveRefreshToken()
        if (DiscordTokenPolicy.shouldRefreshProactively(System.currentTimeMillis(), exp) && !ref.isNullOrBlank()) {
            when (val out = refreshAccessTokenBlocking(null)) {
                is RefreshOutcome.Refreshed -> token = out.accessToken
                is RefreshOutcome.InvalidToken -> return UserFetchOutcome.Unauthorized
                is RefreshOutcome.TransientFailure -> Unit
            }
        }

        return when (val first = executeFetchUserRequest(token)) {
            is UserFetchOutcome.Success -> first
            is UserFetchOutcome.TransientError -> first
            is UserFetchOutcome.Unauthorized -> {
                when (val out = refreshAccessTokenBlocking(token)) {
                    is RefreshOutcome.Refreshed -> {
                        when (val retry = executeFetchUserRequest(out.accessToken)) {
                            is UserFetchOutcome.Success -> retry
                            is UserFetchOutcome.Unauthorized -> {
                                invalidateExpiredSession()
                                UserFetchOutcome.Unauthorized
                            }
                            is UserFetchOutcome.TransientError -> UserFetchOutcome.TransientError
                        }
                    }
                    is RefreshOutcome.InvalidToken -> {
                        invalidateExpiredSession()
                        UserFetchOutcome.Unauthorized
                    }
                    is RefreshOutcome.TransientFailure -> UserFetchOutcome.TransientError
                }
            }
        }
    }

    private fun executeFetchUserRequest(token: String): UserFetchOutcome {
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

            if (responseCode == 401) {
                Timber.w("fetchCurrentUser: HTTP 401 body=%s", responseBody.take(200))
                return UserFetchOutcome.Unauthorized
            }
            if (responseCode !in 200..299) {
                Timber.w("fetchCurrentUser: HTTP %d body=%s", responseCode, responseBody.take(200))
                return UserFetchOutcome.TransientError
            }

            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val username = json.getString("username")
            val name = json.optString("global_name", username)
            val avatarHash = json.optString("avatar")
            val avatar = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
            } else null

            UserFetchOutcome.Success(DiscordUser(id, username, name, avatar), token)
        } catch (e: Exception) {
            Timber.e(e, "fetchCurrentUser: exception")
            UserFetchOutcome.TransientError
        }
    }

    fun fetchCurrentUser(token: String): DiscordUser? =
        (fetchCurrentUserWithOutcome(token) as? UserFetchOutcome.Success)?.user

    fun ensureConnected() {
        managerScope.launch {
            if (!initialized.get()) {
                val ctx = appContext ?: return@launch
                init(ctx)
                if (!initialized.get()) return@launch
            }

            val exp = if (expiresAtMillis > 0L) expiresAtMillis else DiscordTokenStore.retrieveExpiresAt()
            if (isReady() && !DiscordTokenPolicy.shouldRefreshProactively(System.currentTimeMillis(), exp)) {
                return@launch
            }

            val token = accessToken ?: DiscordTokenStore.retrieveSuspend() ?: return@launch
            val ref = refreshToken ?: DiscordTokenStore.retrieveRefreshTokenSuspend()

            if (DiscordTokenPolicy.shouldRefreshProactively(System.currentTimeMillis(), exp) && !ref.isNullOrBlank()) {
                when (refreshAccessTokenBlocking(null)) {
                    is RefreshOutcome.Refreshed -> return@launch
                    is RefreshOutcome.InvalidToken -> return@launch
                    is RefreshOutcome.TransientFailure -> {
                        // Fall through to connect with existing token if it might still work
                    }
                }
            }

            if (!isReady() && _connectionStatus.value != Status.Authorizing) {
                _connectionStatus.value = Status.Authorizing
                nativeSetTokenAndConnect(token)
            }
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
            Timber.i("reconnectWithToken: updating token (token length=%d) and ensuring connection", token.length)
            accessToken = token
            _accessTokenFlow.value = token
            DiscordTokenStore.store(token)
        }
        ensureConnected()
    }

    fun destroy() {
        synchronized(this) {
            if (!initialized.get()) {
                Timber.i("destroy: skipping — not initialized")
                return
            }
            Timber.i("destroy: entering (_ready=%s, _authorized=%s, initialized=%s)", _ready, _authorized, initialized)
            _ready = false
            _authorized = false
            initialized.set(false)
            callbackJob?.cancel()
            callbackJob = null
            nativeDestroy()
            Timber.i("destroy: complete")
        }
    }

    fun disconnect() {
        synchronized(this) {
            if (!initialized.get()) {
                Timber.i("disconnect: skipping — not initialized")
                return
            }
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
        refreshToken = null
        expiresAtMillis = 0L
        lastRefreshFailureAt = 0L
        _accessTokenFlow.value = null
        DiscordTokenStore.clear()
        Timber.i("logout: complete, accessToken cleared")
    }
}
