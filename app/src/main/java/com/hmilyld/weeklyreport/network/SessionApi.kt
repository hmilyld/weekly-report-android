package com.hmilyld.weeklyreport.network

import com.hmilyld.weeklyreport.data.model.ApiTokenCreatedDto
import com.hmilyld.weeklyreport.data.model.TokenResponse

/**
 * The auth surface used by [com.hmilyld.weeklyreport.data.SessionManager].
 * Kept as an interface so the session lifecycle can be tested with a fake.
 */
interface SessionApi {
    /** The value + kind pair is replaced as one reference; never read half of it. */
    var credential: ApiCredential?

    /** Receives the credential that the rejected request actually used. */
    var problemListener: (ApiCredential?) -> Unit

    suspend fun login(
        username: String,
        password: String,
    ): TokenResponse

    suspend fun createApiToken(
        jwt: String,
        name: String,
        baseUrl: String,
    ): ApiTokenCreatedDto

    suspend fun deleteApiToken(
        jwt: String,
        id: Int,
        baseUrl: String,
    )

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ): TokenResponse

    suspend fun logout()
}
