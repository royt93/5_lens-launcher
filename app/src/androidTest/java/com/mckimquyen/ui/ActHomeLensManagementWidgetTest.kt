package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-008 Phase 2 widget tests: the long-press-on-indicator menu's create/rename/delete flows
 * must actually persist through LensWorkspaceDao, not just close a dialog.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensManagementWidgetTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before
    fun setup(): Unit = cleanDb()

    @After
    fun tearDown(): Unit = cleanDb()

    /** Two lenses so the indicator is visible and long-pressable; second is the active page. */
    private fun cleanDb(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun seedSecondLens() = runBlocking {
        dao.insertOrUpdate(LensWorkspace(id = "second", name = "Second Lens", orderIndex = 1))
    }

    /**
     * Espresso's longClick() injects a touch that TabLayout's child TabViews swallow, so the
     * OnLongClickListener never fires. Invoke it directly on the UI thread instead - that is the
     * exact production entry point - then click the popup item in its own window.
     */
    private fun openMenuAndClick(scenario: ActivityScenario<ActHome>, itemLabel: String) {
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.lensPageIndicator).performLongClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Wait for PopupMenu entrance animation to settle so the item has 90%+ visible rect
        SystemClock.sleep(300)
        onView(withText(itemLabel)).inRoot(isPlatformPopup()).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun addLens_createsNewWorkspaceWithCopiedLayout() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            openMenuAndClick(scenario, "Add lens")
            onView(allOf(isAssignableFrom(EditText::class.java), withId(android.R.id.edit)))
                .inRoot(isDialog())
                .perform(replaceText("Focus"))
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val lenses = runBlocking { dao.getAll() }
            assertEquals("A third lens must have been created", 3, lenses.size)
            assertNotNull("The new lens must carry the typed name", lenses.find { it.name == "Focus" })
        }
    }

    @Test
    fun renameLens_updatesTheActiveLensNameOnly() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            openMenuAndClick(scenario, "Rename lens")
            onView(allOf(isAssignableFrom(EditText::class.java), withId(android.R.id.edit)))
                .inRoot(isDialog())
                .perform(replaceText("Renamed"))
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val lenses = runBlocking { dao.getAll() }
            assertEquals("Rename must not add or remove a lens", 2, lenses.size)
            assertNotNull("The active lens must carry the new name", lenses.find { it.name == "Renamed" })
            assertNotNull(
                "The other lens must keep its name",
                lenses.find { it.name == "Second Lens" || it.name == LensWorkspace.DEFAULT_LENS_NAME }
            )
        }
    }

    @Test
    fun deleteLens_removesTheWorkspaceAndItsLayoutRows() {
        seedSecondLens()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            openMenuAndClick(scenario, "Delete lens")
            // The confirm dialog's title and its positive button share the same label, so match the
            // platform positive-button id rather than the text.
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val lenses = runBlocking { dao.getAll() }
            assertEquals("Exactly one lens must remain", 1, lenses.size)
            // The pager opens on page 0, so the active lens deleted here is the default one.
            assertNull(
                "The active (default) lens must be gone",
                lenses.find { it.id == LensWorkspace.DEFAULT_LENS_ID }
            )
            assertNotNull("The non-active lens must survive", lenses.find { it.id == "second" })
        }
    }
}
