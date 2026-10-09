package com.mckimquyen.ui

import android.content.DialogInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensFreezeWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val apps = listOf("com.test.f.a", "com.test.f.b", "com.test.f.c").map(::app)

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = ArrayList(apps)
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        UtilSettings(context).saveLensFrozen(lensId, false)
        clearRows()
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensFrozen(lensId, false)
        UtilSettings(context).saveSmartFocusBias(lensId, UtilSettings.DEFAULT_SMART_FOCUS_BIAS)
        clearRows()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun clearRows() = runBlocking {
        AppDatabase.init(context)
        apps.forEach { dao.deleteForLens("freeze-probe") }
        Unit
    }

    private fun lensView(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        var count = 0
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && count != apps.size) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                view = it.findViewById(R.id.lensViews)
                count = view?.displayedApps?.size ?: 0
            }
            if (count != apps.size) Thread.sleep(POLL_MS)
        }
        assertEquals("the lens grid never held the seeded apps", apps.size, count)
        return requireNotNull(view)
    }

    private fun orderRows() = runBlocking { dao.getAllForLens(lensId) }.associate { it.identifier to it.orderNumber }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && !condition()) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(POLL_MS)
        }
        assertTrue("timed out waiting for: $what", condition())
    }

    @Test
    fun menuLabelNamesTheActionForTheCurrentState() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            lensView(scenario)
            scenario.onActivity { activity ->
                assertEquals(R.string.lens_freeze_positions, activity.lensFreezeMenuLabelRes(0))
                UtilSettings(context).saveLensFrozen(lensId, true)
                assertEquals(R.string.lens_unfreeze_positions, activity.lensFreezeMenuLabelRes(0))
                assertEquals("an out-of-range position must not crash", R.string.lens_freeze_positions,
                    activity.lensFreezeMenuLabelRes(99))
            }
        }
    }

    @Test
    fun freezingStoresTheFlagTheOrderAndKeepsTheDisplayedOrder() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = lensView(scenario)
            lateinit var before: List<String> // assigned in the same onActivity block before any use
            scenario.onActivity { activity ->
                before = view.displayedApps.map { it.packageName.toString() }
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0))
            }
            assertTrue(UtilSettings(context).isLensFrozen(lensId))
            waitUntil("one orderNumber row per app") { orderRows().size == apps.size }
            val stored = orderRows()
            // The stored order numbers reproduce exactly the order the user was looking at.
            val storedOrder = before.map { stored.getValue(AppPersistent.generateIdentifier(it, "Main")) }
            assertEquals(before.indices.toList(), storedOrder)
            scenario.onActivity {
                assertEquals(before, view.displayedApps.map { a -> a.packageName.toString() })
            }
        }
    }

    @Test
    fun aFrozenLensIsNotRearrangedBySmartFocus() {
        val settings = UtilSettings(context)
        settings.saveSmartFocusBias(lensId, true)
        settings.saveLensFrozen(lensId, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = lensView(scenario)
            scenario.onActivity {
                assertFalse("frozen lens must not be reordered by Smart Focus", view.smartFocusAppliedForTest)
                assertEquals(
                    RAppsSingleton.instance.apps!!.map { a -> a.packageName.toString() },
                    view.displayedApps.map { a -> a.packageName.toString() }
                )
            }
        }
    }

    @Test
    fun smartFocusStillRunsOnAnUnfrozenLens() {
        UtilSettings(context).saveSmartFocusBias(lensId, true)
        UtilSettings(context).saveLensFrozen(lensId, false)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = lensView(scenario)
            scenario.onActivity { assertTrue(view.smartFocusAppliedForTest) }
        }
    }

    @Test
    fun unfreezingAsksForConfirmationAndCancelChangesNothing() {
        UtilSettings(context).saveLensFrozen(lensId, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            lensView(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0)
                val dialog = activity.lensDialog
                assertNotNull("the confirmation must be showing", dialog)
                dialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue("cancel must keep the lens frozen", UtilSettings(context).isLensFrozen(lensId))
        }
    }

    @Test
    fun confirmingUnfreezeClearsTheFlagAndTheStoredOrder() {
        runBlocking {
            apps.forEachIndexed { i, a ->
                dao.insert(AppPersistent.defaults(a.packageName.toString(), "Main", lensId).copy(orderNumber = i))
            }
        }
        UtilSettings(context).saveLensFrozen(lensId, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            lensView(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0)
                activity.lensDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            waitUntil("the lens to be unfrozen") { !UtilSettings(context).isLensFrozen(lensId) }
            waitUntil("all orderNumbers reset to -1") { orderRows().values.all { it == -1 } && orderRows().isNotEmpty() }
        }
    }

    @Test
    fun freezingOneLensDoesNotFreezeAnother() {
        val other = "freeze-other-lens"
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                lensView(scenario)
                scenario.onActivity { it.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0) }
                assertTrue(UtilSettings(context).isLensFrozen(lensId))
                assertFalse(UtilSettings(context).isLensFrozen(other))
            }
        } finally {
            UtilSettings(context).deleteLensSettings(other)
        }
    }

    @Test
    fun frozenStateSurvivesRecreatingTheActivity() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            lensView(scenario)
            scenario.onActivity { it.onLensMenuItemSelected(ActHome.MENU_ID_FREEZE_LENS, 0) }
            scenario.recreate()
            // Compare with the seeded app count, never with the destroyed activity's old view.
            val after = lensView(scenario)
            scenario.onActivity {
                assertTrue(UtilSettings(context).isLensFrozen(lensId))
                assertNotNull(after)
                assertEquals(apps.size, after.displayedApps.size)
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
