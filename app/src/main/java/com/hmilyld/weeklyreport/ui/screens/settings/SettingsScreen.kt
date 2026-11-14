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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.notifications.NotificationPermission
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.ui.BiometricUi
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
            kicker = "设置 · SETTINGS",
            title = "这本周记",
            subtitle = "服务器、账号、外观，以及手机端独有的提醒和小部件。",
        )

        SettingsSection("服务器") {
            Text(
                text = "当前地址：${if (state.serverInput.isBlank()) "未设置" else state.serverInput}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.serverInput,
                onValueChange = viewModel::updateServerInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                label = { Text("服务器地址") },
                singleLine = true,
                isError = state.serverError != null,
                supportingText = {
                    Text(
                        state.serverError
                            ?: state.testResult
                            ?: if (BuildConfig.DEBUG) {
                                "调试版允许局域网 HTTP；正式版仅允许 HTTPS。修改地址后会退出登录。"
                            } else {
                                "正式版仅允许 HTTPS；修改地址后会退出登录。"
                            },
                    )
                },
                shape = RoundedCornerShape(12.dp),
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
                    Text("测试连接")
                }
                Button(
                    onClick = { viewModel.saveServer(state.serverInput) },
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Text("保存并重新登录") }
            }
        }

        SettingsSection("账号") {
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
                        text = state.user?.username ?: "正在读取…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "当前登录用户",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.user?.role != null) {
                            Pill(
                                text = if (state.user?.role == "admin") "管理员" else "普通用户",
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
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新账号")
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
                    Text("修改密码", modifier = Modifier.padding(start = Spacing.xs))
                }
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("退出登录", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }

        SettingsSection("外观") {
            Text(
                text = "主题",
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
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                },
                            )
                        },
                    )
                }
            }
        }

        SettingsSection("安全") {
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
                    Text("生物识别解锁", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "已保存的会话需要指纹或面容才能进入；令牌只保存在 Android Keystore。",
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
                            onNotify("此设备没有可用的生物识别，请先在系统设置中录入指纹")
                        } else {
                            BiometricUi.authenticate(
                                context = context,
                                title = "开启生物识别解锁",
                                subtitle = "验证一次，之后打开周记需要指纹或面容",
                                onSuccess = { onBiometricChange(true) },
                                onFailure = onNotify,
                            )
                        }
                    },
                )
            }
        }

        SettingsSection("提醒与小部件") {
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
                    Text("待办到期提醒", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "逾期和三天内到期的待办由本机通知提醒，不依赖网页。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notificationsGranted) {
                    Pill(text = "已允许", color = MaterialTheme.colorScheme.secondary)
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
                            onNotify("无法打开系统通知设置，请手动在系统设置中允许通知")
                        }
                    }) {
                        Text("开启通知", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = viewModel::checkReminders) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("检查提醒", modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                }
                OutlinedButton(onClick = viewModel::pinWidget) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("添加小部件", modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                }
            }
            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    text = "点击「开启通知」后，在本应用的通知设置页打开开关；返回后会自动刷新状态。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        SettingsSection("离线队列") {
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
                    Text("待发送与冲突", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "待发送 ${state.queue.pending} 条 · 冲突 ${state.queue.conflicts} 条" +
                            if (state.queue.failed > 0) " · 失败 ${state.queue.failed} 条" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = viewModel::retryQueue,
                    enabled = state.queue.pending > 0 || state.queue.failed > 0,
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("立即重试", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            if (state.queue.conflicts > 0) {
                Text(
                    text = "有冲突的日报不会自动覆盖服务器；请在页面顶部的提示里逐条选择。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (state.queue.failed > 0) {
                Text(
                    text = "发送失败的修改仍保存在本机。检查内容后点「立即重试」，或重新编辑该项。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "周记 ${BuildConfig.VERSION_NAME}（构建 ${BuildConfig.VERSION_CODE}）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "数据保存在你的自托管服务器；本机只缓存日报草稿、待办和待发送队列。明文密码从不落盘。",
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
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("本机未发送的草稿会保留，下次登录同一账号后继续发送。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
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
        title = { Text("修改密码", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "修改成功后，服务端会用新密码重新加密全部内容，其他设备需要重新登录。",
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
                    label = { Text("当前密码") },
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
                    label = { Text("新密码（至少 8 位，含字母和数字）") },
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
                    label = { Text("再次输入新密码") },
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
                Text("修改密码")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.changingPassword) { Text("取消") }
        },
    )
}
