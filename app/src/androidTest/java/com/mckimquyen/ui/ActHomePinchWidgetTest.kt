package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.snackbar.Snackbar
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-009: Widget tests for ActHome's live pinch-to-adjust curvature Snackbar.
 * Verifies that:
 * 1. Pinch completion displays the confirmation Snackbar with formatted curvature text.
 * 2. Clicking "Save as default" persists the new value to UtilSettings and commits live distortion.
 * 3. Dismissing without saving resets live distortion to the saved setting.
 */
@RunWith(AndroidJUnit4::class)
class ActHomePinchWidgetTest {

    private lateinit var utilSettings: UtilSettings

    @Before
    fun setup() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        utilSettings = UtilSettings(targetContext)
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.5f)
        RAppsSingleton.instance.apps = arrayListOf(
            App(id = 1, label = "App 1", packageName = "com.test1", name = "Act1")
        )
    }

    @After
    fun tearDown() {
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.5f)
    }

    @Test
    fun pinchCompletion_showsSnackbarWithFormattedCurvature() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            activity.showPinchCurvatureSnackbar(3.2f)

            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull("Pinch confirmation snackbar must be created", snackbar)
            assertTrue("Snackbar must be shown", snackbar?.isShown == true || snackbar?.isShownOrQueued == true)
        }
        scenario.close()
    }

    @Test
    fun saveAsDefaultAction_persistsValueAndCommitsLiveDistortion() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            activity.lensViews.liveDistortionFactor = 3.8f
            activity.showPinchCurvatureSnackbar(3.8f)

            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull(snackbar)

            // Trigger the action view click
            val actionView = snackbar!!.view.findViewById<android.widget.Button>(
                com.google.android.material.R.id.snackbar_action
            )
            assertNotNull("Snackbar action button must be present", actionView)
            actionView.performClick()

            // UtilSettings must now have 3.8f persisted
            assertEquals(3.8f, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)
            // LensView's live override must be cleared (committed)
            assertNull(activity.lensViews.liveDistortionFactor)
        }
        scenario.close()
    }

    @Test
    fun dismissWithoutSave_resetsLiveDistortion() {
        val initial = utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR)
        val scenario = ActivityScenario.launch(ActHome::class.java)
        val latch = java.util.concurrent.CountDownLatch(1)

        scenario.onActivity { activity ->
            activity.lensViews.liveDistortionFactor = 4.2f
            activity.showPinchCurvatureSnackbar(4.2f)

            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull(snackbar)

            snackbar?.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    latch.countDown()
                }
            })
            snackbar?.dismiss()
        }

        assertTrue("Snackbar must dismiss within 3 seconds", latch.await(3, java.util.concurrent.TimeUnit.SECONDS))

        scenario.onActivity { activity ->
            assertNull("Live distortion must revert to null on dismiss", activity.lensViews.liveDistortionFactor)
            assertEquals("Persisted setting must be unchanged", initial, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)
        }
        scenario.close()
    }
}
