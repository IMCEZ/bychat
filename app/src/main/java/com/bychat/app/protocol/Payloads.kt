package com.bychat.app.protocol

data class HelloPayload(
    val clientVersion: String,
    val supportedProtocol: List<Int>,
    val capabilities: List<String> = emptyList(),
    val resumeSupported: Boolean = true
)

data class HelloAckPayload(
    val serverVersion: String,
    val serverProtocol: Int,
    val capabilities: List<String>
)

data class AuthChallengePayload(
    val challengeId: String,
    val nonce: String,
    val expiresAt: Long
)

data class AuthRequestPayload(
    val challengeId: String,
    val userId: String,
    val publicKey: String,
    val signature: String,
    val communityId: String
)

data class AuthResultPayload(
    val authenticated: Boolean,
    val sessionToken: String?,
    val expiresAt: Long?,
    val userId: String?
)

data class ReadyPayload(
    val sessionId: String,
    val communityId: String,
    val ownerId: String,
    val syncCursor: Long
)

data class ResumeRequestPayload(
    val sessionToken: String,
    val communityId: String,
    val lastSyncCursor: Long
) {
    override fun toString(): String = "ResumeRequestPayload(sessionToken=***, communityId=$communityId, lastSyncCursor=$lastSyncCursor)"
}

data class ResumeResultPayload(
    val resumed: Boolean,
    val sessionId: String?,
    val syncFromCursor: Long?,
    val token: String?,
    val expiresAt: Long?
) {
    override fun toString(): String = "ResumeResultPayload(resumed=$resumed, sessionId=$sessionId, syncFromCursor=$syncFromCursor, token=***, expiresAt=$expiresAt)"
}

data class MessageSendPayload(
    val channel: String,
    val clientId: String,
    val kind: String,
    val text: String,
    val replyTo: String? = null
)

data class MessageEventPayload(
    val eventId: String,
    val seq: Long,
    val messageId: String,
    val channel: String,
    val senderId: String,
    val kind: String,
    val text: String,
    val ts: Long,
    val clientId: String? = null,
    val replyTo: String? = null
)

data class SyncRequestPayload(
    val communityId: String,
    val afterSeq: Long,
    val limit: Int
)

data class SyncEventPayload(
    val eventId: String,
    val seq: Long,
    val type: String,
    val data: String,
    val ts: Long
)

data class SyncBatchPayload(
    val communityId: String,
    val fromSeq: Long,
    val toSeq: Long,
    val hasMore: Boolean,
    val events: List<SyncEventPayload>
)

data class MemberEventPayload(
    val eventId: String,
    val seq: Long,
    val communityId: String,
    val userId: String,
    val action: String,
    val nickname: String?,
    val ts: Long
)

data class AdminCommandPayload(
    val communityId: String,
    val command: String,
    val targetId: String?,
    val reason: String?
)

data class AdminResultPayload(
    val command: String,
    val targetId: String?,
    val applied: Boolean
)

data class PingPayload(val nonce: String)
data class PongPayload(val nonce: String)
