package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.Outcome
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SessionStateMachine(initial: SessionState = SessionState.Idle) {
    private val lock = ReentrantLock()
    private var current: SessionState = initial

    val state: SessionState get() = lock.withLock { current }

    fun transition(next: SessionState): Outcome<Pair<SessionState, SessionState>> = lock.withLock {
        val previous = current
        if (!allowed(previous, next)) return Outcome.Failure(ErrorCode.CONFLICT, "非法会话状态转换：${name(previous)}到${name(next)}")
        current = next
        Outcome.Success(previous to next)
    }

    private fun allowed(from: SessionState, to: SessionState): Boolean = when (from) {
        SessionState.Idle -> to is SessionState.Resolving || to is SessionState.Closed
        is SessionState.Resolving -> to is SessionState.Connecting || to is SessionState.Reconnecting || to is SessionState.Closing || to is SessionState.Closed
        is SessionState.Connecting -> to is SessionState.Handshaking || to is SessionState.Reconnecting || to is SessionState.Closing || to is SessionState.Closed
        is SessionState.Handshaking -> to is SessionState.Authenticating || to is SessionState.Ready || to is SessionState.Reconnecting || to is SessionState.Closing || to is SessionState.Closed
        is SessionState.Authenticating -> to is SessionState.Ready || to is SessionState.Reconnecting || to is SessionState.Closing || to is SessionState.Closed
        is SessionState.Ready -> to is SessionState.Reconnecting || to is SessionState.Closing || to is SessionState.Closed
        is SessionState.Reconnecting -> to is SessionState.Resolving || to is SessionState.Closing || to is SessionState.Closed
        SessionState.Closing -> to is SessionState.Closed
        is SessionState.Closed -> false
    }

    private fun name(state: SessionState) = state::class.simpleName.orEmpty()
}
