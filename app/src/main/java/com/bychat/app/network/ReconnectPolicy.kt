package com.bychat.app.network

import java.util.concurrent.ThreadLocalRandom

fun interface RandomSource { fun nextDouble(): Double }

class ReconnectPolicy(
    private val baseMillis: Long = 1_000,
    private val maxMillis: Long = 60_000,
    private val multiplier: Double = 2.0,
    private val jitterRatio: Double = 0.20,
    val maxAttempts: Int = 8,
    private val random: RandomSource = RandomSource { ThreadLocalRandom.current().nextDouble() }
) {
    init {
        require(baseMillis > 0 && maxMillis >= baseMillis) { "重连延迟参数无效" }
        require(multiplier >= 1.0) { "重连倍数不能小于1" }
        require(jitterRatio in 0.0..1.0) { "抖动比例无效" }
        require(maxAttempts > 0) { "最大重连次数必须大于0" }
    }

    fun delayMillis(attempt: Int): Long {
        require(attempt >= 1) { "重连次数必须从1开始" }
        var value = baseMillis.toDouble()
        repeat((attempt - 1).coerceAtMost(62)) { value = (value * multiplier).coerceAtMost(maxMillis.toDouble()) }
        val capped = value.coerceAtMost(maxMillis.toDouble())
        val unit = random.nextDouble().coerceIn(0.0, 1.0)
        return (capped * (1.0 - jitterRatio + 2.0 * jitterRatio * unit)).toLong().coerceAtLeast(0)
    }
}
