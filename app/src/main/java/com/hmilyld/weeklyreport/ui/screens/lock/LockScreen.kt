package com.hmilyld.weeklyreport.ui.screens.lock

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.ui.theme.Spacing

@Composable
fun LockScreen(
    username: String,
    biometricAvailable: Boolean,
    onUnlock: () -> Unit,
    onLoginWithPassword: () -> Unit,
    onNotify: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        if (biometricAvailable) onUnlock()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(Spacing.xl)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Spacing.lg)
            )
            Text(
                text = if (username.isBlank()) {
                    stringResource(R.string.lock_subtitle_saved_session)
                } else {
                    stringResource(R.string.lock_subtitle_username, username)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )

            Button(
                onClick = onUnlock,
                enabled = biometricAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xxl)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.size(Spacing.sm))
                Text(stringResource(R.string.lock_unlock_action), fontWeight = FontWeight.SemiBold)
            }
            if (!biometricAvailable) {
                Text(
                    text = stringResource(R.string.lock_no_biometric),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
            Row(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onLoginWithPassword) {
                    Text(stringResource(R.string.lock_login_with_password))
                }
            }
        }
        Text(
            text = stringResource(R.string.lock_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}
