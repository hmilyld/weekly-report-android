package com.hmilyld.weeklyreport.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hmilyld.weeklyreport.WeeklyReportApp

class OutboxWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? WeeklyReportApp ?: return Result.success()
        return when (val result = app.container.syncEngine.drain()) {
            is SyncResult.Done -> Result.success()
            is SyncResult.NeedsLogin -> Result.success()
            is SyncResult.Stopped -> if (result.transient) Result.retry() else Result.success()
        }
    }
}
