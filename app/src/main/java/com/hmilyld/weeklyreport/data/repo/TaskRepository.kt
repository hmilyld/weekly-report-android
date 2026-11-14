package com.hmilyld.weeklyreport.data.repo

import com.hmilyld.weeklyreport.data.SessionManager
import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.local.LocalTask
import com.hmilyld.weeklyreport.data.local.OutboxCounts
import com.hmilyld.weeklyreport.data.local.OutboxKind
import com.hmilyld.weeklyreport.data.local.ServerTask
import com.hmilyld.weeklyreport.data.model.TaskDto
import com.hmilyld.weeklyreport.data.sync.SyncScheduler
import com.hmilyld.weeklyreport.data.sync.SyncTaskPayload
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.network.ApiPayloads
import com.hmilyld.weeklyreport.util.Dates
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskRepository(
    private val db: AppDatabase,
    private val api: ApiClient,
    private val session: SessionManager,
    private val scheduler: SyncScheduler,
    private val onLocalChange: () -> Unit,
) {
    fun localTasks(): List<LocalTask> = db.allLocalTasks().filter { !it.deleted }

    suspend fun refresh() =
        withContext(Dispatchers.IO) {
            val response = api.tasks()
            db.mergeServerTasks(response.tasks.map { it.toServerTask() }, pruneMissing = true)
            onLocalChange()
        }

    suspend fun loadCompletedPage(
        offset: Int,
        limit: Int,
    ) = withContext(Dispatchers.IO) {
        val response = api.completedTasks(offset, limit)
        db.mergeServerTasks(response.tasks.map { it.toServerTask() }, pruneMissing = false)
        onLocalChange()
    }

    suspend fun create(
        content: String,
        deadline: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val localId = UUID.randomUUID().toString()
            db.insertLocalTask(
                localId,
                content,
                deadline,
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
            )
            enqueue(
                localId = localId,
                kind = OutboxKind.TASK_CREATE,
                payload = SyncTaskPayload(
                    content = content,
                    deadline = deadline,
                    includeDeadline = true,
                    isCompleted = false,
                ),
            )
            onLocalChange()
            scheduler.requestDrain()
            localId
        }

    suspend fun toggle(localId: String) =
        withContext(Dispatchers.IO) {
            val task = db.localTask(localId) ?: return@withContext
            val completed = !task.isCompleted
            db.updateLocalTask(localId, task.content, task.deadline, completed)
            enqueue(
                localId = localId,
                kind = if (task.remoteId == null) {
                    OutboxKind.TASK_CREATE
                } else {
                    OutboxKind.TASK_UPDATE
                },
                payload = SyncTaskPayload(
                    content = task.content,
                    deadline = task.deadline,
                    includeDeadline = previousIncludeDeadline(localId),
                    isCompleted = completed,
                ),
            )
            onLocalChange()
            scheduler.requestDrain()
        }

    suspend fun edit(
        localId: String,
        content: String,
        deadline: String?,
        deadlineChanged: Boolean,
    ) = withContext(Dispatchers.IO) {
        val task = db.localTask(localId) ?: return@withContext
        db.updateLocalTask(localId, content, deadline, task.isCompleted)
        enqueue(
            localId = localId,
            kind = if (task.remoteId == null) {
                OutboxKind.TASK_CREATE
            } else {
                OutboxKind.TASK_UPDATE
            },
            payload = SyncTaskPayload(
                content = content,
                deadline = deadline,
                includeDeadline = deadlineChanged || previousIncludeDeadline(localId),
                isCompleted = task.isCompleted,
            ),
        )
        onLocalChange()
        scheduler.requestDrain()
    }

    suspend fun delete(localId: String) =
        withContext(Dispatchers.IO) {
            val task = db.localTask(localId) ?: return@withContext
            if (task.remoteId == null) {
                db.deleteOutboxForEntity(localId)
                db.removeLocalTask(localId)
            } else {
                db.markLocalTaskDeleted(localId)
                enqueue(
                    localId = localId,
                    kind = OutboxKind.TASK_DELETE,
                    payload = SyncTaskPayload(content = task.content),
                )
                onLocalChange()
                scheduler.requestDrain()
            }
        }

    suspend fun counts(): OutboxCounts =
        withContext(Dispatchers.IO) { db.outboxCounts(session.owner()) }

    private fun enqueue(
        localId: String,
        kind: OutboxKind,
        payload: SyncTaskPayload,
    ) {
        db.enqueueTask(
            kind = kind,
            localId = localId,
            payload = ApiPayloads.json.encodeToString(SyncTaskPayload.serializer(), payload),
            owner = session.owner(),
        )
    }

    /** Keep a deadline edit alive across a later toggle in the same offline batch. */
    private fun previousIncludeDeadline(localId: String): Boolean =
        db.outboxForEntity(localId).any { item ->
            runCatching {
                ApiPayloads.json.decodeFromString(
                    SyncTaskPayload.serializer(),
                    item.payload,
                ).includeDeadline
            }.getOrDefault(false)
        }

    private fun TaskDto.toServerTask() =
        ServerTask(
            remoteId = id,
            content = content,
            deadline = deadline,
            isCompleted = isCompleted,
            updatedAt = updatedAt,
            createdAt = Dates.parseServerInstant(createdAt)?.toEpochMilli()
                ?: Instant.now().toEpochMilli(),
        )
}
