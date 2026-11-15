package com.hmilyld.weeklyreport.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(
    val value: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

class SettingsStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wr_settings", Context.MODE_PRIVATE)

    private val _serverBase = MutableStateFlow(prefs.getString(KEY_SERVER_BASE, "").orEmpty())
    val serverBase: StateFlow<String> = _serverBase.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC, false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _lastUsername = MutableStateFlow(prefs.getString(KEY_LAST_USERNAME, "").orEmpty())
    val lastUsername: StateFlow<String> = _lastUsername.asStateFlow()

    fun serverBaseValue(): String = _serverBase.value

    fun setServerBase(baseUrl: String) {
        prefs.edit { putString(KEY_SERVER_BASE, baseUrl) }
        _serverBase.value = baseUrl
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.value) }
        _theme.value = mode
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC, enabled) }
        _biometricEnabled.value = enabled
    }

    fun setLastUsername(username: String) {
        prefs.edit { putString(KEY_LAST_USERNAME, username) }
        _lastUsername.value = username
    }

    fun lastDeadlineNoticeDay(): String = prefs.getString(KEY_LAST_DEADLINE_NOTICE, "").orEmpty()

    fun setLastDeadlineNoticeDay(day: String) {
        prefs.edit { putString(KEY_LAST_DEADLINE_NOTICE, day) }
    }

    fun lastLoginProblem(): String = prefs.getString(KEY_LOGIN_PROBLEM, "").orEmpty()

    fun setLoginProblem(message: String?) {
        prefs.edit { putString(KEY_LOGIN_PROBLEM, message.orEmpty()) }
    }

    // ─── Update channel ─────────────────────────────────
    // A self-hosted app updates through its own GitHub releases, so the check
    // state lives next to the other settings rather than in the database.

    private val _autoUpdateCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, true))
    val autoUpdateCheck: StateFlow<Boolean> = _autoUpdateCheck.asStateFlow()

    fun autoUpdateCheckValue(): Boolean = _autoUpdateCheck.value

    fun setAutoUpdateCheck(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_UPDATE, enabled) }
        _autoUpdateCheck.value = enabled
    }

    fun lastUpdateCheckAt(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun setLastUpdateCheckAt(millis: Long) {
        prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, millis) }
    }

    /** The version the user asked not to be reminded about again. */
    fun skippedUpdateVersion(): String = prefs.getString(KEY_SKIPPED_UPDATE, "").orEmpty()

    fun setSkippedUpdateVersion(version: String?) {
        prefs.edit { putString(KEY_SKIPPED_UPDATE, version.orEmpty()) }
    }

    companion object {
        private const val KEY_SERVER_BASE = "server_base"
        private const val KEY_THEME = "theme"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_LAST_USERNAME = "last_username"
        private const val KEY_LAST_DEADLINE_NOTICE = "last_deadline_notice"
        private const val KEY_LOGIN_PROBLEM = "login_problem"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_SKIPPED_UPDATE = "skipped_update_version"
    }
}
