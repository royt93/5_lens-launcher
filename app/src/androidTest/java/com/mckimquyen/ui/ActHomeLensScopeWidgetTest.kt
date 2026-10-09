package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.app.RApplication
import com.mckimquyen.adt.LensPagerAdapter
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensScopeWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val secondLensId = "scope-second-lens"
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
        cleanLenses()
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
        UtilSettings(context).deleteLensSettings(secondLensId)
        cleanLenses()
        RAppsSingleton.instance.apps = originalApps
    }

    /** Only the default lens survives, under its real name (insertOrUpdate, never insertIfAbsent). */
    private fun cleanLenses() = runBlocking {
        AppDatabase.init(context)
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        dao.getAll().filter { it.id != lensId }.forEach { dao.delete(it) }
        dao.insertOrUpdate(LensWorkspace.createDefault())
        Unit
    }

    private fun seedSecondLens() = runBlocking {
        AppDatabase.getInstance().lensWorkspaceDao()
            .insertOrUpdate(LensWorkspace(id = secondLensId, name = "Second", orderIndex = 1))
        // Prevent onPageSelected -> switchLens(secondLensId) -> execute() from running a real
        // PackageManager scan that overwrites the seeded snapshot during the full suite.
        val us = UtilSettings(context)
        us.saveLensAppScope(secondLensId, LensAppScope.ALL)
        us.saveLensAppSelection(secondLensId, emptySet())
    }

    /** Labels held by the LensView bound to pager [position], or empty if that page is not bound. */
    private fun labelsOnPage(scenario: ActivityScenario<ActHome>, position: Int): List<String> {
        var labels = emptyList<String>()
        scenario.onActivity { activity ->
            val pager = activity.findViewById<ViewPager2>(R.id.lensPager)
            val holder = (pager.getChildAt(0) as RecyclerView).findViewHolderForAdapterPosition(position)
            labels = (holder as? LensPagerAdapter.PageHolder)?.lensView
                ?.appsForTest?.map { it.label.toString() }.orEmpty()
        }
        return labels
    }

    private fun waitForPage(scenario: ActivityScenario<ActHome>, position: Int, expected: List<String>): List<String> {
        var labels = emptyList<String>()
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && labels.toSet() != expected.toSet()) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            labels = labelsOnPage(scenario, position)
            if (labels.toSet() != expected.toSet()) Thread.sleep(POLL_MS)
        }
        return labels
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

    @Test
    fun twoLensesKeepTheirOwnScopeWhenSwipingBetweenThem() {
        seedSecondLens()
        val settings = UtilSettings(context)
        settings.saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail)))
        settings.saveLensAppScope(lensId, LensAppScope.SELECTED)
        // The second lens is left at ALL: it must not inherit the default lens's selection.
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(listOf(mail.label.toString()), waitForPage(scenario, 0, listOf(mail.label.toString())))

            scenario.onActivity { it.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false) }
            val everything = listOf(mail, chat, maps).map { it.label.toString() }
            assertEquals(everything.toSet(), waitForPage(scenario, 1, everything).toSet())

            // Swiping back must still show only the default lens's own selection.
            scenario.onActivity { it.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(0, false) }
            assertEquals(listOf(mail.label.toString()), waitForPage(scenario, 0, listOf(mail.label.toString())))
        }
    }

    @Test
    fun selectingAppsForOneLensWhileAnotherIsOpenDoesNotLeakIntoIt() {
        seedSecondLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val everything = listOf(mail, chat, maps).map { it.label.toString() }
            assertEquals(everything.toSet(), waitForPage(scenario, 0, everything).toSet())

            LensAppScopeEditor(UtilSettings(context)).apply(secondLensId, setOf(LensAppScope.identifierOf(chat)))

            // The edited lens is page 1; the visible default lens (page 0) must be unchanged.
            assertEquals(everything.toSet(), waitForPage(scenario, 0, everything).toSet())
            scenario.onActivity { it.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false) }
            assertEquals(listOf(chat.label.toString()), waitForPage(scenario, 1, listOf(chat.label.toString())))
        }
    }

    @Test
    fun scopeSurvivesRecreatingTheActivity() {
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(maps)))
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(listOf(maps.label.toString()), labelsWhenCountIs(scenario, expected = 1))
            scenario.recreate()
            assertEquals(listOf(maps.label.toString()), labelsWhenCountIs(scenario, expected = 1))
        }
    }

    @Test
    fun anEmptyScopeFallsBackToShowingEveryApp() {
        // Writing an empty selection must never produce a blank lens (LensAppScopeEditor guarantees it).
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, setOf(LensAppScope.identifierOf(chat)))
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, emptySet())
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            assertEquals(3, labelsWhenCountIs(scenario, expected = 3).size)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
