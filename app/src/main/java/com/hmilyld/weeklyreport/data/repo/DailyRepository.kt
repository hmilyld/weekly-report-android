package com.hmilyld.weeklyreport.data.repo

import com.hmilyld.weeklyreport.data.SessionManager
import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.local.CachedDay
import com.hmilyld.weeklyreport.data.local.OutboxCounts
import com.hmilyld.weeklyreport.data.local.OutboxKind
import com.hmilyld.weeklyreport.data.sync.SyncScheduler
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.util.Dates
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyRepository(
    private val db: AppDatabase,
    private val api: ApiClient,
    private val session: SessionManager,
    private val scheduler: SyncScheduler,
    private val onLocalChange: () -> Unit
) {
    fun owner(): String = session.owner()

    suspend fun refreshWeek(monday: LocalDate) = withContext(Dispatchers.IO) {
        val start = Dates.iso(monday)
        val end = Dates.iso(monday.plusDays(6))
        val response = api.dailyWeek(start)
        val reports = response.reports.map { it.date to (it.content to it.updatedAt) }
        db.mergeWeekFromServer(start, end, reports)
    }

    suspend fun weekDays(monday: LocalDate): List<CachedDay?> = withContext(Dispatchers.IO) {
        val rows = db.dailyForWeek(Dates.iso(monday), Dates.iso(monday.plusDays(6))).associateBy { it.date }
        Dates.weekDates(monday).map { rows[Dates.iso(it)] }
    }

    suspend fun day(date: LocalDate): CachedDay? = withContext(Dispatchers.IO) { db.dailyByDate(Dates.iso(date)) }

    suspend fun save(date: LocalDate, content: String) = withContext(Dispatchers.IO) {
        val key = Dates.iso(date)
        val existing = db.dailyByDate(key)
        db.upsertDraft(key, content)
        db.enqueueDaily(
            kind = OutboxKind.DAILY_SAVE,
            date = key,
            payload = content,
            baseUpdatedAt = existing?.serverUpdatedAt,
            owner = owner()
        )
        onLocalChange()
        scheduler.requestDrain()
    }

    suspend fun delete(date: LocalDate) = withContext(Dispatchers.IO) {
        val key = Dates.iso(date)
        val existing = db.dailyByDate(key)
        db.markDailyDeleted(key)
        db.enqueueDaily(
            kind = OutboxKind.DAILY_DELETE,
            date = key,
            payload = "",
            baseUpdatedAt = existing?.serverUpdatedAt,
            owner = owner()
        )
        onLocalChange()
        scheduler.requestDrain()
    }

    suspend fun counts(): OutboxCounts = withContext(Dispatchers.IO) { db.outboxCounts(owner()) }

    suspend fun conflicts() = withContext(Dispatchers.IO) { db.conflicts(owner()) }

    suspend fun resolveOverwrite(conflictId: Long) = withContext(Dispatchers.IO) {
        db.resolveConflictOverwrite(conflictId)
        onLocalChange()
        scheduler.requestDrain()
    }

    suspend fun resolveKeepServer(conflictId: Long) = withContext(Dispatchers.IO) {
        db.resolveConflictKeepServer(conflictId)
        onLocalChange()
    }
}
