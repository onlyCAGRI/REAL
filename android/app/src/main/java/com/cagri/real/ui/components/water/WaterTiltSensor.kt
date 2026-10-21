package com.cagri.real.ui.components.water

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/** Registered only while the full-screen water view is resumed. No permissions required. */
@Composable
internal fun WaterTiltSensor(engine: WaterSurfaceEngine, enabled: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(engine, enabled, owner, context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        var registered = false
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                @Suppress("DEPRECATION")
                val rotation = window?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                val x = event.values[0]
                val y = event.values[1]
                val screenX: Float
                val screenY: Float
                when (rotation) {
                    Surface.ROTATION_90 -> { screenX = -y; screenY = x }
                    Surface.ROTATION_180 -> { screenX = -x; screenY = -y }
                    Surface.ROTATION_270 -> { screenX = y; screenY = -x }
                    else -> { screenX = x; screenY = y }
                }
                // The line stays approximately horizontal in world space; clamp near flat/inverted poses.
                val target = (screenX / maxOf(abs(screenY), 4f)).coerceIn(-0.65f, 0.65f)
                engine.targetTilt += (target - engine.targetTilt) * 0.18f
            }
        }
        fun sync() {
            val active = enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (active && !registered && sensor != null && manager != null) {
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (!active && registered) {
                manager?.unregisterListener(listener)
                registered = false
                engine.targetTilt = 0f
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        owner.lifecycle.addObserver(observer)
        sync()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
            engine.targetTilt = 0f
        }
    }
}
