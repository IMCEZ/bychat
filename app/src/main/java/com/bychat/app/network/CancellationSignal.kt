package com.bychat.app.network

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class CancellationSignal {
    private val cancelled = AtomicBoolean(false)
    private val actions = CopyOnWriteArrayList<() -> Unit>()

    val isCancelled: Boolean get() = cancelled.get()

    fun onCancel(action: () -> Unit) {
        if (cancelled.get()) { action(); return }
        actions += action
        if (cancelled.get() && actions.remove(action)) action()
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        actions.toList().forEach { runCatching(it) }
        actions.clear()
    }

    fun throwIfCancelled() {
        if (cancelled.get()) throw java.io.InterruptedIOException("连接已取消")
    }
}
