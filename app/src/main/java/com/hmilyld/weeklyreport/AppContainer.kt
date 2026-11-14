package com.hmilyld.weeklyreport

import android.content.Context
import com.hmilyld.weeklyreport.data.SessionManager
import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import com.hmilyld.weeklyreport.data.repo.DailyRepository
import com.hmilyld.weeklyreport.data.repo.ReportRepository
import com.hmilyld.weeklyreport.data.repo.TaskRepository
import com.hmilyld.weeklyreport.data.secure.SecureTokenStore
import com.hmilyld.weeklyreport.data.sync.SyncEngine
import com.hmilyld.weeklyreport.data.sync.SyncScheduler
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.notifications.TaskReminderWorker
import com.hmilyld.weeklyreport.widget.TodayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single manual dependency container. Small enough that DI frameworks would add
 * more build risk than they remove.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = SettingsStore(appContext)
    val db = AppDatabase(appContext)
    private val tokenStore = SecureTokenStore(appContext)
    val api = ApiClient(settings)
    val session = SessionManager(settings, tokenStore, api)
    private val scheduler = SyncScheduler(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _dataVersion = MutableStateFlow(0L)
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    val dailyRepository: DailyRepository
    val taskRepository: TaskRepository
    val reportRepository: ReportRepository
    val syncEngine: SyncEngine

    init {
        val changed: () -> Unit = {
            _dataVersion.update { it + 1 }
            TodayWidgetProvider.refresh(appContext)
            scope.launch {
                TaskNotifier.checkAndNotify(appContext, db, settings, force = false)
            }
        }
        dailyRepository = DailyRepository(db, api, session, scheduler, changed)
        taskRepository = TaskRepository(db, api, session, scheduler, changed)
        reportRepository = ReportRepository(db, api)
        syncEngine = SyncEngine(db, api, session, changed)

        // Keep WorkManager, widget and disk work off the main thread: cold
        // start already pays for Compose and the Keystore read.
        scope.launch {
            TodayWidgetProvider.refresh(appContext)
            TaskNotifier.ensureChannel(appContext)
            TaskReminderWorker.schedule(appContext)
            scheduler.requestDrain()
            if (session.state.value is com.hmilyld.weeklyreport.data.SessionState.LoggedIn) {
                syncEngine.drain()
            }
            TaskNotifier.checkAndNotify(appContext, db, settings, force = false)
        }
    }
}
