package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTest {
    @Test fun parsesDomainWithDefaultPort() {
        assertEquals(Endpoint("example.com", "example.com", 18888, false), EndpointParser.parse("example.com", 18888))
    }

    @Test fun parsesDomainWithPort() {
        assertEquals(443, EndpointParser.parse("chat.example.com:443", 18888, true).port)
    }

    @Test fun normalizesUnicodeDomain() {
        assertEquals("xn--fsqu00a.xn--0zwm56d", EndpointParser.parse("例子.测试", 18888).connectHost)
    }

    @Test fun parsesIpv4() {
        assertEquals("192.168.1.8", EndpointParser.parse("192.168.1.8:9000", 18888).connectHost)
    }

    @Test fun parsesBracketedIpv6WithPort() {
        val endpoint = EndpointParser.parse("[2001:db8::1]:9443", 18888)
        assertEquals("2001:db8::1", endpoint.connectHost)
        assertEquals(9443, endpoint.port)
        assertEquals("[2001:db8::1]:9443", endpoint.toString())
    }

    @Test fun parsesBareIpv6WithDefaultPort() {
        assertEquals(18888, EndpointParser.parse("2001:db8::2", 18888).port)
    }

    @Test fun rejectsUrl() = assertBad("https://example.com")
    @Test fun rejectsUserInfo() = assertBad("user@example.com")
    @Test fun rejectsPath() = assertBad("example.com/chat")
    @Test fun rejectsZeroPort() = assertBad("example.com:0")
    @Test fun rejectsLargePort() = assertBad("example.com:65536")
    @Test fun rejectsEmptyPort() = assertBad("example.com:")
    @Test fun rejectsControlCharacter() = assertBad("example.com\n")
    @Test fun rejectsBracketedDomain() = assertBad("[example.com]:443")

    private fun assertBad(value: String) {
        try {
            EndpointParser.parse(value, 18888)
            throw AssertionError("应拒绝：$value")
        } catch (error: ProtocolException) {
            assertEquals(ErrorCode.BAD_REQUEST, error.code)
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }
}
