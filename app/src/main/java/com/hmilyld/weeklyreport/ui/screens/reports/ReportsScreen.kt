package com.hmilyld.weeklyreport.ui.screens.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.components.ConfirmDialog
import com.hmilyld.weeklyreport.ui.components.EmptyState
import com.hmilyld.weeklyreport.ui.components.PaperCard
import com.hmilyld.weeklyreport.ui.components.PeriodNavigator
import com.hmilyld.weeklyreport.ui.components.Pill
import com.hmilyld.weeklyreport.ui.components.ScreenHeader
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onNotify: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showCancelEditConfirm by remember { mutableStateOf(false) }

    fun guarded(action: () -> Unit) {
        if (state.generating || state.saving) return
        if (state.dirty) pendingAction = action else action()
    }

    LaunchedEffect(state.generating) {
        if (state.generating) scrollState.animateScrollTo(0)
    }

    val label = if (state.cadence == ReportCadence.WEEKLY) {
        Dates.weekRangeLabel(state.monday)
    } else {
        Dates.monthLabel(state.year, state.month)
    }
    val periodName = if (state.cadence == ReportCadence.WEEKLY) {
        stringResource(R.string.reports_cadence_weekly)
    } else {
        stringResource(R.string.reports_cadence_monthly)
    }
    val copiedLabel = stringResource(R.string.reports_copied, periodName)
    val copiedSharingLabel = stringResource(R.string.reports_copied_sharing)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The report editor is a text field inside this scroll container;
            // consume the IME inset before scrolling so the keyboard shrinks
            // the viewport instead of covering the caret.
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg),
    ) {
        ScreenHeader(
            kicker = stringResource(R.string.reports_kicker),
            title = stringResource(R.string.reports_title),
            subtitle = stringResource(R.string.reports_subtitle),
            trailing = {
                IconButton(onClick = viewModel::load, enabled = !state.generating) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.reports_reload),
                    )
                }
            },
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        ) {
            SegmentedButton(
                selected = state.cadence == ReportCadence.WEEKLY,
                onClick = { guarded { viewModel.selectCadence(ReportCadence.WEEKLY) } },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = !state.generating && !state.saving,
            ) { Text(stringResource(R.string.reports_cadence_weekly)) }
            SegmentedButton(
                selected = state.cadence == ReportCadence.MONTHLY,
                onClick = { guarded { viewModel.selectCadence(ReportCadence.MONTHLY) } },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = !state.generating && !state.saving,
            ) { Text(stringResource(R.string.reports_cadence_monthly)) }
        }

        PeriodNavigator(
            label = label,
            caption = if (state.cadence == ReportCadence.WEEKLY) {
                stringResource(R.string.reports_caption_weekly)
            } else {
                stringResource(R.string.reports_caption_monthly)
            },
            onPrevious = { guarded(viewModel::previous) },
            onNext = { guarded(viewModel::next) },
            onToday = if (state.isCurrentPeriod) null else {
                { guarded(viewModel::current) }
            },
            todayLabel = if (state.cadence == ReportCadence.WEEKLY) {
                stringResource(R.string.common_this_week)
            } else {
                stringResource(R.string.reports_this_month)
            },
            modifier = Modifier.padding(top = Spacing.md),
            enabled = !state.generating && !state.saving,
        )

        if (state.offline) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                Banner(
                    text = stringResource(R.string.reports_offline_banner),
                    tone = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Outlined.CloudOff,
                )
            }
        }

        if (state.content.isNotBlank() && !state.editing && !state.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reports_body_label, periodName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = viewModel::startEdit,
                    enabled = !state.generating && !state.saving,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(stringResource(R.string.common_edit), modifier = Modifier.padding(start = Spacing.xs))
                }
                TextButton(
                    onClick = { showRegenerateConfirm = true },
                    enabled = !state.generating,
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.reports_regenerate_action),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }

        if (state.generating) {
            PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(
                                stringResource(R.string.reports_generating_title, periodName),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.reports_generating_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                when {
                    state.loading && state.content.isBlank() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.reports_loading, periodName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }

                    state.error != null && state.content.isBlank() -> {
                        EmptyState(
                            title = stringResource(R.string.reports_load_failed),
                            description = state.error.orEmpty(),
                            icon = Icons.Outlined.Description,
                            action = {
                                OutlinedButton(onClick = viewModel::load) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            },
                        )
                    }

                    state.notGenerated -> {
                        EmptyState(
                            title = if (state.cadence == ReportCadence.WEEKLY) {
                                stringResource(R.string.reports_empty_weekly_title)
                            } else {
                                stringResource(R.string.reports_empty_monthly_title)
                            },
                            description = if (state.cadence == ReportCadence.WEEKLY) {
                                stringResource(R.string.reports_empty_weekly_desc)
                            } else {
                                stringResource(R.string.reports_empty_monthly_desc)
                            },
                            icon = Icons.Outlined.AutoAwesome,
                            action = {
                                Button(onClick = viewModel::generate, enabled = !state.generating) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        stringResource(R.string.reports_generate_action, periodName),
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                            },
                        )
                    }

                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.reports_generated_at,
                                    Dates.formatDateTime(state.generatedAt),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.modelName.isNotBlank()) {
                                Pill(
                                    text = state.modelName,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        if (state.editing) {
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = viewModel::updateDraft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                ),
                                minLines = 12,
                                shape = MaterialTheme.shapes.medium,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = state.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        if (state.editing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        if (state.dirty) showCancelEditConfirm = true else viewModel.cancelEdit()
                                    },
                                    enabled = !state.saving,
                                ) { Text(stringResource(R.string.common_cancel)) }
                                Button(
                                    onClick = viewModel::save,
                                    enabled = state.canSave && !state.saving,
                                    modifier = Modifier.padding(start = Spacing.sm),
                                ) {
                                    if (state.saving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(modifier = Modifier.size(Spacing.sm))
                                    }
                                    Text(
                                        if (state.saving) {
                                            stringResource(R.string.common_saving)
                                        } else {
                                            stringResource(R.string.common_save)
                                        },
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, state.content)
                                        onNotify(copiedLabel)
                                    },
                                    enabled = state.content.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.reports_copy),
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                                Button(
                                    onClick = {
                                        copyToClipboard(context, state.content)
                                        onNotify(copiedSharingLabel)
                                        shareText(context, periodName, state.content)
                                    },
                                    enabled = state.content.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.reports_copy_and_share),
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.size(Spacing.xxl))
    }

    pendingAction?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.common_discard_unsaved_title),
            body = stringResource(R.string.reports_discard_period_body),
            confirmLabel = stringResource(R.string.reports_discard_period_confirm),
            destructive = true,
            dismissLabel = stringResource(R.string.common_continue_editing),
            onConfirm = {
                pendingAction = null
                viewModel.cancelEdit()
                action()
            },
            onDismiss = { pendingAction = null },
        )
    }

    if (showCancelEditConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.common_discard_unsaved_title),
            body = stringResource(R.string.reports_cancel_edit_body),
            confirmLabel = stringResource(R.string.common_discard_edits),
            destructive = true,
            dismissLabel = stringResource(R.string.common_continue_editing),
            onConfirm = {
                showCancelEditConfirm = false
                viewModel.cancelEdit()
            },
            onDismiss = { showCancelEditConfirm = false },
        )
    }

    if (showRegenerateConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.reports_regenerate_title, periodName),
            body = stringResource(R.string.reports_regenerate_body),
            confirmLabel = stringResource(R.string.reports_regenerate_action),
            destructive = true,
            onConfirm = {
                showRegenerateConfirm = false
                viewModel.generate()
            },
            onDismiss = { showRegenerateConfirm = false },
        )
    }
}

private fun copyToClipboard(context: android.content.Context, content: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), content))
}

private fun shareText(context: android.content.Context, label: String, content: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.reports_share_subject, label))
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.reports_share_chooser, label)),
    )
}
