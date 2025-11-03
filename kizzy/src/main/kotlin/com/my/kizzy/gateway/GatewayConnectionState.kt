package com.my.kizzy.gateway

import io.ktor.websocket.CloseReason

/**
 * Lightweight connection state exposed so higher layers can react to gateway events.
 */
sealed class GatewayConnectionState {
    object Idle : GatewayConnectionState()
    object Connecting : GatewayConnectionState()
    object Connected : GatewayConnectionState()
    object Reconnecting : GatewayConnectionState()
    data class Disconnected(val reason: CloseReason?) : GatewayConnectionState()
    data class Error(val throwable: Throwable) : GatewayConnectionState()
    object Closing : GatewayConnectionState()
}
