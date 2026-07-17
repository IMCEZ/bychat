package com.bychat.app.network

import com.bychat.app.protocol.AuthChallengePayload
import com.bychat.app.protocol.AuthResultPayload
import com.bychat.app.protocol.Envelope
import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.HelloAckPayload
import com.bychat.app.protocol.HelloPayload
import com.bychat.app.protocol.IdGen
import com.bychat.app.protocol.Outcome
import com.bychat.app.protocol.PROTOCOL_VERSION
import com.bychat.app.protocol.PacketType
import com.bychat.app.protocol.PingPayload
import com.bychat.app.protocol.PongPayload
import com.bychat.app.protocol.ProtocolCodec
import com.bychat.app.protocol.ProtocolErrorPayload
import com.bychat.app.protocol.ProtocolException
import com.bychat.app.protocol.ReadyPayload
import com.bychat.app.protocol.ResumeRequestPayload
import com.bychat.app.protocol.ResumeResultPayload
import com.bychat.app.protocol.capabilitiesToWire
import com.bychat.app.protocol.nowEpochMillis
import com.google.gson.Gson
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class V2ClientSession(
    private val listener: SessionListener,
    private val connector: SocketConnector = DefaultSocketConnector(),
    private val scheduler: TaskScheduler = ExecutorTaskScheduler(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val connectTimeoutMillis: Int = 10_000,
    private val handshakeTimeoutMillis: Int = 10_000,
    private val authTimeoutMillis: Int = 15_000,
    private val heartbeatIntervalMillis: Long = 25_000,
    private val heartbeatTimeoutMillis: Long = 10_000,
    private val idleTimeoutMillis: Long = 75_000
) : ClientSession {
    private val gson = Gson()
    private val machine = SessionStateMachine()
    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "bychat-v2-session").apply { isDaemon = true } }
    private val generation = AtomicLong()
    private val closed = AtomicBoolean(false)
    private val closedNotified = AtomicBoolean(false)
    private val writes = ArrayBlockingQueue<Envelope>(128)
    private val inputLimiter = TokenBucket(clock = clock)
    private val taskLock = Any()
    @Volatile private var request: ConnectionRequest? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var cancellation: CancellationSignal? = null
    @Volatile private var reconnectAttempt = 0
    @Volatile private var lastReceivedAt = 0L
    @Volatile private var pendingPing: String? = null
    private var reconnectTask: ScheduledTask? = null
    private var heartbeatTask: ScheduledTask? = null
    private var heartbeatTimeoutTask: ScheduledTask? = null

    override val state: SessionState get() = machine.state

    override fun connect(request: ConnectionRequest): Outcome<Unit> {
        if (closed.get()) return Outcome.Failure(ErrorCode.CONFLICT, "会话已关闭")
        if (state !is SessionState.Idle) return Outcome.Failure(ErrorCode.CONFLICT, "会话已经启动")
        this.request = request
        val currentGeneration = generation.incrementAndGet()
        return try {
            io.execute { runConnection(request, currentGeneration) }
            Outcome.Success(Unit)
        } catch (_: RejectedExecutionException) {
            Outcome.Failure(ErrorCode.CONFLICT, "会话已关闭")
        }
    }

    override fun send(envelope: Envelope): Outcome<Unit> {
        if (state !is SessionState.Ready || closed.get()) return Outcome.Failure(ErrorCode.CONFLICT, "会话尚未就绪")
        return if (writes.offer(envelope)) Outcome.Success(Unit)
        else Outcome.Failure(ErrorCode.RATE_LIMITED, "待发送消息过多，请稍后重试")
    }

    private fun runConnection(request: ConnectionRequest, currentGeneration: Long) {
        if (!isCurrent(currentGeneration)) return
        val signal = CancellationSignal()
        cancellation = signal
        try {
            move(SessionState.Resolving(request.endpoint))
            signal.throwIfCancelled()
            move(SessionState.Connecting(request.endpoint))
            val connected = connector.connect(request.endpoint, connectTimeoutMillis, signal)
            if (!isCurrent(currentGeneration)) { connected.close(); return }
            socket = connected
            connected.soTimeout = handshakeTimeoutMillis
            move(SessionState.Handshaking(request.endpoint))
            val transport = ProtocolTransport(connected.getInputStream(), connected.getOutputStream())
            sendInternal(transport, PacketType.HELLO, HelloPayload(request.clientVersion, listOf(PROTOCOL_VERSION), capabilitiesToWire(request.capabilities), true))
            val hello = expect(transport, PacketType.HELLO_ACK, currentGeneration)
            val helloAck = ProtocolCodec.decodePayload(hello, HelloAckPayload::class.java)
            if (helloAck.serverProtocol != PROTOCOL_VERSION) throw ProtocolException(ErrorCode.UNSUPPORTED_PROTOCOL)
            connected.soTimeout = authTimeoutMillis
            move(SessionState.Authenticating(request.endpoint))
            if (!tryResume(transport, request, currentGeneration)) authenticate(transport, request, currentGeneration)
            val readyEnvelope = expect(transport, PacketType.READY, currentGeneration)
            val ready = ProtocolCodec.decodePayload(readyEnvelope, ReadyPayload::class.java)
            require(ready.communityId == request.communityId) { "服务器返回了错误社区" }
            connected.soTimeout = 1_000
            reconnectAttempt = 0
            lastReceivedAt = clock.nowMillis()
            move(SessionState.Ready(ready.sessionId, ready.communityId, ready.syncCursor))
            startHeartbeat(currentGeneration)
            readLoop(transport, currentGeneration)
        } catch (error: Throwable) {
            if (!isCurrent(currentGeneration) || closed.get()) return
            handleFailure(error)
        } finally {
            if (generation.get() == currentGeneration) {
                cancelHeartbeat()
                runCatching { socket?.close() }
                socket = null
                cancellation = null
            }
        }
    }

    private fun tryResume(transport: ProtocolTransport, request: ConnectionRequest, currentGeneration: Long): Boolean {
        val saved = request.resumeStore.load(request.endpoint, request.communityId) ?: return false
        if (saved.expiresAt <= nowEpochMillis() || saved.communityId != request.communityId) {
            request.resumeStore.clear(request.endpoint, request.communityId)
            return false
        }
        sendInternal(transport, PacketType.RESUME_REQUEST, ResumeRequestPayload(saved.sessionToken, saved.communityId, saved.lastSyncCursor))
        val response = expect(transport, PacketType.RESUME_RESULT, currentGeneration)
        val result = ProtocolCodec.decodePayload(response, ResumeResultPayload::class.java)
        if (!result.resumed) {
            request.resumeStore.clear(request.endpoint, request.communityId)
            return false
        }
        if (!result.token.isNullOrBlank() && result.expiresAt != null) {
            request.resumeStore.save(request.endpoint, ResumeState(result.token, request.communityId, result.syncFromCursor ?: saved.lastSyncCursor, result.expiresAt))
        }
        return true
    }

    private fun authenticate(transport: ProtocolTransport, request: ConnectionRequest, currentGeneration: Long) {
        val challengeEnvelope = expect(transport, PacketType.AUTH_CHALLENGE, currentGeneration)
        val challenge = ProtocolCodec.decodePayload(challengeEnvelope, AuthChallengePayload::class.java)
        val auth = when (val outcome = request.authProvider.createAuthRequest(challenge)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> throw ProtocolException(outcome.code, outcome.message)
        }
        sendInternal(transport, PacketType.AUTH_REQUEST, auth)
        val resultEnvelope = expect(transport, PacketType.AUTH_RESULT, currentGeneration)
        if (resultEnvelope.error != null) throw ProtocolException(ErrorCode.UNAUTHORIZED, resultEnvelope.error.message)
        val result = ProtocolCodec.decodePayload(resultEnvelope, AuthResultPayload::class.java)
        if (!result.authenticated) throw ProtocolException(ErrorCode.UNAUTHORIZED)
        if (!result.sessionToken.isNullOrBlank() && result.expiresAt != null) {
            request.resumeStore.save(request.endpoint, ResumeState(result.sessionToken, request.communityId, 0, result.expiresAt))
        }
    }

    private fun readLoop(transport: ProtocolTransport, currentGeneration: Long) {
        while (isCurrent(currentGeneration) && !closed.get()) {
            drainWrites(transport)
            try {
                val envelope = transport.receive() ?: throw IOException("服务器关闭了连接")
                lastReceivedAt = clock.nowMillis()
                if (!inputLimiter.tryConsume()) {
                    runCatching { sendInternal(transport, PacketType.ERROR, emptyMap<String, String>(), ProtocolErrorPayload.from(ErrorCode.RATE_LIMITED)) }
                    throw ProtocolException(ErrorCode.RATE_LIMITED)
                }
                when (envelope.type) {
                    PacketType.PING -> {
                        val ping = ProtocolCodec.decodePayload(envelope, PingPayload::class.java)
                        sendInternal(transport, PacketType.PONG, PongPayload(ping.nonce))
                    }
                    PacketType.PONG -> {
                        val pong = ProtocolCodec.decodePayload(envelope, PongPayload::class.java)
                        if (pong.nonce == pendingPing) { pendingPing = null; cancelHeartbeatTimeout() }
                    }
                    else -> safe { listener.onEnvelope(envelope) }
                }
            } catch (_: SocketTimeoutException) {
                if (clock.nowMillis() - lastReceivedAt >= idleTimeoutMillis) throw IOException("连接长时间无响应")
            }
        }
    }

    private fun drainWrites(transport: ProtocolTransport) {
        while (true) transport.send(writes.poll() ?: return)
    }

    private fun expect(transport: ProtocolTransport, expected: String, currentGeneration: Long): Envelope {
        if (!isCurrent(currentGeneration)) throw IOException("连接已失效")
        val envelope = transport.receive() ?: throw IOException("握手期间连接已关闭")
        if (envelope.error != null) throw ProtocolException(errorCode(envelope.error), envelope.error.message)
        if (envelope.type != expected) throw ProtocolException(ErrorCode.BAD_REQUEST, "期望$expected，收到${envelope.type}")
        return envelope
    }

    private fun sendInternal(transport: ProtocolTransport, type: String, payload: Any, error: ProtocolErrorPayload? = null) {
        val envelope = Envelope(PROTOCOL_VERSION, type, IdGen.newRequestId(), nowEpochMillis(), gson.toJsonTree(payload), error)
        transport.send(envelope)
    }

    private fun startHeartbeat(currentGeneration: Long) {
        synchronized(taskLock) {
            heartbeatTask?.cancel()
            heartbeatTask = scheduler.schedule(heartbeatIntervalMillis) {
                if (!isCurrent(currentGeneration) || state !is SessionState.Ready || closed.get()) return@schedule
                io.execute {
                    val active = socket ?: return@execute
                    try {
                        val nonce = IdGen.newRequestId()
                        pendingPing = nonce
                        ProtocolTransport(active.getInputStream(), active.getOutputStream()).let { sendInternal(it, PacketType.PING, PingPayload(nonce)) }
                        scheduleHeartbeatTimeout(currentGeneration, nonce)
                        startHeartbeat(currentGeneration)
                    } catch (error: Throwable) { if (isCurrent(currentGeneration)) handleFailure(error) }
                }
            }
        }
    }

    private fun scheduleHeartbeatTimeout(currentGeneration: Long, nonce: String) {
        synchronized(taskLock) {
            heartbeatTimeoutTask?.cancel()
            heartbeatTimeoutTask = scheduler.schedule(heartbeatTimeoutMillis) {
                if (isCurrent(currentGeneration) && pendingPing == nonce && !closed.get()) {
                    cancellation?.cancel()
                    runCatching { socket?.close() }
                }
            }
        }
    }

    private fun handleFailure(error: Throwable) {
        val reason = when (error) {
            is ProtocolException -> if (error.code == ErrorCode.UNAUTHORIZED || error.code == ErrorCode.FORBIDDEN) CloseReason.AUTH_FAILED else CloseReason.PROTOCOL_ERROR
            else -> CloseReason.NETWORK_ERROR
        }
        val fatal = reason == CloseReason.AUTH_FAILED || reason == CloseReason.PROTOCOL_ERROR
        if (fatal) finish(reason, publicMessage(error, reason)) else scheduleReconnect(publicMessage(error, reason))
    }

    private fun scheduleReconnect(message: String) {
        val currentRequest = request ?: return finish(CloseReason.NETWORK_ERROR, message)
        val attempt = reconnectAttempt + 1
        if (attempt > reconnectPolicy.maxAttempts) return finish(CloseReason.RETRY_EXHAUSTED, "多次重连失败")
        reconnectAttempt = attempt
        val delay = reconnectPolicy.delayMillis(attempt)
        move(SessionState.Reconnecting(attempt, delay))
        val nextGeneration = generation.incrementAndGet()
        synchronized(taskLock) {
            reconnectTask?.cancel()
            reconnectTask = scheduler.schedule(delay) {
                if (isCurrent(nextGeneration) && !closed.get()) io.execute { runConnection(currentRequest, nextGeneration) }
            }
        }
    }

    private fun move(next: SessionState): Boolean {
        val changed = machine.transition(next)
        if (changed is Outcome.Success) {
            safe { listener.onStateChanged(changed.value.first, changed.value.second) }
            return true
        }
        return false
    }

    private fun finish(reason: CloseReason, message: String) {
        if (state !is SessionState.Closed) {
            if (state !is SessionState.Closing) move(SessionState.Closing)
            move(SessionState.Closed(reason, message))
        }
        if (closedNotified.compareAndSet(false, true)) safe { listener.onClosed(reason, message) }
    }

    private fun cancelHeartbeatTimeout() = synchronized(taskLock) { heartbeatTimeoutTask?.cancel(); heartbeatTimeoutTask = null }
    private fun cancelHeartbeat() = synchronized(taskLock) { heartbeatTask?.cancel(); heartbeatTask = null; heartbeatTimeoutTask?.cancel(); heartbeatTimeoutTask = null; pendingPing = null }
    private fun isCurrent(value: Long) = generation.get() == value
    private fun safe(action: () -> Unit) { runCatching(action) }
    private fun errorCode(error: ProtocolErrorPayload) = ErrorCode.entries.firstOrNull { it.code == error.code } ?: ErrorCode.INTERNAL
    private fun publicMessage(error: Throwable, reason: CloseReason) = when (reason) {
        CloseReason.AUTH_FAILED -> "身份验证失败"
        CloseReason.PROTOCOL_ERROR -> error.message ?: "服务器协议错误"
        else -> "网络连接失败"
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        cancellation?.cancel()
        runCatching { socket?.close() }
        writes.clear()
        synchronized(taskLock) { reconnectTask?.cancel(); reconnectTask = null; cancelHeartbeat() }
        finish(CloseReason.USER_REQUEST, "用户已关闭连接")
        scheduler.close()
        io.shutdownNow()
    }
}
