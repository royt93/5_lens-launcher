package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.app.RApplication
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensScopeWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val mail = app("com.test.scope.mail")
    private val chat = app("com.test.scope.chat")
    private val maps = app("com.test.scope.maps")

    @Before
    fun setup() {
        // Restore the real snapshot afterwards; clearing it breaks later tests that need apps.
        // A cold instrumentation process can still run RApplication's first PackageManager scan
        // (its test seam is read before the test registry exists); cancel it so it cannot land
        // on top of the seeded snapshot below.
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat, maps)
        // Pin the active lens: otherwise onPageSelected sees a different saved lens, calls
        // switchLens() -> execute(), and a real PackageManager scan overwrites the seeded apps.
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
        RAppsSingleton.instance.apps = originalApps
    }

    /** Polls until the lens grid holds exactly [expected] apps, returning the labels it holds then. */
    private fun labelsWhenCountIs(scenario: ActivityScenario<ActHome>, expected: Int): List<String> {
        var labels = emptyList<String>()
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && labels.size != expected) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                labels = activity.findViewById<LensView>(R.id.lensViews)
                    ?.appsForTest?.map { it.label.toString() }.orEmpty()
            }
            if (labels.size != expected) Thread.sleep(POLL_MS)
        }
        return labels
    }

    @Test
    fun allScopeShowsEveryApp() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(
                setOf(mail, chat, maps).map { it.label.toString() }.toSet(),
                labelsWhenCountIs(scenario, expected = 3).toSet()
            )
        }
    }

    @Test
    fun selectedScopeShowsOnlyTheSelectedApps() {
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(chat)))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(listOf(chat.label.toString()), labelsWhenCountIs(scenario, expected = 1))
        }
    }

    @Test
    fun changingTheSelectionWhileOpenRefreshesTheGrid() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(3, labelsWhenCountIs(scenario, expected = 3).size)
            LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(mail)))
            assertEquals(listOf(mail.label.toString()), labelsWhenCountIs(scenario, expected = 1))
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
