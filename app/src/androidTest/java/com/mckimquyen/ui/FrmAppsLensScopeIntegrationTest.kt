package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.app.RApplication
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmAppsLensScopeIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private val mail = App(packageName = "com.test.bulk.mail", name = "Main")
    private val chat = App(packageName = "com.test.bulk.chat", name = "Main")
    private val maps = App(packageName = "com.test.bulk.maps", name = "Main")

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat, maps)
        reset()
    }

    @After
    fun tearDown() {
        reset()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun reset() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    private fun withFrmApps(block: (FrmApps) -> Unit) {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<ViewPager2>(R.id.viewpager).setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                block(activity.supportFragmentManager.fragments.filterIsInstance<FrmApps>().first())
            }
        }
    }

    @Test
    fun removeFromAnAllLensSelectsEveryOtherApp() = withFrmApps { fragment ->
        fragment.applyBulkLensScope(lensId, setOf(LensAppScope.identifierOf(chat)), add = false)
        val settings = UtilSettings(context)
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lensId))
        assertEquals(
            setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(maps)),
            settings.getLensAppSelection(lensId)
        )
    }

    @Test
    fun addExtendsASelectedLens() = withFrmApps { fragment ->
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail)))
        fragment.applyBulkLensScope(lensId, setOf(LensAppScope.identifierOf(maps)), add = true)
        assertEquals(
            setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(maps)),
            UtilSettings(context).getLensAppSelection(lensId)
        )
    }
}
