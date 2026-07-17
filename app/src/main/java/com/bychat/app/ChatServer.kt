package com.bychat.app

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ChatServer(
    private val db: LocalDb,
    private val port: Int,
    private val owner: String,
    private val ownerCredential: String,
    private val room: String,
    private val roomPassword: String
) {
    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private val clients = Collections.synchronizedSet(mutableSetOf<Client>())
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            db.provisionRemote(owner, ownerCredential)
            server = ServerSocket(port).apply { reuseAddress = true }
        } catch (e: Exception) {
            running.set(false)
            pool.shutdownNow()
            throw e
        }
        pool.execute {
            while (running.get()) try { accept(server!!.accept()) } catch (_: Exception) { if (!running.get()) break }
        }
    }

    private fun accept(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = 15_000
        val client = Client(socket)
        pool.execute {
            try {
                val login = gson.fromJson(client.reader.readLine() ?: error("连接已关闭"), Packet::class.java)
                require(login.action == "login") { "协议错误" }
                val username = login.username?.trim().orEmpty()
                require(username.length in 2..24) { "用户名无效" }
                require(login.room == room) { "房间不存在" }
                require(roomPassword.isEmpty() || login.roomPassword == roomPassword) { "房间密码错误" }
                db.authenticateRemote(username, login.credential.orEmpty())?.let { error(it) }
                synchronized(clients) { require(clients.none { it.username == username }) { "该用户已经在线" } }
                client.username = username
                socket.soTimeout = 0
                clients += client
                client.send(Packet("ready", data = owner))
                db.history(room).forEach { client.send(Packet("message", message = it)) }
                broadcast(system("$username 加入了房间"))
                while (running.get()) {
                    val line = client.reader.readLine() ?: break
                    handle(client, gson.fromJson(line, Packet::class.java))
                }
            } catch (e: Exception) {
                client.send(Packet("error", error = e.message ?: "连接失败"))
            } finally {
                clients -= client
                client.close()
                if (client.username.isNotEmpty()) broadcast(system("${client.username} 离开了房间"))
            }
        }
    }

    private fun handle(client: Client, packet: Packet) {
        when (packet.action) {
            "message" -> {
                if (db.isMuted(client.username)) { client.send(Packet("error", error = "你已被禁言")); return }
                val input = packet.message ?: return
                if (input.type !in setOf("text", "audio") || input.content.isBlank() || input.content.length > 2_800_000) return
                val message = Message(UUID.randomUUID().toString(), room, client.username, input.type, input.content, System.currentTimeMillis())
                db.save(message); broadcast(message)
            }
            "admin" -> {
                if (client.username != owner) { client.send(Packet("error", error = "没有管理权限")); return }
                admin(client, packet.target.orEmpty(), packet.data.orEmpty())
            }
            "ping" -> client.send(Packet("pong"))
        }
    }

    private fun admin(admin: Client, target: String, command: String) {
        if (target == owner && command in setOf("kick", "mute", "ban")) {
            admin.send(Packet("notice", data = "不能对房主执行此操作"))
            return
        }
        val ok = when (command) {
            "kick" -> { clients.firstOrNull { it.username == target }?.close() != null }
            "mute" -> db.setUserFlag(target, "muted", true)
            "unmute" -> db.setUserFlag(target, "muted", false)
            "ban" -> { val changed = db.setUserFlag(target, "banned", true); clients.firstOrNull { it.username == target }?.close(); changed }
            "unban" -> db.setUserFlag(target, "banned", false)
            "clear" -> { db.clearRoom(room); broadcast(system("房主清空了聊天记录")); true }
            else -> false
        }
        admin.send(Packet("notice", data = if (ok) "操作成功" else "操作失败：用户不存在或命令无效"))
    }

    private fun system(text: String) = Message(UUID.randomUUID().toString(), room, "系统", "system", text, System.currentTimeMillis())
    private fun broadcast(message: Message) { db.save(message); synchronized(clients) { clients.toList().forEach { it.send(Packet("message", message = message)) } } }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
        synchronized(clients) { clients.toList().forEach(Client::close); clients.clear() }
        pool.shutdownNow()
    }

    private inner class Client(private val socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        var username = ""
        @Synchronized fun send(packet: Packet) { runCatching { writer.write(gson.toJson(packet)); writer.newLine(); writer.flush() } }
        fun close() = runCatching { socket.close() }.let { Unit }
    }
}
