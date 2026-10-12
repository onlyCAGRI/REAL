package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.DesignStyle

@Composable
fun SelectionActionButtons(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean,
    designStyle: DesignStyle,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(29.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cancelContent: @Composable BoxScope.() -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(0.75.dp, Color.White.copy(alpha = 0.10f), shape)
                    .clickable(
                        role = Role.Button,
                        enabled = enabled,
                        onClickLabel = "Vazgeç",
                        onClick = onCancel
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Vazgeç",
                    color = Color(0xFFE4E4EA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        val deleteContent: @Composable BoxScope.() -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF87171).copy(alpha = 0.12f), shape)
                    .border(0.75.dp, Color(0xFFF87171).copy(alpha = 0.28f), shape)
                    .clickable(
                        role = Role.Button,
                        enabled = enabled && selectedCount > 0,
                        onClickLabel = "Seçilenleri sil",
                        onClick = onDelete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sil ($selectedCount)",
                    color = if (selectedCount > 0) Color(0xFFF87171) else Color(0xFF8E8E93),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        if (designStyle == DesignStyle.LiquidGlass) {
            LiquidGlassSurface(backdrop, backdropPosition, Modifier.weight(1f).fillMaxHeight(),
                29.dp, reduceDynamicBlur, content = cancelContent)
            LiquidGlassSurface(backdrop, backdropPosition, Modifier.weight(1f).fillMaxHeight(),
                29.dp, reduceDynamicBlur, content = deleteContent)
        } else if (designStyle == DesignStyle.Ice) {
            IcePanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                corners = 29.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                blurRadius = 38.dp,
                dense = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                content = cancelContent
            )
            IcePanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                corners = 29.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                blurRadius = 38.dp,
                dense = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                content = deleteContent
            )
        } else {
            GlassPanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                corners = 29.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                content = cancelContent
            )
            GlassPanel(
                backdrop = backdrop,
                backdropPosition = backdropPosition,
                corners = 29.dp,
                reduceDynamicBlur = reduceDynamicBlur,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                content = deleteContent
            )
        }
    }
}
