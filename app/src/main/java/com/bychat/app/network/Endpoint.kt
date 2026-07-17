package com.bychat.app.network

import com.bychat.app.protocol.ErrorCode
import com.bychat.app.protocol.ProtocolException
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

private const val MAX_HOST_LENGTH = 253

data class Endpoint(
    val displayHost: String,
    val connectHost: String,
    val port: Int,
    val secure: Boolean
) {
    init {
        require(displayHost.isNotBlank()) { "显示主机不能为空" }
        require(connectHost.isNotBlank()) { "连接主机不能为空" }
        require(port in 1..65535) { "端口必须在1到65535之间" }
    }

    val host: String get() = displayHost

    override fun toString(): String {
        val formatted = if (displayHost.contains(':')) "[$displayHost]" else displayHost
        return "$formatted:$port"
    }
}

object EndpointParser {
    fun parse(input: String, defaultPort: Int, secure: Boolean = false): Endpoint {
        if (defaultPort !in 1..65535) bad("默认端口无效")
        if (input.any { it.code < 32 || it.code == 127 }) bad("服务器地址含有控制字符")
        val value = input.trim()
        if (value.isEmpty()) bad("服务器地址不能为空")
        if (value.contains("://") || value.any { it in "/?#@" }) bad("请输入服务器地址而不是网址")

        val parsed = if (value.startsWith('[')) parseBracketedIpv6(value, defaultPort) else parseHost(value, defaultPort)
        val displayHost = parsed.first.removeSuffix(".")
        if (displayHost.isEmpty() || displayHost.length > MAX_HOST_LENGTH) bad("服务器主机名长度无效")
        val connectHost = normalizeHost(displayHost)
        return Endpoint(displayHost, connectHost, parsed.second, secure)
    }

    private fun parseBracketedIpv6(value: String, defaultPort: Int): Pair<String, Int> {
        val closing = value.indexOf(']')
        if (closing <= 1) bad("IPv6地址格式错误")
        val host = value.substring(1, closing)
        val suffix = value.substring(closing + 1)
        val port = when {
            suffix.isEmpty() -> defaultPort
            suffix.startsWith(':') && suffix.length > 1 -> parsePort(suffix.substring(1))
            else -> bad("IPv6地址端口格式错误")
        }
        if (!isIpv6Literal(host)) bad("方括号内必须是IPv6地址")
        return host to port
    }

    private fun parseHost(value: String, defaultPort: Int): Pair<String, Int> {
        val colonCount = value.count { it == ':' }
        if (colonCount > 1) {
            if (!isIpv6Literal(value)) bad("IPv6地址格式错误")
            return value to defaultPort
        }
        if (colonCount == 1) {
            val separator = value.lastIndexOf(':')
            val host = value.substring(0, separator)
            if (host.isEmpty()) bad("服务器主机不能为空")
            return host to parsePort(value.substring(separator + 1))
        }
        return value to defaultPort
    }

    private fun parsePort(value: String): Int {
        if (value.isEmpty() || !value.all(Char::isDigit)) bad("端口格式错误")
        return value.toIntOrNull()?.takeIf { it in 1..65535 } ?: bad("端口必须在1到65535之间")
    }

    private fun normalizeHost(host: String): String {
        if (host.contains(':')) return host.lowercase()
        return try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase().also {
                if (it.isEmpty() || it.length > MAX_HOST_LENGTH) bad("服务器主机名无效")
                if (it.split('.').any { label -> label.isEmpty() || label.length > 63 }) bad("服务器主机名无效")
            }
        } catch (error: IllegalArgumentException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "服务器主机名无效", error)
        }
    }

    private fun isIpv6Literal(host: String): Boolean {
        if (!host.contains(':') || host.contains('%')) return false
        return try {
            InetAddress.getByName(host) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    private fun bad(message: String): Nothing = throw ProtocolException(ErrorCode.BAD_REQUEST, message)
}
