package com.hmilyld.weeklyreport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.R
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

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
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
            _messages.emit(UiMessage.Text(R.string.app_conflict_use_local_done))
            refreshQueue()
        }
    }

    fun resolveKeepServer(id: Long) {
        viewModelScope.launch {
            container.dailyRepository.resolveKeepServer(id)
            _messages.emit(UiMessage.Text(R.string.app_conflict_keep_server_done))
            refreshQueue()
        }
    }

    fun setTheme(mode: ThemeMode) {
        container.settings.setTheme(mode)
    }

    fun logout() {
        viewModelScope.launch {
            container.session.logout()
            _messages.emit(UiMessage.Text(R.string.common_logged_out))
        }
    }

    fun retryQueue() {
        container.syncEngine.let { engine ->
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    container.db.requeueFailed(container.session.owner())
                    engine.drain()
                }
                when (result) {
                    is com.hmilyld.weeklyreport.data.sync.SyncResult.Done ->
                        _messages.emit(
                            if (result.sent > 0) {
                                UiMessage.Text(R.string.common_queue_sent, listOf(result.sent))
                            } else {
                                UiMessage.Text(R.string.app_queue_cleared)
                            }
                        )

                    is com.hmilyld.weeklyreport.data.sync.SyncResult.NeedsLogin ->
                        _messages.emit(UiMessage.Raw(result.reason))

                    is com.hmilyld.weeklyreport.data.sync.SyncResult.Stopped ->
                        _messages.emit(UiMessage.Raw(result.reason))
                }
                refreshQueue()
            }
        }
    }
}
