package com.mckimquyen.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TiltSensorSourceTest {
    private lateinit var manager: SensorManager
    private lateinit var sensor: Sensor
    private val samples = mutableListOf<FloatArray>()
    private val listener = object : TiltSensorSource.Listener {
        override fun onRotation(qx: Float, qy: Float, qz: Float, qw: Float) { samples += floatArrayOf(qx, qy, qz, qw) }
    }

    @Before fun setup() {
        manager = mock(SensorManager::class.java)
        sensor = mock(Sensor::class.java)
        `when`(manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)).thenReturn(sensor)
        `when`(manager.registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt())).thenReturn(true)
    }

    private fun capturedListener(): SensorEventListener {
        val captor = ArgumentCaptor.forClass(SensorEventListener::class.java)
        verify(manager).registerListener(captor.capture(), any(Sensor::class.java), anyInt())
        return captor.value
    }

    private fun event(vararg values: Float): SensorEvent {
        val e = mock(SensorEvent::class.java)
        val field = SensorEvent::class.java.getField("values")
        field.isAccessible = true
        field.set(e, values)
        return e
    }

    @Test fun isUnavailableWithoutAManagerOrWithoutTheSensor() {
        assertFalse(TiltSensorSource(null).available)
        `when`(manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)).thenReturn(null)
        val source = TiltSensorSource(manager)
        assertFalse(source.available)
        assertFalse(source.start(listener))
        assertFalse(source.isRunning)
        verify(manager, never()).registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt())
    }

    @Test fun startRegistersOnceWithUiDelayEvenWhenCalledTwice() {
        val source = TiltSensorSource(manager)
        assertTrue(source.start(listener))
        assertTrue(source.start(listener))
        verify(manager, times(1)).registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt())
        verify(manager).registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), org.mockito.ArgumentMatchers.eq(SensorManager.SENSOR_DELAY_UI))
        assertTrue(source.isRunning)
    }

    @Test fun stopUnregistersOnceAndIsIdempotent() {
        val source = TiltSensorSource(manager)
        source.stop()
        verify(manager, never()).unregisterListener(any(SensorEventListener::class.java))
        source.start(listener)
        source.stop()
        source.stop()
        verify(manager, times(1)).unregisterListener(any(SensorEventListener::class.java))
        assertFalse(source.isRunning)
    }

    @Test fun canRestartAfterStop() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        source.stop()
        assertTrue(source.start(listener))
        verify(manager, times(2)).registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt())
        assertTrue(source.isRunning)
    }

    @Test fun failedRegistrationLeavesItNotRunning() {
        `when`(manager.registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt())).thenReturn(false)
        val source = TiltSensorSource(manager)
        assertFalse(source.start(listener))
        assertFalse(source.isRunning)
    }

    @Test fun deliversTheFourComponentsOfASample() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        capturedListener().onSensorChanged(event(0.1f, 0.2f, 0.3f, 0.9f))
        assertEquals(1, samples.size)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.9f), samples[0].toList())
    }

    @Test fun derivesTheMissingFourthComponentFromAThreeValueSample() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        capturedListener().onSensorChanged(event(0f, 0.6f, 0f))
        assertEquals(1, samples.size)
        assertEquals(0.8f, samples[0][3], 0.001f)
    }

    @Test fun dropsSamplesThatAreNotUsable() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        val l = capturedListener()
        l.onSensorChanged(event(Float.NaN, 0f, 0f, 1f))
        l.onSensorChanged(event(0f, Float.POSITIVE_INFINITY, 0f, 1f))
        l.onSensorChanged(event(0.1f, 0.2f))
        l.onSensorChanged(event())
        assertTrue(samples.isEmpty())
    }

    @Test fun deliversNothingAfterStop() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        val l = capturedListener()
        source.stop()
        l.onSensorChanged(event(0f, 0f, 0f, 1f))
        assertTrue(samples.isEmpty())
    }

    @Test fun anInvalidThreeValueSampleWhoseNormExceedsOneIsClampedNotNaN() {
        val source = TiltSensorSource(manager)
        source.start(listener)
        capturedListener().onSensorChanged(event(1f, 1f, 1f))
        assertEquals(1, samples.size)
        assertFalse(samples[0][3].isNaN())
        assertEquals(0f, samples[0][3], 0f)
    }
}
