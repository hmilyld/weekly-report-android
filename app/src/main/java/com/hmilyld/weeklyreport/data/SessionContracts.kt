package com.hmilyld.weeklyreport.data

import com.hmilyld.weeklyreport.network.AuthKind
import kotlinx.coroutines.flow.StateFlow

/**
 * The small part of [com.hmilyld.weeklyreport.data.prefs.SettingsStore] the
 * session lifecycle needs. Kept as an interface so the lifecycle can be unit
 * tested without Android SharedPreferences.
 */
interface SessionSettings {
    val lastUsername: StateFlow<String>
    val biometricEnabled: StateFlow<Boolean>

    fun serverBaseValue(): String

    fun setLastUsername(username: String)

    fun lastLoginProblem(): String

    fun setLoginProblem(message: String?)

    fun apiTokenId(): Int?

    fun setApiTokenId(id: Int?)

    fun apiTokenServer(): String?

    fun setApiTokenServer(server: String?)
}

/** The credential persistence used by [SessionManager]. */
interface SessionTokenStore {
    fun readToken(): String?

    fun readKind(): AuthKind

    fun saveToken(
        token: String,
        kind: AuthKind,
    )

    fun clear()
}
