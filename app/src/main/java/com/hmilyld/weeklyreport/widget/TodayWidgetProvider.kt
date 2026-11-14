package com.hmilyld.weeklyreport.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hmilyld.weeklyreport.MainActivity
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.WeeklyReportApp
import com.hmilyld.weeklyreport.util.Dates
import com.hmilyld.weeklyreport.util.ListInput

/**
 * Home-screen widget: today's date, a preview of today's entry, and one tap
 * straight into the editor for today. This is one of the four things a web
 * page cannot do.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(
                ComponentName(context, TodayWidgetProvider::class.java),
                null,
                null,
            )
        }

        private fun buildViews(context: Context): RemoteViews {
            val today = Dates.today()
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(
                R.id.widget_date,
                context.getString(
                    R.string.widget_date_header,
                    today.monthValue,
                    today.dayOfMonth,
                    Dates.weekdayShort(today),
                ),
            )
            val app = context.applicationContext as? WeeklyReportApp
            val cached = app?.container?.db?.dailyByDate(Dates.iso(today))
            val preview = cached
                ?.takeIf { !it.deleted }
                ?.content
                ?.let { ListInput.bodyWithoutMarkers(it).trim() }
                .orEmpty()
            views.setTextViewText(
                R.id.widget_preview,
                preview.ifEmpty { context.getString(R.string.widget_empty) },
            )

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_DATE, Dates.iso(today))
                putExtra(MainActivity.EXTRA_OPEN_EDITOR, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                today.toEpochDay().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_preview, pending)
            views.setOnClickPendingIntent(R.id.widget_action, pending)
            views.setOnClickPendingIntent(R.id.widget_date, pending)
            return views
        }
    }
}
