package com.bychat.app.network

data class ResumeState(
    val sessionToken: String,
    val communityId: String,
    val lastSyncCursor: Long,
    val expiresAt: Long
) {
    init {
        require(sessionToken.isNotBlank()) { "会话令牌不能为空" }
        require(communityId.isNotBlank()) { "社区ID不能为空" }
        require(lastSyncCursor >= 0) { "同步游标不能为负数" }
    }

    override fun toString(): String = "ResumeState(sessionToken=***, communityId=$communityId, lastSyncCursor=$lastSyncCursor, expiresAt=$expiresAt)"
}

interface SessionResumeStore {
    fun load(endpoint: Endpoint, communityId: String): ResumeState?
    fun save(endpoint: Endpoint, state: ResumeState)
    fun clear(endpoint: Endpoint, communityId: String)
}

class MemorySessionResumeStore : SessionResumeStore {
    private val values = java.util.concurrent.ConcurrentHashMap<String, ResumeState>()
    override fun load(endpoint: Endpoint, communityId: String): ResumeState? = values[key(endpoint, communityId)]
    override fun save(endpoint: Endpoint, state: ResumeState) { values[key(endpoint, state.communityId)] = state }
    override fun clear(endpoint: Endpoint, communityId: String) { values.remove(key(endpoint, communityId)) }
    private fun key(endpoint: Endpoint, communityId: String) = "${endpoint.connectHost}:${endpoint.port}:$communityId"
}
