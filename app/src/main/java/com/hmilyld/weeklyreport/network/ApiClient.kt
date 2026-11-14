package com.hmilyld.weeklyreport.network

import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.data.model.ChangePasswordRequest
import com.hmilyld.weeklyreport.data.model.CompletedTaskListResponse
import com.hmilyld.weeklyreport.data.model.ContentUpdateRequest
import com.hmilyld.weeklyreport.data.model.DailySaveRequest
import com.hmilyld.weeklyreport.data.model.DailyWeekResponse
import com.hmilyld.weeklyreport.data.model.DayReport
import com.hmilyld.weeklyreport.data.model.LoginRequest
import com.hmilyld.weeklyreport.data.model.MonthlyReportDto
import com.hmilyld.weeklyreport.data.model.TaskCreateRequest
import com.hmilyld.weeklyreport.data.model.TaskDto
import com.hmilyld.weeklyreport.data.model.TaskListResponse
import com.hmilyld.weeklyreport.data.model.TokenResponse
import com.hmilyld.weeklyreport.data.model.UserDto
import com.hmilyld.weeklyreport.data.model.WeeklyReportDto
import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiClient(
    private val settings: SettingsStore,
) {
    var tokenProvider: () -> String? = { null }
    var problemListener: (SessionProblem) -> Unit = {}

    private val json: Json = ApiPayloads.json
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ─── Auth ───────────────────────────────────────────

    suspend fun login(
        username: String,
        password: String,
    ): TokenResponse =
        post(
            "auth/login",
            json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password)),
            notifyUnauthorized = false,
        )

    suspend fun logout() {
        execute("POST", "auth/logout", null, notifyUnauthorized = false)
    }

    suspend fun me(): UserDto = get("users/me")

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ): TokenResponse =
        post(
            "auth/change-password",
            json.encodeToString(
                ChangePasswordRequest.serializer(),
                ChangePasswordRequest(oldPassword, newPassword),
            ),
        )

    // ─── Daily ──────────────────────────────────────────

    suspend fun dailyWeek(weekStart: String): DailyWeekResponse = get("daily/week/$weekStart")

    suspend fun dailyRange(
        start: String,
        end: String,
    ): DailyWeekResponse = get("daily?start_date=$start&end_date=$end")

    suspend fun saveDaily(
        date: String,
        content: String,
    ): DayReport =
        post(
            "daily",
            json.encodeToString(DailySaveRequest.serializer(), DailySaveRequest(date, content)),
        )

    suspend fun deleteDaily(date: String) {
        execute("DELETE", "daily/$date")
    }

    // ─── Tasks ──────────────────────────────────────────

    suspend fun tasks(): TaskListResponse = get("tasks")

    suspend fun completedTasks(
        offset: Int,
        limit: Int,
    ): CompletedTaskListResponse = get("tasks/completed?offset=$offset&limit=$limit")

    suspend fun createTask(
        content: String,
        deadline: String?,
    ): TaskDto =
        post(
            "tasks",
            json.encodeToString(
                TaskCreateRequest.serializer(),
                TaskCreateRequest(content, deadline),
            ),
        )

    suspend fun updateTask(
        id: Int,
        content: String?,
        deadline: String?,
        includeDeadline: Boolean,
        isCompleted: Boolean?,
    ): TaskDto =
        put(
            "tasks/$id",
            buildString {
                append("{")
                var first = true
                if (content != null) {
                    append("\"content\":")
                    append(Json.encodeToString(String.serializer(), content))
                    first = false
                }
                if (!first) append(",")
                append("\"is_completed\":")
                append(if (isCompleted == true) "true" else "false")
                if (includeDeadline) {
                    append(",\"deadline\":")
                    append(
                        if (deadline ==
                            null
                        ) {
                            "null"
                        } else {
                            Json.encodeToString(String.serializer(), deadline)
                        },
                    )
                }
                append("}")
            },
        )

    suspend fun deleteTask(id: Int) {
        execute("DELETE", "tasks/$id")
    }

    // ─── Reports ────────────────────────────────────────

    suspend fun weekly(weekStart: String): WeeklyReportDto = get("weekly/$weekStart")

    suspend fun generateWeekly(weekStart: String): WeeklyReportDto = post("weekly/$weekStart", "{}")

    suspend fun updateWeekly(
        weekStart: String,
        content: String,
    ): WeeklyReportDto =
        put(
            "weekly/$weekStart",
            json.encodeToString(ContentUpdateRequest.serializer(), ContentUpdateRequest(content)),
        )

    suspend fun monthly(
        year: Int,
        month: Int,
    ): MonthlyReportDto = get("monthly/$year/$month")

    suspend fun generateMonthly(
        year: Int,
        month: Int,
    ): MonthlyReportDto = post("monthly/$year/$month", "{}")

    suspend fun updateMonthly(
        year: Int,
        month: Int,
        content: String,
    ): MonthlyReportDto =
        put(
            "monthly/$year/$month",
            json.encodeToString(ContentUpdateRequest.serializer(), ContentUpdateRequest(content)),
        )

    suspend fun health() {
        execute("GET", "health", null, notifyUnauthorized = false)
    }

    /** Test an address that has not been saved yet. */
    suspend fun healthAt(baseUrl: String) {
        execute("GET", "health", null, notifyUnauthorized = false, baseOverride = baseUrl)
    }

    // ─── Transport ──────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): T = decode(execute("GET", path))

    private suspend inline fun <reified T> post(
        path: String,
        body: String,
        notifyUnauthorized: Boolean = true,
    ): T = decode(execute("POST", path, body, notifyUnauthorized))

    private suspend inline fun <reified T> put(
        path: String,
        body: String,
    ): T = decode(execute("PUT", path, body))

    private inline fun <reified T> decode(raw: String): T {
        if (raw.isBlank()) {
            throw ApiException(null, "服务器返回了空内容", ApiErrorKind.PARSE)
        }
        return try {
            json.decodeFromString<T>(raw)
        } catch (error: Exception) {
            throw ApiException(null, "无法解析服务器响应：${error.message}", ApiErrorKind.PARSE)
        }
    }

    private suspend fun execute(
        method: String,
        path: String,
        body: String? = null,
        notifyUnauthorized: Boolean = true,
        baseOverride: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val base = baseOverride ?: settings.serverBaseValue()
            if (base.isBlank()) {
                throw ApiException(null, "请先设置服务器地址", ApiErrorKind.NOT_CONFIGURED)
            }
            if (!BuildConfig.DEBUG && base.startsWith("http://", ignoreCase = true)) {
                throw ApiException(null, "正式版只允许 HTTPS 服务器地址", ApiErrorKind.NOT_CONFIGURED)
            }
            val builder = Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            val requestBody = when (method) {
                "POST", "PUT" -> (body ?: "{}").toRequestBody(mediaType)
                else -> null
            }
            builder.method(method, requestBody)
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (ApiPayloads.decryptionFailed(raw)) {
                        problemListener(SessionProblem.DECRYPTION_FAILED)
                        throw ApiException(
                            response.code,
                            "服务器无法解密已保存内容（密钥缓存已丢失），请重新登录以恢复。",
                            ApiErrorKind.DECRYPTION,
                        )
                    }
                    if (!response.isSuccessful) {
                        val message = ApiPayloads.errorMessage(raw, response.code)
                        if (response.code == 401) {
                            if (notifyUnauthorized) problemListener(SessionProblem.UNAUTHORIZED)
                            throw ApiException(response.code, message, ApiErrorKind.UNAUTHORIZED)
                        }
                        throw ApiException(response.code, message, ApiErrorKind.HTTP)
                    }
                    raw
                }
            } catch (error: ApiException) {
                throw error
            } catch (error: IOException) {
                throw ApiPayloads.toApiException(error)
            }
        }
}
