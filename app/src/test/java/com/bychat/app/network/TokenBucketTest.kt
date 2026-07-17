package com.bychat.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketTest {
    @Test fun limitsBurstAndRefills() {
        var now = 0L
        val bucket = TokenBucket(2.0, 2.0, MonotonicClock { now })
        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())
        now = 500
        assertTrue(bucket.tryConsume())
    }

    @Test fun backwardClockDoesNotAddTokens() {
        var now = 1000L
        val bucket = TokenBucket(1.0, 1.0, MonotonicClock { now })
        assertTrue(bucket.tryConsume())
        now = 0
        assertFalse(bucket.tryConsume())
    }
}
