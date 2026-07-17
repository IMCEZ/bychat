package com.bychat.app.network

class TokenBucket(
    private val ratePerSecond: Double = 20.0,
    private val capacity: Double = 40.0,
    private val clock: MonotonicClock = SystemMonotonicClock
) {
    init { require(ratePerSecond > 0 && capacity > 0) { "令牌桶参数必须大于0" } }
    private var tokens = capacity
    private var lastMillis = clock.nowMillis()

    @Synchronized fun tryConsume(count: Double = 1.0): Boolean {
        require(count > 0) { "消费数量必须大于0" }
        val now = clock.nowMillis()
        if (now > lastMillis) {
            tokens = (tokens + (now - lastMillis) * ratePerSecond / 1000.0).coerceAtMost(capacity)
            lastMillis = now
        }
        if (tokens < count) return false
        tokens -= count
        return true
    }
}
