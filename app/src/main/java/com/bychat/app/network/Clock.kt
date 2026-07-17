package com.bychat.app.network

fun interface MonotonicClock {
    fun nowMillis(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}
