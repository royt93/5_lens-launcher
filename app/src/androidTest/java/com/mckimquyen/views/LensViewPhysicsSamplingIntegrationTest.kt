package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.After
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewPhysicsSamplingIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    // @Before khởi tạo đồng bộ trước mọi test; @After chỉ dùng sau khi setup thành công.
    private lateinit var context: Context
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.AppTheme
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
            lensView.setApps(arrayListOf(App(id = 1, label = "App", packageName = "com.test", name = "Main")))
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            lensView.draw(Canvas())
        }
    }

    @After
    fun cancelGesture() {
        if (!::lensView.isInitialized) return
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val now = SystemClock.uptimeMillis()
            dispatch(MotionEvent.ACTION_CANCEL, now, now, 0f, 0f)
            lensView.onHapticPerformed = null
        }
    }

    @Test
    fun policyIsReadOnceThroughMoveAndResetsOnBothTerminalEvents() {
        setAnimatorScale(REDUCED_SCALE)
        var powerQueries = 0
        val countingContext = object : android.content.ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.POWER_SERVICE) powerQueries++
                return super.getSystemService(name)
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = LensView(countingContext)
            val sample = LensView::class.java.getDeclaredField("mGestureReduceMotion").apply { isAccessible = true }
            fun send(action: Int) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, TOUCH_X, TOUCH_Y, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            try {
                powerQueries = 0
                send(MotionEvent.ACTION_DOWN)
                assertEquals(1, powerQueries)
                repeat(MOVE_COUNT) { send(MotionEvent.ACTION_MOVE) }
                assertEquals("MOVE không được đọc lại power policy", 1, powerQueries)
                send(MotionEvent.ACTION_CANCEL)
                org.junit.Assert.assertNull("CANCEL phải xóa sample", sample.get(view))
                send(MotionEvent.ACTION_DOWN)
                assertEquals(2, powerQueries)
                send(MotionEvent.ACTION_UP)
                org.junit.Assert.assertNull("UP phải xóa sample", sample.get(view))
                assertEquals("UP không đọc lại policy", 2, powerQueries)
            } finally {
                send(MotionEvent.ACTION_CANCEL)
            }
        }
    }

    @Test
    fun releasedReducedMotionGestureCannotVibrateOnDelayedFrame() {
        setAnimatorScale(REDUCED_SCALE)
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bounds = android.graphics.Rect()
            assertTrue(lensView.getAppBounds(0, bounds))
            var haptics = 0
            lensView.onHapticPerformed = { haptics++ }
            val now = SystemClock.uptimeMillis()
            dispatch(MotionEvent.ACTION_DOWN, now, now, bounds.exactCenterX(), bounds.exactCenterY())
            dispatch(MotionEvent.ACTION_UP, now, now + EVENT_STEP_MS, bounds.exactCenterX(), bounds.exactCenterY())
            lensView.draw(Canvas())
            assertEquals("frame sau UP vẫn phải tôn trọng reduced-motion", 0, haptics)
        }
    }

    @Test
    fun reducedMotionChangeDuringGestureTakesEffectOnlyOnNextDown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.Global.getString(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )
        try {
            setAnimatorScale(NORMAL_SCALE)
            assumeFalse("pinch cần máy không tiết kiệm pin/quá nhiệt", LensPhysicsPolicy.shouldReduceLensMotion(context))
            val downTime = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                dispatch(MotionEvent.ACTION_DOWN, downTime, downTime, 300f, 500f)
            }
            setAnimatorScale(REDUCED_SCALE)
            instrumentation.runOnMainSync {
                dispatchPointerDown(downTime)
                assertEquals("gesture must keep reduced-motion value sampled on ACTION_DOWN", LensGestureState.PINCHING, lensView.gestureState)
                dispatch(MotionEvent.ACTION_CANCEL, downTime, downTime + EVENT_STEP_MS, 300f, 500f)
            }

            val nextDownTime = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                dispatch(MotionEvent.ACTION_DOWN, nextDownTime, nextDownTime, 300f, 500f)
                dispatchPointerDown(nextDownTime)
                assertEquals("next gesture must sample the new reduced-motion value", LensGestureState.IDLE, lensView.gestureState)
                dispatch(MotionEvent.ACTION_CANCEL, nextDownTime, nextDownTime + EVENT_STEP_MS, 300f, 500f)
            }

            setAnimatorScale(NORMAL_SCALE)
            val upDownTime = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                dispatch(MotionEvent.ACTION_DOWN, upDownTime, upDownTime, 300f, 500f)
                dispatchPointerDown(upDownTime)
                assertEquals(LensGestureState.PINCHING, lensView.gestureState)
                dispatch(MotionEvent.ACTION_UP, upDownTime, upDownTime + EVENT_STEP_MS, 300f, 500f)
                assertEquals(LensGestureState.IDLE, lensView.gestureState)
            }

            setAnimatorScale(REDUCED_SCALE)
            val afterUpDownTime = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                dispatch(MotionEvent.ACTION_DOWN, afterUpDownTime, afterUpDownTime, 300f, 500f)
                dispatchPointerDown(afterUpDownTime)
                assertEquals("ACTION_UP must clear the prior gesture sample", LensGestureState.IDLE, lensView.gestureState)
                dispatch(MotionEvent.ACTION_CANCEL, afterUpDownTime, afterUpDownTime + EVENT_STEP_MS, 300f, 500f)
            }
        } finally {
            setAnimatorScale(originalScale)
        }
    }

    private fun dispatchPointerDown(downTime: Long) {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = 300f; y = 500f },
            MotionEvent.PointerCoords().apply { x = 600f; y = 500f }
        )
        val event = MotionEvent.obtain(
            downTime, downTime + EVENT_STEP_MS,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            lensView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            lensView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun setAnimatorScale(value: String?) {
        val command = if (value == null) {
            "settings delete global ${Settings.Global.ANIMATOR_DURATION_SCALE}"
        } else {
            "settings put global ${Settings.Global.ANIMATOR_DURATION_SCALE} $value"
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val EVENT_STEP_MS = 10L
        const val NORMAL_SCALE = "1"
        const val REDUCED_SCALE = "0"
        const val MOVE_COUNT = 100
        const val TOUCH_X = 300f
        const val TOUCH_Y = 500f
    }
}
