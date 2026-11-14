package com.hmilyld.weeklyreport.data

import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import com.hmilyld.weeklyreport.data.secure.SecureTokenStore
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.network.SessionProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    data object LoggedOut : SessionState

    data class Locked(val username: String) : SessionState

    data class LoggedIn(val username: String) : SessionState
}

/**
 * Owns the session lifecycle. A 401 or a `_decryption_failed` response always
 * drops the session and surfaces a reason, so screens never render blank
 * content after the backend has lost its key cache.
 */
class SessionManager(
    private val settings: SettingsStore,
    private val tokenStore: SecureTokenStore,
    private val api: ApiClient,
) {
    private var tokenCache: String? = tokenStore.readToken()
    private var cachedUsername: String = settings.lastUsername.value

    private val _state = MutableStateFlow(
        when {
            tokenCache == null -> SessionState.LoggedOut
            settings.biometricEnabled.value -> SessionState.Locked(cachedUsername)
            else -> SessionState.LoggedIn(cachedUsername)
        },
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _problem = MutableStateFlow(settings.lastLoginProblem().ifBlank { null })
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /**
     * Increments whenever a session becomes usable again (login or biometric
     * unlock). UI refreshes and the outbox drain key off this instead of the
     * username, which is equal across a logout/login cycle.
     */
    private val _activationEpoch = MutableStateFlow(0L)
    val activationEpoch: StateFlow<Long> = _activationEpoch.asStateFlow()

    private fun activate() {
        _activationEpoch.value += 1
    }

    init {
        api.tokenProvider = { tokenOrNull() }
        api.problemListener = { onAuthProblem(it) }
    }

    fun tokenOrNull(): String? = if (_state.value is SessionState.LoggedIn) tokenCache else null

    /** Outbox rows are owned by the account that wrote them. */
    fun owner(): String = when (val current = _state.value) {
        is SessionState.LoggedIn -> current.username
        is SessionState.Locked -> current.username
        SessionState.LoggedOut -> ""
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(username.trim(), password)
        tokenCache = response.accessToken
        tokenStore.saveToken(response.accessToken)
        cachedUsername = username.trim()
        settings.setLastUsername(cachedUsername)
        clearProblem()
        _state.value = SessionState.LoggedIn(cachedUsername)
        activate()
    }

    suspend fun logout() {
        runCatching { api.logout() }
        clearSession()
        _state.value = SessionState.LoggedOut
        clearProblem()
    }

    /** Called after a successful BiometricPrompt while the app was locked. */
    fun unlock() {
        if (tokenCache != null) {
            _state.value = SessionState.LoggedIn(cachedUsername)
            activate()
        } else {
            _state.value = SessionState.LoggedOut
        }
    }

    fun lock() {
        if (tokenCache != null && _state.value is SessionState.LoggedIn) {
            _state.value = SessionState.Locked(cachedUsername)
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val response = api.changePassword(oldPassword, newPassword)
        tokenCache = response.accessToken
        tokenStore.saveToken(response.accessToken)
    }

    fun onAuthProblem(problem: SessionProblem) {
        val message = when (problem) {
            SessionProblem.UNAUTHORIZED -> "登录状态已失效，请重新登录。"
            SessionProblem.DECRYPTION_FAILED ->
                "服务器密钥缓存已丢失（后端可能重启过），暂时无法解密已保存内容。请重新登录以恢复数据。"
        }
        clearSession()
        settings.setLoginProblem(message)
        _problem.value = message
        _state.value = SessionState.LoggedOut
    }

    fun clearProblem() {
        settings.setLoginProblem(null)
        _problem.value = null
    }

    private fun clearSession() {
        tokenCache = null
        tokenStore.clear()
    }
}
