package com.bychat.app.network

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

interface SocketConnector {
    @Throws(IOException::class)
    fun connect(endpoint: Endpoint, timeoutMillis: Int, cancellation: CancellationSignal): Socket
}

class DefaultSocketConnector : SocketConnector {
    override fun connect(endpoint: Endpoint, timeoutMillis: Int, cancellation: CancellationSignal): Socket {
        require(timeoutMillis > 0) { "连接超时必须大于0" }
        val started = System.nanoTime()
        val failures = mutableListOf<IOException>()
        val addresses = InetAddress.getAllByName(endpoint.connectHost)
        cancellation.throwIfCancelled()
        for (address in addresses) {
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            val remaining = (timeoutMillis - elapsed).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (remaining <= 0) break
            val socket = Socket()
            cancellation.onCancel { runCatching { socket.close() } }
            try {
                cancellation.throwIfCancelled()
                socket.connect(InetSocketAddress(address, endpoint.port), remaining)
                cancellation.throwIfCancelled()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                return socket
            } catch (error: IOException) {
                runCatching { socket.close() }
                failures += error
                if (cancellation.isCancelled) throw java.io.InterruptedIOException("连接已取消")
            }
        }
        val result = failures.lastOrNull() ?: java.net.SocketTimeoutException("连接服务器超时")
        failures.dropLast(1).forEach(result::addSuppressed)
        throw result
    }
}
