package com.hmilyld.weeklyreport.data

import com.hmilyld.weeklyreport.network.ApiCredential
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.network.AuthKind
import com.hmilyld.weeklyreport.network.SessionApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    data object LoggedOut : SessionState

    data class Locked(
        val username: String,
    ) : SessionState

    data class LoggedIn(
        val username: String,
    ) : SessionState
}

/**
 * Owns the session lifecycle. A 401 always drops the session and surfaces a
 * reason. `_decryption_failed` / 409 responses do not: the credential is still
 * valid, and under envelope encryption a re-login cannot restore a missing
 * server master key, so those are shown as ordinary errors.
 *
 * The credential value + kind are published as one [ApiCredential] reference.
 * Login, unlock, the JWT→token upgrade and logout all update it under the same
 * lock, so a request can never see a half-swapped credential and the upgrade
 * cannot resurrect a session the user just closed.
 */
class SessionManager(
    private val settings: SessionSettings,
    private val tokenStore: SessionTokenStore,
    private val api: SessionApi,
) {
    private val credentialLock = Any()

    @Volatile
    private var authKind: AuthKind = tokenStore.readKind()

    @Volatile
    private var tokenCache: String? = tokenStore.readToken()

    @Volatile
    private var cachedUsername: String = settings.lastUsername.value

    private val _state = MutableStateFlow(
        when {
            tokenCache == null -> SessionState.LoggedOut
            settings.biometricEnabled.value -> SessionState.Locked(cachedUsername)
            else -> SessionState.LoggedIn(cachedUsername)
        },
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** JWT only while exchanging for a token, or as a fallback on old servers. */
    private val _authMode = MutableStateFlow(authKind)
    val authMode: StateFlow<AuthKind> = _authMode.asStateFlow()

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
        api.credential = credentialOrNull()
        api.problemListener = { failed -> onAuthProblem(failed) }
    }

    fun tokenOrNull(): String? = if (_state.value is SessionState.LoggedIn) tokenCache else null

    fun currentUsername(): String = cachedUsername

    /** Outbox rows are owned by the account that wrote them. */
    fun owner(): String =
        when (val current = _state.value) {
            is SessionState.LoggedIn -> current.username
            is SessionState.Locked -> current.username
            SessionState.LoggedOut -> ""
        }

    private fun credentialOrNull(): ApiCredential? {
        val token = tokenCache ?: return null
        return if (_state.value is SessionState.LoggedIn) {
            ApiCredential(token, authKind)
        } else {
            null
        }
    }

    suspend fun login(
        username: String,
        password: String,
    ) {
        val jwt = api.login(username.trim(), password).accessToken
        // Prefer a long-lived API token so a 24-hour JWT expiry cannot sign the
        // user out every day. Older servers without /tokens fall back to JWT.
        if (!createAndStoreApiToken(jwt)) {
            synchronized(credentialLock) {
                authKind = AuthKind.JWT
                tokenCache = jwt
                val saved = runCatching { tokenStore.saveToken(jwt, AuthKind.JWT) }.isSuccess
                if (!saved) {
                    clearSessionLocked()
                    _state.value = SessionState.LoggedOut
                    throw ApiException(null, "无法保存登录状态，请重试", ApiErrorKind.PARSE)
                }
                _authMode.value = AuthKind.JWT
            }
        }
        synchronized(credentialLock) {
            val token = tokenCache
            if (token == null) {
                clearSessionLocked()
                _state.value = SessionState.LoggedOut
                throw ApiException(null, "登录状态保存失败，请重试", ApiErrorKind.PARSE)
            }
            cachedUsername = username.trim()
            settings.setLastUsername(cachedUsername)
            clearProblem()
            // Publish the credential before the state, so a request triggered
            // by LoggedIn never sees a session without a credential.
            api.credential = ApiCredential(token, authKind)
            _state.value = SessionState.LoggedIn(cachedUsername)
        }
        activate()
    }

    /**
     * Existing installations may still hold a JWT issued before long-lived
     * tokens existed. Exchange it in place so the user does not have to log in
     * again when it expires. Safe to call repeatedly; a no-op once upgraded.
     */
    suspend fun upgradeLegacyJwt() {
        if (authKind != AuthKind.JWT) return
        if (_state.value !is SessionState.LoggedIn) return
        val jwt = tokenCache ?: return
        createAndStoreApiToken(jwt, expectedStoredToken = jwt)
    }

    /** Returns true when the JWT was exchanged for a long-lived API token. */
    private suspend fun createAndStoreApiToken(
        jwt: String,
        expectedStoredToken: String? = null,
    ): Boolean {
        // Pin the server for the whole exchange. Without this the token could
        // be created on one host and the old JWT sent to another after the user
        // edits the server address.
        val server = settings.serverBaseValue()
        val previousTokenId = settings.apiTokenId()
            ?.takeIf { settings.apiTokenServer() == server }
        val created = try {
            api.createApiToken(jwt, API_TOKEN_NAME, server)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return false

        var stored = false
        synchronized(credentialLock) {
            val stateValid = if (expectedStoredToken == null) {
                _state.value is SessionState.LoggedOut
            } else {
                _state.value is SessionState.LoggedIn && tokenCache == expectedStoredToken
            }
            val serverValid = settings.serverBaseValue() == server
            if (stateValid && serverValid) {
                stored = runCatching {
                    tokenStore.saveToken(created.token, AuthKind.API_TOKEN)
                    settings.setApiTokenId(created.id)
                    settings.setApiTokenServer(server)
                }.isSuccess
                if (stored) {
                    authKind = AuthKind.API_TOKEN
                    tokenCache = created.token
                    _authMode.value = AuthKind.API_TOKEN
                    if (_state.value is SessionState.LoggedIn) {
                        api.credential = ApiCredential(created.token, AuthKind.API_TOKEN)
                    }
                }
            }
        }

        if (!stored) {
            deleteTokenBestEffort(jwt, created.id, server)
            return false
        }
        if (previousTokenId != null && previousTokenId != created.id) {
            deleteTokenBestEffort(jwt, previousTokenId, server)
        }
        return true
    }

    private suspend fun deleteTokenBestEffort(
        jwt: String,
        id: Int,
        server: String,
    ) {
        try {
            api.deleteApiToken(jwt, id, server)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best effort: the token stays on the server and can be revoked on
            // the web UI; never block login or the upgrade on cleanup.
        }
    }

    suspend fun logout() {
        // /auth/logout accepts JWT only. A token session ends by clearing the
        // local credential; the token itself is revoked from the web UI or the
        // settings revoke action. A locked session has no token in memory, so
        // do not send an unauthenticated request.
        val callServer = authKind == AuthKind.JWT && _state.value is SessionState.LoggedIn
        if (callServer) runCatching { api.logout() }
        synchronized(credentialLock) {
            clearSessionLocked()
            _state.value = SessionState.LoggedOut
        }
        clearProblem()
    }

    /** Called after a successful BiometricPrompt while the app was locked. */
    fun unlock() {
        synchronized(credentialLock) {
            val token = tokenCache
            if (token != null) {
                // Publish the credential before the state for the same reason
                // as login.
                api.credential = ApiCredential(token, authKind)
                _state.value = SessionState.LoggedIn(cachedUsername)
                activate()
            } else {
                api.credential = null
                _state.value = SessionState.LoggedOut
            }
        }
    }

    fun lock() {
        synchronized(credentialLock) {
            if (tokenCache != null && _state.value is SessionState.LoggedIn) {
                api.credential = null
                _state.value = SessionState.Locked(cachedUsername)
            }
        }
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ) {
        if (authKind != AuthKind.JWT) {
            throw ApiException(
                status = null,
                message = "当前为 Token 登录，请到网页端修改密码。",
                kind = ApiErrorKind.HTTP,
            )
        }
        val expectedToken = tokenCache
        val response = api.changePassword(oldPassword, newPassword)
        synchronized(credentialLock) {
            // A concurrent logout or JWT→token upgrade wins; never overwrite
            // the newer credential with the JWT minted for the old session.
            if (_state.value !is SessionState.LoggedIn ||
                authKind != AuthKind.JWT ||
                tokenCache != expectedToken
            ) {
                return
            }
            val saved = runCatching {
                tokenStore.saveToken(response.accessToken, AuthKind.JWT)
            }.isSuccess
            if (!saved) {
                // The server already changed the password, so the old JWT is
                // invalid. Drop the session instead of leaving the app in a
                // half-broken state.
                val message = "密码已修改，请重新登录"
                clearSessionLocked()
                settings.setLoginProblem(message)
                _problem.value = message
                _state.value = SessionState.LoggedOut
                throw ApiException(null, message, ApiErrorKind.UNAUTHORIZED)
            }
            tokenCache = response.accessToken
            api.credential = ApiCredential(response.accessToken, AuthKind.JWT)
            _authMode.value = AuthKind.JWT
        }
    }

    fun onAuthProblem(failedCredential: ApiCredential?) {
        val message = "登录状态已失效，请重新登录。"
        synchronized(credentialLock) {
            // A stale 401 from a request that used an older credential must not
            // clear the session that replaced it (login, unlock or upgrade).
            if (_state.value !is SessionState.LoggedIn) return
            val current = api.credential
            if (failedCredential != null && current != null && current != failedCredential) return
            clearSessionLocked()
            settings.setLoginProblem(message)
            _problem.value = message
            _state.value = SessionState.LoggedOut
        }
    }

    fun clearProblem() {
        settings.setLoginProblem(null)
        _problem.value = null
    }

    /** Clears the published credential before the store, so no request can use
     * a token whose session is already gone. Caller must hold [credentialLock]. */
    private fun clearSessionLocked() {
        api.credential = null
        tokenCache = null
        tokenStore.clear()
    }

    companion object {
        /** Shown on the web token list so the user can identify this device. */
        private const val API_TOKEN_NAME = "Android 客户端"
    }
}
