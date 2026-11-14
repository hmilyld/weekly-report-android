package com.hmilyld.weeklyreport.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmilyld.weeklyreport.data.prefs.ThemeMode

// Warm paper + ink, with a vermilion mark for today and destructive actions.
private val Paper = Color(0xFFF7F2E7)
private val PaperRaised = Color(0xFFFFFBF3)
private val PaperSunken = Color(0xFFEDE4D4)
private val Ink = Color(0xFF1C1A17)
private val InkSoft = Color(0xFF6A6154)
private val Vermilion = Color(0xFFB03A2E)
private val VermilionInk = Color(0xFFFFF8F2)
private val Teal = Color(0xFF2F6D62)
private val OutlineWarm = Color(0xFFCEC3AF)

private val LightColors = lightColorScheme(
    primary = Vermilion,
    onPrimary = VermilionInk,
    primaryContainer = Color(0xFFF3D9D2),
    onPrimaryContainer = Color(0xFF3D0903),
    secondary = Teal,
    onSecondary = Color(0xFFF1FBF8),
    secondaryContainer = Color(0xFFD5E9E3),
    onSecondaryContainer = Color(0xFF08201B),
    tertiary = Color(0xFF7A5B2E),
    onTertiary = Color(0xFFFFF8EC),
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkSoft,
    outline = OutlineWarm,
    outlineVariant = Color(0xFFE2D8C7),
    error = Color(0xFFA3261C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7DDD8),
    onErrorContainer = Color(0xFF410001),
    // Every container / inverse slot is pinned to the warm palette: the M3
    // baseline values are cool gray-purple and would leak into Snackbar,
    // Switch tracks and tooltips.
    surfaceTint = Vermilion,
    surfaceBright = PaperRaised,
    surfaceDim = Color(0xFFE6DECF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF6EC),
    surfaceContainer = Color(0xFFF8F2E6),
    surfaceContainerHigh = Color(0xFFF3ECDE),
    surfaceContainerHighest = Color(0xFFEDE6D8),
    inverseSurface = Color(0xFF2E2A24),
    inverseOnSurface = Color(0xFFF5EEE0),
    inversePrimary = Color(0xFFE98570),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE98570),
    onPrimary = Color(0xFF3A0A02),
    primaryContainer = Color(0xFF5A1A12),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFF8FC9BC),
    onSecondary = Color(0xFF00372E),
    secondaryContainer = Color(0xFF1D4F46),
    onSecondaryContainer = Color(0xFFACEBDE),
    tertiary = Color(0xFFE0C08A),
    onTertiary = Color(0xFF3E2E0B),
    background = Color(0xFF14120E),
    onBackground = Color(0xFFF2EADB),
    surface = Color(0xFF1D1A14),
    onSurface = Color(0xFFF2EADB),
    surfaceVariant = Color(0xFF2A251C),
    onSurfaceVariant = Color(0xFFB8AA95),
    outline = Color(0xFF4B4234),
    outlineVariant = Color(0xFF332D23),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C140F),
    onErrorContainer = Color(0xFFFFDAD4),
    surfaceTint = Color(0xFFE98570),
    surfaceBright = Color(0xFF3A342A),
    surfaceDim = Color(0xFF14120E),
    surfaceContainerLowest = Color(0xFF0F0D0A),
    surfaceContainerLow = Color(0xFF1A1711),
    surfaceContainer = Color(0xFF201C15),
    surfaceContainerHigh = Color(0xFF2A251C),
    surfaceContainerHighest = Color(0xFF352F25),
    inverseSurface = Color(0xFFF2EADB),
    inverseOnSurface = Color(0xFF322E26),
    inversePrimary = Color(0xFF8E2F22),
    scrim = Color(0xFF000000),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.5.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// One corner-radius ladder for cards, sheets, fields and chips, so screens do
// not invent 4/12/14/16/20dp shapes ad hoc.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WeeklyReportTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
