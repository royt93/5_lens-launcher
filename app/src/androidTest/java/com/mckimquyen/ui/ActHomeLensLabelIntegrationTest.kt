package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-014: verifies active lens name label across the real Room + SharedPreferences +
 * ViewPager2 + Activity recreation boundary.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensLabelIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    private val workLensId = "work-lens-integration"

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() {
        utilSettings.deleteLensSettings(workLensId)
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        cleanDb()
    }

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
    fun restoredActiveLensFromPreferences_showsCorrectLabelAcrossRecreate() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = workLensId, name = "Work Space", orderIndex = 1))
        }
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, workLensId)

        val scenario = ActivityScenario.launch(ActHome::class.java)

        val initialText = waitForLabel(scenario, View.VISIBLE)
        assertEquals("Label must match restored active lens name", "Work Space", initialText)

        // Recreate activity
        scenario.recreate()

        val textAfterRecreate = waitForLabel(scenario, View.VISIBLE)
        assertEquals("Label must retain restored active lens name after recreate", "Work Space", textAfterRecreate)

        scenario.close()
    }

    @Test
    fun deletingSecondLens_hidesLabelImmediately() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = workLensId, name = "Work Space", orderIndex = 1))
        }

        val scenario = ActivityScenario.launch(ActHome::class.java)

        // Initially on page 0 (default lens = "Lens 1"), label is visible because 2 lenses exist
        val initialText = waitForLabel(scenario, View.VISIBLE)
        assertEquals("Lens 1", initialText)

        // Delete second lens directly from DB and trigger reload
        runBlocking {
            dao.findById(workLensId)?.let { dao.delete(it) }
        }
        scenario.onActivity { activity ->
            activity.refreshLensList()
        }

        waitForLabel(scenario, View.GONE)

        scenario.close()
    }
}
