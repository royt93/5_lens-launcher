package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** FISH-EXPORT: sharing must export the ACTIVE lens after paging, not the first/default one. */
@RunWith(AndroidJUnit4::class)
class ActHomeLensShareIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = cleanDb()

    @After
    fun tearDown() {
        cleanDb()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = "second-lens", name = "Second Lens", orderIndex = 1))
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun sharingAfterSwipingToTheSecondLens_exportsTheSecondLensNotTheFirst() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        scenario.onActivity { activity ->
            activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
        }
        idle()

        var captured: Intent? = null
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                captured = intent
                latch.countDown()
            }
            activity.onLensMenuItemSelected(5, 1)
        }
        assertTrue("share export must complete", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("sharing the second lens must still launch a chooser", captured)

        assertTrue(
            "export must target the active (second) lens's file, not the default lens's",
            File(File(context.cacheDir, "polaroid"), "Second_Lens.png").exists()
        )

        scenario.close()
    }
}
