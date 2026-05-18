package com.airmouse.client

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * GyroscopeManager maps Android's SensorManager.TYPE_ROTATION_VECTOR
 * to the same alpha/beta/gamma Euler angles that the browser's
 * DeviceOrientationEvent provides. This means your Python server
 * receives identical data whether client is Chrome or this app.
 *
 * Mapping:
 *   alpha = azimuth (Z-axis rotation, 0–360°) — compass heading
 *   beta  = pitch   (X-axis rotation, -180–180°) — front/back tilt
 *   gamma = roll    (Y-axis rotation, -90–90°) — left/right tilt
 */
class GyroscopeManager(
    context: Context,
    private val onReading: (alpha: Double, beta: Double, gamma: Double) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Calibration offsets
    private var offsetAlpha = 0.0
    private var offsetBeta = 0.0
    private var offsetGamma = 0.0
    private var isCalibrated = false

    // Low-pass filter state
    private var filteredAlpha = 0.0
    private var filteredBeta = 0.0
    private var filteredGamma = 0.0
    private val filterCoeff = 0.8f // higher = smoother but more lag

    fun hasGyroscope() = rotationSensor != null

    fun start(samplingRateHz: Int = 60) {
        val delayUs = 1_000_000 / samplingRateHz
        rotationSensor?.let {
            sensorManager.registerListener(this, it, delayUs)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun calibrate(currentAlpha: Double, currentBeta: Double, currentGamma: Double) {
        offsetAlpha = currentAlpha
        offsetBeta = currentBeta
        offsetGamma = currentGamma
        isCalibrated = true
    }

    fun resetCalibration() {
        offsetAlpha = 0.0
        offsetBeta = 0.0
        offsetGamma = 0.0
        isCalibrated = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Convert rotation vector → rotation matrix → Euler angles
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert radians → degrees, matching browser DeviceOrientationEvent convention
        // Android's getOrientation returns: [azimuth, pitch, roll] in radians
        // azimuth: -π to π  → alpha: 0–360°
        // pitch:   -π/2 to π/2 → beta: -180–180°  (approx)
        // roll:    -π to π  → gamma: -90–90°

        var alpha = Math.toDegrees(orientationAngles[0].toDouble()) // azimuth
        var beta  = Math.toDegrees(orientationAngles[1].toDouble()) // pitch
        var gamma = Math.toDegrees(orientationAngles[2].toDouble()) // roll

        // Normalize alpha to 0–360 (browser convention)
        if (alpha < 0) alpha += 360.0

        // Apply calibration offset
        if (isCalibrated) {
            alpha -= offsetAlpha
            beta  -= offsetBeta
            gamma -= offsetGamma

            // Re-normalize alpha after offset
            if (alpha < 0) alpha += 360.0
            if (alpha >= 360.0) alpha -= 360.0
        }

        // Low-pass filter to smooth noise
        filteredAlpha = filteredAlpha * filterCoeff + alpha * (1 - filterCoeff)
        filteredBeta  = filteredBeta  * filterCoeff + beta  * (1 - filterCoeff)
        filteredGamma = filteredGamma * filterCoeff + gamma * (1 - filterCoeff)

        // Round to 2 decimal places (same as Chrome)
        val a = Math.round(filteredAlpha * 100.0) / 100.0
        val b = Math.round(filteredBeta  * 100.0) / 100.0
        val g = Math.round(filteredGamma * 100.0) / 100.0

        onReading(a, b, g)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
