package com.bychat.app.protocol

import com.google.gson.JsonElement

data class Envelope(
    val v: Int,
    val type: String,
    val id: String,
    val ts: Long,
    val payload: JsonElement,
    val error: ProtocolErrorPayload? = null
)

object PacketType {
    const val HELLO = "hello"
    const val HELLO_ACK = "hello_ack"
    const val AUTH_CHALLENGE = "auth_challenge"
    const val AUTH_REQUEST = "auth_request"
    const val AUTH_RESULT = "auth_result"
    const val READY = "ready"
    const val MESSAGE_SEND = "message_send"
    const val MESSAGE_EVENT = "message_event"
    const val SYNC_REQUEST = "sync_request"
    const val SYNC_BATCH = "sync_batch"
    const val MEMBER_EVENT = "member_event"
    const val ADMIN_COMMAND = "admin_command"
    const val ADMIN_RESULT = "admin_result"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"

    val all: Set<String> = setOf(
        HELLO, HELLO_ACK, AUTH_CHALLENGE, AUTH_REQUEST, AUTH_RESULT, READY,
        MESSAGE_SEND, MESSAGE_EVENT, SYNC_REQUEST, SYNC_BATCH, MEMBER_EVENT,
        ADMIN_COMMAND, ADMIN_RESULT, PING, PONG, ERROR
    )
}
