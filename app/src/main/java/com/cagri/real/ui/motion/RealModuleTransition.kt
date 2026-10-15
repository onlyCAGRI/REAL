package com.cagri.real.ui.motion

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
    content: @Composable () -> Unit
) {
    val clock = remember { Animatable(0f) }
    var retained by remember { mutableStateOf(visible) }
    val motion = remember(clock, reduceMotion) { RealEntranceMotion(clock, !reduceMotion) }
    LaunchedEffect(visible, reduceMotion) {
        if (visible) retained = true
        val destination = if (visible) 720f else 0f
        if (reduceMotion) clock.snapTo(destination)
        else if (clock.value != destination) {
            val fullDurationMs = if (visible) 720 else 260
            val remainingFraction = kotlin.math.abs(destination - clock.value) / 720f
            val duration = (fullDurationMs * remainingFraction).toInt().coerceAtLeast(1)
            clock.animateTo(destination, tween(duration, easing = LinearEasing))
        }
        if (!visible) retained = false
    }
    if (visible || retained) {
        Box(modifier.fillMaxSize()
            .realCircleReveal(motion, originX = 0.5f, originY = 0.93f, durationMs = 720)
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
