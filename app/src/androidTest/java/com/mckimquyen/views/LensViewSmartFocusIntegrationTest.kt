package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-006 integration coverage for LensView ordering, geometry, and cache invariance. */
@RunWith(AndroidJUnit4::class)
class LensViewSmartFocusIntegrationTest {

    private lateinit var lensView: LensView
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(appContext, R.style.AppTheme)
        settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
        }
    }

    @After
    fun tearDown() {
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, false)
    }

    private fun apps(): ArrayList<App> = ArrayList((0 until 25).map { index ->
        App(
            id = index,
            label = "App $index",
            packageName = "pkg.$index",
            name = "Activity$index",
            openCount = when (index) {
                0 -> 500L
                1 -> 250L
                else -> 0L
            }
        )
    })

    @Suppress("UNCHECKED_CAST")
    private fun displayedApps(): ArrayList<App> {
        val field = LensView::class.java.getDeclaredField("mApps")
        field.isAccessible = true
        return field.get(lensView) as ArrayList<App>
    }

    private fun gridCache(): LensGridCache {
        val field = LensView::class.java.getDeclaredField("mGridCache")
        field.isAccessible = true
        return field.get(lensView) as LensGridCache
    }

    private fun onMain(block: () -> Unit) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    @Test
    fun highestOpenCountApp_isPlacedInClosestCenterSlot() = onMain {
        lensView.setApps(apps())
        lensView.draw(Canvas())

        val grid = gridCache().grid ?: error("grid must be computed")
        val displayed = displayedApps()
        val index = displayed.indexOfFirst { it.id == 0 }
        assertTrue(index >= 0)

        val col = index % grid.itemCountHorizontal
        val row = index / grid.itemCountHorizontal
        val cx = (grid.itemCountHorizontal - 1) / 2f
        val cy = (grid.itemCountVertical - 1) / 2f
        val distance = (col - cx) * (col - cx) + (row - cy) * (row - cy)

        val allDistances = displayed.indices.map { slot ->
            val c = slot % grid.itemCountHorizontal
            val r = slot / grid.itemCountHorizontal
            (c - cx) * (c - cx) + (r - cy) * (r - cy)
        }
        assertEquals(allDistances.minOrNull(), distance)
    }

    @Test
    fun appBoundsRemainValid_afterSmartFocusArrangement() = onMain {
        lensView.setApps(apps())
        lensView.draw(Canvas())
        displayedApps().indices.forEach { index ->
            val rect = Rect()
            assertTrue("slot $index must retain valid geometry", lensView.getAppBounds(index, rect))
            assertTrue(rect.width() > 0)
            assertTrue(rect.height() > 0)
        }
    }

    @Test
    fun changingOpenCounts_doesNotRecomputeGridGeometry() = onMain {
        lensView.setApps(apps())
        lensView.draw(Canvas())
        val cache = gridCache()
        val recomputeCount = cache.recomputeCount

        val updated = apps().mapIndexed { index, app ->
            app.copy(openCount = if (index == 5) 1_000L else app.openCount)
        }
        lensView.setApps(ArrayList(updated))
        lensView.draw(Canvas())

        assertEquals(recomputeCount, cache.recomputeCount)
        assertEquals(5, displayedApps().minBy { app ->
            val slot = displayedApps().indexOf(app)
            val grid = cache.grid ?: error("grid missing")
            val cx = (grid.itemCountHorizontal - 1) / 2f
            val cy = (grid.itemCountVertical - 1) / 2f
            val col = slot % grid.itemCountHorizontal
            val row = slot / grid.itemCountHorizontal
            (col - cx) * (col - cx) + (row - cy) * (row - cy)
        }.id)
    }

    @Test
    fun disabledSetting_preservesSourceOrderExactly() = onMain {
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, false)
        val source = apps()
        lensView.setApps(source)
        lensView.draw(Canvas())
        assertEquals(source.map { it.id }, displayedApps().map { it.id })
    }
}
