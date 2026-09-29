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
@Config(manifest = Config.NONE)
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
