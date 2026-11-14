package com.hmilyld.weeklyreport.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.data.local.OutboxCounts
import com.hmilyld.weeklyreport.data.model.UserDto
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.data.sync.SyncResult
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.notifications.TaskNotifier
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
    val testResult: String? = null,
    val serverError: String? = null,
    val changingPassword: Boolean = false,
    val passwordError: String? = null,
    val passwordSuccess: Boolean = false,
    val queue: OutboxCounts = OutboxCounts(0, 0),
    val notice: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(serverInput = UrlRules.displayAddress(container.settings.serverBaseValue())),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
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
                        notice = if (error.kind == ApiErrorKind.NETWORK) "离线：暂时无法读取账号信息" else null,
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
                _state.update { it.copy(testing = false, testResult = "连接成功") }
            } catch (error: ApiException) {
                _state.update { it.copy(testing = false, testResult = error.message) }
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
            _state.update { it.copy(serverError = null, testResult = "地址没有变化") }
            return
        }
        viewModelScope.launch {
            // Sign out against the old server first; the new address has no
            // session for this token, so a fresh login is the honest path.
            container.session.logout()
            container.settings.setServerBase(base)
            _messages.emit("服务器地址已更新，请重新登录")
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirm: String) {
        when {
            oldPassword.isEmpty() -> {
                _state.update { it.copy(passwordError = "请输入当前密码") }
                return
            }
            newPassword.length < 8 -> {
                _state.update { it.copy(passwordError = "新密码至少 8 位") }
                return
            }
            !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() } -> {
                _state.update { it.copy(passwordError = "新密码需要同时包含字母和数字") }
                return
            }
            newPassword != confirm -> {
                _state.update { it.copy(passwordError = "两次输入的新密码不一致") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(changingPassword = true, passwordError = null) }
            try {
                container.session.changePassword(oldPassword, newPassword)
                _state.update { it.copy(changingPassword = false, passwordSuccess = true) }
                _messages.emit("密码已修改，其他设备需要重新登录")
            } catch (error: ApiException) {
                // 402 keeps the session alive and points at the current password;
                // 401 is handled by the session and returns to the login screen.
                val message = when {
                    error.isWrongPassword -> "当前密码不正确"
                    error.requiresLogin -> error.message
                    else -> error.message
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
            val result = withContext(Dispatchers.IO) { container.syncEngine.drain() }
            val message = when (result) {
                is SyncResult.Done ->
                    if (result.sent > 0) "已发送 ${result.sent} 条离线修改" else "没有待发送的修改"
                is SyncResult.NeedsLogin -> result.reason
                is SyncResult.Stopped -> result.reason
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
            _messages.emit(if (enabled) "已开启生物识别解锁" else "已关闭生物识别解锁")
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
                !TaskNotifier.hasPermission(container.appContext) -> "请先允许通知权限"
                count == 0 -> "没有逾期或三天内到期的待办"
                else -> "已发送 $count 条到期提醒"
            }
            _messages.emit(message)
        }
    }

    fun pinWidget() {
        val requested = TodayWidgetProvider.requestPin(container.appContext)
        viewModelScope.launch {
            _messages.emit(
                if (requested) "已请求添加主屏小部件" else "当前桌面不支持直接添加，请长按桌面手动添加",
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.session.logout()
            _messages.emit("已退出登录")
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
