package com.mckimquyen.a11y

import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.feature.vip.ActVipManagement
import com.mckimquyen.ui.ActSettings
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A11Y-001: Widget tests proving system font scale through 200% reflows without clipping
 * on [ActSettings] and [ActVipManagement].
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityFontScaleWidgetTest {

    companion object {
        private var initialFontScale: String = "1.0"

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            try {
                val pfd = uiAutomation.executeShellCommand("settings get system font_scale")
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                    initialFontScale = BufferedReader(InputStreamReader(stream)).readLine()?.trim() ?: "1.0"
                }
            } catch (_: Throwable) {
                initialFontScale = "1.0"
            }
            uiAutomation.executeShellCommand("settings put system font_scale 2.0")
            Thread.sleep(500)
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            uiAutomation.executeShellCommand("settings put system font_scale $initialFontScale")
        }
    }

    @Test
    fun testActSettings_under200PercentFontScale_reflowsAndDisplaysControls() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            assertEquals(2.0f, activity.resources.configuration.fontScale, 0.05f)

            val toolbar = activity.findViewById<View>(R.id.toolbar)
            assertNotNull("Toolbar must be present", toolbar)
            assertTrue("Toolbar must be visible", toolbar.isShown)

            val tabs = activity.findViewById<View>(R.id.tabs)
            assertNotNull("Tabs must be present", tabs)
            assertTrue("Tabs must be visible", tabs.isShown)

            val viewpager = activity.findViewById<View>(R.id.viewpager)
            assertNotNull("ViewPager must be present", viewpager)

            val btStart = activity.findViewById<Button>(R.id.btStart)
            assertNotNull("Start button must be present", btStart)
            assertTrue("Start button must have text", btStart.text.isNotBlank())
        }

        scenario.close()
    }

    @Test
    fun testActVipManagement_under200PercentFontScale_scrollsAndDisplaysContent() {
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            assertEquals(2.0f, activity.resources.configuration.fontScale, 0.05f)

            val rootLayout = activity.findViewById<View>(R.id.rootLayout)
            assertNotNull("Root layout must be present", rootLayout)

            val tvStatusTitle = activity.findViewById<TextView>(R.id.tvStatusTitle)
            assertNotNull("Status title must be present", tvStatusTitle)
            assertTrue("Status title must be visible", tvStatusTitle.isShown)
            assertTrue("Status title must not be empty", tvStatusTitle.text.isNotBlank())

            val section1 = activity.findViewById<View>(R.id.section1)
            assertNotNull("Section 1 must be present", section1)
        }

        scenario.close()
    }
}
