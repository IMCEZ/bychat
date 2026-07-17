package com.bychat.app.protocol

const val PROTOCOL_VERSION = 2
const val MIN_SUPPORTED_PROTOCOL = 2

enum class ServerCapability(val wireName: String) {
    VOICE_MESSAGE("voice_message"),
    IMAGE_MESSAGE("image_message"),
    FILE_MESSAGE("file_message"),
    MULTI_COMMUNITY("multi_community"),
    REACTIONS("reactions"),
    EDIT_MESSAGE("edit_message"),
    DELETE_MESSAGE("delete_message")
}

fun capabilitiesToWire(capabilities: Set<ServerCapability>): List<String> =
    capabilities.map(ServerCapability::wireName).sorted()

fun wireToCapabilities(names: Collection<String>): Set<ServerCapability> {
    val byName = ServerCapability.entries.associateBy(ServerCapability::wireName)
    return names.mapNotNull(byName::get).toSet()
}
