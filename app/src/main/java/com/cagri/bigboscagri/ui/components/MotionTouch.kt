package com.cagri.bigboscagri.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a subtle physical touch compression without consuming or intercepting gestures.
 * Relies strictly on the component's existing or provided InteractionSource.
 * Scale: 1.0 -> targetScale (default 0.98f) on press, 0.98 -> 1.0 on release.
 * Bypassed when reduceAnimations is true.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    targetScale: Float = 0.98f,
    reduceAnimations: Boolean = false
): Modifier {
    if (!enabled || reduceAnimations) return this
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = tween(
            durationMillis = if (isPressed) 90 else 140,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Dispatches restrained, purposeful haptic feedback for meaningful state changes.
 * Does not fire on random touch down/up, only on discrete completions, toggles, or ticks.
 */
object RealHaptics {
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun toggle(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
