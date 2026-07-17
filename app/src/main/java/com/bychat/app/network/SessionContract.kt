package com.bychat.app.network

import com.bychat.app.protocol.Envelope
import com.bychat.app.protocol.Outcome
import com.bychat.app.protocol.ServerCapability

sealed interface SessionState {
    data object Idle : SessionState
    data class Resolving(val endpoint: Endpoint) : SessionState
    data class Connecting(val endpoint: Endpoint) : SessionState
    data class Handshaking(val endpoint: Endpoint) : SessionState
    data class Authenticating(val endpoint: Endpoint) : SessionState
    data class Ready(val sessionId: String, val communityId: String, val syncCursor: Long) : SessionState
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : SessionState
    data object Closing : SessionState
    data class Closed(val reason: CloseReason, val message: String) : SessionState
}

enum class CloseReason {
    USER_REQUEST,
    REMOTE_CLOSED,
    NETWORK_ERROR,
    PROTOCOL_ERROR,
    AUTH_FAILED,
    TIMEOUT,
    RETRY_EXHAUSTED
}

data class ConnectionRequest(
    val endpoint: Endpoint,
    val clientVersion: String,
    val communityId: String,
    val capabilities: Set<ServerCapability> = emptySet()
) {
    init {
        require(clientVersion.isNotBlank()) { "客户端版本不能为空" }
        require(communityId.isNotBlank()) { "社区ID不能为空" }
    }
}

interface SessionListener {
    fun onStateChanged(previous: SessionState, current: SessionState)
    fun onEnvelope(envelope: Envelope)
    fun onClosed(reason: CloseReason, message: String)
}

interface ClientSession : AutoCloseable {
    val state: SessionState
    fun connect(request: ConnectionRequest): Outcome<Unit>
    fun send(envelope: Envelope): Outcome<Unit>
    override fun close()
}
