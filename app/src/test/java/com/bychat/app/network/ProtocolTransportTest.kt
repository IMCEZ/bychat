package com.bychat.app.network

import com.bychat.app.protocol.Envelope
import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.IdGen
import com.bychat.app.protocol.PingPayload
import com.bychat.app.protocol.PacketType
import com.bychat.app.protocol.ProtocolCodec
import com.bychat.app.protocol.ProtocolException
import com.bychat.app.protocol.PROTOCOL_VERSION
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class ProtocolTransportTest {
    @Test fun envelopeRoundTripPreservesFields() {
        val payload = JsonParser.parseString("{\"nonce\":\"n-1\"}")
        val source = Envelope(PROTOCOL_VERSION, PacketType.PING, IdGen.newRequestId(), 123L, payload)
        val bytes = ByteArrayOutputStream().also { ProtocolTransport(ByteArrayInputStream(byteArrayOf()), it).send(source) }.toByteArray()
        val decoded = ProtocolTransport(ByteArrayInputStream(bytes), ByteArrayOutputStream()).receive()!!
        assertEquals(source, decoded)
    }

    @Test fun cleanEofReturnsNull() {
        assertNull(ProtocolTransport(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream()).receive())
    }

    @Test fun rejectsMalformedUtf8() = assertError(ErrorCode.BAD_REQUEST, framed(byteArrayOf(0xC3.toByte(), 0x28)))

    @Test fun rejectsInvalidJson() = assertError(ErrorCode.BAD_REQUEST, framed("{invalid".toByteArray()))

    @Test fun rejectsOversizedFrame() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(3) }.toByteArray()
        try {
            ProtocolTransport(ByteArrayInputStream(bytes), ByteArrayOutputStream(), FrameLimits(2)).receive()
            throw AssertionError("应拒绝超限帧")
        } catch (error: ProtocolException) {
            assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, error.code)
        }
    }

    private fun assertError(code: ErrorCode, bytes: ByteArray) {
        try {
            ProtocolTransport(ByteArrayInputStream(bytes), ByteArrayOutputStream()).receive()
            throw AssertionError("应抛出ProtocolException")
        } catch (error: ProtocolException) {
            assertEquals(code, error.code)
        }
    }

    private fun framed(payload: ByteArray): ByteArray = ByteArrayOutputStream().also {
        DataOutputStream(it).apply { writeInt(payload.size); write(payload) }
    }.toByteArray()
}
