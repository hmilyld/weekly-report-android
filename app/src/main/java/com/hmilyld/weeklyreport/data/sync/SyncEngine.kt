package com.hmilyld.weeklyreport.data.sync

import android.util.Log
import com.hmilyld.weeklyreport.data.SessionManager
import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.local.OutboxItem
import com.hmilyld.weeklyreport.data.local.OutboxKind
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.network.ApiPayloads
import com.hmilyld.weeklyreport.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

sealed interface SyncResult {
    data class Done(val sent: Int) : SyncResult
    data class Stopped(val reason: String, val transient: Boolean) : SyncResult
    data class NeedsLogin(val reason: String) : SyncResult
}

/**
 * Drains the outbox in insertion order. Each write is verified against the
 * server first: a day that changed remotely since our baseline is marked as a
 * conflict and left alone until the user explicitly chooses.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val api: ApiClient,
    private val session: SessionManager,
    private val onChanged: () -> Unit,
) {
    private val mutex = Mutex()

    suspend fun drain(): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = session.owner()
            if (owner.isBlank()) return@withLock SyncResult.Stopped("未登录，暂不发送", transient = false)
            // A locked session has no token in memory. Sending unauthenticated
            // requests would turn a 401 into a logout, so wait for unlock.
            if (session.tokenOrNull() == null) {
                return@withLock SyncResult.Stopped("会话已锁定，解锁后再同步", transient = false)
            }
            var sent = 0
            var changed = false
            while (true) {
                val item = db.nextOutbox(owner) ?: break
                Log.i(TAG, "drain ${item.kind} key=${item.entityKey} id=${item.id} rev=${item.revision}")
                try {
                    val outcome = handle(item)
                    when (outcome) {
                        Outcome.Sent -> {
                            sent += 1
                            changed = true
                        }
                        Outcome.Conflict -> changed = true
                    }
                } catch (error: ApiException) {
                    Log.w(TAG, "failed ${item.kind} key=${item.entityKey}: ${error.status} ${error.message}")
                    if (error.requiresLogin) {
                        db.markOutboxError(item.id, error.message)
                        if (changed) onChanged()
                        return@withLock SyncResult.NeedsLogin(error.message)
                    }
                    db.markOutboxError(item.id, error.message)
                    if (changed) onChanged()
                    return@withLock SyncResult.Stopped(
                        error.message,
                        transient = error.kind == ApiErrorKind.NETWORK || (error.status ?: 0) >= 500,
                    )
                }
            }
            if (changed) onChanged()
            SyncResult.Done(sent)
        }
    }

    private suspend fun handle(item: OutboxItem): Outcome = when (item.kind) {
        OutboxKind.DAILY_SAVE -> sendDailySave(item)
        OutboxKind.DAILY_DELETE -> sendDailyDelete(item)
        OutboxKind.TASK_CREATE -> sendTaskCreate(item)
        OutboxKind.TASK_UPDATE -> sendTaskUpdate(item)
        OutboxKind.TASK_DELETE -> sendTaskDelete(item)
    }

    private suspend fun sendDailySave(item: OutboxItem): Outcome {
        val date = item.entityKey
        val server = api.dailyRange(date, date).reports.firstOrNull { it.date == date }
        val cached = db.dailyByDate(date)
        val decision = ReplayRules.dailyDecision(
            serverContent = server?.content,
            baseline = cached?.serverContent,
            localContent = item.payload,
        )
        return when (decision) {
            ReplayRules.Decision.AlreadySynced -> {
                db.markDailyAlreadySynced(date, server?.content.orEmpty(), server?.updatedAt)
                db.deleteOutbox(item.id)
                Outcome.Sent
            }
            ReplayRules.Decision.Conflict -> {
                // Do not overwrite silently: stop this row and surface the choice.
                db.markConflict(item.id, server?.content, server?.updatedAt)
                Log.w(TAG, "conflict on $date; server and local both changed")
                Outcome.Conflict
            }
            ReplayRules.Decision.Send -> {
                val saved = api.saveDaily(date, item.payload)
                db.markDailyPushed(date, item.payload, saved.updatedAt)
                db.deleteOutbox(item.id)
                Outcome.Sent
            }
        }
    }

    private suspend fun sendDailyDelete(item: OutboxItem): Outcome {
        val date = item.entityKey
        val server = api.dailyRange(date, date).reports.firstOrNull { it.date == date }
        if (server == null) {
            db.markDailyRemoved(date)
            db.deleteOutbox(item.id)
            return Outcome.Sent
        }
        val cached = db.dailyByDate(date)
        val unchanged = cached?.serverContent == server.content ||
            (cached?.serverContent.isNullOrEmpty() && server.content.isEmpty())
        if (!unchanged) {
            db.markConflict(item.id, server.content, server.updatedAt)
            Log.w(TAG, "delete conflict on $date")
            return Outcome.Conflict
        }
        try {
            api.deleteDaily(date)
        } catch (error: ApiException) {
            if (!error.isNotFound) throw error
        }
        db.markDailyRemoved(date)
        db.deleteOutbox(item.id)
        return Outcome.Sent
    }

    private suspend fun sendTaskCreate(item: OutboxItem): Outcome {
        val payload = decodePayload(item) ?: return Outcome.Conflict
        val dto = api.createTask(payload.content, payload.deadline)
        val local = db.localTask(item.entityKey)
        if (local == null || local.deleted) {
            // Deleted locally while the create was in flight; undo the remote row.
            runCatching { api.deleteTask(dto.id) }
            db.finishTaskDelete(item.id, item.entityKey)
            return Outcome.Sent
        }
        db.finishTaskCreate(
            outboxId = item.id,
            revision = item.revision,
            localId = item.entityKey,
            remoteId = dto.id,
            content = dto.content,
            deadline = dto.deadline,
            isCompleted = dto.isCompleted,
            remoteUpdatedAt = dto.updatedAt,
        )
        return Outcome.Sent
    }

    private suspend fun sendTaskUpdate(item: OutboxItem): Outcome {
        val payload = decodePayload(item) ?: return Outcome.Conflict
        val local = db.localTask(item.entityKey)
        if (local == null) {
            db.deleteOutbox(item.id)
            return Outcome.Sent
        }
        val remoteId = local.remoteId
        if (remoteId == null) {
            // The create was still queued when this update was written.
            db.markTaskOutboxKindUpdate(item.id)
            return Outcome.Sent
        }
        val deadlineDate = Dates.parseIso(payload.deadline)
        // The backend rejects a past deadline even when the edit does not
        // touch it. Omit it in that case; the UI prevents new past deadlines,
        // so this only guards stale coalesced payloads.
        val includeDeadline = payload.includeDeadline &&
            (deadlineDate == null || !deadlineDate.isBefore(java.time.LocalDate.now()))
        val dto = try {
            api.updateTask(
                id = remoteId,
                content = payload.content,
                deadline = payload.deadline,
                includeDeadline = includeDeadline,
                isCompleted = payload.isCompleted,
            )
        } catch (error: ApiException) {
            if (error.isNotFound) {
                db.finishTaskDelete(item.id, item.entityKey)
                return Outcome.Sent
            }
            throw error
        }
        db.finishTaskUpdate(
            outboxId = item.id,
            revision = item.revision,
            localId = item.entityKey,
            content = dto.content,
            deadline = dto.deadline,
            isCompleted = dto.isCompleted,
            remoteUpdatedAt = dto.updatedAt,
        )
        return Outcome.Sent
    }

    private suspend fun sendTaskDelete(item: OutboxItem): Outcome {
        val local = db.localTask(item.entityKey)
        val remoteId = local?.remoteId
        if (remoteId != null) {
            try {
                api.deleteTask(remoteId)
            } catch (error: ApiException) {
                if (!error.isNotFound) throw error
            }
        }
        db.finishTaskDelete(item.id, item.entityKey)
        return Outcome.Sent
    }

    private fun decodePayload(item: OutboxItem): SyncTaskPayload? = try {
        ApiPayloads.json.decodeFromString(SyncTaskPayload.serializer(), item.payload)
    } catch (error: SerializationException) {
        Log.e(TAG, "bad payload for ${item.kind} ${item.entityKey}", error)
        db.markOutboxError(item.id, "本地队列数据损坏")
        null
    }

    private enum class Outcome { Sent, Conflict }

    companion object {
        private const val TAG = "WRSync"
    }
}
