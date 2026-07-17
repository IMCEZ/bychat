package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

class FrameReader(
    input: InputStream,
    private val limits: FrameLimits = FrameLimits()
) {
    private val stream = DataInputStream(input)

    fun read(): ByteArray? {
        val first = stream.read()
        if (first == -1) return null
        val lengthBytes = ByteArray(4)
        lengthBytes[0] = first.toByte()
        try {
            stream.readFully(lengthBytes, 1, 3)
        } catch (error: EOFException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "数据帧长度头被截断", error)
        }
        val length = ((lengthBytes[0].toInt() and 0xff) shl 24) or
            ((lengthBytes[1].toInt() and 0xff) shl 16) or
            ((lengthBytes[2].toInt() and 0xff) shl 8) or
            (lengthBytes[3].toInt() and 0xff)
        if (length <= 0) throw ProtocolException(ErrorCode.BAD_REQUEST, "数据帧长度必须大于0")
        if (length > limits.maxFrameBytes) {
            throw ProtocolException(ErrorCode.PAYLOAD_TOO_LARGE, "数据帧超过${limits.maxFrameBytes}字节")
        }
        val payload = ByteArray(length)
        try {
            stream.readFully(payload)
        } catch (error: EOFException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "数据帧被截断", error)
        }
        return payload
    }
}
