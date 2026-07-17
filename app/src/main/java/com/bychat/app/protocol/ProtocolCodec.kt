package com.bychat.app.protocol

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

object ProtocolCodec {
    private val gson = Gson()

    fun encode(
        type: String,
        id: String,
        ts: Long,
        payload: Any,
        error: ProtocolErrorPayload? = null
    ): String {
        require(type.isNotBlank()) { "数据包类型不能为空" }
        require(id.isNotBlank()) { "数据包ID不能为空" }
        return gson.toJson(
            Envelope(
                v = PROTOCOL_VERSION,
                type = type,
                id = id,
                ts = ts,
                payload = gson.toJsonTree(payload),
                error = error
            )
        ).replace("\n", "").replace("\r", "")
    }

    fun decode(line: String): Envelope {
        if (line.isBlank()) throw ProtocolException(ErrorCode.BAD_REQUEST, "数据包不能为空")
        val root = try {
            JsonParser.parseString(line)
        } catch (error: JsonSyntaxException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "JSON格式错误", error)
        } catch (error: IllegalStateException) {
            throw ProtocolException(ErrorCode.BAD_REQUEST, "JSON格式错误", error)
        }
        if (!root.isJsonObject) throw ProtocolException(ErrorCode.BAD_REQUEST, "数据包必须是JSON对象")
        val objectValue = root.asJsonObject
        val version = requiredInt(objectValue, "v")
        if (version !in MIN_SUPPORTED_PROTOCOL..PROTOCOL_VERSION) {
            throw ProtocolException(ErrorCode.UNSUPPORTED_PROTOCOL)
        }
        val type = requiredString(objectValue, "type")
        if (type !in PacketType.all) throw ProtocolException(ErrorCode.BAD_REQUEST, "未知数据包类型：$type")
        return Envelope(
            v = version,
            type = type,
            id = requiredString(objectValue, "id"),
            ts = requiredLong(objectValue, "ts"),
            payload = objectValue.get("payload") ?: throw ProtocolException(ErrorCode.BAD_REQUEST, "缺少payload字段"),
            error = objectValue.get("error")?.takeUnless { it.isJsonNull }?.let {
                try {
                    gson.fromJson(it, ProtocolErrorPayload::class.java)
                } catch (error: RuntimeException) {
                    throw ProtocolException(ErrorCode.BAD_REQUEST, "error字段格式错误", error)
                }
            }
        )
    }

    fun <T> decodePayload(envelope: Envelope, type: Class<T>): T = try {
        gson.fromJson(envelope.payload, type)
            ?: throw ProtocolException(ErrorCode.BAD_REQUEST, "payload不能为空")
    } catch (error: ProtocolException) {
        throw error
    } catch (error: RuntimeException) {
        throw ProtocolException(ErrorCode.BAD_REQUEST, "payload格式错误", error)
    }

    private fun requiredString(value: JsonObject, name: String): String = try {
        value.get(name)?.takeUnless { it.isJsonNull }?.asString
            ?.takeIf(String::isNotBlank)
            ?: throw ProtocolException(ErrorCode.BAD_REQUEST, "缺少${name}字段")
    } catch (error: ProtocolException) {
        throw error
    } catch (error: RuntimeException) {
        throw ProtocolException(ErrorCode.BAD_REQUEST, "${name}字段格式错误", error)
    }

    private fun requiredInt(value: JsonObject, name: String): Int = try {
        value.get(name)?.asInt ?: throw ProtocolException(ErrorCode.BAD_REQUEST, "缺少${name}字段")
    } catch (error: ProtocolException) {
        throw error
    } catch (error: RuntimeException) {
        throw ProtocolException(ErrorCode.BAD_REQUEST, "${name}字段格式错误", error)
    }

    private fun requiredLong(value: JsonObject, name: String): Long = try {
        value.get(name)?.asLong ?: throw ProtocolException(ErrorCode.BAD_REQUEST, "缺少${name}字段")
    } catch (error: ProtocolException) {
        throw error
    } catch (error: RuntimeException) {
        throw ProtocolException(ErrorCode.BAD_REQUEST, "${name}字段格式错误", error)
    }
}
