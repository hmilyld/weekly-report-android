package com.hmilyld.weeklyreport.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.local.OutboxCounts
import com.hmilyld.weeklyreport.data.model.UserDto
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.data.sync.SyncResult
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.ui.UiMessage
import com.hmilyld.weeklyreport.util.UrlRules
import com.hmilyld.weeklyreport.widget.TodayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val serverInput: String = "",
    val user: UserDto? = null,
    val loadingUser: Boolean = false,
    val testing: Boolean = false,
    val testResult: UiMessage? = null,
    val serverError: String? = null,
    val changingPassword: Boolean = false,
    val passwordError: UiMessage? = null,
    val passwordSuccess: Boolean = false,
    val queue: OutboxCounts = OutboxCounts(0, 0),
    val notice: UiMessage? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(serverInput = UrlRules.displayAddress(container.settings.serverBaseValue())),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val theme: StateFlow<ThemeMode> = container.settings.theme

    init {
        refreshUser()
        viewModelScope.launch {
            container.dataVersion.collect { refreshQueue() }
        }
        refreshQueue()
    }

    fun refreshUser() {
        viewModelScope.launch {
            _state.update { it.copy(loadingUser = true) }
            try {
                val user = container.api.me()
                _state.update { it.copy(user = user, loadingUser = false, notice = null) }
            } catch (error: ApiException) {
                _state.update {
                    it.copy(
                        loadingUser = false,
                        notice = if (error.kind == ApiErrorKind.NETWORK) {
                            UiMessage.Text(R.string.settings_notice_offline_account)
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    fun updateServerInput(value: String) {
        _state.update { it.copy(serverInput = value, serverError = null, testResult = null) }
    }

    fun testServer(input: String) {
        val normalized = UrlRules.validateAndNormalize(input, BuildConfig.DEBUG)
        if (normalized is UrlRules.Result.Error) {
            _state.update { it.copy(serverError = normalized.message, testResult = null) }
            return
        }
        val base = (normalized as UrlRules.Result.Ok).baseUrl
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null, serverError = null) }
            try {
                container.api.healthAt(base)
                _state.update { it.copy(testing = false, testResult = UiMessage.Text(R.string.settings_test_success)) }
            } catch (error: ApiException) {
                _state.update { it.copy(testing = false, testResult = UiMessage.Raw(error.message)) }
            }
        }
    }

    fun saveServer(input: String) {
        val normalized = UrlRules.validateAndNormalize(input, BuildConfig.DEBUG)
        if (normalized is UrlRules.Result.Error) {
            _state.update { it.copy(serverError = normalized.message) }
            return
        }
        val base = (normalized as UrlRules.Result.Ok).baseUrl
        val previous = container.settings.serverBaseValue()
        if (base == previous) {
            _state.update { it.copy(serverError = null, testResult = UiMessage.Text(R.string.settings_test_unchanged)) }
            return
        }
        viewModelScope.launch {
            // Sign out against the old server first; the new address has no
            // session for this token, so a fresh login is the honest path.
            container.session.logout()
            container.settings.setServerBase(base)
            _messages.emit(UiMessage.Text(R.string.settings_server_saved_relogin))
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirm: String) {
        when {
            oldPassword.isEmpty() -> {
                _state.update { it.copy(passwordError = UiMessage.Text(R.string.settings_password_old_required)) }
                return
            }
            newPassword.length < 8 -> {
                _state.update { it.copy(passwordError = UiMessage.Text(R.string.settings_password_too_short)) }
                return
            }
            !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() } -> {
                _state.update { it.copy(passwordError = UiMessage.Text(R.string.settings_password_needs_mixed)) }
                return
            }
            newPassword != confirm -> {
                _state.update { it.copy(passwordError = UiMessage.Text(R.string.settings_password_mismatch)) }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(changingPassword = true, passwordError = null) }
            try {
                container.session.changePassword(oldPassword, newPassword)
                _state.update { it.copy(changingPassword = false, passwordSuccess = true) }
                _messages.emit(UiMessage.Text(R.string.settings_password_changed))
            } catch (error: ApiException) {
                // 402 keeps the session alive and points at the current password;
                // 401 is handled by the session and returns to the login screen.
                val message = when {
                    error.isWrongPassword -> UiMessage.Text(R.string.settings_password_wrong_old)
                    error.requiresLogin -> UiMessage.Raw(error.message)
                    else -> UiMessage.Raw(error.message)
                }
                _state.update { it.copy(changingPassword = false, passwordError = message) }
            }
        }
    }

    fun clearPasswordError() {
        _state.update { it.copy(passwordError = null) }
    }

    fun consumePasswordSuccess() {
        _state.update { it.copy(passwordSuccess = false) }
    }

    fun retryQueue() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.db.requeueFailed(container.session.owner())
                container.syncEngine.drain()
            }
            val message = when (result) {
                is SyncResult.Done ->
                    if (result.sent > 0) {
                        UiMessage.Text(R.string.common_queue_sent, listOf(result.sent))
                    } else {
                        UiMessage.Text(R.string.settings_queue_nothing_pending)
                    }

                is SyncResult.NeedsLogin -> UiMessage.Raw(result.reason)
                is SyncResult.Stopped -> UiMessage.Raw(result.reason)
            }
            _messages.emit(message)
            refreshQueue()
        }
    }

    fun setTheme(mode: ThemeMode) {
        container.settings.setTheme(mode)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        container.settings.setBiometricEnabled(enabled)
        viewModelScope.launch {
            _messages.emit(
                if (enabled) {
                    UiMessage.Text(R.string.settings_biometric_enabled)
                } else {
                    UiMessage.Text(R.string.settings_biometric_disabled)
                },
            )
        }
    }

    fun checkReminders() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                TaskNotifier.checkAndNotify(
                    container.appContext,
                    container.db,
                    container.settings,
                    force = true,
                )
            }
            val message = when {
                !TaskNotifier.hasPermission(container.appContext) ->
                    UiMessage.Text(R.string.settings_reminder_no_permission)

                count == 0 -> UiMessage.Text(R.string.settings_reminder_none)
                else -> UiMessage.Text(R.string.settings_reminder_sent, listOf(count))
            }
            _messages.emit(message)
        }
    }

    fun pinWidget() {
        val requested = TodayWidgetProvider.requestPin(container.appContext)
        viewModelScope.launch {
            _messages.emit(
                if (requested) {
                    UiMessage.Text(R.string.settings_widget_requested)
                } else {
                    UiMessage.Text(R.string.settings_widget_unsupported)
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.session.logout()
            _messages.emit(UiMessage.Text(R.string.common_logged_out))
        }
    }

    private fun refreshQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val owner = container.session.owner()
            _state.update {
                it.copy(queue = if (owner.isBlank()) OutboxCounts(0, 0) else container.db.outboxCounts(owner))
            }
        }
    }
}
