package com.hmilyld.weeklyreport.ui.screens.daily

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.components.LoadingRow
import com.hmilyld.weeklyreport.ui.components.PaperCard
import com.hmilyld.weeklyreport.ui.components.PeriodNavigator
import com.hmilyld.weeklyreport.ui.components.Pill
import com.hmilyld.weeklyreport.ui.components.ScreenHeader
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.util.Dates
import com.hmilyld.weeklyreport.util.ListInput
import kotlinx.coroutines.delay

@Composable
fun DailyScreen(viewModel: DailyViewModel) {
    val state by viewModel.state.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val saving by viewModel.saving.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        ScreenHeader(
            kicker = "日报 · DAILY",
            title = "一周的记录",
            subtitle = "点任意一天补记进展；一周写成几行，周报就有素材。",
            trailing = {
                IconButton(onClick = { viewModel.load() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新本周")
                }
            },
        )

        PeriodNavigator(
            label = Dates.weekRangeLabel(state.monday),
            caption = "已记 ${state.weekCount} 天 · 周一为周首",
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onToday = if (state.isCurrentWeek) null else viewModel::thisWeek,
            todayLabel = "回到本周",
            modifier = Modifier.padding(top = Spacing.md),
        )

        if (state.offline) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                Banner(
                    text = "离线：显示本机缓存；新修改会先保存在本机，联网后按顺序发送。",
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.CloudOff,
                )
            }
        }
        if (state.error != null) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                Banner(
                    text = state.error.orEmpty(),
                    action = {
                        TextButton(onClick = { viewModel.load() }) { Text("重试") }
                    },
                )
            }
        }

        if (state.loading && state.days.isEmpty()) {
            LoadingRow("正在读取本周日报…")
        } else {
            PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
                Column {
                    state.days.forEachIndexed { index, day ->
                        DayRow(day = day, onClick = { viewModel.openEditor(day.date) })
                        if (index != state.days.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            if (state.weekCount == 0) {
                Text(
                    text = "这一周还没有记录。挑一天，从「1. 」开始写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
                )
            }
        }
        Spacer(modifier = Modifier.size(Spacing.xxl))
    }

    if (editor != null) {
        DailyEditorSheet(
            editor = editor!!,
            saving = saving,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        )
    }
}

@Composable
private fun DayRow(day: DailyDayUi, onClick: () -> Unit) {
    val rowColor = if (day.isToday) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(58.dp)) {
            Text(
                text = Dates.monthDay(day.date),
                style = MaterialTheme.typography.titleMedium,
                color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = Dates.weekdayShort(day.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (day.isToday) {
                Pill(
                    text = "今天",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            val preview = when {
                day.deleted -> "这条记录将被删除"
                day.body.isEmpty() -> "还没有记录，点这里补记"
                else -> day.body
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = if (day.body.isEmpty() || day.deleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (day.pending) {
                Pill(
                    text = if (day.deleted) "待删除" else "待同步",
                    color = if (day.deleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyEditorSheet(
    editor: EditorState,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var value by remember(editor.date, editor.seeded) {
        mutableStateOf(
            TextFieldValue(
                text = editor.initialContent,
                selection = TextRange(editor.initialContent.length),
            ),
        )
    }
    var showDiscard by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dirty = value.text != editor.initialContent
    val body = ListInput.bodyWithoutMarkers(value.text).trim()
    val canSave = dirty && (body.isNotEmpty() || editor.hasRecord) && !saving

    fun handleValueChange(newValue: TextFieldValue) {
        // Never rewrite text while a Chinese IME is composing, and only
        // continue the ordered list for an actual single newline insertion.
        val old = value
        val composing = newValue.composition != null || old.composition != null
        if (composing) {
            value = newValue
            return
        }
        if (old.selection.collapsed) {
            val edit = ListInput.continuationForNewline(
                oldText = old.text,
                oldCursor = old.selection.start,
                newText = newValue.text,
                composing = false,
            )
            if (edit != null) {
                value = TextFieldValue(edit.value, TextRange(edit.caret))
                return
            }
        }
        value = newValue
    }

    val requestClose = {
        if (saving) Unit
        else if (dirty) showDiscard = true
        else {
            keyboard?.hide()
            onDismiss()
        }
    }

    BackHandler { requestClose() }

    LaunchedEffect(Unit) {
        delay(280)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = if (editor.hasRecord) "编辑日报" else "记录这一天的进展",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = Dates.fullDateLabel(editor.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )

            OutlinedTextField(
                value = value,
                onValueChange = ::handleValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .heightIn(min = 240.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("今天做了什么？一两句就够，比如：修了导出 CSV 的日期偏移。") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                ),
                minLines = 9,
                shape = RoundedCornerShape(16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (body.isEmpty()) {
                        "回车自动续号；空条目回车结束列表"
                    } else {
                        "${body.length} 字"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (editor.hasRecord) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !saving,
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = requestClose, enabled = !saving) { Text("取消") }
                Button(
                    onClick = {
                        keyboard?.hide()
                        onSave(ListInput.trimTrailingEmptyItem(value.text))
                    },
                    enabled = canSave,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.size(Spacing.sm))
                    }
                    Text(if (saving) "保存中" else "保存")
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃未保存的修改？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("这一天的内容还没有保存，离开后会丢失这次修改。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    keyboard?.hide()
                    onDismiss()
                }) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条日报？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("${Dates.iso(editor.date)} 的记录将被删除，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    keyboard?.hide()
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
