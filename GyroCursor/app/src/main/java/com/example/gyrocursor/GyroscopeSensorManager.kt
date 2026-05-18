package com.example.gyrocursor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class GyroscopeSensorManager(private val sensorManager: SensorManager) : SensorEventListener {
    private val accelData = FloatArray(3)
    private val magnetData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val inclination = FloatArray(3)
    private val orientation = FloatArray(3)
    private var onOrientationChange: ((Float, Float, Float) -> Unit)? = null

    fun start(callback: (Float, Float, Float) -> Unit) {
        onOrientationChange = callback
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelData, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetData, 0, 3)
            }
        }

        if (accelData[0] != 0f || accelData[1] != 0f || accelData[2] != 0f) {
            SensorManager.getRotationMatrix(rotationMatrix, inclination, accelData, magnetData)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val alpha = (Math.toDegrees(orientation[0].toDouble())).toFloat()
            val beta = (Math.toDegrees(orientation[1].toDouble())).toFloat()
            val gamma = (Math.toDegrees(orientation[2].toDouble())).toFloat()

            onOrientationChange?.invoke(alpha, beta, gamma)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
