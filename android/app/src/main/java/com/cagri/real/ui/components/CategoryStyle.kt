package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.cagri.real.data.CategoryColorOverride
import com.cagri.real.data.ReminderCategory

enum class Glyph { Calendar, Clock, Star, Note, Completed, Journal, Add }

data class CategoryStyle(
    val label: String,
    val glyph: Glyph,
    val base: Color,
    val upper: Color,
    val lower: Color,
    val useDarkText: Boolean = false
) {
    val tint: Color get() = upper
}

fun ReminderCategory.glyph(): Glyph = when (this) {
    ReminderCategory.Today -> Glyph.Calendar
    ReminderCategory.Planned -> Glyph.Clock
    ReminderCategory.Important -> Glyph.Star
    ReminderCategory.NoAlert -> Glyph.Note
    ReminderCategory.Completed -> Glyph.Completed
    ReminderCategory.JournalEntries -> Glyph.Journal
}

fun ReminderCategory.defaultStyle(): CategoryStyle = when (this) {
    ReminderCategory.Today -> CategoryStyle("24 hour", Glyph.Calendar, Color(0xFF141E4E), Color(0xFF23457D), Color(0xFF863D59))
    ReminderCategory.Planned -> CategoryStyle("Planned", Glyph.Clock, Color(0xFFE0F37D), Color(0xFFE0F37D), Color(0xFFE0F37D), useDarkText = true)
    ReminderCategory.Important -> CategoryStyle("Important", Glyph.Star, Color(0xFF4658C8), Color(0xFF4658C8), Color(0xFF4658C8), useDarkText = false)
    ReminderCategory.NoAlert -> CategoryStyle("No alert", Glyph.Note, Color(0xFFDAB7F3), Color(0xFFDAB7F3), Color(0xFFDAB7F3), useDarkText = true)
    ReminderCategory.Completed -> CategoryStyle("Completed", Glyph.Completed, Color(0xFFEE5E36), Color(0xFFEE5E36), Color(0xFFEE5E36), useDarkText = false)
    ReminderCategory.JournalEntries -> CategoryStyle("Journal", Glyph.Journal, Color(0xFF1B2439), Color(0xFF4E648D), Color(0xFF7E5B6D))
}

fun ReminderCategory.resolveStyle(override: CategoryColorOverride?): CategoryStyle {
    val default = defaultStyle()
    if (override == null) return default
    val solidColor = override.customSolidColorArgb?.let { Color(it) }
    val startColor = solidColor ?: override.start?.let { ReminderColorChoices.getOrNull(it)?.color } ?: default.upper
    val endColor = solidColor ?: override.end?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
    return default.copy(
        base = Color(0xFF14151C),
        upper = startColor.copy(alpha = 0.85f),
        lower = endColor.copy(alpha = if (override.end != null) 0.70f else 0.60f),
        useDarkText = override.useDarkText
    )
}

fun ReminderCategory.resolveStyle(colorIndex: Int?): CategoryStyle =
    resolveStyle(colorIndex?.let { CategoryColorOverride(it, null) })

@Composable
fun CategoryGlyph(glyph: Glyph, modifier: Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
            when (glyph) {
                Glyph.Calendar -> {
                    drawRoundRect(tint, Offset(3.5f, 5f), Size(17f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(3.5f, 10f, 20.5f, 10f)
                    line(8f, 3f, 8f, 7f)
                    line(16f, 3f, 16f, 7f)
                    drawCircle(tint, 1.15f, Offset(8.5f, 14f))
                    drawCircle(tint, 1.15f, Offset(15.5f, 14f))
                    drawCircle(tint, 1.15f, Offset(8.5f, 17.5f))
                }
                Glyph.Clock -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 6.5f, 12f, 12.5f)
                    line(12f, 12.5f, 16.5f, 15f)
                }
                Glyph.Star -> {
                    val star = Path().apply {
                        moveTo(12f, 2f)
                        lineTo(15f, 8.4f)
                        lineTo(22f, 9.4f)
                        lineTo(17f, 14.4f)
                        lineTo(18.2f, 21.4f)
                        lineTo(12f, 18.1f)
                        lineTo(5.8f, 21.4f)
                        lineTo(7f, 14.4f)
                        lineTo(2f, 9.4f)
                        lineTo(9f, 8.4f)
                        close()
                    }
                    drawPath(star, tint)
                }
                Glyph.Note -> {
                    drawRoundRect(tint, Offset(5f, 3f), Size(14f, 18f), CornerRadius(2.5f, 2.5f), style = stroke)
                    line(9f, 8f, 15f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 12.5f, 16f)
                }
                Glyph.Completed -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(7.5f, 12f, 10.7f, 15.2f)
                    line(10.7f, 15.2f, 17f, 8.8f)
                }
                Glyph.Journal -> {
                    drawPath(Path().apply {
                        moveTo(6f, 4f)
                        lineTo(17f, 4f)
                        quadraticTo(19f, 4f, 19f, 6f)
                        lineTo(19f, 18f)
                        quadraticTo(19f, 20f, 17f, 20f)
                        lineTo(6f, 20f)
                        quadraticTo(5f, 20f, 5f, 19f)
                        lineTo(5f, 5f)
                        quadraticTo(5f, 4f, 6f, 4f)
                    }, tint, style = stroke)
                    line(9f, 4f, 9f, 22f)
                    line(13f, 8f, 15f, 8f)
                    line(13f, 12f, 15f, 12f)
                }
                Glyph.Add -> {
                    line(12f, 4.5f, 12f, 19.5f)
                    line(4.5f, 12f, 19.5f, 12f)
                }
            }
        }
    }
}
