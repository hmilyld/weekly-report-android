package com.hmilyld.weeklyreport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.ui.theme.Spacing

@Composable
fun PeriodNavigator(
    label: String,
    caption: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: (() -> Unit)?,
    todayLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PaperCard {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = enabled) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = stringResource(R.string.common_previous_period),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, enabled = enabled) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = stringResource(R.string.common_next_period),
                    )
                }
            }
        }
        if (onToday != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToday, enabled = enabled) {
                    Icon(
                        Icons.Outlined.Today,
                        contentDescription = null,
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                    Text(todayLabel)
                }
            }
        }
    }
}
