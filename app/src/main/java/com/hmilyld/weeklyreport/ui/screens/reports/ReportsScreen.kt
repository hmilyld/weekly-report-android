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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmilyld.weeklyreport.ui.components.Banner
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
    val periodName = if (state.cadence == ReportCadence.WEEKLY) "周报" else "月报"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg),
    ) {
        ScreenHeader(
            kicker = "报告 · REPORTS",
            title = "写成一页",
            subtitle = "从日报自动汇总；生成后仍可编辑、复制并交给系统分享面板。",
            trailing = {
                IconButton(onClick = viewModel::load, enabled = !state.generating) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "重新读取")
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
            ) { Text("周报") }
            SegmentedButton(
                selected = state.cadence == ReportCadence.MONTHLY,
                onClick = { guarded { viewModel.selectCadence(ReportCadence.MONTHLY) } },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = !state.generating && !state.saving,
            ) { Text("月报") }
        }

        PeriodNavigator(
            label = label,
            caption = if (state.cadence == ReportCadence.WEEKLY) "周一至周日" else "自然月",
            onPrevious = { guarded(viewModel::previous) },
            onNext = { guarded(viewModel::next) },
            onToday = if (state.isCurrentPeriod) null else {
                { guarded(viewModel::current) }
            },
            todayLabel = if (state.cadence == ReportCadence.WEEKLY) "回到本周" else "回到本月",
            modifier = Modifier.padding(top = Spacing.md),
            enabled = !state.generating && !state.saving,
        )

        if (state.offline) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) {
                Banner(
                    text = "离线：显示上次读取的缓存；生成和保存需要联网。",
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
                    text = "${periodName}正文",
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
                    Text("编辑", modifier = Modifier.padding(start = Spacing.xs))
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
                    Text("重新生成", modifier = Modifier.padding(start = Spacing.xs))
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
                            Text("正在生成${periodName}…", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "服务器正在调用 LLM，可能需要几十秒，请保持页面打开。",
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
                                "正在读取$periodName…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }

                    state.error != null && state.content.isBlank() -> {
                        EmptyState(
                            title = "读取失败",
                            description = state.error.orEmpty(),
                            icon = Icons.Outlined.Description,
                            action = {
                                OutlinedButton(onClick = viewModel::load) { Text("重试") }
                            },
                        )
                    }

                    state.notGenerated -> {
                        EmptyState(
                            title = "还没有${periodName}",
                            description = if (state.cadence == ReportCadence.WEEKLY) {
                                "先在「日报」里补几条本周记录，再回来生成。"
                            } else {
                                "先在「日报」里补全本月记录，再回来生成。"
                            },
                            icon = Icons.Outlined.AutoAwesome,
                            action = {
                                Button(onClick = viewModel::generate, enabled = !state.generating) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text("生成$periodName", modifier = Modifier.padding(start = Spacing.xs))
                                }
                            },
                        )
                    }

                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "生成于 ${Dates.formatDateTime(state.generatedAt)}",
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
                                shape = RoundedCornerShape(14.dp),
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
                                ) { Text("取消") }
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
                                    Text(if (state.saving) "保存中" else "保存")
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
                                        onNotify("已复制${periodName}")
                                    },
                                    enabled = state.content.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("复制", modifier = Modifier.padding(start = Spacing.xs))
                                }
                                Button(
                                    onClick = {
                                        copyToClipboard(context, state.content)
                                        onNotify("已复制，正在打开系统分享面板")
                                        shareText(context, periodName, state.content)
                                    },
                                    enabled = state.content.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Outlined.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("复制并分享", modifier = Modifier.padding(start = Spacing.xs))
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
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("放弃未保存的修改？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("切换周期会丢失当前编辑内容。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    viewModel.cancelEdit()
                    action()
                }) {
                    Text("放弃并切换", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("继续编辑") }
            },
        )
    }

    if (showCancelEditConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelEditConfirm = false },
            title = { Text("放弃未保存的修改？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("退出编辑后会丢失这次修改。") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelEditConfirm = false
                    viewModel.cancelEdit()
                }) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelEditConfirm = false }) { Text("继续编辑") }
            },
        )
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("重新生成$periodName？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("服务器会再次调用 LLM 并覆盖当前内容，手工修改也会被覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateConfirm = false
                    viewModel.generate()
                }) {
                    Text("重新生成", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text("取消") }
            },
        )
    }
}

private fun copyToClipboard(context: android.content.Context, content: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("周记", content))
}

private fun shareText(context: android.content.Context, label: String, content: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "周记 $label")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(send, "分享$label"))
}
