package com.mckimquyen.adt

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.CheckedTextView
import android.widget.FrameLayout
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensAppChoiceAdapterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    private fun app(name: String, icon: Bitmap? = null) =
        App(label = name, packageName = "com.test.choice", name = name, icon = icon)

    @After
    fun cleanup() = BitmapCache.clear()

    @Test
    fun cachedIconForTheComponentWinsOverAppBitmap() {
        val fallback = bitmap()
        val cached = bitmap()
        val app = app("Mail", fallback)
        RAppsSingleton.instance.setAppIcon(app.iconCacheKey, cached)
        val row = LensAppChoiceAdapter(context, listOf(app)).getView(0, null, FrameLayout(context)) as CheckedTextView
        assertEquals("Mail", row.text.toString())
        assertSame(cached, (row.compoundDrawablesRelative[0] as BitmapDrawable).bitmap)
        assertNotNull(row.checkMarkDrawable)
    }

    @Test
    fun appBitmapIsShownWhenCacheMisses() {
        val icon = bitmap()
        val row = LensAppChoiceAdapter(context, listOf(app("Chat", icon)))
            .getView(0, null, FrameLayout(context)) as CheckedTextView
        assertSame(icon, (row.compoundDrawablesRelative[0] as BitmapDrawable).bitmap)
    }

    @Test
    fun recycledRowNeverKeepsThePreviousAppsIcon() {
        val icon = bitmap()
        val adapter = LensAppChoiceAdapter(context, listOf(app("Mail", icon), app("Missing")))
        val parent = FrameLayout(context)
        val first = adapter.getView(0, null, parent) as CheckedTextView
        val originalDrawable = first.compoundDrawablesRelative[0]
        val recycled = adapter.getView(1, first, parent) as CheckedTextView
        assertSame(first, recycled)
        assertEquals("Missing", recycled.text.toString())
        assertNotNull(recycled.compoundDrawablesRelative[0])
        assertNotSame(originalDrawable, recycled.compoundDrawablesRelative[0])
    }

    @Test
    fun iconUsesNamedFortyDpSizeAndLabelRemainsAdapterItem() {
        val adapter = LensAppChoiceAdapter(context, listOf(app("Calendar", bitmap())))
        val row = adapter.getView(0, null, FrameLayout(context)) as CheckedTextView
        val expected = (LensAppChoiceAdapter.ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        assertEquals(expected, row.compoundDrawablesRelative[0].bounds.width())
        assertEquals(expected, row.compoundDrawablesRelative[0].bounds.height())
        assertEquals("Calendar", adapter.getItem(0).toString())
    }
}
