package com.hmilyld.weeklyreport.ui.screens.tasks

import android.content.Context
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.local.LocalTask
import com.hmilyld.weeklyreport.notifications.NotificationPermission
import com.hmilyld.weeklyreport.notifications.TaskNotifier
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.components.ConfirmDialog
import com.hmilyld.weeklyreport.ui.components.PaperCard
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            ScreenHeader(
                kicker = stringResource(R.string.tasks_kicker),
                title = stringResource(R.string.tasks_title),
                subtitle = stringResource(R.string.tasks_subtitle),
                trailing = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.tasks_refresh)
                            )
                        }
                    }
                }
            )
        }

        if (state.offline) {
            item {
                Banner(
                    text = stringResource(R.string.tasks_offline_banner),
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.CloudOff
                )
            }
        }
        if (state.error != null) {
            item {
                Banner(
                    text = state.error.orEmpty(),
                    action = {
                        TextButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                )
            }
        }
        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Banner(
                    text = stringResource(R.string.tasks_notification_banner),
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.NotificationsNone,
                    action = {
                        TextButton(onClick = {
                            if (!NotificationPermission.openSettings(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tasks_notification_settings_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Text(stringResource(R.string.tasks_notification_enable)) }
                    }
                )
            }
        }

        item { TaskComposer(onAdd = viewModel::create) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.tasks_active_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = " ${state.active.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.loading && state.active.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.tasks_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
            }
        } else if (state.active.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tasks_empty_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            }
        } else {
            items(state.active, key = { it.localId }, contentType = { "active" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { viewModel.toggle(task.localId) },
                    onSaveEdit = { content, deadline, changed ->
                        viewModel.edit(task.localId, content, deadline, changed)
                    },
                    onDelete = { deleteTarget = task }
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_completed_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = " ${state.totalCompleted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.completed.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tasks_empty_completed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )
            }
        } else {
            items(state.completed, key = { it.localId }, contentType = { "completed" }) { task ->
                CompletedTaskRow(
                    task = task,
                    onRestore = { viewModel.toggle(task.localId) },
                    onDelete = { deleteTarget = task }
                )
            }
            if (state.hasMoreCompleted) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = viewModel::loadMoreCompleted,
                            enabled = !state.loadingMore
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(Spacing.sm))
                            }
                            Text(
                                stringResource(
                                    R.string.tasks_load_more,
                                    state.completed.size,
                                    state.totalCompleted
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_title),
            body = stringResource(R.string.tasks_delete_body, task.content),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(task.localId)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskComposer(onAdd: (String, LocalDate?) -> Unit) {
    // rememberSaveable keeps a half-typed task alive across rotation and tab
    // switches; the deadline is stored as ISO text so no custom Saver is needed.
    var content by rememberSaveable { mutableStateOf("") }
    var deadlineIso by rememberSaveable { mutableStateOf<String?>(null) }
    val deadline = Dates.parseIso(deadlineIso)
    PaperCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.tasks_composer_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeadlineField(
                    date = deadline,
                    onChange = { deadlineIso = it?.let(Dates::iso) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            onAdd(content, deadline)
                            content = ""
                            deadlineIso = null
                        }
                    },
                    enabled = content.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.tasks_add),
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
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
    onDelete: () -> Unit
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
                    label = { Text(stringResource(R.string.tasks_field_content)) },
                    shape = MaterialTheme.shapes.small
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeadlineField(
                        date = deadline,
                        onChange = {
                            deadline = it
                            deadlineChanged = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        onSaveEdit(content, deadline, deadlineChanged)
                        editing = false
                    }) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.common_save)
                        )
                    }
                    IconButton(onClick = {
                        content = task.content
                        deadline = Dates.parseIso(task.deadline)
                        deadlineChanged = false
                        editing = false
                    }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconToggleButton(checked = false, onCheckedChange = { onToggle() }) {
                    Icon(
                        imageVector = Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.tasks_mark_done),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.content,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    DeadlineBadge(task.deadline)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.tasks_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.tasks_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
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
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.tasks_restore),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            text = task.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.tasks_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeadlineBadge(rawDeadline: String?) {
    val deadline = Dates.parseIso(rawDeadline) ?: return
    val context = LocalContext.current
    val state = Dates.deadlineState(deadline, LocalDate.now())
    val (label, color) = when (state) {
        DeadlineState.OVERDUE ->
            context.getString(R.string.tasks_deadline_overdue, dateLabel(context, deadline)) to
                MaterialTheme.colorScheme.error
        DeadlineState.URGENT ->
            context.getString(R.string.tasks_deadline_urgent, dateLabel(context, deadline)) to
                MaterialTheme.colorScheme.tertiary
        DeadlineState.LATER -> dateLabel(context, deadline) to MaterialTheme.colorScheme.onSurfaceVariant
        DeadlineState.NONE -> return
    }
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = Spacing.xs)
        )
    }
}

private fun dateLabel(context: Context, date: LocalDate): String = context.getString(R.string.tasks_deadline_date, date.monthValue, date.dayOfMonth)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineField(
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { showPicker = true },
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = date?.let { dateLabel(context, it) }
                    ?: stringResource(R.string.tasks_deadline_set),
                modifier = Modifier.padding(start = Spacing.xs),
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (date != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.tasks_deadline_clear),
                    modifier = Modifier.size(18.dp)
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
            }
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
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
