package com.bychat.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun doublesAndCapsWithoutJitterAtMidpoint() {
        val policy = ReconnectPolicy(random = RandomSource { 0.5 })
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L), (1..8).map(policy::delayMillis))
    }

    @Test fun jitterStaysWithinTwentyPercent() {
        assertEquals(800L, ReconnectPolicy(random = RandomSource { 0.0 }).delayMillis(1))
        assertEquals(1200L, ReconnectPolicy(random = RandomSource { 1.0 }).delayMillis(1))
    }

    @Test fun rejectsInvalidAttempt() {
        try { ReconnectPolicy().delayMillis(0); throw AssertionError("应拒绝") }
        catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().isNotBlank()) }
    }
}
