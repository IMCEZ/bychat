package com.bychat.app.protocol

import java.time.Clock
import java.util.UUID

object IdGen {
    fun newRequestId(): String = "r-${UUID.randomUUID()}"
    fun newEventId(): String = "e-${UUID.randomUUID()}"
    fun newMessageClientId(): String = "c-${UUID.randomUUID()}"
}

fun nowEpochMillis(clock: Clock = Clock.systemUTC()): Long = clock.millis()
