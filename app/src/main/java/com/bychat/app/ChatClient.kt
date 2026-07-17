package com.bychat.app

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ChatClient(
    private val onPacket: (Packet) -> Unit,
    private val onClosed: (String) -> Unit
) {
    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val connected = AtomicBoolean(false)
    private val closedNotified = AtomicBoolean(false)
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    fun connect(host: String, port: Int, username: String, credential: String, room: String, roomPassword: String) {
        executor.execute {
            try {
                val socket = Socket().also { it.connect(InetSocketAddress(host, port), 10_000); it.tcpNoDelay = true }
                this.socket = socket
                writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                connected.set(true)
                send(Packet("login", username, credential, room, roomPassword))
                BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).use { reader ->
                    while (connected.get()) {
                        val line = reader.readLine() ?: break
                        val packet = gson.fromJson(line, Packet::class.java)
                        main.post { onPacket(packet) }
                    }
                }
                closeWith("连接已关闭")
            } catch (e: Exception) {
                closeWith(e.message ?: "无法连接服务器")
            }
        }
    }

    @Synchronized fun send(packet: Packet) {
        if (!connected.get()) return
        try {
            writer?.apply { write(gson.toJson(packet)); newLine(); flush() }
        } catch (e: Exception) {
            closeWith(e.message ?: "发送失败")
        }
    }

    private fun closeWith(reason: String) {
        connected.set(false)
        runCatching { socket?.close() }
        if (closedNotified.compareAndSet(false, true)) main.post { onClosed(reason) }
    }

    fun close() {
        closedNotified.set(true)
        connected.set(false)
        runCatching { socket?.close() }
        executor.shutdownNow()
    }
}
