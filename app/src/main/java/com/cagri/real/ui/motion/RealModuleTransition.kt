package com.cagri.real.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Keeps the destination composed until the reverse reveal completes. */
@Composable
internal fun RealModuleTransition(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    originInRoot: Offset? = null,
    content: @Composable () -> Unit
) {
    val clock = remember { Animatable(if (visible) 500f else 0f) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var extent by remember { mutableStateOf(IntSize.Zero) }
    // The caller captures the icon at the instant of the tap, before the dock collapses.
    val ox = originInRoot?.let { if (extent.width > 0) ((it.x - rootPosition.x) / extent.width).coerceIn(0f, 1f) else 0.5f } ?: 0.5f
    val oy = originInRoot?.let { if (extent.height > 0) ((it.y - rootPosition.y) / extent.height).coerceIn(0f, 1f) else 0.93f } ?: 0.93f
    var retained by remember { mutableStateOf(visible) }
    val motion = remember(clock, reduceMotion) { RealEntranceMotion(clock, !reduceMotion) }
    LaunchedEffect(visible, reduceMotion) {
        if (visible) retained = true
        val destination = if (visible) 500f else 0f
        if (reduceMotion) clock.snapTo(destination)
        else if (clock.value != destination) {
            val fullDurationMs = if (visible) 500 else 260
            val remainingFraction = kotlin.math.abs(destination - clock.value) / 500f
            val duration = (fullDurationMs * remainingFraction).toInt().coerceAtLeast(1)
            clock.animateTo(destination, tween(duration, easing = LinearEasing))
        }
        if (!visible) retained = false
    }
    if (visible || retained) {
        Box(modifier.fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInRoot(); extent = it.size }
            .realCircleReveal(motion, originX = ox, originY = oy, durationMs = 500)
            .pointerInput(visible) {
                if (!visible) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }) {
            content()
        }
        // Consume repeated Back events while the departing scene is retained.
        BackHandler(enabled = !visible) { }
    }
}
