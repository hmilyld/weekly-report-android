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
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tasks_notification_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.tasks_notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
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
        val summary = when {
            overdue > 0 && urgent > 0 ->
                context.getString(R.string.tasks_notification_summary_both, overdue, urgent)
            overdue > 0 -> context.getString(R.string.tasks_notification_summary_overdue, overdue)
            else -> context.getString(R.string.tasks_notification_summary_urgent, urgent)
        }
        val detail = due.take(6).joinToString("\n") { (content, deadline, state) ->
            val prefix = if (state == DeadlineState.OVERDUE) {
                context.getString(R.string.tasks_notification_prefix_overdue)
            } else {
                context.getString(R.string.tasks_notification_prefix_urgent)
            }
            context.getString(
                R.string.tasks_notification_detail,
                prefix,
                content,
                deadline.monthValue,
                deadline.dayOfMonth,
            )
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
            .setContentTitle(context.getString(R.string.tasks_notification_title, summary))
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
