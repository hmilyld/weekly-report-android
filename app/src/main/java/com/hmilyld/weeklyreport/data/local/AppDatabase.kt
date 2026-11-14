package com.hmilyld.weeklyreport.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class OutboxKind { DAILY_SAVE, DAILY_DELETE, TASK_CREATE, TASK_UPDATE, TASK_DELETE }
enum class OutboxState { PENDING, CONFLICT, FAILED }

data class CachedDay(
    val date: String,
    val content: String,
    val serverContent: String?,
    val serverUpdatedAt: String?,
    val pending: Boolean,
    val deleted: Boolean,
)

data class LocalTask(
    val localId: String,
    val remoteId: Int?,
    val content: String,
    val deadline: String?,
    val isCompleted: Boolean,
    val dirty: Boolean,
    val deleted: Boolean,
    val createdAt: Long,
)

data class OutboxItem(
    val id: Long,
    val kind: OutboxKind,
    val entityKey: String,
    val payload: String,
    val baseUpdatedAt: String?,
    val state: OutboxState,
    val revision: Long,
    val owner: String,
    val attempts: Int,
    val lastError: String?,
    val serverContent: String?,
    val serverUpdatedAt: String?,
    val createdAt: Long,
)

data class ReportCacheEntry(
    val cacheKey: String,
    val content: String,
    val modelName: String,
    val generatedAt: String?,
)

data class OutboxCounts(val pending: Int, val conflicts: Int, val failed: Int = 0)

/**
 * Plain SQLite storage for offline drafts, the task cache and the send queue.
 * Every method is synchronous and guarded by one lock; callers use Dispatchers.IO.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    private val lock = Any()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_cache (
                date TEXT PRIMARY KEY,
                content TEXT NOT NULL DEFAULT '',
                server_content TEXT,
                server_updated_at TEXT,
                pending INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE tasks_cache (
                local_id TEXT PRIMARY KEY,
                remote_id INTEGER,
                content TEXT NOT NULL,
                deadline TEXT,
                is_completed INTEGER NOT NULL DEFAULT 0,
                remote_updated_at TEXT,
                dirty INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE outbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                entity_key TEXT NOT NULL,
                payload TEXT NOT NULL,
                base_updated_at TEXT,
                state TEXT NOT NULL DEFAULT 'PENDING',
                revision INTEGER NOT NULL DEFAULT 0,
                owner TEXT NOT NULL DEFAULT '',
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                server_content TEXT,
                server_updated_at TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_outbox_state ON outbox(state, owner)")
        db.execSQL("CREATE INDEX idx_outbox_entity ON outbox(entity_key)")
        db.execSQL(
            """
            CREATE TABLE report_cache (
                cache_key TEXT PRIMARY KEY,
                content TEXT NOT NULL,
                model_name TEXT NOT NULL DEFAULT '',
                generated_at TEXT
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ─── Daily cache ────────────────────────────────────

    fun dailyByDate(date: String): CachedDay? = synchronized(lock) {
        readable.query("daily_cache", null, "date = ?", arrayOf(date), null, null, null).use {
            if (it.moveToFirst()) it.toCachedDay() else null
        }
    }

    fun dailyForWeek(start: String, end: String): List<CachedDay> = synchronized(lock) {
        readable.query(
            "daily_cache",
            null,
            "date BETWEEN ? AND ?",
            arrayOf(start, end),
            null,
            null,
            "date ASC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toCachedDay()) }
        }
    }

    fun upsertDraft(date: String, content: String) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val existing = dailyByDateUnlocked(date)
            val values = ContentValues().apply {
                put("content", content)
                put("pending", 1)
                put("deleted", 0)
            }
            if (existing == null) {
                values.put("date", date)
                writable.insertOrThrow("daily_cache", null, values)
            } else {
                writable.update("daily_cache", values, "date = ?", arrayOf(date))
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun markDailyDeleted(date: String) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val existing = dailyByDateUnlocked(date)
            val values = ContentValues().apply {
                put("pending", 1)
                put("deleted", 1)
            }
            if (existing == null) {
                values.put("date", date)
                values.put("content", "")
                writable.insertOrThrow("daily_cache", null, values)
            } else {
                writable.update("daily_cache", values, "date = ?", arrayOf(date))
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    /**
     * Merge a week pulled from the server. A pending local draft keeps its
     * content and baseline: if the remote content changed meanwhile, the old
     * baseline stays so the replay detects a conflict instead of overwriting.
     */
    fun mergeWeekFromServer(start: String, end: String, reports: List<Pair<String, Pair<String, String?>>>) =
        synchronized(lock) {
            writable.beginTransaction()
            try {
                val seen = mutableSetOf<String>()
                for ((date, pair) in reports) {
                    val (serverContent, serverUpdatedAt) = pair
                    seen += date
                    val existing = dailyByDateUnlocked(date)
                    if (existing == null) {
                        writable.insertOrThrow(
                            "daily_cache",
                            null,
                            ContentValues().apply {
                                put("date", date)
                                put("content", serverContent)
                                put("server_content", serverContent)
                                put("server_updated_at", serverUpdatedAt)
                                put("pending", 0)
                                put("deleted", 0)
                            },
                        )
                    } else if (existing.pending) {
                        // Only accept the remote value as a new baseline when it did
                        // not change. Otherwise keep the old baseline so the send
                        // path stops and asks before overwriting.
                        val stale = existing.serverContent
                        val unchanged = (stale.isNullOrEmpty() && serverContent.isEmpty()) || stale == serverContent
                        if (unchanged) {
                            writable.update(
                                "daily_cache",
                                ContentValues().apply {
                                    put("server_content", serverContent)
                                    put("server_updated_at", serverUpdatedAt)
                                },
                                "date = ?",
                                arrayOf(date),
                            )
                        }
                    } else {
                        writable.update(
                            "daily_cache",
                            ContentValues().apply {
                                put("content", serverContent)
                                put("server_content", serverContent)
                                put("server_updated_at", serverUpdatedAt)
                                put("pending", 0)
                                put("deleted", 0)
                            },
                            "date = ?",
                            arrayOf(date),
                        )
                    }
                }
                val where = buildString {
                    append("pending = 0 AND date BETWEEN ? AND ?")
                    if (seen.isNotEmpty()) {
                        append(" AND date NOT IN (")
                        append(seen.joinToString(",") { "?" })
                        append(")")
                    }
                }
                val args = buildList {
                    add(start)
                    add(end)
                    addAll(seen)
                }.toTypedArray()
                writable.delete("daily_cache", where, args)
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
        }

    fun markDailyPushed(date: String, sentContent: String, serverUpdatedAt: String?) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val existing = dailyByDateUnlocked(date) ?: return@synchronized
            val values = ContentValues().apply {
                put("server_content", sentContent)
                put("server_updated_at", serverUpdatedAt)
                if (existing.content == sentContent) {
                    put("pending", 0)
                    put("deleted", 0)
                }
            }
            writable.update("daily_cache", values, "date = ?", arrayOf(date))
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun markDailyAlreadySynced(
        date: String,
        serverContent: String,
        serverUpdatedAt: String?,
    ) = synchronized(lock) {
        val existing = dailyByDateUnlocked(date)
        if (existing == null) {
            writable.insertOrThrow(
                "daily_cache",
                null,
                ContentValues().apply {
                    put("date", date)
                    put("content", serverContent)
                    put("server_content", serverContent)
                    put("server_updated_at", serverUpdatedAt)
                    put("pending", 0)
                    put("deleted", 0)
                },
            )
            return@synchronized
        }
        // The replay found the server already holding the queued payload. If
        // the cache has since moved to a different value, a newer edit exists;
        // only advance the server baseline, never overwrite that edit.
        val values = ContentValues().apply {
            put("server_content", serverContent)
            put("server_updated_at", serverUpdatedAt)
            if (existing.content == serverContent) {
                put("content", serverContent)
                put("pending", 0)
                put("deleted", 0)
            }
        }
        writable.update("daily_cache", values, "date = ?", arrayOf(date))
    }

    fun markDailyRemoved(date: String) = synchronized(lock) {
        writable.delete("daily_cache", "date = ?", arrayOf(date))
    }

    // ─── Tasks cache ────────────────────────────────────

    fun allLocalTasks(): List<LocalTask> = synchronized(lock) {
        readable.query("tasks_cache", null, null, null, null, null, "created_at DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toLocalTask()) }
        }
    }

    fun localTask(localId: String): LocalTask? = synchronized(lock) {
        readable.query("tasks_cache", null, "local_id = ?", arrayOf(localId), null, null, null).use {
            if (it.moveToFirst()) it.toLocalTask() else null
        }
    }

    fun insertLocalTask(localId: String, content: String, deadline: String?, isCompleted: Boolean, createdAt: Long) =
        synchronized(lock) {
            writable.insertWithOnConflict(
                "tasks_cache",
                null,
                ContentValues().apply {
                    put("local_id", localId)
                    put("content", content)
                    put("deadline", deadline)
                    put("is_completed", if (isCompleted) 1 else 0)
                    put("dirty", 1)
                    put("deleted", 0)
                    put("created_at", createdAt)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

    fun updateLocalTask(localId: String, content: String, deadline: String?, isCompleted: Boolean) = synchronized(lock) {
        writable.update(
            "tasks_cache",
            ContentValues().apply {
                put("content", content)
                put("deadline", deadline)
                put("is_completed", if (isCompleted) 1 else 0)
                put("dirty", 1)
            },
            "local_id = ?",
            arrayOf(localId),
        )
    }

    fun markLocalTaskDeleted(localId: String) = synchronized(lock) {
        writable.update(
            "tasks_cache",
            ContentValues().apply {
                put("deleted", 1)
                put("dirty", 1)
            },
            "local_id = ?",
            arrayOf(localId),
        )
    }

    fun removeLocalTask(localId: String) = synchronized(lock) {
        writable.delete("tasks_cache", "local_id = ?", arrayOf(localId))
    }

    fun mergeServerTasks(tasks: List<ServerTask>, pruneMissing: Boolean) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val seen = mutableSetOf<Int>()
            for (task in tasks) {
                seen += task.remoteId
                val existing = taskByRemoteIdUnlocked(task.remoteId)
                if (existing == null) {
                    writable.insertWithOnConflict(
                        "tasks_cache",
                        null,
                        ContentValues().apply {
                            put("local_id", "r:${task.remoteId}")
                            put("remote_id", task.remoteId)
                            put("content", task.content)
                            put("deadline", task.deadline)
                            put("is_completed", if (task.isCompleted) 1 else 0)
                            put("remote_updated_at", task.updatedAt)
                            put("dirty", 0)
                            put("deleted", 0)
                            put("created_at", task.createdAt)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                } else if (!existing.dirty && !existing.deleted) {
                    writable.update(
                        "tasks_cache",
                        ContentValues().apply {
                            put("content", task.content)
                            put("deadline", task.deadline)
                            put("is_completed", if (task.isCompleted) 1 else 0)
                            put("remote_updated_at", task.updatedAt)
                        },
                        "local_id = ?",
                        arrayOf(existing.localId),
                    )
                }
            }
            if (pruneMissing) {
                val placeholders = seen.joinToString(",") { "?" }
                val args = seen.map { it.toString() }.toTypedArray()
                val where = if (seen.isEmpty()) {
                    "dirty = 0 AND deleted = 0 AND remote_id IS NOT NULL"
                } else {
                    "dirty = 0 AND deleted = 0 AND remote_id IS NOT NULL AND remote_id NOT IN ($placeholders)"
                }
                writable.delete("tasks_cache", where, if (seen.isEmpty()) null else args)
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun setTaskRemote(localId: String, remoteId: Int, remoteUpdatedAt: String?) = synchronized(lock) {
        writable.update(
            "tasks_cache",
            ContentValues().apply {
                put("remote_id", remoteId)
                put("remote_updated_at", remoteUpdatedAt)
                put("dirty", 0)
            },
            "local_id = ?",
            arrayOf(localId),
        )
    }

    fun markTaskSynced(localId: String, content: String, deadline: String?, isCompleted: Boolean, remoteUpdatedAt: String?) =
        synchronized(lock) {
            writable.update(
                "tasks_cache",
                ContentValues().apply {
                    put("content", content)
                    put("deadline", deadline)
                    put("is_completed", if (isCompleted) 1 else 0)
                    put("remote_updated_at", remoteUpdatedAt)
                    put("dirty", 0)
                },
                "local_id = ?",
                arrayOf(localId),
            )
        }

    fun finishTaskCreate(
        outboxId: Long,
        revision: Long,
        localId: String,
        remoteId: Int,
        content: String,
        deadline: String?,
        isCompleted: Boolean,
        remoteUpdatedAt: String?,
    ) = synchronized(lock) {
        writable.beginTransaction()
        try {
            markTaskSynced(localId, content, deadline, isCompleted, remoteUpdatedAt)
            setTaskRemote(localId, remoteId, remoteUpdatedAt)
            val item = outboxByIdUnlocked(outboxId)
            when {
                item == null -> Unit
                item.revision == revision -> writable.delete("outbox", "id = ?", arrayOf(outboxId.toString()))
                // A newer edit arrived while the create was in flight: the row
                // already holds that latest payload, so it becomes an update.
                else -> writable.update(
                    "outbox",
                    ContentValues().apply { put("kind", OutboxKind.TASK_UPDATE.name) },
                    "id = ?",
                    arrayOf(outboxId.toString()),
                )
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun finishTaskUpdate(
        outboxId: Long,
        revision: Long,
        localId: String,
        content: String,
        deadline: String?,
        isCompleted: Boolean,
        remoteUpdatedAt: String?,
    ) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val item = outboxByIdUnlocked(outboxId)
            if (item == null || item.revision == revision) {
                markTaskSynced(localId, content, deadline, isCompleted, remoteUpdatedAt)
            }
            if (item != null && item.revision == revision) {
                writable.delete("outbox", "id = ?", arrayOf(outboxId.toString()))
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun finishTaskDelete(outboxId: Long, localId: String) = synchronized(lock) {
        writable.beginTransaction()
        try {
            writable.delete("outbox", "id = ?", arrayOf(outboxId.toString()))
            writable.delete("outbox", "entity_key = ?", arrayOf(localId))
            writable.delete("tasks_cache", "local_id = ?", arrayOf(localId))
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    fun taskByRemoteId(remoteId: Int): LocalTask? = synchronized(lock) { taskByRemoteIdUnlocked(remoteId) }

    // ─── Outbox ─────────────────────────────────────────

    fun enqueueDaily(
        kind: OutboxKind,
        date: String,
        payload: String,
        baseUpdatedAt: String?,
        owner: String,
    ): Long = synchronized(lock) {
        writable.beginTransaction()
        try {
            writable.delete(
                "outbox",
                "entity_key = ? AND (kind IN (?, ?) OR state = ?)",
                arrayOf(date, OutboxKind.DAILY_SAVE.name, OutboxKind.DAILY_DELETE.name, OutboxState.FAILED.name),
            )
            val id = writable.insertOrThrow(
                "outbox",
                null,
                ContentValues().apply {
                    put("kind", kind.name)
                    put("entity_key", date)
                    put("payload", payload)
                    put("base_updated_at", baseUpdatedAt)
                    put("state", OutboxState.PENDING.name)
                    put("owner", owner)
                    put("revision", 1)
                    put("created_at", System.currentTimeMillis())
                },
            )
            writable.setTransactionSuccessful()
            id
        } finally {
            writable.endTransaction()
        }
    }

    fun enqueueTask(kind: OutboxKind, localId: String, payload: String, owner: String): Long = synchronized(lock) {
        writable.beginTransaction()
        try {
            val existing = outboxForEntityUnlocked(localId).firstOrNull()
            val id: Long
            when (kind) {
                OutboxKind.TASK_CREATE -> {
                    if (existing != null && existing.kind == OutboxKind.TASK_CREATE) {
                        writable.update(
                            "outbox",
                            ContentValues().apply {
                                put("payload", payload)
                                put("revision", existing.revision + 1)
                            },
                            "id = ?",
                            arrayOf(existing.id.toString()),
                        )
                        id = existing.id
                    } else {
                        writable.delete("outbox", "entity_key = ?", arrayOf(localId))
                        id = insertTaskOutbox(kind, localId, payload, owner)
                    }
                }
                OutboxKind.TASK_UPDATE -> {
                    if (existing != null && existing.kind == OutboxKind.TASK_CREATE) {
                        writable.update(
                            "outbox",
                            ContentValues().apply {
                                put("payload", payload)
                                put("revision", existing.revision + 1)
                            },
                            "id = ?",
                            arrayOf(existing.id.toString()),
                        )
                        id = existing.id
                    } else {
                        writable.delete(
                            "outbox",
                            "entity_key = ? AND (kind IN (?, ?) OR state = ?)",
                            arrayOf(localId, OutboxKind.TASK_UPDATE.name, OutboxKind.TASK_DELETE.name, OutboxState.FAILED.name),
                        )
                        id = insertTaskOutbox(kind, localId, payload, owner)
                    }
                }
                OutboxKind.TASK_DELETE -> {
                    writable.delete("outbox", "entity_key = ?", arrayOf(localId))
                    id = insertTaskOutbox(kind, localId, payload, owner)
                }
                else -> error("daily kinds go through enqueueDaily")
            }
            writable.setTransactionSuccessful()
            id
        } finally {
            writable.endTransaction()
        }
    }

    fun nextOutbox(owner: String): OutboxItem? = synchronized(lock) {
        readable.query(
            "outbox",
            null,
            "state = ? AND owner = ?",
            arrayOf(OutboxState.PENDING.name, owner),
            null,
            null,
            "id ASC",
            "1",
        ).use { if (it.moveToFirst()) it.toOutbox() else null }
    }

    fun outboxById(id: Long): OutboxItem? = synchronized(lock) {
        readable.query("outbox", null, "id = ?", arrayOf(id.toString()), null, null, null).use {
            if (it.moveToFirst()) it.toOutbox() else null
        }
    }

    fun deleteOutbox(id: Long) = synchronized(lock) {
        writable.delete("outbox", "id = ?", arrayOf(id.toString()))
    }

    fun deleteOutbox(id: Long, revision: Long) = synchronized(lock) {
        writable.delete("outbox", "id = ? AND revision = ?", arrayOf(id.toString(), revision.toString()))
    }

    fun deleteOutboxForEntity(localId: String) = synchronized(lock) {
        writable.delete("outbox", "entity_key = ?", arrayOf(localId))
    }

    fun outboxForEntity(localId: String): List<OutboxItem> = synchronized(lock) {
        outboxForEntityUnlocked(localId)
    }

    fun markOutboxError(id: Long, message: String) = synchronized(lock) {
        writable.execSQL(
            "UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?",
            arrayOf(message, id.toString()),
        )
    }

    /** A permanent 4xx failed this item; park it so it does not block the rest. */
    fun markOutboxFailed(id: Long, message: String) = synchronized(lock) {
        writable.execSQL(
            "UPDATE outbox SET state = ?, attempts = attempts + 1, last_error = ? WHERE id = ?",
            arrayOf(OutboxState.FAILED.name, message, id.toString()),
        )
    }

    /** Explicit user retry: put parked items back into the pending queue. */
    fun requeueFailed(owner: String) = synchronized(lock) {
        writable.execSQL(
            "UPDATE outbox SET state = ?, attempts = 0, last_error = NULL WHERE state = ? AND owner = ?",
            arrayOf(OutboxState.PENDING.name, OutboxState.FAILED.name, owner),
        )
    }

    fun markTaskOutboxKindUpdate(id: Long) = synchronized(lock) {
        writable.update(
            "outbox",
            ContentValues().apply { put("kind", OutboxKind.TASK_UPDATE.name) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun markConflict(id: Long, serverContent: String?, serverUpdatedAt: String?) = synchronized(lock) {
        writable.update(
            "outbox",
            ContentValues().apply {
                put("state", OutboxState.CONFLICT.name)
                put("server_content", serverContent)
                put("server_updated_at", serverUpdatedAt)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun outboxCounts(owner: String): OutboxCounts = synchronized(lock) {
        var pending = 0
        var conflicts = 0
        var failed = 0
        readable.rawQuery(
            "SELECT state, COUNT(*) FROM outbox WHERE owner = ? GROUP BY state",
            arrayOf(owner),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getString(0)) {
                    OutboxState.PENDING.name -> pending = cursor.getInt(1)
                    OutboxState.CONFLICT.name -> conflicts = cursor.getInt(1)
                    OutboxState.FAILED.name -> failed = cursor.getInt(1)
                }
            }
        }
        OutboxCounts(pending, conflicts, failed)
    }

    fun conflicts(owner: String): List<OutboxItem> = synchronized(lock) {
        readable.query(
            "outbox",
            null,
            "state = ? AND owner = ?",
            arrayOf(OutboxState.CONFLICT.name, owner),
            null,
            null,
            "id ASC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toOutbox()) }
        }
    }

    /** User chose "overwrite server": accept the server snapshot as the baseline, then resend. */
    fun resolveConflictOverwrite(id: Long) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val item = outboxByIdUnlocked(id) ?: return@synchronized
            if (item.kind == OutboxKind.DAILY_SAVE || item.kind == OutboxKind.DAILY_DELETE) {
                writable.update(
                    "daily_cache",
                    ContentValues().apply {
                        put("server_content", item.serverContent)
                        put("server_updated_at", item.serverUpdatedAt)
                    },
                    "date = ?",
                    arrayOf(item.entityKey),
                )
            }
            writable.update(
                "outbox",
                ContentValues().apply {
                    put("state", OutboxState.PENDING.name)
                    put("revision", item.revision + 1)
                    putNull("server_content")
                    putNull("server_updated_at")
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    /** User chose "keep the server version": drop the local edit and cache the remote value. */
    fun resolveConflictKeepServer(id: Long) = synchronized(lock) {
        writable.beginTransaction()
        try {
            val item = outboxByIdUnlocked(id) ?: return@synchronized
            if (item.kind == OutboxKind.DAILY_SAVE || item.kind == OutboxKind.DAILY_DELETE) {
                if (item.serverContent.isNullOrEmpty()) {
                    writable.delete("daily_cache", "date = ?", arrayOf(item.entityKey))
                } else {
                    writable.update(
                        "daily_cache",
                        ContentValues().apply {
                            put("content", item.serverContent)
                            put("server_content", item.serverContent)
                            put("server_updated_at", item.serverUpdatedAt)
                            put("pending", 0)
                            put("deleted", 0)
                        },
                        "date = ?",
                        arrayOf(item.entityKey),
                    )
                }
            }
            writable.delete("outbox", "id = ?", arrayOf(id.toString()))
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    // ─── Report cache ───────────────────────────────────

    fun cacheReport(entry: ReportCacheEntry) = synchronized(lock) {
        writable.insertWithOnConflict(
            "report_cache",
            null,
            ContentValues().apply {
                put("cache_key", entry.cacheKey)
                put("content", entry.content)
                put("model_name", entry.modelName)
                put("generated_at", entry.generatedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun cachedReport(cacheKey: String): ReportCacheEntry? = synchronized(lock) {
        readable.query("report_cache", null, "cache_key = ?", arrayOf(cacheKey), null, null, null).use {
            if (it.moveToFirst()) {
                ReportCacheEntry(
                    cacheKey = it.getString(it.getColumnIndexOrThrow("cache_key")),
                    content = it.getString(it.getColumnIndexOrThrow("content")),
                    modelName = it.getString(it.getColumnIndexOrThrow("model_name")),
                    generatedAt = it.getStringOrNull("generated_at"),
                )
            } else {
                null
            }
        }
    }

    fun clearReport(cacheKey: String) = synchronized(lock) {
        writable.delete("report_cache", "cache_key = ?", arrayOf(cacheKey))
    }

    // ─── Internals ──────────────────────────────────────

    private val readable: SQLiteDatabase get() = readableDatabase
    private val writable: SQLiteDatabase get() = writableDatabase

    private fun dailyByDateUnlocked(date: String): CachedDay? =
        readable.query("daily_cache", null, "date = ?", arrayOf(date), null, null, null).use {
            if (it.moveToFirst()) it.toCachedDay() else null
        }

    private fun taskByRemoteIdUnlocked(remoteId: Int): LocalTask? =
        readable.query("tasks_cache", null, "remote_id = ?", arrayOf(remoteId.toString()), null, null, null).use {
            if (it.moveToFirst()) it.toLocalTask() else null
        }

    private fun outboxForEntityUnlocked(entityKey: String): List<OutboxItem> =
        readable.query("outbox", null, "entity_key = ?", arrayOf(entityKey), null, null, "id ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toOutbox()) }
        }

    private fun outboxByIdUnlocked(id: Long): OutboxItem? =
        readable.query("outbox", null, "id = ?", arrayOf(id.toString()), null, null, null).use {
            if (it.moveToFirst()) it.toOutbox() else null
        }

    private fun insertTaskOutbox(kind: OutboxKind, localId: String, payload: String, owner: String): Long =
        writable.insertOrThrow(
            "outbox",
            null,
            ContentValues().apply {
                put("kind", kind.name)
                put("entity_key", localId)
                put("payload", payload)
                put("state", OutboxState.PENDING.name)
                put("owner", owner)
                put("revision", 1)
                put("created_at", System.currentTimeMillis())
            },
        )

    private fun android.database.Cursor.toCachedDay() = CachedDay(
        date = getString(getColumnIndexOrThrow("date")),
        content = getString(getColumnIndexOrThrow("content")),
        serverContent = getStringOrNull("server_content"),
        serverUpdatedAt = getStringOrNull("server_updated_at"),
        pending = getInt(getColumnIndexOrThrow("pending")) == 1,
        deleted = getInt(getColumnIndexOrThrow("deleted")) == 1,
    )

    private fun android.database.Cursor.toLocalTask() = LocalTask(
        localId = getString(getColumnIndexOrThrow("local_id")),
        remoteId = getIntOrNull("remote_id"),
        content = getString(getColumnIndexOrThrow("content")),
        deadline = getStringOrNull("deadline"),
        isCompleted = getInt(getColumnIndexOrThrow("is_completed")) == 1,
        dirty = getInt(getColumnIndexOrThrow("dirty")) == 1,
        deleted = getInt(getColumnIndexOrThrow("deleted")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
    )

    private fun android.database.Cursor.toOutbox() = OutboxItem(
        id = getLong(getColumnIndexOrThrow("id")),
        kind = OutboxKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
        entityKey = getString(getColumnIndexOrThrow("entity_key")),
        payload = getString(getColumnIndexOrThrow("payload")),
        baseUpdatedAt = getStringOrNull("base_updated_at"),
        state = OutboxState.valueOf(getString(getColumnIndexOrThrow("state"))),
        revision = getLong(getColumnIndexOrThrow("revision")),
        owner = getString(getColumnIndexOrThrow("owner")),
        attempts = getInt(getColumnIndexOrThrow("attempts")),
        lastError = getStringOrNull("last_error"),
        serverContent = getStringOrNull("server_content"),
        serverUpdatedAt = getStringOrNull("server_updated_at"),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
    )

    companion object {
        private const val NAME = "weekly_report.db"
        private const val VERSION = 1
    }
}

data class ServerTask(
    val remoteId: Int,
    val content: String,
    val deadline: String?,
    val isCompleted: Boolean,
    val updatedAt: String?,
    val createdAt: Long,
)

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getIntOrNull(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}
