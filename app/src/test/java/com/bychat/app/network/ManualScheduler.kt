package com.bychat.app.network

import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong

class ManualScheduler : TaskScheduler, MonotonicClock {
    private data class Entry(val at: Long, val order: Long, val task: () -> Unit, var cancelled: Boolean = false) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int = compareValuesBy(this, other, Entry::at, Entry::order)
    }

    private val sequence = AtomicLong()
    private val entries = PriorityQueue<Entry>()
    private var now = 0L
    private var closed = false

    override fun nowMillis(): Long = now

    override fun schedule(delayMillis: Long, task: () -> Unit): ScheduledTask {
        require(delayMillis >= 0)
        check(!closed)
        val entry = Entry(now + delayMillis, sequence.incrementAndGet(), task)
        entries += entry
        return object : ScheduledTask { override fun cancel() { entry.cancelled = true } }
    }

    fun advanceBy(millis: Long) {
        require(millis >= 0)
        val target = now + millis
        while (entries.isNotEmpty() && entries.peek().at <= target) {
            val entry = entries.poll()
            now = entry.at
            if (!entry.cancelled) entry.task()
        }
        now = target
    }

    fun pendingCount(): Int = entries.count { !it.cancelled }

    override fun close() { closed = true; entries.clear() }
}
