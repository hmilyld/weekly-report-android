package com.hmilyld.weeklyreport.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.prefs.ThemeMode
import com.hmilyld.weeklyreport.ui.theme.Spacing
import com.hmilyld.weeklyreport.ui.theme.WeeklyReportTheme

/**
 * Shared components rendered in one place, so design changes can be reviewed
 * in the IDE without installing the app on a device.
 */
@Preview(name = "Light", showBackground = true, widthDp = 380)
@Composable
private fun ComponentsLightPreview() {
    ComponentCatalog()
}

@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComponentsDarkPreview() {
    ComponentCatalog()
}

@Preview(
    name = "Large font",
    showBackground = true,
    widthDp = 380,
    fontScale = 1.5f,
)
@Composable
private fun ComponentsLargeFontPreview() {
    ComponentCatalog()
}

@Composable
private fun ComponentCatalog() {
    WeeklyReportTheme(ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                ScreenHeader(
                    kicker = stringResource(R.string.daily_kicker),
                    title = stringResource(R.string.daily_title),
                    subtitle = stringResource(R.string.daily_subtitle),
                    trailing = {
                        Button(onClick = {}) { Text(stringResource(R.string.previews_refresh)) }
                    },
                )
                PaperCard(modifier = Modifier.padding(top = Spacing.md)) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            stringResource(R.string.previews_today_progress),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Pill(
                            text = stringResource(R.string.daily_pill_pending_sync),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                Banner(
                    text = stringResource(R.string.app_banner_conflict_count, 2),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                EmptyState(
                    title = stringResource(R.string.reports_empty_weekly_title),
                    description = stringResource(R.string.reports_empty_weekly_desc),
                    icon = Icons.Outlined.Checklist,
                )
            }
        }
    }
}
