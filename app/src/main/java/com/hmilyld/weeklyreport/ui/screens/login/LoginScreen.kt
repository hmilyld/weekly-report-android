package com.hmilyld.weeklyreport.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.hmilyld.weeklyreport.ui.components.Banner
import com.hmilyld.weeklyreport.ui.theme.Spacing

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    problem: String?,
    onNotify: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.syncFromSettings()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = 64.dp, bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
                Text(
                    text = "周记 · WEEKLY LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = "把这一周写下来",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                text = "自己的服务器，自己的记录。登录后可以离线写日报和待办，联网时按顺序补发。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            if (problem != null) {
                Box(modifier = Modifier.padding(top = Spacing.lg)) {
                    Banner(
                        text = problem,
                        icon = Icons.Outlined.Lock,
                    )
                }
            }

            OutlinedTextField(
                value = state.serverInput,
                onValueChange = viewModel::updateServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xl),
                label = { Text(if (state.serverInput.isBlank()) "服务器地址（首次必填）" else "服务器地址") },
                placeholder = { Text("https://report.example.com:18001") },
                singleLine = true,
                isError = state.serverError != null,
                supportingText = {
                    Text(
                        text = state.serverError
                            ?: state.testResult
                            ?: "正式版仅支持 HTTPS；调试版可用局域网 HTTP",
                    )
                },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = viewModel::testServer,
                    enabled = !state.testing && !state.loggingIn,
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(Spacing.sm))
                    }
                    Text("测试连接")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.lg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::updateUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                label = { Text("密码") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (state.showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisible) {
                        Icon(
                            imageVector = if (state.showPassword) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (state.showPassword) "隐藏密码" else "显示密码",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
            )

            if (state.formError != null) {
                Text(
                    text = state.formError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            Button(
                onClick = viewModel::login,
                enabled = !state.loggingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                }
                Text("登录", fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "密码只用于换取登录令牌，不会保存在设备上；令牌由 Android Keystore 加密。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            )
        }
    }
}
