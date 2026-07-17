package com.bychat.app.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface ScheduledTask { fun cancel() }
interface TaskScheduler : AutoCloseable {
    fun schedule(delayMillis: Long, task: () -> Unit): ScheduledTask
    override fun close()
}

class ExecutorTaskScheduler : TaskScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "bychat-session-timer").apply { isDaemon = true } }
    private val futures = ConcurrentHashMap.newKeySet<ScheduledFuture<*>>()
    private val closed = AtomicBoolean(false)

    override fun schedule(delayMillis: Long, task: () -> Unit): ScheduledTask {
        require(delayMillis >= 0) { "延迟不能为负数" }
        check(!closed.get()) { "调度器已关闭" }
        val completed = AtomicBoolean(false)
        val future = executor.schedule({
            try { task() } finally { completed.set(true) }
        }, delayMillis, TimeUnit.MILLISECONDS)
        if (!completed.get()) futures += future
        return object : ScheduledTask {
            private val cancelled = AtomicBoolean(false)
            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    future.cancel(false)
                    futures.remove(future)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        futures.toList().forEach { it.cancel(false) }
        futures.clear()
        executor.shutdownNow()
    }
}
