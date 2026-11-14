package com.hmilyld.weeklyreport.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.notifications.NotificationPermission
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.ui.BiometricUi
import com.hmilyld.weeklyreport.ui.components.ConfirmDialog
import com.hmilyld.weeklyreport.ui.components.PaperCard
import com.hmilyld.weeklyreport.ui.components.Pill
import com.hmilyld.weeklyreport.ui.components.ScreenHeader
import com.hmilyld.weeklyreport.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    theme: ThemeMode,
    biometricEnabled: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onNotify: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(TaskNotifier.hasPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = TaskNotifier.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        ScreenHeader(
            kicker = stringResource(R.string.settings_kicker),
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        SettingsSection(stringResource(R.string.settings_section_server)) {
            Text(
                text = stringResource(
                    R.string.settings_server_current,
                    if (state.serverInput.isBlank()) {
                        stringResource(R.string.settings_server_unset)
                    } else {
                        state.serverInput
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.serverInput,
                onValueChange = viewModel::updateServerInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                label = { Text(stringResource(R.string.common_server_address)) },
                singleLine = true,
                isError = state.serverError != null,
                supportingText = {
                    Text(
                        state.serverError
                            ?: state.testResult
                            ?: if (BuildConfig.DEBUG) {
                                stringResource(R.string.settings_server_hint_debug)
                            } else {
                                stringResource(R.string.settings_server_hint_release)
                            },
                    )
                },
                shape = MaterialTheme.shapes.small,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { viewModel.testServer(state.serverInput) },
                    enabled = !state.testing,
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(Spacing.xs))
                    }
                    Text(stringResource(R.string.common_test_connection))
                }
                Button(
                    onClick = { viewModel.saveServer(state.serverInput) },
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Text(stringResource(R.string.settings_save_and_relogin)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_section_account)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.user?.username?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.md),
                ) {
                    Text(
                        text = state.user?.username ?: stringResource(R.string.settings_reading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_current_user),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.user?.role != null) {
                            Pill(
                                text = if (state.user?.role == "admin") {
                                    stringResource(R.string.settings_role_admin)
                                } else {
                                    stringResource(R.string.settings_role_user)
                                },
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::refreshUser, enabled = !state.loadingUser) {
                    if (state.loadingUser) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.settings_refresh_account),
                        )
                    }
                }
            }
            if (state.notice != null) {
                Text(
                    text = state.notice.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(onClick = { showPasswordDialog = true }) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.settings_change_password),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.settings_logout),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = { onThemeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                },
                            )
                        },
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_section_security)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.md),
                ) {
                    Text(
                        stringResource(R.string.settings_biometric_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_biometric_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            onBiometricChange(false)
                        } else if (!BiometricUi.isAvailable(context)) {
                            onNotify(context.getString(R.string.settings_biometric_unavailable))
                        } else {
                            BiometricUi.authenticate(
                                context = context,
                                title = context.getString(R.string.settings_biometric_prompt_title),
                                subtitle = context.getString(R.string.settings_biometric_prompt_subtitle),
                                onSuccess = { onBiometricChange(true) },
                                onFailure = onNotify,
                            )
                        }
                    },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_reminders)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.md),
                ) {
                    Text(
                        stringResource(R.string.tasks_notification_name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_notifications_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notificationsGranted) {
                    Pill(
                        text = stringResource(R.string.settings_notifications_granted),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                maxItemsInEachRow = 2,
            ) {
                if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = {
                        if (!NotificationPermission.openSettings(context)) {
                            onNotify(context.getString(R.string.settings_notifications_settings_failed))
                        }
                    }) {
                        Text(
                            stringResource(R.string.settings_notifications_enable),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(onClick = viewModel::checkReminders) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.settings_check_reminders),
                        modifier = Modifier.padding(start = Spacing.xs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = viewModel::pinWidget) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.settings_add_widget),
                        modifier = Modifier.padding(start = Spacing.xs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    text = stringResource(R.string.settings_notifications_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_queue)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.md),
                ) {
                    Text(
                        stringResource(R.string.settings_queue_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (state.queue.failed > 0) {
                            stringResource(
                                R.string.settings_queue_summary_failed,
                                state.queue.pending,
                                state.queue.conflicts,
                                state.queue.failed,
                            )
                        } else {
                            stringResource(
                                R.string.settings_queue_summary,
                                state.queue.pending,
                                state.queue.conflicts,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = viewModel::retryQueue,
                    enabled = state.queue.pending > 0 || state.queue.failed > 0,
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.settings_retry_now),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
            if (state.queue.conflicts > 0) {
                Text(
                    text = stringResource(R.string.settings_queue_conflict_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (state.queue.failed > 0) {
                Text(
                    text = stringResource(R.string.settings_queue_failed_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleSmall,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Spacer(modifier = Modifier.size(Spacing.xxl))
    }

    if (showPasswordDialog) {
        PasswordDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearPasswordError()
                showPasswordDialog = false
            },
        )
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout_title),
            body = stringResource(R.string.settings_logout_body),
            confirmLabel = stringResource(R.string.settings_logout_confirm),
            destructive = true,
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout()
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
        PaperCard {
            Column(modifier = Modifier.padding(Spacing.lg)) { content() }
        }
    }
}

@Composable
private fun PasswordDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.passwordSuccess) {
        if (state.passwordSuccess) {
            viewModel.consumePasswordSuccess()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.changingPassword) onDismiss() },
        title = { Text(stringResource(R.string.settings_change_password), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_password_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        viewModel.clearPasswordError()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    label = { Text(stringResource(R.string.settings_password_current)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        viewModel.clearPasswordError()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    label = { Text(stringResource(R.string.settings_password_new)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        viewModel.clearPasswordError()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    label = { Text(stringResource(R.string.settings_password_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (state.passwordError != null) {
                    Text(
                        text = state.passwordError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.changePassword(oldPassword, newPassword, confirmPassword) },
                enabled = !state.changingPassword,
            ) {
                if (state.changingPassword) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(Spacing.xs))
                }
                Text(stringResource(R.string.settings_change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.changingPassword) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
