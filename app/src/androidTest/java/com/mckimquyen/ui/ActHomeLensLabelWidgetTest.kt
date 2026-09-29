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
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun singleLens_hidesLensNameLabel() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertNotNull("tvLensName must exist in layout", tvName)
                assertEquals(
                    "Lens name label must be GONE when only one lens exists",
                    View.GONE,
                    tvName.visibility
                )
            }
        }
    }

    @Test
    fun multipleLenses_showsActiveLensNameAndUpdatesOnSwipe() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertEquals(
                    "Lens name label must be VISIBLE when multiple lenses exist",
                    View.VISIBLE,
                    tvName.visibility
                )
                assertEquals(
                    "Default lens name should be displayed initially",
                    "Lens 1",
                    tvName.text.toString()
                )
            }

            // Swipe to second page
            scenario.onActivity { activity ->
                activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
            }
            idle()

            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertEquals(
                    "Lens name label must update to 'Work' on page swipe",
                    "Work",
                    tvName.text.toString()
                )
            }
        }
    }

    @Test
    fun longPressOnLensNameLabel_triggersManagementMenu() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            var menuOpened = false
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                // performLongClick returns true if the OnLongClickListener handled it
                menuOpened = tvName.performLongClick()
            }
            assertTrue("Long-pressing tvLensName must invoke the management menu handler", menuOpened)
        }
    }
}
