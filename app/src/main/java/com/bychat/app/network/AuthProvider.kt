package com.bychat.app.network

import com.bychat.app.protocol.AuthChallengePayload
import com.bychat.app.protocol.AuthRequestPayload
import com.bychat.app.protocol.Outcome

interface AuthProvider {
    fun createAuthRequest(challenge: AuthChallengePayload): Outcome<AuthRequestPayload>
}
