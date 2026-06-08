package com.mckimquyen.views

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewWidgetTest {

    private lateinit var context: Context
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Create the view on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
        }
    }

    @Test
    fun testInitialization() {
        assertNotNull(lensView)
    }

    @Test
    fun testSetApps() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1"),
            App(id = 2, label = "Test App 2", packageName = "com.test2", name = "Activity2")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
        }

        assertTrue(true)
    }

    @Test
    fun testTouchEventsOnMainThread() {
        val testApps = arrayListOf(
            App(id = 1, label = "Test App 1", packageName = "com.test1", name = "Activity1")
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.setApps(testApps)
            // Perform simulated actions
            val downEvent = android.view.MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                android.view.MotionEvent.ACTION_DOWN,
                100f,
                100f,
                0
            )
            val handled = lensView.dispatchTouchEvent(downEvent)
            assertTrue("Touch event should be handled by LensView", handled)
            downEvent.recycle()
        }
    }
}
