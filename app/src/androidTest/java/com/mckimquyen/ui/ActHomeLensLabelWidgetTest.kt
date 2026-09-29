package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensLabelWidgetTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = cleanDb()

    @After
    fun tearDown() = cleanDb()

    private fun cleanDb() = runBlocking {
        AppDatabase.init(context)
        val all = dao.getAll()
        all.filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun waitForLabel(scenario: ActivityScenario<ActHome>, expectedVisibility: Int, timeoutMs: Long = 5000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastText = ""
        var lastVis = -1
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                if (tvName != null) {
                    lastVis = tvName.visibility
                    lastText = tvName.text.toString()
                }
            }
            if (lastVis == expectedVisibility) {
                return lastText
            }
            android.os.SystemClock.sleep(100)
        }
        assertEquals("Label visibility must match within timeout", expectedVisibility, lastVis)
        return lastText
    }

    @Test
    fun singleLens_hidesLensNameLabel() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLabel(scenario, View.GONE)
        }
    }

    @Test
    fun multipleLenses_showsActiveLensNameAndUpdatesOnSwipe() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val initialText = waitForLabel(scenario, View.VISIBLE)
            assertEquals("Lens 1", initialText)

            // Swipe to second page
            scenario.onActivity { activity ->
                activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
            }

            val swipedText = waitForLabel(scenario, View.VISIBLE)
            assertEquals("Work", swipedText)
        }
    }

    @Test
    fun longPressOnLensNameLabel_triggersManagementMenu() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForLabel(scenario, View.VISIBLE)
            var menuOpened = false
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                menuOpened = tvName.performLongClick()
            }
            assertTrue("Long-pressing tvLensName must invoke the management menu handler", menuOpened)
        }
    }
}
