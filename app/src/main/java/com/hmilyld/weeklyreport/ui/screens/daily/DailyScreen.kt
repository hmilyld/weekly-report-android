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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.components.ConfirmDialog
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
            kicker = stringResource(R.string.daily_kicker),
            title = stringResource(R.string.daily_title),
            subtitle = stringResource(R.string.daily_subtitle),
            trailing = {
                IconButton(onClick = { viewModel.load() }) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.daily_refresh_week),
                    )
                }
            },
        )

        PeriodNavigator(
            label = Dates.weekRangeLabel(state.monday),
            caption = stringResource(R.string.daily_week_caption, state.weekCount),
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onToday = if (state.isCurrentWeek) null else viewModel::thisWeek,
            todayLabel = stringResource(R.string.common_this_week),
            modifier = Modifier.padding(top = Spacing.md),
        )

        if (state.offline) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                Banner(
                    text = stringResource(R.string.daily_offline_banner),
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
                        TextButton(onClick = { viewModel.load() }) {
                            Text(stringResource(R.string.common_retry))
                        }
                    },
                )
            }
        }

        if (state.loading && state.days.isEmpty()) {
            LoadingRow(stringResource(R.string.daily_loading))
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
                    text = stringResource(R.string.daily_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
                )
            }
        }
        Spacer(modifier = Modifier.size(Spacing.xxl))
    }

    editor?.let { current ->
        DailyEditorSheet(
            editor = current,
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
                    text = stringResource(R.string.daily_pill_today),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            val preview = when {
                day.deleted -> stringResource(R.string.daily_row_deleted)
                day.body.isEmpty() -> stringResource(R.string.daily_row_empty)
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
                    text = if (day.deleted) {
                        stringResource(R.string.daily_pill_pending_delete)
                    } else {
                        stringResource(R.string.daily_pill_pending_sync)
                    },
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
    var value by rememberSaveable(editor.date, editor.seeded, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = editor.initialContent,
                selection = TextRange(editor.initialContent.length),
            ),
        )
    }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

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
                text = if (editor.hasRecord) {
                    stringResource(R.string.daily_editor_title_edit)
                } else {
                    stringResource(R.string.daily_editor_title_new)
                },
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
                placeholder = { Text(stringResource(R.string.daily_editor_placeholder)) },
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
                        stringResource(R.string.daily_editor_hint_empty)
                    } else {
                        stringResource(R.string.daily_editor_char_count, body.length)
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
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
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
                TextButton(onClick = requestClose, enabled = !saving) {
                    Text(stringResource(R.string.common_cancel))
                }
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
                    Text(
                        if (saving) {
                            stringResource(R.string.common_saving)
                        } else {
                            stringResource(R.string.common_save)
                        },
                    )
                }
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.common_discard_unsaved_title),
            body = stringResource(R.string.daily_discard_body),
            confirmLabel = stringResource(R.string.common_discard_edits),
            destructive = true,
            dismissLabel = stringResource(R.string.common_continue_editing),
            onConfirm = {
                showDiscard = false
                keyboard?.hide()
                onDismiss()
            },
            onDismiss = { showDiscard = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.daily_delete_title),
            body = stringResource(R.string.daily_delete_body, Dates.iso(editor.date)),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                keyboard?.hide()
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
