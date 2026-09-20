package com.cagri.bigboscagri.ui.components

import android.os.Build
import android.provider.Settings
import android.util.Log
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
import com.cagri.bigboscagri.BuildConfig

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
 *
 * Semantic mapping:
 * - save / completion -> CONFIRM
 * - toggle ON -> TOGGLE_ON
 * - toggle OFF -> TOGGLE_OFF
 * - picker tick -> SEGMENT_TICK
 */
object RealHaptics {
    private const val TAG = "RealHaptics"
    private var lastTickTime = 0L

    /**
     * Mechanical wheel tick when centered value changes.
     * Uses SEGMENT_TICK on API 34+, falls back to CLOCK_TICK on < 34.
     */
    fun tick(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        perform(view, constant, "SEGMENT_TICK", isFrequent = true)
    }

    /**
     * Semantic toggle feedback.
     * Turning ON uses TOGGLE_ON (API 34+) or KEYBOARD_TAP (< 34).
     * Turning OFF uses TOGGLE_OFF (API 34+) or CLOCK_TICK (< 34).
     */
    fun toggle(view: View, on: Boolean) {
        val constant = if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_OFF
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
        perform(view, constant, if (on) "TOGGLE_ON" else "TOGGLE_OFF")
    }

    /**
     * Confirmation on reminder save or completion.
     * Uses CONFIRM on API 30+, falls back to KEYBOARD_TAP on < 30.
     */
    fun success(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        perform(view, constant, "CONFIRM")
    }

    private fun perform(
        view: View,
        feedbackConstant: Int,
        actionName: String,
        isFrequent: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val shouldLog = BuildConfig.DEBUG && (!isFrequent || now - lastTickTime > 400)
        if (isFrequent) {
            lastTickTime = now
        }

        val performed = view.performHapticFeedback(feedbackConstant)

        if (shouldLog) {
            val isViewHapticEnabled = view.isHapticFeedbackEnabled
            val systemHapticSetting = runCatching {
                Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1)
            }.getOrDefault(-1)
            Log.d(
                TAG,
                "[$actionName] constant=$feedbackConstant performed=$performed " +
                "viewHapticEnabled=$isViewHapticEnabled systemHapticSetting=$systemHapticSetting sdk=${Build.VERSION.SDK_INT}"
            )
        }
    }
}
