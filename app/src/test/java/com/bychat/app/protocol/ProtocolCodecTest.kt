package com.bychat.app.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun helloRoundTripPreservesValues() {
        val payload = HelloPayload("1.0.0", listOf(2), listOf("voice_message"))
        val json = ProtocolCodec.encode(PacketType.HELLO, "r-1", 100L, payload)
        val envelope = ProtocolCodec.decode(json)
        val decoded = ProtocolCodec.decodePayload(envelope, HelloPayload::class.java)

        assertFalse(json.contains('\n'))
        assertEquals(PROTOCOL_VERSION, envelope.v)
        assertEquals(PacketType.HELLO, envelope.type)
        assertEquals(payload, decoded)
    }

    @Test
    fun pingRoundTripPreservesNonce() {
        val json = ProtocolCodec.encode(PacketType.PING, "r-2", 200L, PingPayload("nonce-1"))
        val decoded = ProtocolCodec.decodePayload(ProtocolCodec.decode(json), PingPayload::class.java)
        assertEquals("nonce-1", decoded.nonce)
    }

    @Test
    fun unknownCapabilityIsIgnored() {
        val decoded = wireToCapabilities(listOf("voice_message", "future_capability"))
        assertEquals(setOf(ServerCapability.VOICE_MESSAGE), decoded)
    }

    @Test
    fun unsupportedVersionReturnsStableError() {
        val error = expectProtocolException("""{"v":1,"type":"ping","id":"r-3","ts":1,"payload":{"nonce":"x"}}""")
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, error.code)
    }

    @Test
    fun invalidJsonReturnsBadRequest() {
        val error = expectProtocolException("{invalid")
        assertEquals(ErrorCode.BAD_REQUEST, error.code)
    }

    @Test
    fun unknownPacketTypeReturnsBadRequest() {
        val error = expectProtocolException("""{"v":2,"type":"unknown","id":"r-4","ts":1,"payload":{}}""")
        assertEquals(ErrorCode.BAD_REQUEST, error.code)
    }

    @Test
    fun generatedIdsHaveDistinctStablePrefixes() {
        val request = IdGen.newRequestId()
        val event = IdGen.newEventId()
        val client = IdGen.newMessageClientId()
        assertTrue(request.startsWith("r-"))
        assertTrue(event.startsWith("e-"))
        assertTrue(client.startsWith("c-"))
        assertEquals(3, setOf(request, event, client).size)
    }

    private fun expectProtocolException(input: String): ProtocolException = try {
        ProtocolCodec.decode(input)
        fail("应抛出ProtocolException")
        throw AssertionError("unreachable")
    } catch (error: ProtocolException) {
        error
    }
}
