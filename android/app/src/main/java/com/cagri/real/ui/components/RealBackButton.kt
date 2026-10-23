package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared back glyph with a 48dp touch target; screens match their adjacent chrome. */
@Composable
internal fun RealBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = t("Geri"),
    enabled: Boolean = true,
    tint: Color = Color.White,
    /** Screen-owned glass surface, drawn behind the shared glyph. */
    surface: (@Composable () -> Unit)? = null,
    visualSize: Dp = 36.dp,
    glyphSize: Dp = 16.dp
) {
    Box(modifier.size(48.dp).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onBack)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(visualSize).clip(CircleShape)
            .then(if (surface == null) Modifier.background(tint.copy(alpha = 0.10f)) else Modifier),
            contentAlignment = Alignment.Center) {
            surface?.invoke()
            Canvas(Modifier.size(glyphSize)) {
                val path = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.15f)
                    lineTo(size.width * 0.28f, size.height * 0.5f)
                    lineTo(size.width * 0.62f, size.height * 0.85f)
                }
                drawPath(path, tint.copy(alpha = if (enabled) tint.alpha else tint.alpha * 0.35f),
                    style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
