package com.bychat.app.protocol

enum class ErrorCode(
    val code: Int,
    val wireName: String,
    val retryable: Boolean,
    val userMessage: String
) {
    OK(0, "ok", false, "操作成功"),
    BAD_REQUEST(1000, "bad_request", false, "请求格式错误"),
    UNSUPPORTED_PROTOCOL(1001, "unsupported_protocol", false, "协议版本不受支持"),
    UNAUTHORIZED(1002, "unauthorized", false, "身份验证失败"),
    FORBIDDEN(1003, "forbidden", false, "没有执行此操作的权限"),
    NOT_FOUND(1004, "not_found", false, "请求的内容不存在"),
    RATE_LIMITED(1005, "rate_limited", true, "操作过于频繁，请稍后重试"),
    PAYLOAD_TOO_LARGE(1006, "payload_too_large", false, "发送的内容过大"),
    INTERNAL(1007, "internal", true, "服务器内部错误，请稍后重试"),
    CONFLICT(1008, "conflict", false, "当前状态与请求冲突")
}

data class ProtocolErrorPayload(
    val code: Int,
    val name: String,
    val message: String,
    val retryable: Boolean
) {
    companion object {
        fun from(code: ErrorCode, message: String = code.userMessage) = ProtocolErrorPayload(
            code = code.code,
            name = code.wireName,
            message = message,
            retryable = code.retryable
        )
    }
}

class ProtocolException(
    val code: ErrorCode,
    message: String = code.userMessage,
    cause: Throwable? = null
) : Exception(message, cause)
