package com.bychat.app

data class Message(
    val id: String,
    val room: String,
    val sender: String,
    val type: String,
    val content: String,
    val timestamp: Long
)

data class Packet(
    val action: String,
    val username: String? = null,
    val credential: String? = null,
    val room: String? = null,
    val roomPassword: String? = null,
    val message: Message? = null,
    val target: String? = null,
    val data: String? = null,
    val error: String? = null
)
