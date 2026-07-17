package com.bychat.app.network

import com.bychat.app.protocol.Envelope
import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolCodec
import com.bychat.app.protocol.ProtocolException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class ProtocolTransport(
    input: InputStream,
    output: OutputStream,
    limits: FrameLimits = FrameLimits()
) {
    private val reader = FrameReader(input, limits)
    private val writer = FrameWriter(output, limits)

    fun send(envelope: Envelope) {
        val json = ProtocolCodec.encode(envelope.type, envelope.id, envelope.ts, envelope.payload, envelope.error)
        writer.write(json.toByteArray(StandardCharsets.UTF_8))
    }

    fun receive(): Envelope? {
        val bytes = reader.read() ?: return null
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: CharacterCodingException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "数据帧不是有效UTF-8", error)
        }
        return ProtocolCodec.decode(text)
    }
}
