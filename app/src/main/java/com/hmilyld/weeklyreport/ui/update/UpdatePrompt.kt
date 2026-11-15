package com.hmilyld.weeklyreport.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.update.UpdateInfo
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.util.MarkdownLite

/**
 * The release-channel prompt. One dialog covers the whole flow: offer the
 * update, show download progress, then ask for the install permission before
 * handing the APK to the system installer.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val info = state.available ?: return
    if (!state.promptVisible) return

    // The user leaves for the "install unknown apps" screen and comes back;
    // re-read the grant so the button flips to 安装 without reopening the dialog.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    UpdateDialog(
        info = info,
        currentVersion = state.currentVersion,
        phase = state.phase,
        installAllowed = state.installAllowed,
        error = state.error,
        notice = state.notice,
        onDownload = viewModel::download,
        onInstall = viewModel::install,
        onGrantPermission = viewModel::openInstallPermissionSettings,
        onOpenReleasePage = viewModel::openReleasePage,
        onPostpone = viewModel::postponePrompt,
        onDismiss = viewModel::hidePrompt,
    )
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    phase: UpdatePhase,
    installAllowed: Boolean,
    error: String?,
    notice: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onPostpone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = phase is UpdatePhase.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.update_title, info.versionName),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.update_from_version, currentVersion),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val notes = MarkdownLite.parse(info.notes)
                if (notes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        notes.forEach { line ->
                            ReleaseNoteLine(line)
                        }
                    }
                }
                when (val current = phase) {
                    is UpdatePhase.Downloading -> Column(
                        modifier = Modifier.padding(top = Spacing.md),
                    ) {
                        Text(
                            text = if (current.progress > 0f) {
                                stringResource(
                                    R.string.update_downloading_percent,
                                    (current.progress * 100).toInt(),
                                )
                            } else {
                                stringResource(R.string.update_downloading)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (current.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { current.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xs),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xs),
                            )
                        }
                    }

                    is UpdatePhase.ReadyToInstall -> Text(
                        text = if (installAllowed) {
                            stringResource(R.string.update_ready)
                        } else {
                            stringResource(R.string.update_needs_permission)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installAllowed) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = Spacing.md),
                    )

                    is UpdatePhase.Idle -> if (info.apkSize > 0L) {
                        Text(
                            text = stringResource(
                                R.string.update_size_hint,
                                formatSize(info.apkSize),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
                val message = error ?: notice
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onOpenReleasePage) {
                    Text(stringResource(R.string.update_open_release))
                }
                when {
                    downloading -> TextButton(onClick = {}, enabled = false) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }

                    phase is UpdatePhase.ReadyToInstall && !installAllowed ->
                        TextButton(onClick = onGrantPermission) {
                            Text(
                                stringResource(R.string.update_allow_install),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                    phase is UpdatePhase.ReadyToInstall ->
                        TextButton(onClick = onInstall) {
                            Text(
                                stringResource(R.string.update_install),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                    else -> TextButton(onClick = onDownload) {
                        Text(
                            stringResource(R.string.update_download),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                // Only this button records "do not offer this version again";
                // back and outside taps just close the dialog.
                TextButton(onClick = onPostpone, enabled = !downloading) {
                    Text(stringResource(R.string.update_later))
                }
                TextButton(onClick = onDismiss, enabled = !downloading) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun ReleaseNoteLine(line: MarkdownLite.Line) {
    if (line.kind == MarkdownLite.Kind.BLANK) {
        // A spacer keeps paragraphs apart without a second text node.
        Text(text = "", style = MaterialTheme.typography.labelSmall)
        return
    }
    val text = buildAnnotatedString {
        line.segments.forEach { segment ->
            if (segment.bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(segment.text)
                }
            } else {
                append(segment.text)
            }
        }
    }
    when (line.kind) {
        MarkdownLite.Kind.HEADING -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        MarkdownLite.Kind.BULLET -> Text(
            text = "· " + text,
            style = MaterialTheme.typography.bodySmall,
        )

        else -> Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Compact size for the dialog: "12.3 MB", or a dash when GitHub omits it. */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
