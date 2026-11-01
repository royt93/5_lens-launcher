package com.mckimquyen.util

import android.content.Intent
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Config.NONE (same as DebugStrictModeAndThemedIconTest): buildShareIntent touches the real
// android.content.Intent framework class, which needs Robolectric's shadow to work at all in a
// JVM unit test - but nothing here needs the real app manifest/resources/Application, so skip
// all three entirely rather than pulling in RApplication.onCreate()'s side effects.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PolaroidExportHelperTest {

    @Test
    fun `buildCaptionLine1 uses the lens name when present`() {
        assertEquals("Work", PolaroidExportHelper.buildCaptionLine1("Work", fallbackName = "Fisheye Launcher"))
    }

    @Test
    fun `buildCaptionLine1 falls back to the given name for a blank lens name`() {
        assertEquals(
            "Fisheye Launcher",
            PolaroidExportHelper.buildCaptionLine1("   ", fallbackName = "Fisheye Launcher")
        )
    }

    @Test
    fun `buildCaptionLine1 truncates a very long lens name with an ellipsis`() {
        val longName = "A".repeat(40)
        val line1 = PolaroidExportHelper.buildCaptionLine1(longName, fallbackName = "Fisheye Launcher")
        assertEquals(24, line1.length)
        assertTrue(line1.endsWith("…"))
    }

    @Test
    fun `buildCaptionLine1 keeps Vietnamese diacritics intact`() {
        assertEquals(
            "Công việc",
            PolaroidExportHelper.buildCaptionLine1("Công việc", fallbackName = "Fisheye Launcher")
        )
    }

    @Test
    fun `sanitizeFileName keeps a simple name unchanged`() {
        assertEquals("Work", PolaroidExportHelper.sanitizeFileName("Work"))
    }

    @Test
    fun `sanitizeFileName replaces unsafe characters with underscores`() {
        assertEquals("Work_Personal", PolaroidExportHelper.sanitizeFileName("Work/Personal"))
    }

    @Test
    fun `sanitizeFileName falls back to a default for a blank name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName falls back to a default for an all-emoji name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("😀😀"))
    }

    // B7 (test-audit): locks in TODAY's known behavior - two differently-named lenses that
    // sanitize to the same filename DO collide (writeToCache overwrites, no dedupe). This is not
    // asserting the collision is fine, only pinning it so a future change is a deliberate,
    // reviewed decision - see the `ponytail:` comment on PolaroidExportHelper.sanitizeFileName.
    @Test
    fun `sanitizeFileName collides for differently-punctuated lens names with the same letters`() {
        val a = PolaroidExportHelper.sanitizeFileName("Work/Personal")
        val b = PolaroidExportHelper.sanitizeFileName("Work Personal")
        assertEquals("today's regex-replace sanitizer does not disambiguate these", a, b)
    }

    @Test
    fun `calculatePolaroidLayout adds a border and a caption band around the content`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(800, 800)
        assertEquals(880, layout.outerWidth)
        assertEquals(1060, layout.outerHeight)
        assertEquals(40, layout.contentLeft)
        assertEquals(40, layout.contentTop)
        assertTrue(layout.captionLine1BaselineY > layout.contentTop + 800)
        assertTrue(layout.captionLine2BaselineY > layout.captionLine1BaselineY)
    }

    @Test
    fun `calculatePolaroidLayout coerces non-positive content size to at least 1px`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(0, -5)
        assertEquals(81, layout.outerWidth)
        assertEquals(261, layout.outerHeight)
    }

    @Test
    fun `caption puts optical on line two and via on line three`() {
        val c = PolaroidExportHelper.buildCaptionLines(
            "Work", "Fisheye Launcher", "Fisheye · d=2.5 · s=1.0 · hood=100%", "via Fisheye Launcher"
        )
        assertEquals("Work", c.line1)
        assertEquals("Fisheye · d=2.5 · s=1.0 · hood=100%", c.line2)
        assertEquals("via Fisheye Launcher", c.line3)
    }

    @Test
    fun `without optical text the old two line caption is kept`() {
        val c = PolaroidExportHelper.buildCaptionLines("Work", "Fisheye Launcher", "", "via Fisheye Launcher")
        assertEquals("Work", c.line1)
        assertEquals("via Fisheye Launcher", c.line2)
        assertEquals("", c.line3)
    }

    @Test
    fun `a blank optical text counts as no optical text`() {
        val c = PolaroidExportHelper.buildCaptionLines("Work", "Fisheye Launcher", "   ", "via Fisheye Launcher")
        assertEquals("via Fisheye Launcher", c.line2)
        assertEquals("", c.line3)
    }

    @Test
    fun `the layout leaves room for three caption lines`() {
        val three = PolaroidExportHelper.calculatePolaroidLayout(1080, 1944, captionLines = 3)
        val two = PolaroidExportHelper.calculatePolaroidLayout(1080, 1944)
        assertTrue(three.outerHeight > two.outerHeight)
        assertTrue(three.captionLine3BaselineY > three.captionLine2BaselineY)
        assertTrue("line 3 must sit inside the frame", three.captionLine3BaselineY < three.outerHeight)
        assertEquals(two.outerWidth, three.outerWidth)
        assertEquals(two.captionLine2BaselineY, three.captionLine2BaselineY)
    }

    @Test
    fun `two caption lines keep the old height and have no line three`() {
        val two = PolaroidExportHelper.calculatePolaroidLayout(800, 800, captionLines = 2)
        assertEquals(1060, two.outerHeight)
        assertEquals(0, two.captionLine3BaselineY)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `buildShareIntent targets an image share with the given uri and read permission`() {
        val uri = "content://com.mckimquyen.lenslauncher.fileprovider/polaroid/Work.png".toUri()
        val intent = PolaroidExportHelper.buildShareIntent(uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
