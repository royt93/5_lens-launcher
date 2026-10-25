package com.mckimquyen.adt

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.testing.DeviceStateGuard
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-031: Widget tests proving app row action buttons collapse at font scale 2.0x,
 * expanding label width, while keeping actions reachable in ivAppMenu and touch target >= 48dp.
 */
@RunWith(AndroidJUnit4::class)
class AppRowFontScaleWidgetTest {

    @get:Rule
    val deviceStateGuard = DeviceStateGuard()

    private fun setSystemFontScale(scale: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("settings put system font_scale $scale")
        ).use { it.readBytes() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000L
        while (context.resources.configuration.fontScale != scale &&
            android.os.SystemClock.uptimeMillis() < deadline) {
            android.os.SystemClock.sleep(50L)
        }
    }

    private fun app(packageName: String, label: String = "Test App Long Name Label") = App(
        packageName = packageName,
        name = "$packageName.MainActivity",
        label = label
    )

    @Test
    fun under200PercentFontScale_collapsesActionButtonsAndReAnchorsLabel() {
        setSystemFontScale(2.0f)

        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val recyclerView = RecyclerView(activity)
                recyclerView.layoutManager = LinearLayoutManager(activity)

                val testApp = app("com.test.fontscale.app")
                val adapter = AppAdapter(activity, mutableListOf(testApp))
                recyclerView.adapter = adapter
                recyclerView.measure(0, 0)
                recyclerView.layout(0, 0, 1080, 2000)

                val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0)) as AppAdapter.AppViewHolder
                adapter.bindViewHolder(holder, 0)

                val ivAppHide = holder.itemView.findViewById<ImageView>(R.id.ivAppHide)
                val btAppLock = holder.itemView.findViewById<ImageView>(R.id.btAppLock)
                val ivAppMenu = holder.itemView.findViewById<ImageView>(R.id.ivAppMenu)
                val tvAppLabel = holder.itemView.findViewById<TextView>(R.id.tvAppLabel)

                // 1. Inline action buttons must be collapsed
                assertEquals("Hide button must be GONE at font scale 2.0", View.GONE, ivAppHide.visibility)
                assertEquals("Lock button must be GONE at font scale 2.0", View.GONE, btAppLock.visibility)
                assertEquals("Menu button must stay VISIBLE at font scale 2.0", View.VISIBLE, ivAppMenu.visibility)

                // 2. Touch target of menu button must be >= 48dp
                val density = activity.resources.displayMetrics.density
                val minTouchTargetPx = (48 * density).toInt()
                assertTrue(
                    "Touch target width must be at least 48dp",
                    ivAppMenu.layoutParams.width >= minTouchTargetPx || ivAppMenu.minimumWidth >= minTouchTargetPx
                )

                // 3. Label layout params must be anchored to ivAppMenu
                val labelParams = tvAppLabel.layoutParams as RelativeLayout.LayoutParams
                val anchor = if (labelParams.getRule(RelativeLayout.START_OF) != 0) {
                    labelParams.getRule(RelativeLayout.START_OF)
                } else {
                    labelParams.getRule(RelativeLayout.LEFT_OF)
                }
                assertEquals(
                    "tvAppLabel must anchor to start of ivAppMenu when actions are collapsed",
                    R.id.ivAppMenu,
                    anchor
                )

                // 4. Menu options must include hide item
                holder.itemView.findViewById<View>(R.id.ivAppMenu).performClick()
                assertNotNull("App popup menu must be created", holder.appMenuForTest)
                val menu = holder.appMenuForTest.menu
                val hideItem = menu.findItem(R.id.menuItemHide)
                assertNotNull("Hide menu item must exist", hideItem)
                assertTrue("Hide menu item must be visible under large font", hideItem.isVisible)
            }
        }
    }

    @Test
    fun underNormalFontScale_keepsActionButtonsVisible() {
        setSystemFontScale(1.0f)

        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val recyclerView = RecyclerView(activity)
                recyclerView.layoutManager = LinearLayoutManager(activity)

                val testApp = app("com.test.normalfont.app")
                val adapter = AppAdapter(activity, mutableListOf(testApp))
                recyclerView.adapter = adapter
                recyclerView.measure(0, 0)
                recyclerView.layout(0, 0, 1080, 2000)

                val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0)) as AppAdapter.AppViewHolder
                adapter.bindViewHolder(holder, 0)

                val ivAppHide = holder.itemView.findViewById<ImageView>(R.id.ivAppHide)
                val ivAppMenu = holder.itemView.findViewById<ImageView>(R.id.ivAppMenu)
                val tvAppLabel = holder.itemView.findViewById<TextView>(R.id.tvAppLabel)

                // Inline hide button must be visible for normal non-self app
                assertEquals("Hide button must be VISIBLE at 1.0x font", View.VISIBLE, ivAppHide.visibility)
                assertEquals("Menu button must be VISIBLE at 1.0x font", View.VISIBLE, ivAppMenu.visibility)

                val labelParams = tvAppLabel.layoutParams as RelativeLayout.LayoutParams
                val anchor = if (labelParams.getRule(RelativeLayout.START_OF) != 0) {
                    labelParams.getRule(RelativeLayout.START_OF)
                } else {
                    labelParams.getRule(RelativeLayout.LEFT_OF)
                }
                assertEquals(
                    "tvAppLabel must anchor to start of btAppLock under normal font",
                    R.id.btAppLock,
                    anchor
                )

                // Menu options must NOT show redundant hide item
                holder.itemView.findViewById<View>(R.id.ivAppMenu).performClick()
                val menu = holder.appMenuForTest.menu
                val hideItem = menu.findItem(R.id.menuItemHide)
                assertTrue("Hide menu item must not be visible under normal font", hideItem == null || !hideItem.isVisible)
            }
        }
    }
}
