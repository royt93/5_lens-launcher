package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-018: two real LensViews bound to different lenses must lay the same app list out at
 * different icon sizes. Reading the cell bounds (not just a setting) proves the draw path
 * consumes the per-lens value, which a settings-only test cannot.
 */
@RunWith(AndroidJUnit4::class)
class LensViewIconSizeIntegrationTest {

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val APP_COUNT = 12
        const val SMALL_DP = 16f
        const val LARGE_DP = 50f
        const val FIRST_INDEX = 0
        const val OTHER_LENS = "other-lens"
        const val THIRD_LENS = "third-lens"
        const val INHERITING_LENS = "some-lens-without-override"
    }

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun apps() = ArrayList(
        (0 until APP_COUNT).map { App(id = it, label = "App $it", packageName = "com.t.a$it", name = "Act$it") }
    )

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme
        )
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @After
    fun tearDown() {
        settings.deleteLensSettings(OTHER_LENS)
        settings.deleteLensSettings(THIRD_LENS)
        rawPrefs().edit().clear().commit()
    }

    private fun laidOutView(lensId: String): LensView {
        var result: LensView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = LensView(context).apply {
                this.lensId = lensId
                setApps(apps())
                measure(
                    View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
                draw(Canvas())
            }
        }
        return requireNotNull(result) { "LensView was not created on the main thread" }
    }

    private fun cellWidth(view: LensView): Int {
        val bounds = Rect()
        assertTrue("grid must have been laid out", view.getAppBounds(FIRST_INDEX, bounds))
        return bounds.width()
    }

    @Test
    fun aLensWithItsOwnSize_laysOutDifferentlyFromAnotherLens() {
        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)

        val inheriting = cellWidth(laidOutView(INHERITING_LENS))
        val own = cellWidth(laidOutView(OTHER_LENS))

        assertTrue("a larger icon size must produce larger cells (own=$own, inherited=$inheriting)", own > inheriting)
    }

    @Test
    fun aLensWithoutOverride_followsTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        val small = cellWidth(laidOutView(THIRD_LENS))

        settings.save(UtilSettings.KEY_ICON_SIZE, LARGE_DP)
        val large = cellWidth(laidOutView(THIRD_LENS))

        assertTrue("inheriting lens must follow the shared size ($small -> $large)", large > small)
    }

    @Test
    fun changingOneLensSize_doesNotChangeAnotherLensLayout() {
        settings.saveIconSize(OTHER_LENS, SMALL_DP)
        settings.saveIconSize(THIRD_LENS, SMALL_DP)
        val thirdBefore = cellWidth(laidOutView(THIRD_LENS))

        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        val other = cellWidth(laidOutView(OTHER_LENS))
        val thirdAfter = cellWidth(laidOutView(THIRD_LENS))

        assertEquals("the untouched lens keeps its geometry", thirdBefore, thirdAfter)
        assertNotEquals("the changed lens does not", thirdAfter, other)
    }

    @Test
    fun theSameViewRedrawnAfterASizeChange_picksUpTheNewSize() {
        settings.saveIconSize(OTHER_LENS, SMALL_DP)
        val view = laidOutView(OTHER_LENS)
        val before = cellWidth(view)

        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas()) }

        assertTrue("a redraw must recompute the cached grid for the new size", cellWidth(view) > before)
    }

    @Test
    fun theDefaultLens_stillUsesTheLegacySharedKey() {
        settings.save(UtilSettings.KEY_ICON_SIZE, LARGE_DP)
        val large = cellWidth(laidOutView(LensWorkspace.DEFAULT_LENS_ID))
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        val small = cellWidth(laidOutView(LensWorkspace.DEFAULT_LENS_ID))
        assertTrue("default lens tracks the unsuffixed key ($large -> $small)", small < large)
    }
}
