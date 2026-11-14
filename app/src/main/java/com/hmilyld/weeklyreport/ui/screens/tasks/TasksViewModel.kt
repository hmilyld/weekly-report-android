package com.hmilyld.weeklyreport.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.data.local.LocalTask
import com.hmilyld.weeklyreport.data.repo.TaskRepository
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.util.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksUiState(
    val active: List<LocalTask> = emptyList(),
    val completed: List<LocalTask> = emptyList(),
    val completedLimit: Int = PAGE_SIZE,
    val totalCompleted: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val loadingMore: Boolean = false,
) {
    val hasMoreCompleted: Boolean get() = completed.size < totalCompleted

    companion object {
        const val PAGE_SIZE = 20
    }
}

class TasksViewModel(
    private val repo: TaskRepository,
    changes: Flow<Long>,
) : ViewModel() {
    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            changes.drop(1).collect { reloadLocal() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.active.isEmpty() && it.completed.isEmpty()) }
            reloadLocal()
            _state.update { it.copy(refreshing = true) }
            try {
                repo.refresh()
                repo.loadCompletedPage(0, TasksUiState.PAGE_SIZE)
                _state.update {
                    it.copy(offline = false, error = null, totalCompleted = maxOf(it.totalCompleted, it.completed.size))
                }
            } catch (error: ApiException) {
                if (error.requiresLogin) {
                    _state.update { it.copy(loading = false, refreshing = false) }
                    return@launch
                }
                val offline = error.kind == ApiErrorKind.NETWORK
                _state.update {
                    it.copy(
                        offline = offline,
                        error = if (offline) null else error.message,
                    )
                }
                if (!offline) _messages.emit(error.message)
            } finally {
                reloadLocal()
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun loadMoreCompleted() {
        val current = _state.value
        if (current.loadingMore || !current.hasMoreCompleted) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                repo.loadCompletedPage(current.completed.size, TasksUiState.PAGE_SIZE)
                _state.update { it.copy(completedLimit = it.completedLimit + TasksUiState.PAGE_SIZE) }
                reloadLocal()
            } catch (error: ApiException) {
                _messages.emit(error.message)
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun create(content: String, deadline: LocalDate?) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _messages.emit("请输入待办内容") }
            return
        }
        if (deadline != null && deadline.isBefore(LocalDate.now())) {
            viewModelScope.launch { _messages.emit("截止日期不能早于今天") }
            return
        }
        viewModelScope.launch {
            repo.create(trimmed, deadline?.let(Dates::iso))
            _messages.emit("已添加待办")
            reloadLocal()
        }
    }

    fun toggle(localId: String) {
        viewModelScope.launch {
            repo.toggle(localId)
            reloadLocal()
        }
    }

    fun edit(localId: String, content: String, deadline: LocalDate?, deadlineChanged: Boolean) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _messages.emit("待办内容不能为空") }
            return
        }
        if (deadlineChanged && deadline != null && deadline.isBefore(LocalDate.now())) {
            viewModelScope.launch { _messages.emit("截止日期不能早于今天") }
            return
        }
        viewModelScope.launch {
            repo.edit(localId, trimmed, deadline?.let(Dates::iso), deadlineChanged)
            _messages.emit("已保存修改")
            reloadLocal()
        }
    }

    fun delete(localId: String) {
        viewModelScope.launch {
            repo.delete(localId)
            _messages.emit("已删除待办")
            reloadLocal()
        }
    }

    private suspend fun reloadLocal() {
        val tasks = repo.localTasks()
        val active = tasks.filter { !it.isCompleted }
            .sortedWith(
                compareBy<LocalTask> { it.deadline == null }
                    .thenBy { it.deadline.orEmpty() }
                    .thenByDescending { it.createdAt },
            )
        val completed = tasks.filter { it.isCompleted }
            .sortedWith(
                compareByDescending<LocalTask> { it.deadline != null }
                    .thenByDescending { it.deadline.orEmpty() }
                    .thenByDescending { it.createdAt },
            )
        _state.update { current ->
            current.copy(
                active = active,
                completed = completed.take(current.completedLimit),
                totalCompleted = maxOf(current.totalCompleted, completed.size),
            )
        }
    }
}
