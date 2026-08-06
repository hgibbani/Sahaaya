package com.sahaaya.sensor.fall

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sahaaya.domain.model.FallSensitivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the accelerometer into [FallDetectionEngine] and emits confirmed falls.
 *
 * SENSOR_DELAY_GAME (~20 ms) rather than NORMAL (~200 ms): an impact lasts tens
 * of milliseconds, and at NORMAL the peak is simply not sampled. This is the
 * single most important constant in the fall pipeline - too slow and the
 * detector cannot work at all, too fast and the battery cost is not justified.
 */
@Singleton
class FallSensorMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Whether this device has the hardware at all. Very few Android phones do not. */
    fun isSupported(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /**
     * Emits a [FallCandidate] each time the engine confirms a fall.
     *
     * Cold: the sensor is registered when collection starts and unregistered
     * when it stops, so nothing runs while the service is not monitoring.
     */
    fun detectFalls(sensitivity: FallSensitivity): Flow<FallCandidate> = callbackFlow {
        val manager = sensorManager
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (manager == null || accelerometer == null) {
            close()
            return@callbackFlow
        }

        val engine = FallDetectionEngine(sensitivity)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val candidate = engine.feed(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    atMillis = System.currentTimeMillis(),
                )
                if (candidate != null) trySend(candidate)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, accelerometer, SAMPLING_PERIOD_MICROS)
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        /** 20 ms. Fast enough to catch an impact peak. */
        const val SAMPLING_PERIOD_MICROS = 20_000
    }
}
