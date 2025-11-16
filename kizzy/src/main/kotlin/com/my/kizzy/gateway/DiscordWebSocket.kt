package com.my.kizzy.gateway

import com.my.kizzy.gateway.entities.Heartbeat
import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import com.my.kizzy.gateway.entities.Payload
import com.my.kizzy.gateway.entities.Ready
import com.my.kizzy.gateway.entities.Resume
import com.my.kizzy.gateway.entities.op.OpCode
import com.my.kizzy.gateway.entities.op.OpCode.DISPATCH
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT
import com.my.kizzy.gateway.entities.op.OpCode.HELLO
import com.my.kizzy.gateway.entities.op.OpCode.IDENTIFY
import com.my.kizzy.gateway.entities.op.OpCode.INVALID_SESSION
import com.my.kizzy.gateway.entities.op.OpCode.PRESENCE_UPDATE
import com.my.kizzy.gateway.entities.op.OpCode.RECONNECT
import com.my.kizzy.gateway.entities.op.OpCode.RESUME
import com.my.kizzy.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.NORMAL
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.logging.Level
import java.util.logging.Level.INFO
import java.util.logging.Level.WARNING
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow


/**
 * Modified by Zion Huang
 */
open class DiscordWebSocket(
    private val token: String,
) : CoroutineScope {
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private val client: HttpClient = HttpClient {
        install(WebSockets)
    }
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        lastErrorInternal.value = throwable
        connectionStateInternal.value = GatewayConnectionState.Error(throwable)
        Logger.getLogger("Kizzy").log(WARNING, "Gateway coroutine failure", throwable)
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext =
        job + Dispatchers.Default + coroutineExceptionHandler

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var websocket: DefaultClientWebSocketSession? = null
    private var receiverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var connected = false
    private var manualShutdown = false

    private val presenceChannel = Channel<Presence>(Channel.CONFLATED)
    private val connectionStateInternal =
        MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Idle)
    val connectionState = connectionStateInternal.asStateFlow()
    private val lastActivityInternal = MutableStateFlow<Long?>(null)
    val lastActivityAt = lastActivityInternal.asStateFlow()
    private val lastErrorInternal = MutableStateFlow<Throwable?>(null)
    val lastError = lastErrorInternal.asStateFlow()

    private val connectionMutex = Mutex()

    init {
        launch { processPresenceQueue() }
    }

    suspend fun connect() {
        ensureConnected()
    }

    suspend fun restart() {
        connectionMutex.withLock {
            manualShutdown = false
            closeActiveSocket()
        }
        ensureConnected(force = true)
    }

    fun isRunning(): Boolean {
        return !job.isCancelled
    }

    fun isWebSocketConnected(): Boolean {
        return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
    }

    suspend fun sendActivity(presence: Presence) {
        if (presenceChannel.isClosedForSend) return
        presenceChannel.send(presence)
    }

    private suspend fun processPresenceQueue() {
        for (presence in presenceChannel) {
            var attempt = 0
            while (isActive && !manualShutdown) {
                ensureConnected()
                if (!waitUntilReady()) {
                    attempt++
                    delay(backoffMillis(attempt))
                    continue
                }

                runCatching {
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $PRESENCE_UPDATE")
                    send(op = PRESENCE_UPDATE, d = presence)
                }.onSuccess {
                    lastActivityInternal.value = System.currentTimeMillis()
                    break
                }.onFailure { throwable ->
                    lastErrorInternal.value = throwable
                    connectionStateInternal.value = GatewayConnectionState.Error(throwable)
                    attempt++
                    delay(backoffMillis(attempt))
                }
            }
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val clampedAttempt = attempt.coerceAtMost(6)
        val base = 200L
        val multiplier = 1.5.pow(clampedAttempt.toDouble())
        return (base * multiplier).toLong().coerceAtMost(5_000L)
    }

    private suspend fun ensureConnected(force: Boolean = false) {
        if (!force && isSocketConnectedToAccount()) return
        connectionMutex.withLock {
            if (!force && isSocketConnectedToAccount()) return
            connectionStateInternal.value =
                if (connected) GatewayConnectionState.Reconnecting else GatewayConnectionState.Connecting
            openSocket()
        }
    }

    private suspend fun openSocket() {
        var attempt = 0
        while (isActive && !manualShutdown && websocket == null) {
            try {
                val url = resumeGatewayUrl ?: gatewayUrl
                Logger.getLogger("Kizzy").log(INFO, "Gateway: Connecting to $url")
                websocket = client.webSocketSession(url)
                receiverJob?.cancel()
                receiverJob = launch { receiveMessages(websocket!!) }
                return
            } catch (e: Exception) {
                lastErrorInternal.value = e
                connectionStateInternal.value = GatewayConnectionState.Error(e)
                attempt++
                delay(backoffMillis(attempt))
            }
        }
    }

    private suspend fun receiveMessages(session: DefaultClientWebSocketSession) {
        try {
            while (isActive && !manualShutdown) {
                val frame = session.incoming.receive()
                if (frame is Frame.Text) {
                    val jsonString = frame.readText()
                    onMessage(json.decodeFromString(jsonString))
                }
            }
        } catch (e: Exception) {
            if (!manualShutdown) {
                lastErrorInternal.value = e
                connectionStateInternal.value = GatewayConnectionState.Error(e)
            }
        } finally {
            onSocketClosed()
        }
    }

    private suspend fun onSocketClosed() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connected = false
        val closeReason = websocket?.closeReason?.await()
        Logger.getLogger("Kizzy").log(
            INFO,
            "Gateway: Closed with code: ${closeReason?.code}, reason: ${closeReason?.message}"
        )
        connectionMutex.withLock {
            closeActiveSocket()
            if (!manualShutdown) {
                connectionStateInternal.value = GatewayConnectionState.Disconnected(closeReason)
                openSocket()
            }
        }
    }

    private suspend fun onMessage(payload: Payload) {
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")
        payload.s?.let { sequence = it }
        when (payload.op) {
            DISPATCH -> payload.handleDispatch()
            HEARTBEAT -> sendHeartBeat()
            RECONNECT -> restart()
            INVALID_SESSION -> handleInvalidSession()
            HELLO -> payload.handleHello()
            else -> Unit
        }
    }

    open fun Payload.handleDispatch() {
        when (this.t.toString()) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                connected = true
                connectionStateInternal.value = GatewayConnectionState.Connected
            }

            "RESUMED" -> {
                connected = true
                connectionStateInternal.value = GatewayConnectionState.Connected
                Logger.getLogger("Kizzy").log(INFO, "Gateway: Session Resumed")
            }

            else -> Unit
        }
    }

    private suspend fun handleInvalidSession() {
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Handling Invalid Session")
        sessionId = null
        sequence = 0
        delay(150)
        sendIdentify()
    }

    private suspend fun Payload.handleHello() {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    private suspend fun sendHeartBeat() {
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend fun sendIdentify() {
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d = token.toIdentifyPayload()
        )
    }

    private suspend fun sendResume() {
        Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $RESUME")
        send(
            op = RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token
            )
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            while (isActive && websocket != null) {
                runCatching { sendHeartBeat() }
                delay(interval)
            }
        }
    }

    private fun isSocketConnectedToAccount(): Boolean {
        return connected && websocket?.isActive == true
    }

    private suspend fun waitUntilReady(timeoutMillis: Long = 15_000L): Boolean {
        if (isSocketConnectedToAccount()) return true
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && isActive && !manualShutdown) {
            if (isSocketConnectedToAccount()) return true
            delay(50)
        }
        return isSocketConnectedToAccount()
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        val session = websocket
        if (session == null) {
            Logger.getLogger("Kizzy")
                .log(WARNING, "Gateway: Tried to send $op while socket is null")
            return
        }

        val payload = json.encodeToString(
            Payload(
                op = op,
                d = json.encodeToJsonElement(d),
            )
        )

        try {
            session.send(Frame.Text(payload))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            lastErrorInternal.value = t
            connectionStateInternal.value = GatewayConnectionState.Error(t)
            Logger.getLogger("Kizzy").log(WARNING, "Gateway: Failed sending $op", t)

            withContext(NonCancellable) {
                connectionMutex.withLock {
                    closeActiveSocket()
                }
            }
        }
    }

    private suspend fun closeActiveSocket() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connected = false
        runCatching {
            websocket?.close(CloseReason(NORMAL.code, "Closing"))
        }
        websocket = null
    }

    fun close() {
        manualShutdown = true
        presenceChannel.close()
        connectionStateInternal.value = GatewayConnectionState.Closing
        heartbeatJob?.cancel()
        receiverJob?.cancel()
        runBlocking {
            connectionMutex.withLock {
                closeActiveSocket()
            }
        }
        job.cancel()
        client.close()
        Logger.getLogger("Kizzy").log(Level.SEVERE, "Gateway: Connection to gateway closed")
    }
}