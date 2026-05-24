package com.vibetuned.ln_reader.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import kotlin.math.sqrt

/**
 * Calls [onShake] when the device experiences a strong jolt. Uses [Sensor.TYPE_LINEAR_ACCELERATION]
 * if available (gravity already removed), otherwise falls back to [Sensor.TYPE_ACCELEROMETER] with a
 * low-pass filter to estimate and subtract gravity.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService<SensorManager>()
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val needsGravityFilter: Boolean =
        sensor?.type == Sensor.TYPE_ACCELEROMETER

    private val gravity = FloatArray(3)
    private var lastShake = 0L
    private var started = false

    fun start() {
        if (started || sensor == null) return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        started = true
        lastShake = 0L
        gravity[0] = 0f; gravity[1] = 0f; gravity[2] = 0f
    }

    fun stop() {
        if (!started) return
        sensorManager?.unregisterListener(this)
        started = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (rawX, rawY, rawZ) = event.values
        val x: Float; val y: Float; val z: Float
        if (needsGravityFilter) {
            // Standard low-pass filter to isolate gravity (alpha = 0.8).
            gravity[0] = 0.8f * gravity[0] + 0.2f * rawX
            gravity[1] = 0.8f * gravity[1] + 0.2f * rawY
            gravity[2] = 0.8f * gravity[2] + 0.2f * rawZ
            x = rawX - gravity[0]
            y = rawY - gravity[1]
            z = rawZ - gravity[2]
        } else {
            x = rawX; y = rawY; z = rawZ
        }
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < SHAKE_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - lastShake < COOLDOWN_MS) return
        lastShake = now
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** Linear acceleration magnitude (m/s²) that counts as a shake. */
        private const val SHAKE_THRESHOLD = 13f
        /** Ignore repeat shakes within this window. */
        private const val COOLDOWN_MS = 1_200L
    }
}
