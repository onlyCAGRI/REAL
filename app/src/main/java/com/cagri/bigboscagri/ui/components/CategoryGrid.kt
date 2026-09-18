package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.ReminderCategory

private enum class Glyph { Calendar, Clock, Star, Note, Completed, Add }

private data class CategoryStyle(
    val label: String,
    val glyph: Glyph,
    val base: Color,
    val upper: Color,
    val lower: Color
)

private val categories = listOf(
    CategoryStyle("Bugün", Glyph.Calendar, Color(0xFF141E4E), Color(0xFF23457D), Color(0xFF863D59)),
    CategoryStyle("Planlı", Glyph.Clock, Color(0xFF202142), Color(0xFF32655E), Color(0xFF803B4D)),
    CategoryStyle("Önemli", Glyph.Star, Color(0xFF352137), Color(0xFF8E664A), Color(0xFF7B355F)),
    CategoryStyle("Uyarı yok", Glyph.Note, Color(0xFF222142), Color(0xFF414E83), Color(0xFF674878)),
    CategoryStyle("Tamamlananlar", Glyph.Completed, Color(0xFF152F35), Color(0xFF387D70), Color(0xFF333F77)),
    CategoryStyle("Ekle", Glyph.Add, Color(0xFF1C1C1E), Color.Transparent, Color.Transparent)
)

@Composable
fun CategoryGrid(
    onOpenCategory: (ReminderCategory) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        categories.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    val target = when (category.glyph) {
                        Glyph.Calendar -> ReminderCategory.Today
                        Glyph.Clock -> ReminderCategory.Planned
                        Glyph.Star -> ReminderCategory.Important
                        Glyph.Note -> ReminderCategory.NoAlert
                        Glyph.Completed -> ReminderCategory.Completed
                        Glyph.Add -> null
                    }
                    CategoryCard(
                        category = category,
                        onClick = { if (target == null) onAdd() else onOpenCategory(target) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: CategoryStyle, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        // Only the image is rounded: clipping this column also clips label glyphs.
        modifier = modifier.clickable(
            role = Role.Button, onClickLabel = category.label, onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(shape)
                .drawWithCache {
                    val topLight = Brush.radialGradient(
                        colors = listOf(category.upper, Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(category.lower, Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(category.base)
                        drawRect(topLight)
                        drawRect(bottomLight)
                    }
                }
                .border(0.75.dp, Color.White.copy(alpha = 0.07f), shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryGlyph(category.glyph, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.label,
            color = Color(0xFFE7E7EB), fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 3.dp)
        )
    }
}

// Small repo-native glyphs keep the white icon style consistent without adding
// an icon-pack dependency. Coordinates use a 24-unit drawing space.
@Composable
private fun CategoryGlyph(glyph: Glyph, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val white = Color.White
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(white, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
            when (glyph) {
                Glyph.Calendar -> {
                    drawRoundRect(white, Offset(3.5f, 5f), Size(17f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(3.5f, 10f, 20.5f, 10f)
                    line(8f, 3f, 8f, 7f)
                    line(16f, 3f, 16f, 7f)
                    drawCircle(white, 1.15f, Offset(8.5f, 14f))
                    drawCircle(white, 1.15f, Offset(15.5f, 14f))
                    drawCircle(white, 1.15f, Offset(8.5f, 17.5f))
                }
                Glyph.Clock -> {
                    drawCircle(white, 9f, Offset(12f, 12f), style = stroke)
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
                    drawPath(star, white)
                }
                Glyph.Note -> {
                    drawRoundRect(white, Offset(5f, 3f), Size(14f, 18f), CornerRadius(2.5f, 2.5f), style = stroke)
                    line(9f, 8f, 15f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 12.5f, 16f)
                }
                Glyph.Completed -> {
                    drawCircle(white, 9f, Offset(12f, 12f), style = stroke)
                    line(7.5f, 12f, 10.7f, 15.2f)
                    line(10.7f, 15.2f, 17f, 8.8f)
                }
                Glyph.Add -> {
                    line(12f, 4.5f, 12f, 19.5f)
                    line(4.5f, 12f, 19.5f, 12f)
                }
            }
        }
    }
}
