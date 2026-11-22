package com.hmilyld.weeklyreport.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

enum class ApiErrorKind {
    HTTP,
    NETWORK,
    UNAUTHORIZED,
    DECRYPTION,
    ENCRYPTION_KEY_MISSING,
    NOT_CONFIGURED,
    PARSE,
}

class ApiException(
    val status: Int?,
    override val message: String,
    val kind: ApiErrorKind = ApiErrorKind.HTTP,
) : Exception(message) {
    val isNotFound: Boolean get() = status == 404
    val isWrongPassword: Boolean get() = status == 402

    /**
     * Only an invalid/expired credential drops the session. Decryption and
     * key-missing responses are server-side content problems: the token is
     * still valid and re-login does not fix them, so they are surfaced as
     * normal errors instead of forcing the user back to the login screen.
     */
    val requiresLogin: Boolean get() = kind == ApiErrorKind.UNAUTHORIZED
}

/**
 * Reads FastAPI error payloads ("detail" may be a string or a validation list),
 * and detects the server's `_decryption_failed` marker in any response body.
 */
object ApiPayloads {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private fun detailMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val element = json.parseToJsonElement(body)
            val detail = (element as? JsonObject)?.get("detail")
            when (detail) {
                null -> null
                is JsonPrimitive -> detail.content
                is JsonArray -> detail.mapNotNull { item ->
                    (item as? JsonObject)?.get("msg")?.jsonPrimitive?.content
                }.joinToString("；").ifEmpty { null }
                else -> null
            }
        }.getOrNull()
    }

    fun errorMessage(
        body: String?,
        status: Int,
    ): String =
        detailMessage(body) ?: when (status) {
            401 -> "登录状态已失效，请重新登录"
            402 -> "当前密码不正确"
            403 -> "没有权限执行该操作"
            404 -> "请求的内容不存在"
            409 -> "请求与服务器状态冲突（409）"
            429 -> "尝试次数过多，请稍后再试"
            in 500..599 -> "服务器暂时不可用（$status）"
            else -> "请求失败（$status）"
        }

    fun decryptionMessage(body: String?): String = detailMessage(body) ?: "内容暂时无法解密，请检查服务端加密配置。"

    fun errorCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            (json.parseToJsonElement(body) as? JsonObject)
                ?.get("code")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    /** Maps the new 409 encryption codes to their own error kinds. */
    fun errorKind(
        status: Int,
        body: String?,
    ): ApiErrorKind =
        when {
            status == 401 -> ApiErrorKind.UNAUTHORIZED
            status == 409 && errorCode(body) == "encryption_key_missing" ->
                ApiErrorKind.ENCRYPTION_KEY_MISSING
            status == 409 && errorCode(body) == "decryption_failed" -> ApiErrorKind.DECRYPTION
            else -> ApiErrorKind.HTTP
        }

    fun decryptionFailed(body: String?): Boolean {
        if (body.isNullOrBlank() || !body.contains("_decryption_failed")) return false
        return runCatching {
            fun walk(element: JsonElement): Boolean =
                when (element) {
                    is JsonObject -> element.entries.any { (key, value) ->
                        val failed = (value as? JsonPrimitive)?.content == "true"
                        (key == "_decryption_failed" && failed) || walk(value)
                    }
                    is JsonArray -> element.any { walk(it) }
                    else -> false
                }
            walk(json.parseToJsonElement(body))
        }.getOrDefault(false)
    }

    fun toApiException(error: Throwable): ApiException =
        when (error) {
            is ApiException -> error
            is SocketTimeoutException -> ApiException(
                status = null,
                message = "连接超时，请检查服务器地址或网络",
                kind = ApiErrorKind.NETWORK,
            )
            is IOException -> ApiException(
                status = null,
                message = "无法连接服务器，请检查网络或服务器地址",
                kind = ApiErrorKind.NETWORK,
            )
            else -> ApiException(
                status = null,
                message = error.message ?: "请求失败",
                kind = ApiErrorKind.PARSE,
            )
        }
}
