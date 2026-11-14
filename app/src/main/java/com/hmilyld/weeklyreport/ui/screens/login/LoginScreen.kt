package com.hmilyld.weeklyreport.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.ui.asText
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
                // Consume status/navigation/cutout insets before scrolling so
                // the IME shrinks the viewport instead of covering the fields.
                .safeDrawingPadding()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.lg, bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
                Text(
                    text = stringResource(R.string.login_kicker),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                text = stringResource(R.string.login_subtitle),
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
                label = {
                    Text(
                        if (state.serverInput.isBlank()) {
                            stringResource(R.string.login_server_label_required)
                        } else {
                            stringResource(R.string.common_server_address)
                        },
                    )
                },
                placeholder = { Text("https://report.example.com:18001") },
                singleLine = true,
                isError = state.serverError != null,
                supportingText = {
                    val serverError = state.serverError
                    val testResult = state.testResult
                    Text(
                        text = when {
                            serverError != null -> serverError
                            testResult != null -> testResult.asText()
                            else -> stringResource(R.string.login_server_hint)
                        },
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
                    Text(stringResource(R.string.common_test_connection))
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
                label = { Text(stringResource(R.string.login_username)) },
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
                label = { Text(stringResource(R.string.login_password)) },
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
                            contentDescription = if (state.showPassword) {
                                stringResource(R.string.login_hide_password)
                            } else {
                                stringResource(R.string.login_show_password)
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
            )

            val formError = state.formError
            if (formError != null) {
                Text(
                    text = formError.asText(),
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
                    .heightIn(min = 52.dp),
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
                Text(stringResource(R.string.login_action), fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = stringResource(R.string.login_footer),
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
