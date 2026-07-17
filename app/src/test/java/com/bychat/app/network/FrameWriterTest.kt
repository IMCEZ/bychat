package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class FrameWriterTest {
    @Test fun writesBigEndianLengthAndPayload() {
        val output = ByteArrayOutputStream()
        FrameWriter(output).write(byteArrayOf(1, 2, 3))
        val input = DataInputStream(ByteArrayInputStream(output.toByteArray()))
        assertEquals(3, input.readInt())
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArray(3).also(input::readFully))
    }

    @Test fun rejectsEmptyBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertError(ErrorCode.BAD_REQUEST) { FrameWriter(output).write(byteArrayOf()) }
        assertEquals(0, output.size())
    }

    @Test fun rejectsOversizedBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertError(ErrorCode.PAYLOAD_TOO_LARGE) { FrameWriter(output, FrameLimits(2)).write(byteArrayOf(1, 2, 3)) }
        assertEquals(0, output.size())
    }

    @Test fun concurrentWritesNeverInterleave() {
        val output = ByteArrayOutputStream()
        val writer = FrameWriter(output)
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        repeat(40) { index -> executor.execute { start.await(); writer.write(ByteArray(128) { index.toByte() }) } }
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
        val reader = FrameReader(ByteArrayInputStream(output.toByteArray()))
        val frames = generateSequence(reader::read).toList()
        assertEquals(40, frames.size)
        frames.forEach { frame -> assertTrue(frame.all { it == frame[0] }) }
    }

    private fun assertError(code: ErrorCode, action: () -> Unit) {
        try { action(); throw AssertionError("应抛出ProtocolException") }
        catch (error: ProtocolException) { assertEquals(code, error.code) }
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
