package com.example.myapp.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {
    
    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    
    private val SHAKE_THRESHOLD = 800f
    private val TIME_THRESHOLD = 100L
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            
            if ((currentTime - lastUpdate) > TIME_THRESHOLD) {
                val diffTime = currentTime - lastUpdate
                lastUpdate = currentTime
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000
                
                if (speed > SHAKE_THRESHOLD) {
                    onShake()
                }
                
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}



