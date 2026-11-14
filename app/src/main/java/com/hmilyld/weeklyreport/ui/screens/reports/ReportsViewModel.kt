package com.hmilyld.weeklyreport.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.repo.ReportRepository
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.ui.UiMessage
import com.hmilyld.weeklyreport.util.Dates
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReportCadence { WEEKLY, MONTHLY }

data class ReportUiState(
    val cadence: ReportCadence = ReportCadence.WEEKLY,
    val monday: LocalDate = Dates.mondayOf(LocalDate.now()),
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val content: String = "",
    val modelName: String = "",
    val generatedAt: String? = null,
    val loading: Boolean = true,
    val generating: Boolean = false,
    val saving: Boolean = false,
    val notGenerated: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val editing: Boolean = false,
    val draft: String = ""
) {
    val dirty: Boolean get() = editing && draft != content
    val periodKey: String
        get() = if (cadence == ReportCadence.WEEKLY) {
            "W:${Dates.iso(monday)}"
        } else {
            "M:$year-$month"
        }
    val isCurrentPeriod: Boolean
        get() = if (cadence == ReportCadence.WEEKLY) {
            monday == Dates.mondayOf(LocalDate.now())
        } else {
            val now = LocalDate.now()
            year == now.year && month == now.monthValue
        }
    val canSave: Boolean get() = editing && draft != content && draft.isNotBlank()
}

class ReportsViewModel(private val repo: ReportRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var loadSeq = 0

    init {
        load()
    }

    fun selectCadence(cadence: ReportCadence) {
        if (_state.value.generating || _state.value.saving) return
        if (_state.value.cadence == cadence) return
        _state.update { it.copy(cadence = cadence, editing = false, draft = "") }
        load()
    }

    fun previous() {
        if (_state.value.generating || _state.value.saving) return
        _state.update {
            if (it.cadence == ReportCadence.WEEKLY) {
                it.copy(monday = it.monday.minusDays(7))
            } else {
                val date = LocalDate.of(it.year, it.month, 1).minusMonths(1)
                it.copy(year = date.year, month = date.monthValue)
            }.copy(editing = false, draft = "")
        }
        load()
    }

    fun next() {
        if (_state.value.generating || _state.value.saving) return
        _state.update {
            if (it.cadence == ReportCadence.WEEKLY) {
                it.copy(monday = it.monday.plusDays(7))
            } else {
                val date = LocalDate.of(it.year, it.month, 1).plusMonths(1)
                it.copy(year = date.year, month = date.monthValue)
            }.copy(editing = false, draft = "")
        }
        load()
    }

    fun current() {
        if (_state.value.generating || _state.value.saving) return
        val now = LocalDate.now()
        _state.update {
            if (it.cadence == ReportCadence.WEEKLY) {
                it.copy(monday = Dates.mondayOf(now))
            } else {
                it.copy(year = now.year, month = now.monthValue)
            }.copy(editing = false, draft = "")
        }
        load()
    }

    fun load() {
        val state = _state.value
        val seq = ++loadSeq
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notGenerated = false, offline = false) }
            try {
                if (state.cadence == ReportCadence.WEEKLY) {
                    val report = repo.weekly(Dates.iso(state.monday))
                    if (seq == loadSeq) applyReport(report?.content, report?.modelName, report?.generatedAt)
                } else {
                    val report = repo.monthly(state.year, state.month)
                    if (seq == loadSeq) applyReport(report?.content, report?.modelName, report?.generatedAt)
                }
            } catch (error: ApiException) {
                if (seq != loadSeq) return@launch
                if (error.requiresLogin) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                if (error.kind == ApiErrorKind.NETWORK) {
                    val cached = if (state.cadence == ReportCadence.WEEKLY) {
                        repo.cachedWeekly(Dates.iso(state.monday))
                    } else {
                        repo.cachedMonthly(state.year, state.month)
                    }
                    if (cached != null) {
                        _state.update {
                            it.copy(
                                content = cached.content,
                                modelName = cached.modelName,
                                generatedAt = cached.generatedAt,
                                offline = true,
                                notGenerated = false,
                                loading = false,
                                editing = false
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                offline = true,
                                error = error.message,
                                loading = false,
                                notGenerated = true
                            )
                        }
                    }
                } else {
                    _state.update {
                        it.copy(error = error.message, loading = false)
                    }
                    _messages.emit(UiMessage.Raw(error.message))
                }
            } finally {
                if (seq == loadSeq) _state.update { it.copy(loading = false) }
            }
        }
    }

    fun generate() {
        val state = _state.value
        if (state.generating || state.saving) return
        val requestPeriod = state.periodKey
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            try {
                val content: String
                val modelName: String
                val generatedAt: String?
                if (state.cadence == ReportCadence.WEEKLY) {
                    val report = repo.generateWeekly(Dates.iso(state.monday))
                    content = report.content
                    modelName = report.modelName
                    generatedAt = report.generatedAt
                } else {
                    val report = repo.generateMonthly(state.year, state.month)
                    content = report.content
                    modelName = report.modelName
                    generatedAt = report.generatedAt
                }
                if (_state.value.periodKey != requestPeriod) return@launch
                applyReport(content, modelName, generatedAt)
                _state.update { it.copy(notGenerated = false) }
                _messages.emit(
                    if (state.cadence == ReportCadence.WEEKLY) {
                        UiMessage.Text(R.string.reports_generated_weekly)
                    } else {
                        UiMessage.Text(R.string.reports_generated_monthly)
                    }
                )
            } catch (error: ApiException) {
                if (!error.requiresLogin) {
                    _state.update { it.copy(error = error.message) }
                    _messages.emit(UiMessage.Raw(error.message))
                }
            } finally {
                _state.update { it.copy(generating = false) }
            }
        }
    }

    fun startEdit() {
        if (_state.value.generating || _state.value.saving) return
        _state.update { it.copy(editing = true, draft = it.content) }
    }

    fun updateDraft(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editing = false, draft = "") }
    }

    fun save() {
        val state = _state.value
        if (!state.canSave || state.saving || state.generating) return
        val requestPeriod = state.periodKey
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val content: String
                val modelName: String
                val generatedAt: String?
                if (state.cadence == ReportCadence.WEEKLY) {
                    val report = repo.updateWeekly(Dates.iso(state.monday), state.draft)
                    content = report.content
                    modelName = report.modelName
                    generatedAt = report.generatedAt
                } else {
                    val report = repo.updateMonthly(state.year, state.month, state.draft)
                    content = report.content
                    modelName = report.modelName
                    generatedAt = report.generatedAt
                }
                if (_state.value.periodKey != requestPeriod) return@launch
                applyReport(content, modelName, generatedAt)
                _state.update { it.copy(editing = false, draft = "") }
                _messages.emit(UiMessage.Text(R.string.reports_saved))
            } catch (error: ApiException) {
                if (!error.requiresLogin) {
                    _state.update { it.copy(error = error.message) }
                    _messages.emit(UiMessage.Raw(error.message))
                }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun applyReport(content: String?, modelName: String?, generatedAt: String?) {
        if (content == null) {
            _state.update {
                it.copy(
                    content = "",
                    modelName = "",
                    generatedAt = null,
                    notGenerated = true,
                    loading = false,
                    editing = false
                )
            }
        } else {
            _state.update {
                it.copy(
                    content = content,
                    modelName = modelName.orEmpty(),
                    generatedAt = generatedAt,
                    notGenerated = false,
                    loading = false,
                    editing = false
                )
            }
        }
    }
}
