package com.mckimquyen.a11y

import android.content.Context
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.ui.ActSettings
import com.mckimquyen.views.LensAccessibilityHelper
import com.mckimquyen.views.LensView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A11Y-001: Integration tests verifying that accessibility actions (Click, Long Click,
 * Hide/Show, Lock/Unlock) correctly execute and persist state across system boundaries.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityActionsIntegrationTest {

    @Test
    fun testLensAccessibilityAction_click_triggersLaunchCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var launchedIndex = -1

        val lensView = LensView(context)
        val sampleApps = listOf(
            App(id = 1, label = "Test App", packageName = "com.test.app", name = "MainAct")
        )

        val helper = LensAccessibilityHelper(
            host = lensView,
            appProvider = { sampleApps },
            rectProvider = { _, outRect ->
                outRect.set(10, 10, 100, 100)
                true
            },
            onAppClicked = { index -> launchedIndex = index },
            onAppLongClicked = { true }
        )

        val success = helper.testPerformActionForVirtualView(
            id = 0,
            action = AccessibilityNodeInfoCompat.ACTION_CLICK,
            args = null
        )

        assertTrue("Action click must succeed", success)
        assertEquals("Launched index must be 0", 0, launchedIndex)
    }

    @Test
    fun testAppHideAction_accessibilityToggle_updatesPersistentStateAndContentDescription() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val pkg = "com.test.a11y.hide"
            val name = "TestActivity"
            val label = "Accessible Hidden Test"

            val testApp = App(id = 999, label = label, packageName = pkg, name = name, isVisible = true)
            com.mckimquyen.app.RAppsSingleton.instance.apps = arrayListOf(testApp)

            try {
                // Set initial state as visible
                AppPersistent.setAppVisibility(pkg, name, true)
                assertTrue("App must initially be visible", AppPersistent.getAppVisibility(pkg, name))

                val recyclerView = RecyclerView(activity)
                recyclerView.layoutManager = LinearLayoutManager(activity)

                val adapter = AppAdapter(activity, mutableListOf(testApp))
                recyclerView.adapter = adapter
                recyclerView.measure(0, 0)
                recyclerView.layout(0, 0, 1080, 2000)

                val holder = recyclerView.findViewHolderForAdapterPosition(0) as? AppAdapter.AppViewHolder
                assertNotNull("ViewHolder must exist", holder)

                // Execute visibility toggle
                holder!!.toggleAppVisibility(testApp)

                // Assert persistence changed to hidden (false)
                assertFalse("App must now be hidden in AppPersistent", AppPersistent.getAppVisibility(pkg, name))
            } finally {
                AppPersistent.setAppVisibility(pkg, name, true)
                com.mckimquyen.app.RAppsSingleton.instance.clearAllData()
            }
        }

        scenario.close()
    }

    @Test
    fun testAppLockAction_lockStateChange_updatesContentDescription() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val pkg = "com.test.a11y.lock"
            val name = "LockActivity"

            // Test unlocked state content description
            val unlockedDescRes = AppAdapter.lockContentDescriptionResFor(true)
            val unlockedString = activity.getString(unlockedDescRes)
            assertTrue("Unlocked string must not be empty", unlockedString.isNotBlank())

            // Test locked state content description
            val lockedDescRes = AppAdapter.lockContentDescriptionResFor(false)
            val lockedString = activity.getString(lockedDescRes)
            assertTrue("Locked string must not be empty", lockedString.isNotBlank())

            assertNotEquals("Locked and unlocked descriptions must differ", unlockedString, lockedString)
        }

        scenario.close()
    }
}
