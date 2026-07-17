package com.bychat.app.network

import com.bychat.app.protocol.Outcome
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {
    private val endpoint = Endpoint("localhost", "localhost", 18888, false)

    @Test fun acceptsCompleteAuthenticationPath() {
        val machine = SessionStateMachine()
        listOf(
            SessionState.Resolving(endpoint), SessionState.Connecting(endpoint), SessionState.Handshaking(endpoint),
            SessionState.Authenticating(endpoint), SessionState.Ready("s", "c", 0), SessionState.Closing,
            SessionState.Closed(CloseReason.USER_REQUEST, "关闭")
        ).forEach { assertTrue(machine.transition(it) is Outcome.Success) }
    }

    @Test fun rejectsSkippingFromIdleToReady() {
        assertTrue(SessionStateMachine().transition(SessionState.Ready("s", "c", 0)) is Outcome.Failure)
    }

    @Test fun closedIsTerminal() {
        val machine = SessionStateMachine(SessionState.Closed(CloseReason.USER_REQUEST, "关闭"))
        assertTrue(machine.transition(SessionState.Resolving(endpoint)) is Outcome.Failure)
    }
}
