package com.mckimquyen.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * FISH-033: đăng ký `TYPE_GAME_ROTATION_VECTOR` (không cần quyền). Không giữ tham chiếu Activity: chỉ giữ
 * [Listener] do chủ sở hữu truyền vào và bỏ nó trong [stop]. Chủ sở hữu (ActHome) gọi [start]/[stop] theo
 * vòng đời; cả hai an toàn khi gọi lặp.
 */
class TiltSensorSource(private val manager: SensorManager?) {
    interface Listener {
        fun onRotation(qx: Float, qy: Float, qz: Float, qw: Float)
    }

    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private var listener: Listener? = null
    private var registered: SensorEventListener? = null

    val available: Boolean get() = sensor != null

    val isRunning: Boolean get() = registered != null

    /** True nếu đang chạy sau khi gọi; false khi máy không có sensor hoặc đăng ký thất bại. */
    fun start(target: Listener): Boolean {
        val source = sensor ?: return false
        val sm = manager ?: return false
        listener = target
        if (registered != null) return true
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = deliver(event.values)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!sm.registerListener(sensorListener, source, SensorManager.SENSOR_DELAY_UI)) {
            listener = null
            return false
        }
        registered = sensorListener
        return true
    }

    fun stop() {
        val current = registered ?: return
        registered = null
        listener = null
        manager?.unregisterListener(current)
    }

    private fun deliver(values: FloatArray) {
        val target = listener ?: return
        if (values.size < MIN_COMPONENTS) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        // Rotation vector có thể bỏ thành phần thứ tư; suy ra từ chuẩn đơn vị và không để căn âm ra NaN.
        val w = if (values.size > W_INDEX) values[W_INDEX] else sqrt((1f - x * x - y * y - z * z).coerceAtLeast(0f))
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !w.isFinite()) return
        target.onRotation(x, y, z, w)
    }

    private companion object {
        const val MIN_COMPONENTS = 3
        const val W_INDEX = 3
    }
}
