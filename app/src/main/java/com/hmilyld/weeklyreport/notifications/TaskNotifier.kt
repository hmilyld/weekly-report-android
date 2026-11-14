package com.hmilyld.weeklyreport.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hmilyld.weeklyreport.MainActivity
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import com.hmilyld.weeklyreport.util.Dates
import com.hmilyld.weeklyreport.util.DeadlineState

/**
 * Local deadline reminder. Runs from WorkManager and from an explicit check in
 * Settings, so a demonstration does not have to wait for the daily schedule.
 */
object TaskNotifier {
    const val CHANNEL_ID = "task_deadlines"
    private const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "待办到期提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "逾期和三天内到期的本地提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns how many tasks were mentioned; 0 means nothing was due. */
    fun checkAndNotify(
        context: Context,
        db: AppDatabase,
        settings: SettingsStore,
        force: Boolean,
    ): Int {
        ensureChannel(context)
        if (!hasPermission(context)) return 0
        val today = Dates.today()
        val todayIso = Dates.iso(today)
        if (!force && settings.lastDeadlineNoticeDay() == todayIso) return 0

        val due = db.allLocalTasks()
            .filter { !it.deleted && !it.isCompleted }
            .mapNotNull { task ->
                val deadline = Dates.parseIso(task.deadline) ?: return@mapNotNull null
                val state = Dates.deadlineState(deadline, today)
                if (state == DeadlineState.OVERDUE || state == DeadlineState.URGENT) {
                    Triple(task.content, deadline, state)
                } else {
                    null
                }
            }
            .sortedBy { it.second }
        if (due.isEmpty()) {
            settings.setLastDeadlineNoticeDay(todayIso)
            return 0
        }

        val overdue = due.count { it.third == DeadlineState.OVERDUE }
        val urgent = due.size - overdue
        val summary = buildString {
            if (overdue > 0) append("$overdue 项已逾期")
            if (overdue > 0 && urgent > 0) append("，")
            if (urgent > 0) append("$urgent 项三天内到期")
        }
        val detail = due.take(6).joinToString("\n") { (content, deadline, state) ->
            val prefix = if (state == DeadlineState.OVERDUE) "已逾期" else "临期"
            "· [$prefix] $content（${deadline.monthValue}月${deadline.dayOfMonth}日）"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TASKS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            4201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("待办提醒 · $summary")
            .setContentText(due.first().first)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
        settings.setLastDeadlineNoticeDay(todayIso)
        return due.size
    }
}
