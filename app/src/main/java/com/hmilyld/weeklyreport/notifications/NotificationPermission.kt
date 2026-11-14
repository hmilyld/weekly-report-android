package com.hmilyld.weeklyreport.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Some OEM builds (notably MIUI/HyperOS) do not show the runtime
 * POST_NOTIFICATIONS dialog reliably, or close it immediately. Opening the
 * app's notification settings page is the dependable path; the caller
 * re-checks permission when the app resumes.
 */
object NotificationPermission {
    private const val TAG = "NotificationPermission"

    fun openSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "settings activity not found: ${intent.action}", error)
            } catch (error: SecurityException) {
                Log.w(TAG, "settings activity denied: ${intent.action}", error)
            }
        }
        return false
    }
}
