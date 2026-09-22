package com.mckimquyen.views

import android.graphics.Rect
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-020 regression: LensView used to own a second, independent OnApplyWindowInsetsListener
 * that subtracted systemBars/displayCutout insets from the grid's own available drawing area -
 * on top of ActHome.applyHomeColumnInsets already reserving that same space via this view's
 * layout margins. That double-reservation left a large blank strip at the top and bottom of the
 * grid, and re-fired mGridCache.clear()+invalidate() on every insets dispatch (a real
 * contributor to the reported continuous flicker). The fix removed that listener entirely -
 * this proves dispatching insets to the view no longer moves the grid at all.
 */
@RunWith(AndroidJUnit4::class)
class LensViewInsetsRegressionWidgetTest {

    @Test
    fun dispatchingWindowInsets_doesNotMoveOrResizeTheGrid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        var lensView: LensView? = null
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, 1080, 2000)
                setApps(ArrayList((0 until 20).map {
                    App(id = it, packageName = context.packageName, name = "app$it", iconCacheKey = "insets-test-$it")
                }))
            }
        }

        val boundsBefore = Rect()
        instrumentation.runOnMainSync {
            lensView!!.getAppBounds(0, boundsBefore)
        }

        // A large, deliberately unrealistic inset (bigger than any real status/nav bar) - if the
        // old double-reservation code path were still present, this would visibly shift bounds.
        val largeInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(50, 200, 50, 200))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(50, 200, 50, 200))
            .build()

        instrumentation.runOnMainSync {
            lensView!!.dispatchApplyWindowInsets(largeInsets.toWindowInsets() as WindowInsets)
        }

        val boundsAfter = Rect()
        instrumentation.runOnMainSync {
            lensView!!.getAppBounds(0, boundsAfter)
        }

        assertEquals(
            "grid cell bounds must be unaffected by window insets dispatch",
            boundsBefore,
            boundsAfter
        )
    }
}
