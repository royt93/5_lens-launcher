package com.mckimquyen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** FISH-037: contact-sheet export through the real bitmap/Canvas/file path. */
@RunWith(AndroidJUnit4::class)
class PolaroidContactSheetIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val PAGE_W = 400
        const val PAGE_H = 700
        const val APP_COUNT = 12
        const val ICON_PX = 48
        const val AWAIT_SECONDS = 20L
        const val SHEET_FILE = "lenses.png"
        const val OPTICAL = "Fisheye · d=2.5 · s=1.0 · hood=100%"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    // Kept resumed for the whole test. Without a foreground Activity the TECNO "Hiber" freezer puts the
    // instrumented process into FrozenState a few seconds in, and the whole class hangs on that device.
    private var scenario: ActivityScenario<OrganizationHarnessActivity>? = null
    private var uiContext: Context = context
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private val apps = ArrayList((0 until APP_COUNT).map { i ->
        App(id = i, packageName = "com.sheet.app$i", name = "App$i", label = "App $i", iconCacheKey = "sheet#$i")
    })
    private lateinit var host: View

    private fun sheetFile() = File(File(context.cacheDir, "polaroid"), SHEET_FILE)

    @Before
    fun setUp() {
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        val launched = ActivityScenario.launch(OrganizationHarnessActivity::class.java)
        scenario = launched
        launched.onActivity { uiContext = it }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { host = View(uiContext).apply { layout(0, 0, PAGE_W, PAGE_H) } }
        sheetFile().delete()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        BitmapCache.clear()
        File(context.cacheDir, "polaroid").listFiles()?.forEach { it.delete() }
    }

    private fun specs(count: Int, optical: String = OPTICAL) =
        (0 until count).map { PolaroidExportHelper.LensExportSpec("lens$it", "Lens $it", optical) }

    private fun export(
        specs: List<PolaroidExportHelper.LensExportSpec>,
        liveView: LensView? = null,
        liveLensId: String? = null,
        appsForLens: (String) -> ArrayList<App> = { ArrayList(apps) }
    ): android.net.Uri? {
        var result: android.net.Uri? = null
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            PolaroidExportHelper.exportContactSheetAsync(uiContext, host, specs, liveView, liveLensId, appsForLens) {
                result = it
                latch.countDown()
            }
        }
        assertTrue("export must call back", latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        return result
    }

    private fun expectedLayout(count: Int): SheetLayout {
        val polaroid = PolaroidExportHelper.calculatePolaroidLayout(PAGE_W, PAGE_H)
        return PolaroidContactSheetLayout.compute(count, polaroid.outerWidth, polaroid.outerHeight)
    }

    private fun decode(): Bitmap = requireNotNull(BitmapFactory.decodeFile(sheetFile().absolutePath)) { "sheet not decodable" }

    private fun hasInk(bmp: Bitmap, cell: SheetCell): Boolean =
        (cell.top until cell.top + cell.height).any { y ->
            (cell.left until cell.left + cell.width).any { x -> bmp.getPixel(x, y) != Color.WHITE }
        }

    @Test
    fun threeLensesProduceTheComputedSizeWhiteGapsAndAnEmptyFourthCell() {
        assertNotNull(export(specs(3)))
        val expected = expectedLayout(3)
        val bmp = decode()
        assertEquals(expected.width, bmp.width)
        assertEquals(expected.height, bmp.height)
        assertEquals("outer gap is white", Color.WHITE, bmp.getPixel(0, 0))
        val unused = expectedLayout(4).cells[3]
        val cell0 = expected.cells[0]
        val emptyX = cell0.left + (cell0.width + PolaroidContactSheetLayout.GAP_PX) + cell0.width / 2
        val emptyY = cell0.top + (cell0.height + PolaroidContactSheetLayout.GAP_PX) + cell0.height / 2
        assertEquals("empty cell at (${unused.left},${unused.top}) stays white", Color.WHITE, bmp.getPixel(emptyX, emptyY))
        expected.cells.forEach { assertTrue("cell ${it.index} must contain ink", hasInk(bmp, it)) }
        bmp.recycle()
    }

    @Test
    fun theViaFooterIsPrintedOnceBelowTheLastRowOfCells() {
        assertNotNull(export(specs(3)))
        val expected = expectedLayout(3)
        val bmp = decode()
        val footerTop = expected.height - PolaroidContactSheetLayout.FOOTER_PX
        val lastCellBottom = expected.cells.maxOf { it.top + it.height }
        assertTrue("footer starts below every cell", footerTop >= lastCellBottom)
        val footerHasInk = (footerTop until expected.height).any { y ->
            (0 until expected.width).any { x -> bmp.getPixel(x, y) != Color.WHITE }
        }
        assertTrue("the via footer must contain ink", footerHasInk)
        bmp.recycle()
    }

    @Test
    fun aLensWithNoAppsStillGetsAFilledFrameWithItsCaption() {
        assertNotNull(export(specs(2)) { ArrayList() })
        val bmp = decode()
        expectedLayout(2).cells.forEach { assertTrue("cell ${it.index} has caption ink", hasInk(bmp, it)) }
        bmp.recycle()
    }

    @Test
    fun theLiveViewIsCapturedForItsOwnLensAndOthersAreBuiltTemporarily() {
        val seen = ArrayList<String>()
        var live: LensView? = null
        instrumentation.runOnMainSync {
            live = LensView(uiContext).apply { lensId = "lens0"; layout(0, 0, PAGE_W, PAGE_H); setApps(ArrayList(apps)) }
        }
        assertNotNull(export(specs(2), live, "lens0") { id -> seen.add(id); ArrayList(apps) })
        assertEquals("only the non-live lens asks for its apps", listOf("lens1"), seen)
        val bmp = decode()
        expectedLayout(2).cells.forEach { assertTrue(hasInk(bmp, it)) }
        bmp.recycle()
    }

    @Test
    fun aFailingProviderReturnsNullAndLeavesNoFile() {
        val uri = export(specs(3)) { id -> if (id == "lens1") error("boom") else ArrayList(apps) }
        assertNull(uri)
        assertFalse("no half-written sheet", sheetFile().exists())
    }

    @Test
    fun nineRequestsYieldExactlySixCells() {
        assertNotNull(export(specs(9)))
        val expected = expectedLayout(PolaroidContactSheetLayout.MAX_LENSES)
        val bmp = decode()
        assertEquals(expected.width, bmp.width)
        assertEquals(expected.height, bmp.height)
        assertEquals(PolaroidContactSheetLayout.MAX_LENSES, expected.cells.size)
        expected.cells.forEach { assertTrue("cell ${it.index} must contain ink", hasInk(bmp, it)) }
        bmp.recycle()
    }

    @Test
    fun anUnlaidOutHostReturnsNullWithoutWritingAFile() {
        instrumentation.runOnMainSync { host = View(uiContext) }
        assertNull(export(specs(2)))
        assertFalse(sheetFile().exists())
    }

    @Test
    fun emptySpecsReturnNull() {
        assertNull(export(emptyList()))
        assertFalse(sheetFile().exists())
    }
}
