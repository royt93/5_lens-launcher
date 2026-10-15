package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensManagementActivityWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.deleteAll()
        dao.insertAll(
            listOf(
                LensWorkspace.createDefault().copy(groupName = "Personal"),
                LensWorkspace("work", "Work", 1, 20L, "Work"),
                LensWorkspace("travel", "Travel", 2, 30L, "work"),
                LensWorkspace("ungrouped", "Ungrouped", 3, 40L)
            )
        )
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() = runBlocking {
        dao.deleteAll()
        dao.insertIfAbsent(LensWorkspace.createDefault())
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        UtilSettings(context).saveCollapsedLensGroups(emptySet())
    }

    @Test
    fun managerShowsCanonicalGroupsAndUngroupedLensInOrder() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                assertNotNull(list)
                val adapter = requireNotNull(list.adapter)
                assertEquals(7, adapter.itemCount)
                assertEquals("Personal", rowText(list, 0))
                assertEquals("Lens 1", rowText(list, 1))
                assertEquals("Work", rowText(list, 2))
                assertEquals("Work", rowText(list, 3))
                assertEquals("Travel", rowText(list, 4))
                assertEquals(activity.getString(R.string.lens_group_ungrouped), rowText(list, 5))
                assertEquals("Ungrouped", rowText(list, 6))
            }
        }
    }

    @Test
    fun savedCollapsedGroupsHideSectionsWhenManagerReopens() {
        UtilSettings(context).saveCollapsedLensGroups(setOf("Work"))
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                assertEquals(5, requireNotNull(list.adapter).itemCount)
            }
        }
    }

    @Test
    fun collapsingGroupHidesItsLensRowsAndPersistsCanonicalState() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val header = requireNotNull(list.findViewHolderForAdapterPosition(4)?.itemView)
                header.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(setOf("Work"), UtilSettings(context).getCollapsedLensGroups())
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                assertEquals(5, requireNotNull(list.adapter).itemCount)
            }
        }
    }

    @Test
    fun tappingLensRowReturnsExactLensIdToHome() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                requireNotNull(list.findViewHolderForAdapterPosition(5)?.itemView).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals("work", UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
        }
    }

    private fun rowText(list: RecyclerView, position: Int): String? =
        list.findViewHolderForAdapterPosition(position)?.itemView?.let { row ->
            row.findViewById<TextView>(R.id.tvLensGroupRow)?.text?.toString()
                ?: row.findViewById<TextView>(R.id.tvLensGroupHeader)?.text?.toString()
        }
}
