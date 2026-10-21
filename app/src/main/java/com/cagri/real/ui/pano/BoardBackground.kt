package com.cagri.real.ui.pano

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cagri.real.boards.*

private fun BoardBackgroundColor.tint() = when (this) {
    BoardBackgroundColor.BLACK -> Color(0xFF000000)
    BoardBackgroundColor.GRAY -> Color(0xFF141416)
    BoardBackgroundColor.BLUE -> Color(0xFF0C192C)
    BoardBackgroundColor.GREEN -> Color(0xFF0B1F17)
}

/** World-anchored grid, with bounded drawing work even at one percent zoom. */
@Composable
internal fun BoardBackground(viewport: BoardViewport, look: BoardLook) {
    Canvas(Modifier.fillMaxSize()) {
        val visibleLook = look.supported()
        drawRect(visibleLook.color.tint())
        if (visibleLook.pattern == BoardBackgroundPattern.NONE) return@Canvas
        var spacing = 24.dp.toPx() * viewport.zoom
        while (spacing < 12.dp.toPx()) spacing *= 2f
        val dx = viewport.x.dp.toPx()
        val dy = viewport.y.dp.toPx()
        val firstX = ((dx % spacing) + spacing) % spacing
        val firstY = ((dy % spacing) + spacing) % spacing
        val ink = Color.White.copy(alpha = if (visibleLook.pattern == BoardBackgroundPattern.DOTS) 0.08f else 0.045f)
        var x = firstX
        while (x < size.width) {
            if (visibleLook.pattern == BoardBackgroundPattern.GRID) drawLine(ink, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
            else {
                var y = firstY
                while (y < size.height) { drawCircle(ink, 0.9.dp.toPx(), Offset(x, y)); y += spacing }
            }
            x += spacing
        }
        if (visibleLook.pattern == BoardBackgroundPattern.GRID) {
            var y = firstY
            while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx()); y += spacing }
        }
    }
}

/** Inline picker used in the People sheet: visual tiles for the same black/gray + dots/grid options. */
@Composable
internal fun BoardLookSelector(look: BoardLook, onChange: (BoardLook) -> Unit) {
    val current = look.supported()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(BoardBackgroundColor.BLACK, BoardBackgroundColor.GRAY).forEach { color ->
                LookTile(color, current.pattern, color.name.lowercase().replaceFirstChar { it.uppercase() },
                    current.color == color, Modifier.weight(1f)) { onChange(current.copy(color = color)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(BoardBackgroundPattern.DOTS, BoardBackgroundPattern.GRID).forEach { pattern ->
                LookTile(current.color, pattern, pattern.name.lowercase().replaceFirstChar { it.uppercase() },
                    current.pattern == pattern, Modifier.weight(1f)) { onChange(current.copy(pattern = pattern)) }
            }
        }
    }
}

@Composable
private fun LookTile(color: BoardBackgroundColor, pattern: BoardBackgroundPattern, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape)
        .border(if (selected) 1.5.dp else 0.8.dp, if (selected) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f), shape)
        .clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(11.dp))) {
            drawRect(color.tint())
            val spacing = 10.dp.toPx()
            val ink = Color.White.copy(alpha = if (pattern == BoardBackgroundPattern.DOTS) 0.22f else 0.14f)
            var x = spacing / 2
            while (x < size.width) {
                if (pattern == BoardBackgroundPattern.GRID) drawLine(ink, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
                else {
                    var y = spacing / 2
                    while (y < size.height) { drawCircle(ink, 1.2.dp.toPx(), Offset(x, y)); y += spacing }
                }
                x += spacing
            }
            if (pattern == BoardBackgroundPattern.GRID) {
                var y = spacing / 2
                while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx()); y += spacing }
            }
        }
        Text(label, color = if (selected) Color.White else Color(0xFF9A9AA2), fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    }
}
