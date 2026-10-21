package com.cagri.real.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * A number that rolls when it changes: a higher value slides in from below, a lower one from above,
 * the way iOS animates counters.
 */
@Composable
fun RollingNumberText(
    value: Int,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    reduceAnimations: Boolean = LocalLiquidReduceMotion.current
) {
    if (reduceAnimations) {
        Text(value.toString(), modifier, color = color, style = style, maxLines = 1, softWrap = false)
        return
    }
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(dampingRatio = 0.82f, stiffness = 600f)) { if (up) it else -it } + fadeIn(tween(160))) togetherWith
                (slideOutVertically(spring(dampingRatio = 0.82f, stiffness = 600f)) { if (up) -it else it } + fadeOut(tween(120))) using
                SizeTransform(clip = true)
        },
        label = "rollingNumber"
    ) { shown ->
        Text(shown.toString(), color = color, style = style, maxLines = 1, softWrap = false)
    }
}
