package com.hmilyld.weeklyreport.ui.screens.tasks

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hmilyld.weeklyreport.data.local.LocalTask
import com.hmilyld.weeklyreport.notifications.NotificationPermission
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.components.PaperCard
import com.hmilyld.weeklyreport.ui.components.Pill
import com.hmilyld.weeklyreport.ui.components.ScreenHeader
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.util.Dates
import com.hmilyld.weeklyreport.util.DeadlineState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(TaskNotifier.hasPermission(context)) }
    var deleteTarget by remember { mutableStateOf<LocalTask?>(null) }
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

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            ScreenHeader(
                kicker = "待办 · TASKS",
                title = "要做的事",
                subtitle = "按截止日期排序；逾期和三天内到期会标出来。",
                trailing = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新待办")
                        }
                    }
                },
            )
        }

        if (state.offline) {
            item {
                Banner(
                    text = "离线：显示本机缓存，新待办会在联网后按顺序补发。",
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.CloudOff,
                )
            }
        }
        if (state.error != null) {
            item {
                Banner(
                    text = state.error.orEmpty(),
                    action = {
                        TextButton(onClick = viewModel::refresh) { Text("重试") }
                    },
                )
            }
        }
        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Banner(
                    text = "允许通知后，逾期和临期待办会在本机提醒。",
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.NotificationsNone,
                    action = {
                        TextButton(onClick = {
                            if (!NotificationPermission.openSettings(context)) {
                                Toast.makeText(
                                    context,
                                    "无法打开系统通知设置，请手动在系统设置中允许",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }) { Text("开启") }
                    },
                )
            }
        }

        item { TaskComposer(onAdd = viewModel::create) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("未完成", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = " ${state.active.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading && state.active.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "正在读取待办…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        } else if (state.active.isEmpty()) {
            item {
                Text(
                    text = "没有未完成的待办。上面写一条，回车或点「添加」就记下了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        } else {
            items(state.active, key = { it.localId }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { viewModel.toggle(task.localId) },
                    onSaveEdit = { content, deadline, changed ->
                        viewModel.edit(task.localId, content, deadline, changed)
                    },
                    onDelete = { deleteTarget = task },
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已完成", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = " ${state.totalCompleted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.completed.isEmpty()) {
            item {
                Text(
                    text = "还没有完成的待办。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }
        } else {
            items(state.completed, key = { it.localId }) { task ->
                CompletedTaskRow(
                    task = task,
                    onRestore = { viewModel.toggle(task.localId) },
                    onDelete = { deleteTarget = task },
                )
            }
            if (state.hasMoreCompleted) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        OutlinedButton(
                            onClick = viewModel::loadMoreCompleted,
                            enabled = !state.loadingMore,
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(Spacing.sm))
                            }
                            Text("加载更多（${state.completed.size}/${state.totalCompleted}）")
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条待办？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("「${task.content}」将被删除，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(task.localId)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskComposer(onAdd: (String, LocalDate?) -> Unit) {
    var content by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }
    PaperCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("下一条要做的事") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeadlineField(
                    date = deadline,
                    onChange = { deadline = it },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            onAdd(content, deadline)
                            content = ""
                            deadline = null
                        }
                    },
                    enabled = content.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("添加", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: LocalTask,
    onToggle: () -> Unit,
    onSaveEdit: (String, LocalDate?, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(task.localId) { mutableStateOf(false) }
    var content by remember(task.localId, task.content) { mutableStateOf(task.content) }
    var deadline by remember(task.localId, task.deadline) { mutableStateOf(Dates.parseIso(task.deadline)) }
    var deadlineChanged by remember(task.localId) { mutableStateOf(false) }

    PaperCard {
        if (editing) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("待办内容") },
                    shape = RoundedCornerShape(12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeadlineField(
                        date = deadline,
                        onChange = {
                            deadline = it
                            deadlineChanged = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        onSaveEdit(content, deadline, deadlineChanged)
                        editing = false
                    }) {
                        Icon(Icons.Outlined.Check, contentDescription = "保存")
                    }
                    IconButton(onClick = {
                        content = task.content
                        deadline = Dates.parseIso(task.deadline)
                        deadlineChanged = false
                        editing = false
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "标记为已完成",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.content,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DeadlineBadge(task.deadline)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑待办",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除待办",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTaskRow(
    task: LocalTask,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "恢复为未完成",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = task.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "删除待办",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DeadlineBadge(rawDeadline: String?) {
    val deadline = Dates.parseIso(rawDeadline) ?: return
    val state = Dates.deadlineState(deadline, LocalDate.now())
    val (label, color) = when (state) {
        DeadlineState.OVERDUE -> "已逾期 · ${dateLabel(deadline)}" to MaterialTheme.colorScheme.error
        DeadlineState.URGENT -> "即将到期 · ${dateLabel(deadline)}" to MaterialTheme.colorScheme.tertiary
        DeadlineState.LATER -> dateLabel(deadline) to MaterialTheme.colorScheme.onSurfaceVariant
        DeadlineState.NONE -> return
    }
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

private fun dateLabel(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineField(
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { showPicker = true },
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = date?.let(::dateLabel) ?: "截止日期",
                modifier = Modifier.padding(start = Spacing.xs),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (date != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "清除截止日期",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showPicker) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
