package com.cagri.real.ui.components

import android.annotation.SuppressLint

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LiquidSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduceAnimations: Boolean = LocalLiquidReduceMotion.current
) {
    if (labels.isEmpty()) return
    val selected = selectedIndex.coerceIn(labels.indices)
    val height = 40.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) + 8.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(height).liquidChrome(26.dp).padding(4.dp)) {
        val cellWidth = maxWidth / labels.size
        val selectionX = animateDpAsState(
            cellWidth * selected,
            animationSpec = if (reduceAnimations) snap() else tween(LiquidGlassTokens.settleMillis),
            label = "liquidSegmentSelection"
        )
        Box(Modifier.offset { IntOffset(selectionX.value.roundToPx(), 0) }.width(cellWidth).fillMaxHeight().liquidChrome(22.dp, selected = true))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                Box(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = index == selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 14.sp, maxLines = 1,
                        color = if (!enabled) Color(0xFF8E8E93) else if (index == selected) Color.White else Color(0xFFB1B1B7),
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}


fun Modifier.liquidWheelWindow(rowHeight: Dp): Modifier = drawWithCache {
    val inset = 2.dp.toPx()
    val origin = Offset(inset, rowHeight.toPx())
    val window = Size((size.width - inset * 2).coerceAtLeast(0f), rowHeight.toPx())
    val radius = CornerRadius(14.dp.toPx())
    val stroke = Stroke(0.6.dp.toPx())
    val edge = liquidContentEdge()
    onDrawBehind {
        drawRoundRect(Color.White.copy(alpha = 0.045f), origin, window, radius)
        drawRoundRect(edge, origin, window, radius, style = stroke)
    }
}
