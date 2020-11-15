package com.example.wearossensorapp.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class SensorCollector : SensorEventListener {

  val TAG = "SensorCollector"

  private var sensorManager: SensorManager
  private lateinit var sensor: Sensor

  //Exposes most recently read values to the foreground service
  var values: MutableList<SensorData> = mutableListOf<SensorData>()

  //Used to ignore results that have low accuracy
  private val sensorThreshhold = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

  //Used to control sampling rate
  private val DELAY_IN_SEC = 5

  constructor(sensorManager: SensorManager) {
    this.sensorManager = sensorManager
  }

  fun start() {
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    //for debugging
    this.sensor = sensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    sensorManager.registerListener(this, this.sensor, SensorManager.SENSOR_DELAY_UI, 100000)
  }

  override fun onSensorChanged(event: SensorEvent?) {
    val eventSensor = event?.sensor ?: return
    if (eventSensor.type == sensor.type) {
      Log.d(TAG, "Value: " + event.values[0] + " accuracy: " + event.accuracy)
      if (event.accuracy >= sensorThreshhold) {
        if(this.values.size > 10){
          //Simulating delay
          sensorManager.unregisterListener(this)
          this.values.clear()
          Thread.sleep( DELAY_IN_SEC * 1000L)
          sensorManager.registerListener(this, this.sensor, SensorManager.SENSOR_DELAY_UI, 100000)
        }
        this.values.add(SensorData(event.accuracy, event.timestamp, event.values[0]))
      } else{
        Log.d(TAG, "onSensorChanged: sensor data accuracy is not accurate enough")
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}