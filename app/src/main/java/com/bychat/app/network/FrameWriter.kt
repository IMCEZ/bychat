package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import java.io.DataOutputStream
import java.io.OutputStream

class FrameWriter(
    output: OutputStream,
    private val limits: FrameLimits = FrameLimits()
) {
    private val stream = DataOutputStream(output)
    private val lock = Any()

    fun write(payload: ByteArray) {
        if (payload.isEmpty()) throw ProtocolException(ErrorCode.BAD_REQUEST, "数据帧不能为空")
        if (payload.size > limits.maxFrameBytes) {
            throw ProtocolException(ErrorCode.PAYLOAD_TOO_LARGE, "数据帧超过${limits.maxFrameBytes}字节")
        }
        synchronized(lock) {
            stream.writeInt(payload.size)
            stream.write(payload)
            stream.flush()
        }
    }
}
