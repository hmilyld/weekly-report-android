package com.hmilyld.weeklyreport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.data.SessionState
import com.hmilyld.weeklyreport.data.local.OutboxItem
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.screens.daily.DailyScreen
import com.hmilyld.weeklyreport.ui.screens.daily.DailyViewModel
import com.hmilyld.weeklyreport.ui.screens.lock.LockScreen
import com.hmilyld.weeklyreport.ui.screens.login.LoginScreen
import com.hmilyld.weeklyreport.ui.screens.login.LoginViewModel
import com.hmilyld.weeklyreport.ui.screens.reports.ReportsScreen
import com.hmilyld.weeklyreport.ui.screens.reports.ReportsViewModel
import com.hmilyld.weeklyreport.ui.screens.settings.SettingsScreen
import com.hmilyld.weeklyreport.ui.screens.settings.SettingsViewModel
import com.hmilyld.weeklyreport.ui.screens.tasks.TasksScreen
import com.hmilyld.weeklyreport.ui.screens.tasks.TasksViewModel
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.util.ListInput
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WidgetOpen(
    val date: LocalDate?,
    val openEditor: Boolean,
    val tab: String?,
    val id: Long,
)

private enum class AppTab(val label: String) {
    DAILY("日报"),
    TASKS("待办"),
    REPORTS("报告"),
    SETTINGS("设置"),
}

@Composable
fun AppRoot(
    container: AppContainer,
    widgetRequest: StateFlow<WidgetOpen?>,
    onWidgetRequestConsumed: () -> Unit,
) {
    val appViewModel: AppViewModel = viewModel(factory = SimpleVmFactory { AppViewModel(container) })
    val session by appViewModel.session.collectAsState()
    val problem by appViewModel.problem.collectAsState()
    val theme by appViewModel.theme.collectAsState()

    when (val current = session) {
        is SessionState.LoggedOut -> {
            val loginViewModel: LoginViewModel = viewModel(
                factory = SimpleVmFactory { LoginViewModel(container.settings, container.session, container.api) },
            )
            LoginScreen(
                viewModel = loginViewModel,
                problem = problem,
                onNotify = {},
            )
        }

        is SessionState.Locked -> {
            val context = LocalContext.current
            val available = remember { BiometricUi.isAvailable(context) }
            LockScreen(
                username = current.username,
                biometricAvailable = available,
                onUnlock = {
                    BiometricUi.authenticate(
                        context = context,
                        title = "解锁周记",
                        subtitle = current.username,
                        onSuccess = { container.session.unlock() },
                        onFailure = { },
                    )
                },
                onLoginWithPassword = { appViewModel.logout() },
                onNotify = {},
            )
        }

        is SessionState.LoggedIn -> {
            LoggedInRoot(
                container = container,
                username = current.username,
                theme = theme,
                appViewModel = appViewModel,
                widgetRequest = widgetRequest,
                onWidgetRequestConsumed = onWidgetRequestConsumed,
            )
        }
    }
}

@Composable
private fun LoggedInRoot(
    container: AppContainer,
    username: String,
    theme: com.hmilyld.weeklyreport.data.prefs.ThemeMode,
    appViewModel: AppViewModel,
    widgetRequest: StateFlow<WidgetOpen?>,
    onWidgetRequestConsumed: () -> Unit,
) {
    val dailyViewModel: DailyViewModel = viewModel(
        factory = SimpleVmFactory { DailyViewModel(container.dailyRepository, container.dataVersion) },
    )
    val tasksViewModel: TasksViewModel = viewModel(
        factory = SimpleVmFactory { TasksViewModel(container.taskRepository, container.dataVersion) },
    )
    val reportsViewModel: ReportsViewModel = viewModel(
        factory = SimpleVmFactory { ReportsViewModel(container.reportRepository) },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SimpleVmFactory { SettingsViewModel(container) },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DAILY.name) }
    val tab = AppTab.entries.firstOrNull { it.name == selectedTab } ?: AppTab.DAILY
    val counts by appViewModel.counts.collectAsState()
    val conflicts by appViewModel.conflicts.collectAsState()
    var showConflicts by remember { mutableStateOf(false) }
    val biometricEnabled by container.settings.biometricEnabled.collectAsState()

    val request by widgetRequest.collectAsState()
    LaunchedEffect(request?.id) {
        val open = request ?: return@LaunchedEffect
        if (open.tab != null) {
            selectedTab = when (open.tab) {
                "tasks" -> AppTab.TASKS.name
                "reports" -> AppTab.REPORTS.name
                "settings" -> AppTab.SETTINGS.name
                else -> AppTab.DAILY.name
            }
        }
        if (open.date != null) {
            selectedTab = AppTab.DAILY.name
            dailyViewModel.openDateFromWidget(open.date)
        }
        onWidgetRequestConsumed()
    }

    var previousUsername by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        if (previousUsername != null && previousUsername != username) {
            dailyViewModel.load(showLoading = false)
            tasksViewModel.refresh()
            settingsViewModel.refreshUser()
        }
        previousUsername = username
    }

    LaunchedEffect(Unit) {
        merge(
            dailyViewModel.messages,
            tasksViewModel.messages,
            reportsViewModel.messages,
            settingsViewModel.messages,
            appViewModel.messages,
        ).collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    LaunchedEffect(conflicts.isEmpty()) {
        if (conflicts.isEmpty()) showConflicts = false
    }

    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message, withDismissAction = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { selectedTab = entry.name },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    AppTab.DAILY -> Icons.Filled.EditNote
                                    AppTab.TASKS -> Icons.Filled.Checklist
                                    AppTab.REPORTS -> Icons.Filled.Article
                                    AppTab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                counts.conflicts > 0 -> Banner(
                    text = "有 ${counts.conflicts} 条离线修改与服务器冲突，需要你确认后才覆盖。",
                    action = {
                        TextButton(onClick = { showConflicts = true }) { Text("处理") }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )

                counts.pending > 0 -> Banner(
                    text = "${counts.pending} 条修改待发送，联网后按顺序补发。",
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.Sync,
                    action = {
                        TextButton(onClick = appViewModel::retryQueue) { Text("重试") }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    AppTab.DAILY -> DailyScreen(dailyViewModel)
                    AppTab.TASKS -> TasksScreen(tasksViewModel)
                    AppTab.REPORTS -> ReportsScreen(reportsViewModel, onNotify = notify)
                    AppTab.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel,
                        theme = theme,
                        biometricEnabled = biometricEnabled,
                        onThemeChange = appViewModel::setTheme,
                        onBiometricChange = settingsViewModel::setBiometricEnabled,
                        onNotify = notify,
                    )
                }
            }
        }
    }

    if (showConflicts && conflicts.isNotEmpty()) {
        ConflictDialog(
            conflict = conflicts.first(),
            total = conflicts.size,
            onKeepServer = {
                appViewModel.resolveKeepServer(conflicts.first().id)
            },
            onOverwrite = {
                appViewModel.resolveOverwrite(conflicts.first().id)
            },
            onDismiss = { showConflicts = false },
        )
    }
}

@Composable
private fun ConflictDialog(
    conflict: OutboxItem,
    total: Int,
    onKeepServer: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val localText = if (conflict.kind.name == "DAILY_DELETE") {
        "本地：删除这一天的记录"
    } else {
        "本地：${ListInput.bodyWithoutMarkers(conflict.payload).trim().ifEmpty { "（空白）" }}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${conflict.entityKey} 有冲突",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = "这一天的记录在离线期间被服务器更新过（还剩 $total 条冲突）。为避免静默覆盖，需要你选择保留哪一版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(localText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "服务器：${conflict.serverContent?.takeIf { it.isNotBlank() } ?: "（空白）"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("用本地覆盖", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onKeepServer) { Text("保留服务器版本") }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}
