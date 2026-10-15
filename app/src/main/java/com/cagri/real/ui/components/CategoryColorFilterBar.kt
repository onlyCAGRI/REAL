package com.cagri.real.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun CategoryColorFilterButton(
    hasFilter: Boolean,
    onClick: () -> Unit,
    selectedColorIndex: Int? = null,
    modifier: Modifier = Modifier.size(44.dp),
    applyLiquidChrome: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val baseDotColor = Color(0xFFA8A8B2)
    val activeDotColor = if (hasFilter) {
        if (selectedColorIndex != null && selectedColorIndex in ReminderColorChoices.indices) {
            ReminderColorChoices[selectedColorIndex].color
        } else {
            Color.White
        }
    } else {
        baseDotColor
    }

    Box(
        modifier = modifier
            .then(
                if (LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass) {
                    Modifier
                        .pressScale(interactionSource, reduceAnimations = LocalLiquidReduceMotion.current, targetScale = 0.97f)
                        .then(if (applyLiquidChrome) Modifier.liquidChrome(selected = hasFilter) else Modifier)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = if (hasFilter) "Renk filtresini değiştir" else "Renk filtresi"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val cy = size.height / 2f
            val r = 1.75.dp.toPx()
            drawCircle(baseDotColor, r, Offset(2.5.dp.toPx(), cy))
            drawCircle(activeDotColor, r, Offset(10.dp.toPx(), cy))
            drawCircle(baseDotColor, r, Offset(17.5.dp.toPx(), cy))
        }
    }
}

@Composable
fun CategoryEditButton(
    onClick: () -> Unit,
    contentDescription: String = "Kategoriyi düzenle",
    modifier: Modifier = Modifier.size(44.dp),
    applyLiquidChrome: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(
                if (LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass) {
                    Modifier
                        .pressScale(interactionSource, reduceAnimations = LocalLiquidReduceMotion.current, targetScale = 0.97f)
                        .then(if (applyLiquidChrome) Modifier.liquidChrome() else Modifier)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 1.4.dp.toPx()
            val path = Path().apply {
                moveTo(3.5.dp.toPx(), 12.5.dp.toPx())
                lineTo(13.dp.toPx(), 3.dp.toPx())
                lineTo(17.dp.toPx(), 7.dp.toPx())
                lineTo(7.5.dp.toPx(), 16.5.dp.toPx())
                lineTo(2.5.dp.toPx(), 17.5.dp.toPx())
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFFA8A8B2),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawLine(
                color = Color(0xFFA8A8B2),
                start = Offset(11.dp.toPx(), 5.dp.toPx()),
                end = Offset(15.dp.toPx(), 9.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun CategoryLiquidActionGroup(
    hasFilter: Boolean,
    selectedColorIndex: Int?,
    onFilterClick: () -> Unit,
    onEditClick: () -> Unit,
    editContentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(112.dp)
            .height(48.dp)
            .liquidChrome(corners = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CategoryColorFilterButton(
            hasFilter = hasFilter,
            selectedColorIndex = selectedColorIndex,
            onClick = onFilterClick,
            modifier = Modifier.size(55.dp, 48.dp),
            applyLiquidChrome = false
        )
        Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.13f)))
        CategoryEditButton(
            onClick = onEditClick,
            contentDescription = editContentDescription,
            modifier = Modifier.size(55.dp, 48.dp),
            applyLiquidChrome = false
        )
    }
}








@Composable
fun CategoryColorSelectorHeader(
    selectedColorFilter: Int?,
    onSelectColor: (Int?) -> Unit,
    onClose: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, end = 8.dp, start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            
            val isClearActive = selectedColorFilter == null
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222226))
                    .border(
                        width = if (isClearActive) 1.5.dp else 0.75.dp,
                        color = if (isClearActive) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .clickable {
                        onSelectColor(null) 
                    }
                    .semantics { contentDescription = "Filtreyi temizle / Tümünü göster" },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.Close, Modifier.size(13.dp), Color(0xFFC8C8D0))
            }

            
            ReminderColorDisplayOrder.forEach { index ->
                val choice = ReminderColorChoices[index]
                val isSelected = selectedColorFilter == index
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(choice.color)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            onSelectColor(index) 
                        }
                        .semantics { contentDescription = "${choice.label} rengine göre filtrele" },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.width(6.dp))

        
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(34.dp)
                .semantics { contentDescription = "Renk seçiciyi kapat" }
        ) {
            ActionIcon(ActionGlyph.Close, Modifier.size(18.dp), color = Color(0xFF888890))
        }
    }
}
