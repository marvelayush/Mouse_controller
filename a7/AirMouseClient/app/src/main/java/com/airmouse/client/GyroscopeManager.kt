package com.airmouse.client

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Converts Android rotation vector to the EXACT same alpha/beta/gamma
 * that Chrome's DeviceOrientationEvent sends, so the Python server
 * mouse logic works identically.
 *
 * Chrome DeviceOrientationEvent convention (what your server expects):
 *   alpha = compass heading, 0–360°, rotating phone flat clockwise increases alpha
 *   beta  = front/back tilt, -180 to 180°, tilting top toward you = positive
 *   gamma = left/right tilt, -90 to 90°, tilting right = positive
 *
 * Your server's mouse.move() does:
 *   X axis: blend of -d_alpha (flat) and d_gamma (upright)
 *   Y axis: -d_beta
 *
 * So we need:
 *   - Tilting phone UP   → beta increases → cursor goes UP   (server does -d_beta)
 *   - Tilting phone DOWN → beta decreases → cursor goes DOWN
 *   - Tilting RIGHT      → gamma increases → cursor goes RIGHT (server does +d_gamma)
 *   - Tilting LEFT       → gamma decreases → cursor goes LEFT
 */
class GyroscopeManager(
    context: Context,
    private val onReading: (alpha: Double, beta: Double, gamma: Double) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Calibration offsets
    private var offsetAlpha = 0.0
    private var offsetBeta = 0.0
    private var offsetGamma = 0.0

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
    }

    fun resetCalibration() {
        offsetAlpha = 0.0
        offsetBeta = 0.0
        offsetGamma = 0.0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Step 1: rotation vector → rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Step 2: remap axes to match Chrome's coordinate system
        // Chrome assumes the phone is held portrait (Y axis pointing up the screen)
        // Android's default is also portrait, but we remap to be explicit
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )

        // Step 3: get Euler angles in radians
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // orientationAngles[0] = azimuth (rotation around Z) → alpha
        // orientationAngles[1] = pitch   (rotation around X) → beta
        // orientationAngles[2] = roll    (rotation around Y) → gamma

        // Step 4: convert to degrees matching Chrome's ranges
        var alpha = Math.toDegrees(orientationAngles[0].toDouble())
        var beta  = Math.toDegrees(orientationAngles[1].toDouble())
        var gamma = Math.toDegrees(orientationAngles[2].toDouble())

        // Alpha: Chrome gives 0–360, Android gives -180 to 180
        if (alpha < 0) alpha += 360.0

        // Beta: Chrome gives -180 to 180 (tilting top toward you = positive)
        // Android pitch: tilting top away = positive, so negate
        beta = -beta

        // Gamma: Chrome gives -90 to 90 (tilting right = positive)
        // Android roll matches Chrome's gamma sign already

        // Step 5: apply calibration
        alpha -= offsetAlpha
        beta  -= offsetBeta
        gamma -= offsetGamma
        if (alpha < 0) alpha += 360.0
        if (alpha >= 360.0) alpha -= 360.0

        // Step 6: round to 2 decimal places like Chrome does
        val a = Math.round(alpha * 100.0) / 100.0
        val b = Math.round(beta  * 100.0) / 100.0
        val g = Math.round(gamma * 100.0) / 100.0

        onReading(a, b, g)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
