package com.hmilyld.weeklyreport

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.ui.AppRoot
import com.hmilyld.weeklyreport.ui.WidgetOpen
import com.hmilyld.weeklyreport.ui.theme.WeeklyReportTheme
import com.hmilyld.weeklyreport.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    private val widgetOpen = MutableStateFlow<WidgetOpen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // enableEdgeToEdge() turns the contrast scrim on; with a NavigationBar
        // that would paint a translucent gray band over the app's own surface.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val container = (application as WeeklyReportApp).container
        widgetOpen.value = parseIntent(intent)

        setContent {
            val theme by container.settings.theme.collectAsState()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            WeeklyReportTheme(theme) {
                AppRoot(
                    container = container,
                    widgetRequest = widgetOpen,
                    onWidgetRequestConsumed = { widgetOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetOpen.value = parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?): WidgetOpen? {
        if (intent == null) return null
        val date = Dates.parseIso(intent.getStringExtra(EXTRA_OPEN_DATE))
        val tab = intent.getStringExtra(EXTRA_OPEN_TAB)
        if (date == null && tab == null) return null
        return WidgetOpen(
            date = date,
            openEditor = intent.getBooleanExtra(EXTRA_OPEN_EDITOR, false),
            tab = tab,
            id = System.nanoTime(),
        )
    }

    companion object {
        const val EXTRA_OPEN_DATE = "open_date"
        const val EXTRA_OPEN_EDITOR = "open_editor"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_TASKS = "tasks"
    }
}
