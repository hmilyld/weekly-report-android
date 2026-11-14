package com.hmilyld.weeklyreport.ui.screens.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.data.local.CachedDay
import com.hmilyld.weeklyreport.data.repo.DailyRepository
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.util.Dates
import com.hmilyld.weeklyreport.util.ListInput
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

data class DailyDayUi(
    val date: LocalDate,
    val content: String,
    val pending: Boolean,
    val deleted: Boolean,
    val isToday: Boolean,
) {
    val body: String get() = ListInput.bodyWithoutMarkers(content).trim()
    val hasRecord: Boolean get() = !deleted && body.isNotEmpty()
}

data class EditorState(
    val date: LocalDate,
    val initialContent: String,
    val seeded: Boolean,
    val hasRecord: Boolean,
)

data class DailyUiState(
    val monday: LocalDate = Dates.mondayOf(Dates.today()),
    val days: List<DailyDayUi> = emptyList(),
    val loading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
) {
    val weekCount: Int get() = days.count { it.hasRecord }
    val isCurrentWeek: Boolean get() = monday == Dates.mondayOf(LocalDate.now())
}

class DailyViewModel(
    private val repo: DailyRepository,
    changes: Flow<Long>,
) : ViewModel() {
    private val _state = MutableStateFlow(DailyUiState())
    val state: StateFlow<DailyUiState> = _state.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var loadedOnce = false

    init {
        load()
        viewModelScope.launch {
            changes.drop(1).collect { loadInternal(showLoading = false) }
        }
    }

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch { loadInternal(showLoading) }
    }

    private suspend fun loadInternal(showLoading: Boolean) {
        val monday = _state.value.monday
        if (showLoading) _state.update { it.copy(loading = true) }
        val local = repo.weekDays(monday)
        _state.update { it.copy(days = local.toRows(monday)) }
        try {
            repo.refreshWeek(monday)
            val merged = repo.weekDays(monday)
            _state.update {
                it.copy(days = merged.toRows(monday), offline = false, error = null, loading = false)
            }
        } catch (error: ApiException) {
            if (error.requiresLogin) {
                _state.update { it.copy(loading = false) }
                return
            }
            val offline = error.kind == ApiErrorKind.NETWORK
            _state.update {
                it.copy(
                    loading = false,
                    offline = offline,
                    error = if (offline) null else error.message,
                )
            }
            if (!offline) _messages.emit(error.message)
        }
        loadedOnce = true
    }

    fun previousWeek() {
        _state.update { it.copy(monday = it.monday.minusDays(7)) }
        load()
    }

    fun nextWeek() {
        _state.update { it.copy(monday = it.monday.plusDays(7)) }
        load()
    }

    fun thisWeek() {
        _state.update { it.copy(monday = Dates.mondayOf(LocalDate.now())) }
        load()
    }

    fun openEditor(date: LocalDate) {
        viewModelScope.launch {
            val cached = repo.day(date)
            _editor.value = editorState(date, cached)
        }
    }

    /** Entry point for the widget: jump to the week of [date] and open its editor. */
    fun openDateFromWidget(date: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(monday = Dates.mondayOf(date)) }
            if (loadedOnce || _state.value.days.isEmpty()) loadInternal(showLoading = false)
            val cached = repo.day(date)
            _editor.value = editorState(date, cached)
        }
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun save(content: String) {
        val editor = _editor.value ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                repo.save(editor.date, content)
                _editor.value = null
                _messages.emit("已保存到本机，联网后自动同步")
                loadInternal(showLoading = false)
            } catch (error: ApiException) {
                _messages.emit(error.message)
            } finally {
                _saving.value = false
            }
        }
    }

    fun delete() {
        val editor = _editor.value ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                repo.delete(editor.date)
                _editor.value = null
                _messages.emit("已删除，联网后同步")
                loadInternal(showLoading = false)
            } catch (error: ApiException) {
                _messages.emit(error.message)
            } finally {
                _saving.value = false
            }
        }
    }

    private fun editorState(date: LocalDate, cached: CachedDay?): EditorState {
        val content = cached?.takeIf { !it.deleted }?.content.orEmpty()
        val seeded = content.isBlank()
        val hasRecord = ListInput.bodyWithoutMarkers(content).trim().isNotEmpty() && !cached?.deleted.orFalse()
        return EditorState(
            date = date,
            initialContent = if (seeded) ListInput.FIRST_ITEM else content,
            seeded = seeded,
            hasRecord = hasRecord || cached?.pending == true,
        )
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    private fun List<CachedDay?>.toRows(monday: LocalDate): List<DailyDayUi> {
        val today = LocalDate.now()
        val byDate = filterNotNull().associateBy { it.date }
        return Dates.weekDates(monday).map { date ->
            val row = byDate[Dates.iso(date)]
            DailyDayUi(
                date = date,
                content = row?.content.orEmpty(),
                pending = row?.pending == true,
                deleted = row?.deleted == true,
                isToday = date == today,
            )
        }
    }
}
