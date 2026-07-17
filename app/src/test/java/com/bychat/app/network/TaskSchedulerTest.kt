package com.bychat.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSchedulerTest {
    @Test fun executesInTimeOrder() {
        val scheduler = ManualScheduler()
        val values = mutableListOf<Int>()
        scheduler.schedule(20) { values += 2 }
        scheduler.schedule(10) { values += 1 }
        scheduler.advanceBy(20)
        assertEquals(listOf(1, 2), values)
    }

    @Test fun cancellationIsIdempotent() {
        val scheduler = ManualScheduler()
        var calls = 0
        val task = scheduler.schedule(1) { calls++ }
        task.cancel(); task.cancel(); scheduler.advanceBy(1)
        assertEquals(0, calls)
    }
}
