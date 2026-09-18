package com.cagri.bigboscagri.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cagri.bigboscagri.R

private val BigBosFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private val baseline = Typography()
private val BigBosTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = BigBosFont),
    displayMedium = baseline.displayMedium.copy(fontFamily = BigBosFont),
    displaySmall = baseline.displaySmall.copy(fontFamily = BigBosFont),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = BigBosFont),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = BigBosFont),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = BigBosFont),
    titleLarge = baseline.titleLarge.copy(fontFamily = BigBosFont),
    titleMedium = baseline.titleMedium.copy(fontFamily = BigBosFont),
    titleSmall = baseline.titleSmall.copy(fontFamily = BigBosFont),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = BigBosFont),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = BigBosFont),
    bodySmall = baseline.bodySmall.copy(fontFamily = BigBosFont),
    labelLarge = baseline.labelLarge.copy(fontFamily = BigBosFont),
    labelMedium = baseline.labelMedium.copy(fontFamily = BigBosFont),
    labelSmall = baseline.labelSmall.copy(fontFamily = BigBosFont)
)

private val BigBosColors = darkColorScheme(
    primary = Color(0xFF78A9FF),
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF202024),
    onSurfaceVariant = Color(0xFFAAAAB2),
    outline = Color(0xFF34343A)
)

@Composable
fun BigBosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BigBosColors,
        typography = BigBosTypography,
        content = content
    )
}
