package com.hmilyld.weeklyreport.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

enum class ApiErrorKind { HTTP, NETWORK, UNAUTHORIZED, DECRYPTION, NOT_CONFIGURED, PARSE }

class ApiException(
    val status: Int?,
    override val message: String,
    val kind: ApiErrorKind = ApiErrorKind.HTTP,
) : Exception(message) {
    val isNotFound: Boolean get() = status == 404
    val isWrongPassword: Boolean get() = status == 402
    val requiresLogin: Boolean
        get() = kind == ApiErrorKind.UNAUTHORIZED || kind == ApiErrorKind.DECRYPTION
}

enum class SessionProblem { UNAUTHORIZED, DECRYPTION_FAILED }

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

    fun errorMessage(
        body: String?,
        status: Int,
    ): String {
        if (!body.isNullOrBlank()) {
            runCatching {
                val element = json.parseToJsonElement(body)
                val detail = (element as? JsonObject)?.get("detail") ?: return@runCatching
                when (detail) {
                    is JsonPrimitive -> return detail.content
                    is JsonArray -> {
                        val messages = detail.mapNotNull { item ->
                            (item as? JsonObject)?.get("msg")?.jsonPrimitive?.content
                        }
                        if (messages.isNotEmpty()) return messages.joinToString("；")
                    }
                    else -> Unit
                }
            }
        }
        return when (status) {
            401 -> "登录状态已失效，请重新登录"
            402 -> "当前密码不正确"
            403 -> "没有权限执行该操作"
            404 -> "请求的内容不存在"
            429 -> "尝试次数过多，请稍后再试"
            in 500..599 -> "服务器暂时不可用（$status）"
            else -> "请求失败（$status）"
        }
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
