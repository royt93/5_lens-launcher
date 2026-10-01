package com.mckimquyen.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget-layer proof that RApplication's test seam preserves custom UI state in ActHome:
 * when sDisableAutoAppRefresh is enabled, launching ActHome will not trigger the background
 * scan that clobbers the active in-memory apps snapshot.
 */
@RunWith(AndroidJUnit4::class)
class RApplicationSeamWidgetTest {

    private var originalApps: ArrayList<App>? = null
    private var originalDisableScan: Boolean = true

    @Before
    fun setup() {
        originalApps = RAppsSingleton.instance.apps
        originalDisableScan = RApplication.sDisableAutoAppRefresh
        RApplication.sDisableAutoAppRefresh = true
    }

    @After
    fun tearDown() {
        RApplication.sDisableAutoAppRefresh = originalDisableScan
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun actHome_preservesSeededSnapshot_whenScanDisabled() {
        val testApp = App(
            packageName = "com.test.widget.seam",
            name = "Seam Test App",
            icon = null
        )
        RAppsSingleton.instance.apps = arrayListOf(testApp)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            // Confirm snapshot is still our test app, not replaced by device's PackageManager scan
            val currentApps = RAppsSingleton.instance.apps
            org.junit.Assert.assertNotNull("snapshot should not be null", currentApps)
            val nonNullApps = currentApps ?: return@use
            assertEquals(1, nonNullApps.size)
            assertEquals("com.test.widget.seam", nonNullApps[0].packageName)
            assertEquals("Seam Test App", nonNullApps[0].name)
        }
    }
}
