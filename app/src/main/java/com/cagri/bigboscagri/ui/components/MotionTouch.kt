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










object RealHaptics {
    private const val TAG = "RealHaptics"
    private var lastTickTime = 0L

    




    fun tick(view: View) {
        perform(
            view = view,
            actionName = "PICKER_TICK",
            constant = HapticFeedbackConstants.LONG_PRESS,
            isFrequent = true
        )
    }

    



    fun toggle(view: View, on: Boolean) {
        perform(
            view = view,
            actionName = if (on) "TOGGLE_ON" else "TOGGLE_OFF",
            constant = HapticFeedbackConstants.LONG_PRESS
        )
    }

    



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

        
        val isSystemHapticOn = runCatching {
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0
        }.getOrDefault(false)

        if (!isSystemHapticOn) {
            if (shouldLog) {
                logHaptic(view, actionName, constant, performed = false, detail = "system_touch_haptic_off")
            }
            return
        }

        
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
