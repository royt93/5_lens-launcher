package com.mckimquyen.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Coverage gap found by whole-codebase audit sweep: UtilIconPackManager backs the custom
 * icon-pack feature (real XML parsing + icon-generation math from third-party icon packs) but
 * had zero test at any tier before this. Its interesting logic lives in private helper methods
 * on the non-static inner IconPack class, invoked here via reflection rather than refactoring
 * production code just to make it testable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilIconPackManagerTest {

    private val manager = UtilIconPackManager()
    private val iconPack = manager.IconPack()

    private fun invokePrivate(name: String, vararg args: Any?): Any? {
        val method = iconPack.javaClass.declaredMethods
            .first { it.name == name && it.parameterTypes.size == args.size }
        method.isAccessible = true
        return method.invoke(iconPack, *args)
    }

    private fun backImages(): MutableList<Bitmap> {
        val field = iconPack.javaClass.getDeclaredField("mBackImages")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(iconPack) as MutableList<Bitmap>
    }

    private fun factor(): Float {
        val field = iconPack.javaClass.getDeclaredField("mFactor")
        field.isAccessible = true
        return field.get(iconPack) as Float
    }

    @Test
    fun `calculateDestRect with the default 1_0 factor exactly fills the back image bounds`() {
        val rect = invokePrivate("calculateDestRect", 100, 100) as RectF

        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(100f, rect.right, 0.01f)
        assertEquals(100f, rect.bottom, 0.01f)
    }

    @Test
    fun `calculateDestRect shrinks and centers the rect for a smaller factor`() {
        val xpp = XmlPullParserFactory.newInstance().newPullParser()
        xpp.setInput(StringReader("<scale factor=\"0.5\" />"))
        xpp.next()
        invokePrivate("parseScale", xpp)

        val rect = invokePrivate("calculateDestRect", 100, 100) as RectF

        assertEquals(25f, rect.left, 0.01f)
        assertEquals(25f, rect.top, 0.01f)
        assertEquals(75f, rect.right, 0.01f)
        assertEquals(75f, rect.bottom, 0.01f)
    }

    @Test
    fun `getMostAppropriateBackImage returns the sole image when only one exists`() {
        val only = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        backImages().add(only)

        val defaultBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val result = invokePrivate("getMostAppropriateBackImage", defaultBitmap)

        assertSame(only, result)
    }

    @Test
    fun `getMostAppropriateBackImage picks the closest hue match among multiple candidates`() {
        val redBack = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val greenBack = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        backImages().add(redBack)
        backImages().add(greenBack)

        val defaultBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val result = invokePrivate("getMostAppropriateBackImage", defaultBitmap)

        assertSame("a red default icon should match the red back image, not green", redBack, result)
    }

    @Test
    fun `parseScale falls back to 1_0 on a malformed factor instead of crashing`() {
        val xpp = XmlPullParserFactory.newInstance().newPullParser()
        xpp.setInput(StringReader("<scale factor=\"not-a-number\" />"))
        xpp.next()

        invokePrivate("parseScale", xpp)

        assertEquals(1.0f, factor(), 0.0001f)
    }

    @Test
    fun `parseScale reads a well-formed factor`() {
        val xpp = XmlPullParserFactory.newInstance().newPullParser()
        xpp.setInput(StringReader("<scale factor=\"0.8\" />"))
        xpp.next()

        invokePrivate("parseScale", xpp)

        assertEquals(0.8f, factor(), 0.0001f)
    }
}
