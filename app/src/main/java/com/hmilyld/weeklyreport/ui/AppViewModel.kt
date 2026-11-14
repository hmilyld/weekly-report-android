package com.hmilyld.weeklyreport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.data.local.OutboxCounts
import com.hmilyld.weeklyreport.data.local.OutboxItem
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(private val container: AppContainer) : ViewModel() {
    val session = container.session.state
    val problem = container.session.problem
    val theme: StateFlow<ThemeMode> = container.settings.theme

    private val _counts = MutableStateFlow(OutboxCounts(0, 0))
    val counts: StateFlow<OutboxCounts> = _counts.asStateFlow()

    private val _conflicts = MutableStateFlow<List<OutboxItem>>(emptyList())
    val conflicts: StateFlow<List<OutboxItem>> = _conflicts.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            container.dataVersion.collect { refreshQueue() }
        }
        refreshQueue()
    }

    fun refreshQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val owner = container.session.owner()
            if (owner.isBlank()) {
                _counts.value = OutboxCounts(0, 0)
                _conflicts.value = emptyList()
            } else {
                _counts.value = container.db.outboxCounts(owner)
                _conflicts.value = container.db.conflicts(owner)
            }
        }
    }

    fun resolveOverwrite(id: Long) {
        viewModelScope.launch {
            container.dailyRepository.resolveOverwrite(id)
            _messages.emit("已用本地版本覆盖服务器")
            refreshQueue()
        }
    }

    fun resolveKeepServer(id: Long) {
        viewModelScope.launch {
            container.dailyRepository.resolveKeepServer(id)
            _messages.emit("已保留服务器版本")
            refreshQueue()
        }
    }

    fun setTheme(mode: ThemeMode) {
        container.settings.setTheme(mode)
    }

    fun logout() {
        viewModelScope.launch {
            container.session.logout()
            _messages.emit("已退出登录")
        }
    }

    fun retryQueue() {
        container.syncEngine.let { engine ->
            viewModelScope.launch {
                when (val result = withContext(Dispatchers.IO) { engine.drain() }) {
                    is com.hmilyld.weeklyreport.data.sync.SyncResult.Done ->
                        _messages.emit(if (result.sent > 0) "已发送 ${result.sent} 条离线修改" else "队列已清空")
                    is com.hmilyld.weeklyreport.data.sync.SyncResult.NeedsLogin ->
                        _messages.emit(result.reason)
                    is com.hmilyld.weeklyreport.data.sync.SyncResult.Stopped ->
                        _messages.emit(result.reason)
                }
                refreshQueue()
            }
        }
    }
}
