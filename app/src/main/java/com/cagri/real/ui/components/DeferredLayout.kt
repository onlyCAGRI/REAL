package com.cagri.real.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/*
 * Layout-phase twins of Modifier.height / padding(bottom) and PaddingValues. The size is read
 * while measuring, so an animated Dp (composer height, bottom gap) relayouts its own node every
 * frame instead of recomposing the whole screen that owns the animation.
 */

/** Modifier.height(height()) with the value read during measure. */
internal fun Modifier.deferredHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val h = constraints.constrainHeight(height().roundToPx().coerceAtLeast(0))
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/** Modifier.padding(bottom = bottom()) with the value read during measure. */
internal fun Modifier.deferredBottomPadding(bottom: () -> Dp): Modifier = layout { measurable, constraints ->
    val b = bottom().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -b))
    layout(placeable.width, constraints.constrainHeight(placeable.height + b)) { placeable.place(0, 0) }
}

/** Modifier.padding(top = top()) with the value read during measure. */
internal fun Modifier.deferredTopPadding(top: () -> Dp): Modifier = layout { measurable, constraints ->
    val t = top().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -t))
    layout(placeable.width, constraints.constrainHeight(placeable.height + t)) { placeable.place(0, t) }
}

/** PaddingValues whose bottom is read by the lazy list during measure, not when the screen composes. */
internal class DeferredBottomPaddingValues(
    private val top: Dp,
    private val bottom: () -> Dp
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = top
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = bottom()
}
