package com.cagri.real.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cagri.real.data.ReminderSortOrder

private val GlyphSecondaryColor = Color(0xFFA8A8B2)

@Composable
fun HomeSortControl(
    sortOrder: ReminderSortOrder,
    onToggleSortOrder: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(44.dp)
            .then(if (LocalDesignStyle.current == com.cagri.real.ui.DesignStyle.LiquidGlass) Modifier.pressScale(interactionSource, reduceAnimations = reduceAnimations, targetScale = 0.97f).liquidChrome() else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggleSortOrder
            )
            .semantics {
                role = Role.Button
                contentDescription = when (sortOrder) {
                    ReminderSortOrder.RECENTLY_ADDED, ReminderSortOrder.OLDEST_FIRST ->
                        "Son eklenene göre sıralanıyor. Bildirim zamanına göre sırala."
                    ReminderSortOrder.REMINDER_TIME, ReminderSortOrder.NEAREST_DATE ->
                        "Bildirim zamanına göre sıralanıyor. Son eklenene göre sırala."
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = sortOrder,
            transitionSpec = {
                if (reduceAnimations) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 160))
                        .togetherWith(fadeOut(animationSpec = tween(durationMillis = 140)))
                }
            },
            label = "sortGlyphTransition"
        ) { order ->
            when (order) {
                ReminderSortOrder.RECENTLY_ADDED, ReminderSortOrder.OLDEST_FIRST -> {
                    RecentSortGlyph(modifier = Modifier.size(20.dp))
                }
                ReminderSortOrder.REMINDER_TIME, ReminderSortOrder.NEAREST_DATE -> {
                    TimeSortGlyph(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentSortGlyph(
    modifier: Modifier = Modifier,
    color: Color = GlyphSecondaryColor
) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), 4.5.dp.toPx()),
            end = Offset(18.dp.toPx(), 4.5.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), 10.dp.toPx()),
            end = Offset(12.5.dp.toPx(), 10.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), 15.5.dp.toPx()),
            end = Offset(7.dp.toPx(), 15.5.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun TimeSortGlyph(
    modifier: Modifier = Modifier,
    color: Color = GlyphSecondaryColor
) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 6.5.dp.toPx()

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - 4.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + 3.2.dp.toPx(), center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
