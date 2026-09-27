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
 * Uses strictly View.performHapticFeedback with standard constants for safe device compatibility.
 *
 * Semantic mapping:
 * - save / completion -> LONG_PRESS
 * - toggle ON / OFF -> LONG_PRESS
 * - picker tick (selection settles) -> LONG_PRESS
 */
object RealHaptics {
    private const val TAG = "RealHaptics"
    private var lastTickTime = 0L

    /**
     * Mechanical wheel tick when centered value changes and settles.
     * Fires after scroll stops and a new value is selected.
     * Mapped to safe standard LONG_PRESS.
     */
    fun tick(view: View) {
        perform(
            view = view,
            actionName = "PICKER_TICK",
            constant = HapticFeedbackConstants.LONG_PRESS,
            isFrequent = true
        )
    }

    /**
     * Semantic toggle feedback.
     * Mapped to safe standard LONG_PRESS.
     */
    fun toggle(view: View, on: Boolean) {
        perform(
            view = view,
            actionName = if (on) "TOGGLE_ON" else "TOGGLE_OFF",
            constant = HapticFeedbackConstants.LONG_PRESS
        )
    }

    /**
     * Confirmation on reminder save or completion.
     * Mapped to safe standard LONG_PRESS.
     */
    fun success(view: View) {
        perform(
            view = view,
            actionName = "CONFIRM",
            constant = HapticFeedbackConstants.LONG_PRESS
        )
    }

    private fun perform(
        view: View,
        actionName: String,
        constant: Int,
        isFrequent: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val shouldLog = BuildConfig.DEBUG && (!isFrequent || now - lastTickTime > 400)
        if (isFrequent) {
            lastTickTime = now
        }

        // 1. Respect user's global system touch-haptic preference
        val isSystemHapticOn = runCatching {
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0
        }.getOrDefault(false)

        if (!isSystemHapticOn) {
            if (shouldLog) {
                logHaptic(view, actionName, constant, performed = false, detail = "system_touch_haptic_off")
            }
            return
        }

        // 2. Single safe View haptic backend
        val performed = view.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )

        if (shouldLog) {
            logHaptic(view, actionName, constant, performed, detail = "ok")
        }
    }

    private fun logHaptic(
        view: View,
        actionName: String,
        constant: Int,
        performed: Boolean,
        detail: String
    ) {
        val isViewHapticEnabled = view.isHapticFeedbackEnabled
        val systemHapticSetting = runCatching {
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1)
        }.getOrDefault(-1)
        Log.d(
            TAG,
            "[$actionName] backend=VIEW_HAPTIC constant=$constant performed=$performed " +
            "viewHapticEnabled=$isViewHapticEnabled systemHapticSetting=$systemHapticSetting sdk=${Build.VERSION.SDK_INT} detail=$detail"
        )
    }
}
