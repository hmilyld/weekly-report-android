package com.hmilyld.weeklyreport.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Writes are queued locally and drained in order. WorkManager with a network
 * constraint is the durable retry path after the process dies or connectivity
 * returns; the repository also triggers an in-process drain for immediacy.
 */
class SyncScheduler(private val context: Context) {
    fun requestDrain() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            // Append to a running drain instead of dropping a request that
            // arrives while the worker is finishing.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_NAME = "weekly-report-outbox"
    }
}
