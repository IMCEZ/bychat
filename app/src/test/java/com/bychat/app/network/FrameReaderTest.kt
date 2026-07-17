package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FrameReaderTest {
    @Test fun cleanEofReturnsNull() = assertNull(FrameReader(ByteArrayInputStream(byteArrayOf())).read())

    @Test fun readsConsecutiveFrames() {
        val output = ByteArrayOutputStream()
        FrameWriter(output).apply { write(byteArrayOf(1)); write(byteArrayOf(2, 3)) }
        val reader = FrameReader(ByteArrayInputStream(output.toByteArray()))
        assertArrayEquals(byteArrayOf(1), reader.read())
        assertArrayEquals(byteArrayOf(2, 3), reader.read())
        assertNull(reader.read())
    }

    @Test fun rejectsZeroLength() = assertLengthError(0, ErrorCode.BAD_REQUEST)
    @Test fun rejectsNegativeLength() = assertLengthError(-1, ErrorCode.BAD_REQUEST)
    @Test fun rejectsOversizedLength() = assertLengthError(3, ErrorCode.PAYLOAD_TOO_LARGE, FrameLimits(2))

    @Test fun rejectsTruncatedPayload() {
        val bytes = encoded(3, byteArrayOf(1))
        assertError(ErrorCode.BAD_REQUEST) { FrameReader(ByteArrayInputStream(bytes)).read() }
    }

    @Test fun partialLengthIsRejected() {
        assertError(ErrorCode.BAD_REQUEST) { FrameReader(ByteArrayInputStream(byteArrayOf(0, 0))).read() }
    }

    private fun assertLengthError(length: Int, code: ErrorCode, limits: FrameLimits = FrameLimits()) {
        assertError(code) { FrameReader(ByteArrayInputStream(encoded(length)), limits).read() }
    }

    private fun encoded(length: Int, payload: ByteArray = byteArrayOf()): ByteArray = ByteArrayOutputStream().also {
        DataOutputStream(it).apply { writeInt(length); write(payload) }
    }.toByteArray()

    private fun assertError(code: ErrorCode, action: () -> Unit) {
        try { action(); throw AssertionError("应抛出ProtocolException") }
        catch (error: ProtocolException) { assertEquals(code, error.code) }
    }
}
