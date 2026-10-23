package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.cagri.real.BuildConfig

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
        animationSpec = if (isPressed) {
            tween(durationMillis = 90, easing = RealMotion.Standard)
        } else {
            RealMotion.snappy<Float>()
        },
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

object RealHaptics {
    private const val TAG = "RealHaptics"
    private const val TICK_INTERVAL_MS = 70L
    private const val ACTION_INTERVAL_MS = 35L
    private var lastFeedbackTime: Long? = null
    private var lastTickLogTime: Long? = null

    fun tick(view: View) {
        perform(view, "PICKER_TICK", HapticFeedbackConstants.CLOCK_TICK, isFrequent = true, fallbackMillis = 30)
    }

    fun toggle(view: View, on: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        perform(view, if (on) "TOGGLE_ON" else "TOGGLE_OFF", constant, fallbackMillis = 38)
    }

    fun success(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        perform(view, "CONFIRM", constant, fallbackMillis = 50)
    }

    /**
     * Phones whose motor has none of Android's light effects (tick / click; e.g. Galaxy A53: no supported effects or
     * primitives) silently skip CLOCK_TICK, CONFIRM and TOGGLE — only LONG_PRESS, which has a built-in pattern, is felt.
     * There REAL plays a short plain pulse of its own instead, still only when the phone's touch feedback is on.
     */
    private var plainMotor: Boolean? = null

    private fun vibrator(view: View): Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") view.context.getSystemService(Vibrator::class.java)

    private fun needsPlainPulse(view: View): Boolean = plainMotor ?: run {
        val v = vibrator(view)
        val plain = v != null && v.hasVibrator() && (Build.VERSION.SDK_INT < 30 ||
            v.areAllEffectsSupported(VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK) != Vibrator.VIBRATION_EFFECT_SUPPORT_YES)
        plainMotor = plain
        plain
    }

    private fun touchFeedbackOn(view: View): Boolean = view.isHapticFeedbackEnabled &&
        runCatching { @Suppress("DEPRECATION") Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }
            .getOrDefault(true)

    private fun perform(
        view: View,
        actionName: String,
        constant: Int,
        isFrequent: Boolean = false,
        fallbackMillis: Long = 24
    ) {
        // Ignore stale views during navigation. All UI callers run on the main thread.
        if (!view.isAttachedToWindow || !view.isShown || !view.hasWindowFocus()) return

        val now = SystemClock.uptimeMillis()
        val interval = if (isFrequent) TICK_INTERVAL_MS else ACTION_INTERVAL_MS
        val previous = lastFeedbackTime
        if (previous != null && now - previous < interval) return
        lastFeedbackTime = now

        // Let Android enforce the current view/system/user haptic settings. Do not gate
        // on the deprecated Settings key or bypass settings with IGNORE flags.
        val performed = if (needsPlainPulse(view)) {
            touchFeedbackOn(view) && runCatching {
                vibrator(view)?.vibrate(VibrationEffect.createOneShot(fallbackMillis, VibrationEffect.DEFAULT_AMPLITUDE))
            }.isSuccess
        } else view.performHapticFeedback(constant)
        val previousLog = lastTickLogTime
        if (BuildConfig.DEBUG && (!isFrequent || previousLog == null || now - previousLog >= 400L)) {
            if (isFrequent) lastTickLogTime = now
            Log.d(
                TAG,
                "[${actionName}] backend=VIEW_HAPTIC constant=${constant} performed=${performed} " +
                    "viewHapticEnabled=${view.isHapticFeedbackEnabled} sdk=${Build.VERSION.SDK_INT}"
            )
        }
    }
}
