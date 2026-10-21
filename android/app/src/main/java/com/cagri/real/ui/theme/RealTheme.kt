package com.cagri.real.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cagri.real.R

internal val RealFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private val baseline = Typography()
private val RealTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = RealFont),
    displayMedium = baseline.displayMedium.copy(fontFamily = RealFont),
    displaySmall = baseline.displaySmall.copy(fontFamily = RealFont),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = RealFont),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = RealFont),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = RealFont),
    titleLarge = baseline.titleLarge.copy(fontFamily = RealFont),
    titleMedium = baseline.titleMedium.copy(fontFamily = RealFont),
    titleSmall = baseline.titleSmall.copy(fontFamily = RealFont),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = RealFont),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = RealFont),
    bodySmall = baseline.bodySmall.copy(fontFamily = RealFont),
    labelLarge = baseline.labelLarge.copy(fontFamily = RealFont),
    labelMedium = baseline.labelMedium.copy(fontFamily = RealFont),
    labelSmall = baseline.labelSmall.copy(fontFamily = RealFont)
)

private val RealColors = darkColorScheme(
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
fun RealTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RealColors,
        typography = RealTypography
    ) {
        // Default clickables press-scale like the rest of REAL instead of drawing a Material ripple.
        // Material components (IconButton, TextButton…) draw their own ripple and are unaffected.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides com.cagri.real.ui.components.RealPressIndication,
            content = content
        )
    }
}
