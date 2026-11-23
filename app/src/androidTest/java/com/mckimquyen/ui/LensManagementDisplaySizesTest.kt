package com.mckimquyen.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The lens manager on whatever display the device is in (phone, landscape, `wm size` tablet-like): how much of the screen the list
 * really gets under the 192 dp collapsing toolbar, whether the last row can be scrolled into view above the system bar, and a
 * screenshot to look at. Geometry is logged with tag F248LM so the profiles can be compared. Rotation is NOT done here: see
 * `run-display-tests.sh`, which rotates with the launcher in front and runs one test per `am instrument`. A run that asks for an
 * orientation with `-e displayExpectOrientation landscape|portrait` fails when the Activity reports another one.
 */
@RunWith(AndroidJUnit4::class)
class LensManagementDisplaySizesTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F248LM"
        const val EXPECT_ARG = "displayExpectOrientation"
        const val LENS_COUNT = 12
        const val ROWS_WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 700L
        const val COPY_WAIT_S = 10L
        const val PNG_QUALITY = 100
        const val SHOT_DIR = "lmshots"
        const val SYSTEM_BAR_TOLERANCE_PX = 1
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before fun setUp() = runBlocking {
        AppDatabase.init(context)
        context.getSharedPreferences("lens_access_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        dao.deleteAll()
        dao.insertAll((0 until LENS_COUNT).map { i ->
            if (i == 0) LensWorkspace.createDefault() else LensWorkspace("lens$i", "Lens $i", i, 10L + i)
        })
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After fun tearDown() = runBlocking {
        context.getSharedPreferences("lens_access_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        dao.deleteAll()
        dao.insertIfAbsent(LensWorkspace.createDefault())
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        UtilSettings(context).saveCollapsedLensGroups(emptySet())
    }

    private fun awaitRows(scenario: ActivityScenario<LensManagementActivity>) {
        val deadline = SystemClock.uptimeMillis() + ROWS_WAIT_MS
        var count = 0
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { count = it.findViewById<RecyclerView>(R.id.rvLensGroups).adapter?.itemCount ?: 0 }
            if (count >= LENS_COUNT) break
            Thread.sleep(POLL_MS)
        }
        assertTrue("the list must load all $LENS_COUNT lens rows, had $count", count >= LENS_COUNT)
        SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    /** Fails (not skips) when a run asked for an orientation the Activity is not in: such a run would be photographing the wrong thing. */
    private fun checkOrientation(scenario: ActivityScenario<LensManagementActivity>): String {
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

    @Test fun theListKeepsRoomAndItsLastRowCanBeScrolledAboveTheSystemBar() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            val dims = checkOrientation(scenario)
            awaitRows(scenario)
            // Measured on the Pixel (phone portrait/landscape 3.5x, tablet-like 2.0x): with the 192 dp collapsing toolbar expanded the
            // list is laid out 128 dp lower than collapsed (448 px and 256 px), and the list's own bottom edge is the screen bottom, under
            // the navigation bar, in both states (the list carries paddingBottom for it). So the list bottom is NOT compared with the
            // visible frame bottom (an earlier version did, and that failed on all three profiles for the 24 dp / 32 dp bar alone).
            // What matters to a user, and is asserted below, is that the last row can be scrolled to a position above the bar. The
            // expanded and collapsed geometry are logged for comparison.
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val bottom = IntArray(2).also { list.getLocationOnScreen(it) }[1] + list.height
                Log.i(TAG, "EXPANDED list bottom=$bottom frameBottom=${frame.bottom} below-frame=${bottom - frame.bottom}px (${"%.0f".format((bottom - frame.bottom) / dm.density)}dp)")
                activity.findViewById<AppBarLayout>(R.id.lensManagerAppBar).setExpanded(false, false)
            }
            SystemClock.sleep(SETTLE_MS)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val appBar = activity.findViewById<View>(R.id.lensManagerAppBar)
                val actions = activity.findViewById<View>(R.id.lensGroupActions)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val listTop = IntArray(2).also { list.getLocationOnScreen(it) }[1]
                val listBottom = listTop + list.height
                val firstRow = list.getChildAt(0)
                val rowHeight = firstRow?.height ?: 0
                val density = dm.density
                Log.i(TAG, "COLLAPSED display $dims @${dm.densityDpi}dpi frameBottom=${frame.bottom} appBar=${appBar.height}px (${"%.0f".format(appBar.height / density)}dp) " +
                    "list=${list.width}x${list.height}px (${"%.0f".format(list.height / density)}dp) top=$listTop bottom=$listBottom firstRowHeight=$rowHeight " +
                    "rowsFullyVisibleWithoutScroll=${if (rowHeight > 0) list.height / rowHeight else -1} canScrollDown=${list.canScrollVertically(1)} " +
                    "actionsVisible=${actions.visibility == View.VISIBLE} listPaddingBottom=${list.paddingBottom}")
                assertTrue("positive control: the list has rows", list.adapter!!.itemCount >= LENS_COUNT && rowHeight > 0)
                assertTrue("with the app bar collapsed the list must reach the screen bottom (so its bottom padding, not a gap, clears the bar): bottom=$listBottom display=${dm.heightPixels}", listBottom == dm.heightPixels || listBottom <= frame.bottom + SYSTEM_BAR_TOLERANCE_PX)
                assertTrue("the list must carry bottom padding for the system bar: padding=${list.paddingBottom} bar=${dm.heightPixels - frame.bottom}", list.paddingBottom >= dm.heightPixels - frame.bottom)
                assertTrue("the list must show at least one whole row: height ${list.height} < row $rowHeight", list.height >= rowHeight)
                // Reach the last row the way a finger would: scroll to it, then it must sit inside the list and above the frame bottom.
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(list.adapter!!.itemCount - 1, 0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvLensGroups)
                val last = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)
                assertTrue("the last row must be bound after scrolling to it", last != null)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val rowTop = IntArray(2).also { last!!.itemView.getLocationOnScreen(it) }[1]
                val rowBottom = rowTop + last!!.itemView.height
                val listTop = IntArray(2).also { list.getLocationOnScreen(it) }[1]
                val clippedBy = rowBottom - minOf(listTop + list.height, frame.bottom)
                Log.i(TAG, "last row top=$rowTop bottom=$rowBottom frameBottom=${frame.bottom} overshootPx=$clippedBy")
                assertTrue("the last row must end above the system bar: bottom=$rowBottom frameBottom=${frame.bottom}", rowBottom <= frame.bottom + SYSTEM_BAR_TOLERANCE_PX)
            }
        }
    }

    @Test fun theScreenshotMatchesTheActivityDisplayAndIsWrittenForReview() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            val dims = checkOrientation(scenario)
            awaitRows(scenario)
            var bitmap: Bitmap? = null
            var result = -1
            var width = 0
            var height = 0
            val latch = CountDownLatch(1)
            // PixelCopy needs a window with a surface: on one run it threw IllegalArgumentException "Window doesn't have a backing surface!"
            // right after the rows loaded, on others it did not. There is no public way to ask for the surface, so retry the request
            // until it is accepted (bounded), keeping the last failure for the message.
            val surfaceDeadline = SystemClock.uptimeMillis() + ROWS_WAIT_MS
            var requested = false
            var lastError: Throwable? = null
            while (!requested && SystemClock.uptimeMillis() < surfaceDeadline) {
                scenario.onActivity { activity ->
                    width = activity.resources.displayMetrics.widthPixels
                    // The window bounds, not displayMetrics: on a phone with a display cutout (TECNO KJ7: 108 px) displayMetrics.heightPixels is 2328 for a
                    // 2436 px window, while PixelCopy of the decor view is the full 2436.
                    height = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) activity.windowManager.currentWindowMetrics.bounds.height() else activity.resources.displayMetrics.heightPixels
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
            val file = File(dir, "lensmanager-$dims.png")
            file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            Log.i(TAG, "screenshot ${file.name} ${shot.width}x${shot.height} display=${width}x$height bytes=${file.length()}")
            assertEquals("the image width must equal the Activity display width", width, shot.width)
            assertEquals("the image height must equal the Activity display height", height, shot.height)
            assertTrue("the image must not be blank", (0 until shot.width step 97).any { x -> (0 until shot.height step 89).any { y -> shot.getPixel(x, y) != shot.getPixel(0, 0) } })
        }
    }
}
