package com.hmilyld.weeklyreport.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.weeklyreport.AppContainer
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.SessionState
import com.hmilyld.weeklyreport.data.local.OutboxItem
import com.hmilyld.weeklyreport.data.local.OutboxKind
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
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
import com.hmilyld.weeklyreport.ui.update.UpdatePrompt
import com.hmilyld.weeklyreport.ui.update.UpdateViewModel
import com.hmilyld.weeklyreport.util.ListInput
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

data class WidgetOpen(
    val date: LocalDate?,
    val openEditor: Boolean,
    val tab: String?,
    val id: Long,
)

private enum class AppTab(
    @StringRes val labelRes: Int,
) {
    DAILY(R.string.app_tab_daily),
    TASKS(R.string.app_tab_tasks),
    REPORTS(R.string.app_tab_reports),
    SETTINGS(R.string.app_tab_settings),
}

// Material 3 navigation bars show the filled icon for the current tab and the
// outlined one elsewhere; mixing styles per screen is what makes them look off.
private fun AppTab.icon(selected: Boolean): ImageVector =
    if (selected) {
        when (this) {
            AppTab.DAILY -> Icons.Filled.EditNote
            AppTab.TASKS -> Icons.Filled.Checklist
            AppTab.REPORTS -> Icons.AutoMirrored.Filled.Article
            AppTab.SETTINGS -> Icons.Filled.Settings
        }
    } else {
        when (this) {
            AppTab.DAILY -> Icons.Outlined.EditNote
            AppTab.TASKS -> Icons.Outlined.Checklist
            AppTab.REPORTS -> Icons.AutoMirrored.Outlined.Article
            AppTab.SETTINGS -> Icons.Outlined.Settings
        }
    }

@Composable
fun AppRoot(
    container: AppContainer,
    widgetRequest: StateFlow<WidgetOpen?>,
    onWidgetRequestConsumed: () -> Unit,
) {
    val appViewModel: AppViewModel =
        viewModel(factory = SimpleVmFactory { AppViewModel(container) })
    val session by appViewModel.session.collectAsState()
    val problem by appViewModel.problem.collectAsState()
    val theme by appViewModel.theme.collectAsState()

    // The release channel is independent of the login session, so the prompt
    // sits above the session switch and can appear on the login screen too.
    val updateViewModel: UpdateViewModel = viewModel(
        factory = SimpleVmFactory {
            UpdateViewModel(container.updateChecker, container.appContext)
        },
    )
    LaunchedEffect(Unit) { updateViewModel.checkOnStart() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = session) {
            is SessionState.LoggedOut -> {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = SimpleVmFactory {
                        LoginViewModel(container.settings, container.session, container.api)
                    },
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
                val unlockTitle = stringResource(R.string.app_biometric_unlock_title)
                LockScreen(
                    username = current.username,
                    biometricAvailable = available,
                    onUnlock = {
                        BiometricUi.authenticate(
                            context = context,
                            title = unlockTitle,
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
                    theme = theme,
                    appViewModel = appViewModel,
                    updateViewModel = updateViewModel,
                    widgetRequest = widgetRequest,
                    onWidgetRequestConsumed = onWidgetRequestConsumed,
                )
            }
        }
    }

    UpdatePrompt(viewModel = updateViewModel)
}

@Composable
private fun LoggedInRoot(
    container: AppContainer,
    theme: ThemeMode,
    appViewModel: AppViewModel,
    updateViewModel: UpdateViewModel,
    widgetRequest: StateFlow<WidgetOpen?>,
    onWidgetRequestConsumed: () -> Unit,
) {
    val dailyViewModel: DailyViewModel = viewModel(
        factory = SimpleVmFactory {
            DailyViewModel(container.dailyRepository, container.dataVersion)
        },
    )
    val tasksViewModel: TasksViewModel = viewModel(
        factory = SimpleVmFactory {
            TasksViewModel(container.taskRepository, container.dataVersion)
        },
    )
    val reportsViewModel: ReportsViewModel = viewModel(
        factory = SimpleVmFactory { ReportsViewModel(container.reportRepository) },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SimpleVmFactory { SettingsViewModel(container) },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveableStates = rememberSaveableStateHolder()
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

    val activationEpoch by container.session.activationEpoch.collectAsState()
    LaunchedEffect(activationEpoch) {
        // Logout/login with the same username keeps the Activity-scoped
        // ViewModels alive. Refresh on every new activation so a recovered
        // session never shows the previous session's cache.
        if (activationEpoch > 0) {
            dailyViewModel.load(showLoading = false)
            tasksViewModel.refresh()
            settingsViewModel.refreshUser()
        }
    }

    LaunchedEffect(Unit) {
        merge(
            dailyViewModel.messages,
            tasksViewModel.messages,
            reportsViewModel.messages,
            settingsViewModel.messages,
            appViewModel.messages,
            updateViewModel.messages,
        ).collect { message ->
            snackbarHostState.showSnackbar(
                message.resolve(snackbarContext),
                withDismissAction = true,
            )
        }
    }

    LaunchedEffect(conflicts.isEmpty()) {
        if (conflicts.isEmpty()) showConflicts = false
    }

    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message, withDismissAction = true) }
    }

    // Back from any secondary tab returns to the daily log instead of leaving
    // the app; the start destination keeps the default exit behaviour.
    BackHandler(enabled = tab != AppTab.DAILY) {
        selectedTab = AppTab.DAILY.name
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // From 600dp up the destinations move into a navigation rail, which is
        // the Material 3 answer for tablets, foldables and landscape phones.
        val wide = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEach { entry ->
                        NavigationRailItem(
                            selected = tab == entry,
                            onClick = { selectedTab = entry.name },
                            icon = { Icon(entry.icon(tab == entry), contentDescription = null) },
                            label = { Text(stringResource(entry.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!wide) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            AppTab.entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = tab == entry,
                                    onClick = { selectedTab = entry.name },
                                    icon = {
                                        Icon(
                                            imageVector = entry.icon(tab == entry),
                                            // The label below already names the
                                            // tab; a description here would be
                                            // read twice.
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(stringResource(entry.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
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
                            text = stringResource(
                                R.string.app_banner_conflict_count,
                                counts.conflicts,
                            ),
                            action = {
                                TextButton(onClick = { showConflicts = true }) {
                                    Text(stringResource(R.string.app_banner_action))
                                }
                            },
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.xs,
                            ),
                        )

                        counts.failed > 0 -> Banner(
                            text = stringResource(R.string.app_banner_failed_count, counts.failed),
                            tone = MaterialTheme.colorScheme.error,
                            icon = Icons.Outlined.WarningAmber,
                            action = {
                                TextButton(onClick = { selectedTab = AppTab.SETTINGS.name }) {
                                    Text(stringResource(R.string.app_banner_action))
                                }
                            },
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.xs,
                            ),
                        )

                        counts.pending > 0 -> Banner(
                            text = stringResource(
                                R.string.app_banner_pending_count,
                                counts.pending,
                            ),
                            tone = MaterialTheme.colorScheme.secondary,
                            icon = Icons.Outlined.Sync,
                            action = {
                                TextButton(onClick = appViewModel::retryQueue) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            },
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.xs,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // Reading and writing screens get a comfortable line length on
                        // wide windows instead of stretching across the whole tablet.
                        Box(
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxHeight(),
                        ) {
                            // Each tab keeps its own scroll position and text fields
                            // when the user switches away and back.
                            saveableStates.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    AppTab.DAILY -> DailyScreen(dailyViewModel)
                                    AppTab.TASKS -> TasksScreen(tasksViewModel)
                                    AppTab.REPORTS -> ReportsScreen(
                                        reportsViewModel,
                                        onNotify = notify,
                                    )
                                    AppTab.SETTINGS -> SettingsScreen(
                                        viewModel = settingsViewModel,
                                        theme = theme,
                                        biometricEnabled = biometricEnabled,
                                        onThemeChange = appViewModel::setTheme,
                                        onBiometricChange = settingsViewModel::setBiometricEnabled,
                                        onNotify = notify,
                                        updateViewModel = updateViewModel,
                                    )
                                }
                            }
                        }
                    }
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
    val localText = if (conflict.kind == OutboxKind.DAILY_DELETE) {
        stringResource(R.string.app_conflict_local_delete)
    } else {
        stringResource(
            R.string.app_conflict_local_text,
            ListInput.bodyWithoutMarkers(conflict.payload).trim()
                .ifEmpty { stringResource(R.string.app_conflict_blank) },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_conflict_title, conflict.entityKey),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.app_conflict_body, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(localText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(
                        R.string.app_conflict_server_text,
                        conflict.serverContent?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.app_conflict_blank),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(
                    stringResource(R.string.app_conflict_overwrite),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onKeepServer) {
                    Text(stringResource(R.string.app_conflict_keep_server))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.app_conflict_later))
                }
            }
        },
    )
}
