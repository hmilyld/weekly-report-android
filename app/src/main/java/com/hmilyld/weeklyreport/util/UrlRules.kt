package com.hmilyld.weeklyreport.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Server address rules. Release builds refuse plain HTTP; debug builds allow it
 * for a LAN development server. Both normalize to a base ending in /api/v1.
 */
object UrlRules {
    sealed interface Result {
        data class Ok(val baseUrl: String) : Result
        data class Error(val message: String) : Result
    }

    fun validateAndNormalize(raw: String?, allowCleartext: Boolean): Result {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return Result.Error("请先填写服务器地址")
        }
        if (trimmed.contains(' ') || trimmed.contains('\n')) {
            return Result.Error("服务器地址不能包含空格")
        }
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("://") -> return Result.Error("只支持 http:// 或 https:// 地址")
            else -> (if (allowCleartext) "http://" else "https://") + trimmed
        }
        val url = withScheme.toHttpUrlOrNull()
            ?: return Result.Error("服务器地址格式不正确")
        if (url.scheme != "http" && url.scheme != "https") {
            return Result.Error("只支持 http:// 或 https:// 地址")
        }
        if (url.scheme == "http" && !allowCleartext) {
            return Result.Error("正式版只允许 HTTPS 地址；局域网 HTTP 仅用于调试版")
        }
        if (url.query != null || url.fragment != null) {
            return Result.Error("服务器地址不能带查询参数")
        }
        val path = url.encodedPath.trimEnd('/')
        val normalizedPath = if (path.endsWith("/api/v1")) path else "$path/api/v1"
        val base = url.newBuilder()
            .encodedPath(normalizedPath)
            .build()
            .toString()
            .trimEnd('/') + "/"
        return Result.Ok(base)
    }

    /** The stored server address without the /api/v1 suffix, for display. */
    fun displayAddress(baseUrl: String?): String = baseUrl.orEmpty().trimEnd('/').removeSuffix("/api/v1")
}
