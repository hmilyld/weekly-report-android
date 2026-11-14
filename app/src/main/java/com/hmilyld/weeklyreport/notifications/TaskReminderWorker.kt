package com.hmilyld.weeklyreport.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hmilyld.weeklyreport.WeeklyReportApp
import java.util.concurrent.TimeUnit

class TaskReminderWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : androidx.work.CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? WeeklyReportApp ?: return Result.success()
        TaskNotifier.checkAndNotify(
            context = applicationContext,
            db = app.container.db,
            settings = app.container.settings,
            force = false,
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "weekly-report-task-reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
