package com.mckimquyen.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeSearchConfigurationWidgetTest {

    private companion object {
        const val LANDSCAPE_WIDTH_DP = 851
        const val LANDSCAPE_HEIGHT_DP = 393
        const val LARGE_FONT_SCALE = 1.5f
        const val STRESS_FONT_SCALE = 2.0f
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val baseContext get() = instrumentation.targetContext

    private fun inflateSearchContent(
        nightMode: Boolean = false,
        orientation: Int = Configuration.ORIENTATION_PORTRAIT,
        fontScale: Float = baseContext.resources.configuration.fontScale
    ): View {
        val config = Configuration(baseContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            this.orientation = orientation
            this.fontScale = fontScale
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenWidthDp = LANDSCAPE_WIDTH_DP
                screenHeightDp = LANDSCAPE_HEIGHT_DP
            }
        }
        val themed = ContextThemeWrapper(
            baseContext.createConfigurationContext(config),
            R.style.AppTheme_NoActionBar
        )
        val root = LayoutInflater.from(themed).inflate(R.layout.act_home, null, false)
        val searchContent = root.findViewById<View>(R.id.searchContent)
        val keypad = root.findViewById<View>(R.id.scrollT9Keypad)
        keypad.visibility = View.VISIBLE
        val widthPx = (if (orientation == Configuration.ORIENTATION_LANDSCAPE) LANDSCAPE_WIDTH_DP else config.screenWidthDp) *
            themed.resources.displayMetrics.density
        val heightPx = (if (orientation == Configuration.ORIENTATION_LANDSCAPE) LANDSCAPE_HEIGHT_DP else config.screenHeightDp) *
            themed.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx.toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        searchContent.measure(
            View.MeasureSpec.makeMeasureSpec(root.measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(root.measuredHeight, View.MeasureSpec.EXACTLY)
        )
        searchContent.layout(0, 0, searchContent.measuredWidth, searchContent.measuredHeight)
        return searchContent
    }

    @Test
    fun keypadLabelsAndTouchTargetsFitUnderDarkLandscapeLargeFontConfigs() = instrumentation.runOnMainSync {
        val scenarios = listOf(
            Triple(false, Configuration.ORIENTATION_PORTRAIT, 1.0f),
            Triple(false, Configuration.ORIENTATION_LANDSCAPE, 1.0f),
            Triple(true, Configuration.ORIENTATION_PORTRAIT, 1.0f),
            Triple(true, Configuration.ORIENTATION_LANDSCAPE, 1.0f),
            Triple(false, Configuration.ORIENTATION_PORTRAIT, LARGE_FONT_SCALE),
            Triple(true, Configuration.ORIENTATION_LANDSCAPE, STRESS_FONT_SCALE)
        )
        for ((dark, orientation, fontScale) in scenarios) {
            val root = inflateSearchContent(dark, orientation, fontScale)
            val buttons = listOf(
                R.id.btnT9_1, R.id.btnT9_2, R.id.btnT9_3, R.id.btnT9_4,
                R.id.btnT9_5, R.id.btnT9_6, R.id.btnT9_7, R.id.btnT9_8,
                R.id.btnT9_9, R.id.btnT9_Clear, R.id.btnT9_0, R.id.btnT9_Backspace
            ).map { root.findViewById<MaterialButton>(it) }

            buttons.forEach { button ->
                assertTrue("key ${button.text} needs 48dp touch height", button.measuredHeight >= 48 * button.resources.displayMetrics.density)
                assertTrue("key ${button.text} text must fit: bottom=${button.layout?.getLineBottom(button.layout.lineCount - 1)} height=${button.height} pad=${button.paddingBottom} in dark=$dark orientation=$orientation font=$fontScale", button.layout == null ||
                    button.layout.getLineBottom(button.layout.lineCount - 1) <= button.height - button.paddingBottom)
            }
            for (i in buttons.indices) for (j in i + 1 until buttons.size) {
                // Detached inflation has no window offsets; every key shares the GridLayout parent.
                val a = buttons[i]
                val b = buttons[j]
                val overlap = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
                assertTrue("T9 keys overlap in orientation=$orientation font=$fontScale", !overlap)
            }
        }
    }

    @Test
    fun landscapeActivity_keepsKeypadAndResultVisible() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(baseContext)
        val keys = listOf(com.mckimquyen.util.UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD,
            com.mckimquyen.util.UtilSettings.KEY_SEARCH_FILTER_PRESET)
        val saved = keys.associateWith { prefs.all[it] }
        val apps = com.mckimquyen.app.RAppsSingleton.instance.apps
        (baseContext.applicationContext as com.mckimquyen.app.RApplication).appRefreshPipeline.cancel()
        prefs.edit().putBoolean(keys[0], true).putString(keys[1], "ALL").commit()
        com.mckimquyen.app.RAppsSingleton.instance.apps = arrayListOf(
            com.mckimquyen.model.App(label = "Chrome", packageName = "com.test.chrome", name = "Main", isVisible = true)
        )
        try {
            androidx.test.core.app.ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var originalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                scenario.onActivity { originalOrientation = it.requestedOrientation }
                try {
                    scenario.onActivity {
                        it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        it.openSearchFromHome()
                    }
                    val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                    var ready = false
                    var configDiagnostic = ""
                    while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity {
                            val keypad = it.findViewById<View>(R.id.scrollT9Keypad)
                            configDiagnostic = "orientation=${it.resources.configuration.orientation} requested=${it.requestedOrientation} keypad=${keypad.width}x${keypad.height} visibility=${keypad.visibility}"
                            // Locale override can retain a stale orientation field on ColorOS;
                            // use the actual window geometry to wait for physical rotation.
                            val decor = it.window.decorView
                            ready = decor.width > decor.height && keypad.isLaidOut
                        }
                        if (!ready) android.os.SystemClock.sleep(50)
                    }
                    assertTrue("Activity must reach landscape and lay out keypad: $configDiagnostic", ready)
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        activity.findViewById<View>(R.id.btnT9_2).performClick()
                        activity.findViewById<View>(R.id.btnT9_4).performClick()
                        activity.findViewById<View>(R.id.btnT9_7).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val scroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollT9Keypad)
                        val last = activity.findViewById<View>(R.id.btnT9_Backspace)
                        val visible = android.graphics.Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                        val scrollLocation = IntArray(2).also(scroll::getLocationOnScreen)
                        // The keypad viewport itself must stay above system bars, and it must be
                        // scrollable when its 4 rows are taller than the viewport.
                        assertTrue("T9 viewport must stay inside visible frame: bottom=${scrollLocation[1] + scroll.height} visible=${visible.bottom}",
                            scrollLocation[1] + scroll.height <= visible.bottom)
                        val grid = activity.findViewById<View>(R.id.layoutT9Keypad)
                        assertTrue("Content taller than viewport must be scrollable: grid=${grid.height} viewport=${scroll.height}",
                            grid.height <= scroll.height || scroll.canScrollVertically(1))
                        scroll.scrollTo(0, scroll.getChildAt(0).height)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val scroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollT9Keypad)
                        val last = activity.findViewById<View>(R.id.btnT9_Backspace)
                        val scrollLocation = IntArray(2).also(scroll::getLocationOnScreen)
                        val lastLocation = IntArray(2).also(last::getLocationOnScreen)
                        assertTrue("Last key must be reachable after scroll: lastBottom=${lastLocation[1] + last.height} viewportBottom=${scrollLocation[1] + scroll.height}",
                            lastLocation[1] + last.height <= scrollLocation[1] + scroll.height + 1)
                        val list = activity.findViewById<View>(R.id.rvSearchResults)
                        assertEquals("Chrome", activity.searchResultAdapterForTest?.currentListForTest()?.single()?.label)
                        assertTrue("Landscape must keep a full 48dp result row visible above the keypad: height=${list.height}", list.height >= 48 * list.resources.displayMetrics.density)
                    }
                } finally {
                    scenario.onActivity { it.requestedOrientation = originalOrientation }
                }
            }
        } finally {
            prefs.edit().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        null -> remove(key)
                    }
                }
            }.commit()
            com.mckimquyen.app.RAppsSingleton.instance.apps = apps
        }
    }

    @Test
    fun darkModeChipIconTintResolvesToReadableThemeColors() = instrumentation.runOnMainSync {
        val content = inflateSearchContent(nightMode = true)
        val allChip = content.findViewById<Chip>(R.id.chipFilterAll)
        val expectedChecked = MaterialColors.getColor(allChip, com.google.android.material.R.attr.colorOnSecondaryContainer)
        val expectedUnchecked = MaterialColors.getColor(allChip, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val tint = requireNotNull(allChip.chipIconTint)
        assertEquals(expectedChecked, tint.getColorForState(intArrayOf(android.R.attr.state_checked), 0))
        assertEquals(expectedUnchecked, tint.getColorForState(intArrayOf(-android.R.attr.state_checked), 0))
        assertTrue("dark theme surface remains opaque", Color.alpha(MaterialColors.getColor(allChip, com.google.android.material.R.attr.colorSurface)) == 255)
    }
}
