package com.hmilyld.weeklyreport.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.SessionManager
import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.ui.UiMessage
import com.hmilyld.weeklyreport.util.UrlRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverInput: String = "",
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loggingIn: Boolean = false,
    val testing: Boolean = false,
    val serverError: String? = null,
    val formError: UiMessage? = null,
    val testResult: UiMessage? = null,
)

class LoginViewModel(
    private val settings: SettingsStore,
    private val session: SessionManager,
    private val api: ApiClient,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            serverInput = UrlRules.displayAddress(settings.serverBaseValue()),
            username = settings.lastUsername.value,
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun updateServer(value: String) {
        _state.update { it.copy(serverInput = value, serverError = null, testResult = null) }
    }

    fun syncFromSettings() {
        _state.update {
            it.copy(
                serverInput = UrlRules.displayAddress(settings.serverBaseValue()),
                username = if (it.username.isBlank()) settings.lastUsername.value else it.username,
            )
        }
    }

    fun updateUsername(value: String) {
        _state.update { it.copy(username = value, formError = null) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value, formError = null) }
    }

    fun togglePasswordVisible() {
        _state.update { it.copy(showPassword = !it.showPassword) }
    }

    fun testServer() {
        val input = _state.value.serverInput
        val normalized = UrlRules.validateAndNormalize(input, BuildConfig.DEBUG)
        if (normalized is UrlRules.Result.Error) {
            _state.update { it.copy(serverError = normalized.message, testResult = null) }
            return
        }
        val base = (normalized as UrlRules.Result.Ok).baseUrl
        viewModelScope.launch {
            _state.update { it.copy(testing = true, serverError = null, testResult = null) }
            try {
                api.healthAt(base)
                settings.setServerBase(base)
                _state.update {
                    it.copy(testResult = UiMessage.Text(R.string.login_test_success_saved), testing = false)
                }
            } catch (error: ApiException) {
                _state.update { it.copy(testResult = UiMessage.Raw(error.message), testing = false) }
            }
        }
    }

    fun login() {
        val state = _state.value
        if (state.loggingIn) return
        if (state.username.isBlank() || state.password.isEmpty()) {
            _state.update { it.copy(formError = UiMessage.Text(R.string.login_error_credentials)) }
            return
        }
        val normalized = UrlRules.validateAndNormalize(state.serverInput, BuildConfig.DEBUG)
        if (normalized is UrlRules.Result.Error) {
            _state.update { it.copy(serverError = normalized.message) }
            return
        }
        val base = (normalized as UrlRules.Result.Ok).baseUrl
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, formError = null, serverError = null) }
            settings.setServerBase(base)
            try {
                session.login(state.username, state.password)
                _state.update { it.copy(password = "", loggingIn = false) }
            } catch (error: ApiException) {
                val message = when {
                    error.status == 401 -> UiMessage.Text(R.string.login_error_wrong_password)
                    error.status == 429 -> UiMessage.Raw(error.message)
                    error.kind == ApiErrorKind.NETWORK -> UiMessage.Raw(error.message)
                    error.kind == ApiErrorKind.NOT_CONFIGURED -> UiMessage.Raw(error.message)
                    else -> UiMessage.Raw(error.message)
                }
                _state.update { it.copy(loggingIn = false, formError = message) }
            }
        }
    }
}
