package com.hmilyld.weeklyreport.network

import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.data.model.ApiTokenCreateRequest
import com.hmilyld.weeklyreport.data.model.ApiTokenCreatedDto
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

/** Which credential the data endpoints receive. Admin/auth endpoints need JWT. */
enum class AuthKind { JWT, API_TOKEN }

/**
 * The credential value and its kind travel as one immutable value. The field
 * holding it is replaced with a single reference write, so a concurrent request
 * can never observe a JWT value tagged as an API token (or the reverse).
 */
data class ApiCredential(
    val value: String,
    val kind: AuthKind,
)

internal fun authHeaderFor(credential: ApiCredential?): Pair<String, String>? =
    credential?.let {
        when (it.kind) {
            AuthKind.JWT -> "Authorization" to "Bearer ${it.value}"
            AuthKind.API_TOKEN -> "X-API-Token" to it.value
        }
    }

class ApiClient(
    private val settings: SettingsStore,
) : SessionApi {
    @Volatile
    override var credential: ApiCredential? = null

    override var problemListener: (ApiCredential?) -> Unit = {}

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

    override suspend fun login(
        username: String,
        password: String,
    ): TokenResponse =
        post(
            "auth/login",
            json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password)),
            notifyUnauthorized = false,
            anonymous = true,
        )

    override suspend fun logout() {
        execute("POST", "auth/logout", null, notifyUnauthorized = false)
    }

    suspend fun me(): UserDto = get("users/me")

    override suspend fun changePassword(
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

    /**
     * Creates a long-lived API token with the short-lived JWT obtained at login.
     * Token management endpoints accept JWT only, never X-API-Token. The base URL
     * is pinned by the caller so a concurrent server change cannot send the old
     * JWT to another host.
     */
    override suspend fun createApiToken(
        jwt: String,
        name: String,
        baseUrl: String,
    ): ApiTokenCreatedDto =
        postWithBearer(
            "tokens",
            json.encodeToString(ApiTokenCreateRequest.serializer(), ApiTokenCreateRequest(name)),
            jwt,
            baseUrl,
        )

    override suspend fun deleteApiToken(
        jwt: String,
        id: Int,
        baseUrl: String,
    ) {
        execute(
            "DELETE",
            "tokens/$id",
            notifyUnauthorized = false,
            baseOverride = baseUrl,
            bearerOverride = jwt,
        )
    }

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
            json.encodeToString(
                DailySaveRequest.serializer(),
                DailySaveRequest(date, content, append = false),
            ),
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

    suspend fun generateWeekly(weekStart: String): WeeklyReportDto =
        // force=true is the explicit "generate or regenerate" action; keep it
        // explicit so a future server default change cannot silently reuse an
        // existing report.
        post("weekly/$weekStart?force=true", "{}")

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
        execute("GET", "health", null, notifyUnauthorized = false, anonymous = true)
    }

    /** Test an address that has not been saved yet. */
    suspend fun healthAt(baseUrl: String) {
        execute(
            "GET",
            "health",
            null,
            notifyUnauthorized = false,
            baseOverride = baseUrl,
            anonymous = true,
        )
    }

    // ─── Transport ──────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): T = decode(execute("GET", path))

    private suspend inline fun <reified T> post(
        path: String,
        body: String,
        notifyUnauthorized: Boolean = true,
        anonymous: Boolean = false,
    ): T =
        decode(
            execute(
                "POST",
                path,
                body,
                notifyUnauthorized,
                anonymous = anonymous,
            ),
        )

    private suspend inline fun <reified T> postWithBearer(
        path: String,
        body: String,
        bearer: String,
        baseOverride: String,
    ): T =
        decode(
            execute(
                "POST",
                path,
                body,
                notifyUnauthorized = false,
                baseOverride = baseOverride,
                bearerOverride = bearer,
            ),
        )

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
        bearerOverride: String? = null,
        anonymous: Boolean = false,
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
            // Data endpoints take X-API-Token; account endpoints need Bearer.
            // bearerOverride is used only while exchanging a JWT for a token.
            // Login and a not-yet-saved server test must never leak the current
            // credential to another host.
            val requestCredential = if (anonymous || bearerOverride != null) null else credential
            if (bearerOverride != null) {
                builder.header("Authorization", "Bearer $bearerOverride")
            } else {
                authHeaderFor(requestCredential)?.let { (name, value) ->
                    builder.header(name, value)
                }
            }
            val requestBody = when (method) {
                "POST", "PUT" -> (body ?: "{}").toRequestBody(mediaType)
                else -> null
            }
            builder.method(method, requestBody)
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful && response.code == 401) {
                        val message = ApiPayloads.errorMessage(raw, response.code)
                        if (notifyUnauthorized) problemListener(requestCredential)
                        throw ApiException(response.code, message, ApiErrorKind.UNAUTHORIZED)
                    }
                    // A successful list can carry _decryption_failed per item.
                    // Never decode it into empty content: the token is still
                    // valid, this is a server-side key problem.
                    if (ApiPayloads.decryptionFailed(raw)) {
                        throw ApiException(
                            response.code,
                            ApiPayloads.decryptionMessage(raw),
                            ApiErrorKind.DECRYPTION,
                        )
                    }
                    if (!response.isSuccessful) {
                        throw ApiException(
                            response.code,
                            ApiPayloads.errorMessage(raw, response.code),
                            ApiPayloads.errorKind(response.code, raw),
                        )
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
