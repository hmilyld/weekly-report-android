package com.hmilyld.weeklyreport.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class SetupStatus(@SerialName("needs_setup") val needsSetup: Boolean)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val role: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
)

@Serializable
data class DayReport(
    val id: Int? = null,
    val date: String,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class DailyWeekResponse(
    val reports: List<DayReport> = emptyList(),
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class DailySaveRequest(val date: String, val content: String)

@Serializable
data class TaskDto(
    val id: Int,
    val content: String,
    val deadline: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class TaskListResponse(
    val tasks: List<TaskDto> = emptyList(),
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class CompletedTaskListResponse(
    val tasks: List<TaskDto> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 20,
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class TaskCreateRequest(val content: String, val deadline: String? = null)

@Serializable
data class TaskUpdateRequest(
    val content: String? = null,
    val deadline: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean? = null,
)

@Serializable
data class WeeklyReportDto(
    val id: Int? = null,
    @SerialName("week_start") val weekStart: String,
    @SerialName("week_end") val weekEnd: String? = null,
    val content: String = "",
    @SerialName("model_name") val modelName: String = "",
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class MonthlyReportDto(
    val id: Int? = null,
    val year: Int,
    val month: Int,
    val content: String = "",
    @SerialName("model_name") val modelName: String = "",
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("_decryption_failed") val decryptionFailed: Boolean = false,
)

@Serializable
data class ContentUpdateRequest(val content: String)

@Serializable
data class MessageResponse(val message: String? = null)

@Serializable
data class DetailResponse(val detail: String? = null)
