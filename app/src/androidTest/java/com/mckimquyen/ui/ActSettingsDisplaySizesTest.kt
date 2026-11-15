package com.mckimquyen.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Settings tab (27 switches, the longest screen) on whatever display the device is in: can its last element be scrolled to a
 * position above the system bar and the banner, and a screenshot to look at. Geometry is logged with tag F265SET so the profiles can be
 * compared. ActSettings starts an ad flow in onCreate (overlay `flAdOpenApp`, banner), so this test records whether the overlay is
 * visible and fails the screenshot if it is, rather than photographing an ad. The two first-launch dialogs (policy, language) are
 * avoided by seeding their keys, as AdOverlayContrastWidgetTest does; DeviceStateGuard restores the preferences. Rotation and the
 * closing of a test ad with KEYCODE_BACK are done by `run-display-tests-adguard.sh`, never by this test.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsDisplaySizesTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F265SET"
        const val EXPECT_ARG = "displayExpectOrientation"
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
        const val SETTLE_MS = 900L
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val COPY_WAIT_S = 10L
        const val PNG_QUALITY = 100
        const val SHOT_DIR = "settingsshots"
        const val BAR_TOLERANCE_PX = 1
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun seedFirstLaunchKeys() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(UtilSettings.KEY_READ_POLICY, true).putBoolean(LANGUAGE_SELECTED_KEY, true).commit()
    }

    @After fun nothing() = Unit

    private fun checkOrientation(scenario: ActivityScenario<ActSettings>): String {
        var portrait = true
        var dims = ""
        scenario.onActivity {
            val dm = it.resources.displayMetrics
            portrait = dm.heightPixels >= dm.widthPixels
            dims = "${dm.widthPixels}x${dm.heightPixels}"
        }
        val expected = InstrumentationRegistry.getArguments().getString(EXPECT_ARG)
        Log.i(TAG, "orientation check expected=$expected activity=$dims portrait=$portrait")
        if (expected != null) assertEquals("the Activity is not in the orientation this run asked for (rotate from the script first)", expected, if (portrait) "portrait" else "landscape")
        return dims
    }

    private fun showSettingsTab(scenario: ActivityScenario<ActSettings>) {
        scenario.onActivity { it.viewpager.setCurrentItem(FragmentPagerAdapter.TAB_SETTINGS, false) }
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<FrmSettings>().firstOrNull()
                ready = fragment?.view?.let { v -> v.isAttachedToWindow && v.height > 0 && v.findViewById<View>(R.id.llImportLayout) != null } ?: false
            }
            if (!ready) Thread.sleep(POLL_MS)
        }
        assertTrue("the Settings tab must be laid out", ready)
        SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private fun overlayVisible(activity: ActSettings): Boolean = activity.findViewById<View>(R.id.flAdOpenApp)?.visibility == View.VISIBLE

    @Test fun theLastSettingCanBeScrolledAboveTheSystemBarAndTheBanner() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            checkOrientation(scenario)
            showSettingsTab(scenario)
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<FrmSettings>().first()
                val scroll = fragment.view as NestedScrollView
                val last = scroll.findViewById<View>(R.id.llImportLayout)
                assertNotNull("positive control: the last element exists", last)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                val scrollTop = IntArray(2).also { scroll.getLocationOnScreen(it) }[1]
                val scrollBottom = scrollTop + scroll.height
                val lastTop = IntArray(2).also { last.getLocationOnScreen(it) }[1]
                val lastBottom = lastTop + last.height
                val banner = activity.findViewById<View>(R.id.bannerContainer)
                val bannerTop = if (banner != null && banner.visibility == View.VISIBLE && banner.height > 0) IntArray(2).also { banner.getLocationOnScreen(it) }[1] else -1
                val content = scroll.getChildAt(0)
                Log.i(TAG, "display ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi frameBottom=${frame.bottom} scroll=${scroll.width}x${scroll.height} " +
                    "content=${content.height}px (${"%.0f".format(content.height / dm.density)}dp) scrollMax=${content.height - scroll.height} " +
                    "scrollTop=$scrollTop scrollBottom=$scrollBottom lastTop=$lastTop lastBottom=$lastBottom bannerTop=$bannerTop " +
                    "overlayVisible=${overlayVisible(activity)} tabs=${activity.tabs.height} paddingBottom=${scroll.paddingBottom} clipToPadding=${scroll.clipToPadding}")
                val limit = if (bannerTop >= 0) minOf(frame.bottom, bannerTop) else frame.bottom
                assertTrue("the last setting (bottom $lastBottom) must end above the system bar / banner ($limit)", lastBottom <= limit + BAR_TOLERANCE_PX)
                assertTrue("the last setting must be inside the scroll viewport: top=$lastTop viewport=[$scrollTop,$scrollBottom]", lastTop >= scrollTop - BAR_TOLERANCE_PX && lastBottom <= scrollBottom + BAR_TOLERANCE_PX)
                scroll.scrollTo(0, 0)
            }
        }
    }

    /**
     * The T9 switch says that short windows use the keypad even when it is off (SearchKeypadPolicy). The note must exist, carry the
     * string resource, and be reachable by scrolling inside the viewport on every display profile.
     */
    @Test fun theT9ShortScreenNoteIsShownAndReachable() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            checkOrientation(scenario)
            showSettingsTab(scenario)
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<FrmSettings>().first()
                val scroll = fragment.view as NestedScrollView
                val note = scroll.findViewById<android.widget.TextView>(R.id.tvEnableT9SearchKeypadShortScreenNote)
                assertNotNull("the T9 short-screen note must exist", note)
                assertEquals("the note must show the string resource", activity.getString(R.string.setting_enable_t9_search_keypad_short_screen_note), note.text.toString())
                // Scroll so the note's top edge is at the viewport top: its position inside the scrolled content is the sum of the offsets of
                // its ancestors up to the NestedScrollView's child, not its own 0,0.
                val rect = Rect(0, 0, note.width, note.height)
                scroll.offsetDescendantRectToMyCoords(note, rect)
                scroll.scrollTo(0, rect.top)
                val scrollTop = IntArray(2).also { scroll.getLocationOnScreen(it) }[1]
                val scrollBottom = scrollTop + scroll.height
                val top = IntArray(2).also { note.getLocationOnScreen(it) }[1]
                val bottom = top + note.height
                Log.i(TAG, "T9NOTE display ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi note=${note.width}x${note.height} lines=${note.lineCount} top=$top bottom=$bottom viewport=[$scrollTop,$scrollBottom] visible=${note.isShown}")
                assertTrue("the note must be shown", note.isShown && note.height > 0)
                assertTrue("the note must be inside the scroll viewport: top=$top bottom=$bottom viewport=[$scrollTop,$scrollBottom]", top >= scrollTop - BAR_TOLERANCE_PX && bottom <= scrollBottom + BAR_TOLERANCE_PX)
                scroll.scrollTo(0, 0)
            }
        }
    }

    @Test fun theScreenshotMatchesTheActivityDisplayAndShowsNoAdOverlay() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            val dims = checkOrientation(scenario)
            showSettingsTab(scenario)
            var overlay = false
            scenario.onActivity { overlay = overlayVisible(it) }
            Log.i(TAG, "overlay visible at capture time = $overlay")
            assertTrue("an ad overlay is covering the screen: stop and let the owner clear it (R4), do not photograph it", !overlay)
            var bitmap: Bitmap? = null
            var result = -1
            var width = 0
            var height = 0
            val latch = CountDownLatch(1)
            val deadline = SystemClock.uptimeMillis() + WAIT_MS
            var requested = false
            var lastError: Throwable? = null
            while (!requested && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    width = activity.resources.displayMetrics.widthPixels
                    height = activity.resources.displayMetrics.heightPixels
                    val bmp = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                    try {
                        PixelCopy.request(activity.window, bmp, { code -> result = code; latch.countDown() }, Handler(Looper.getMainLooper()))
                        bitmap = bmp
                        requested = true
                    } catch (e: IllegalArgumentException) {
                        lastError = e
                    }
                }
                if (!requested) Thread.sleep(POLL_MS)
            }
            assertTrue("PixelCopy must accept the request within the wait, last error: $lastError", requested)
            assertTrue("PixelCopy must answer", latch.await(COPY_WAIT_S, TimeUnit.SECONDS))
            val shot = requireNotNull(bitmap)
            assertEquals("PixelCopy must succeed", PixelCopy.SUCCESS, result)
            val dir = File(context.filesDir, SHOT_DIR).apply { mkdirs() }
            val file = File(dir, "settings-$dims.png")
            file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            Log.i(TAG, "screenshot ${file.name} ${shot.width}x${shot.height} display=${width}x$height bytes=${file.length()}")
            assertEquals("the image width must equal the Activity display width", width, shot.width)
            assertEquals("the image height must equal the Activity display height", height, shot.height)
            assertTrue("the image must not be blank", (0 until shot.width step 97).any { x -> (0 until shot.height step 89).any { y -> shot.getPixel(x, y) != shot.getPixel(0, 0) } })
        }
    }
}
