package com.mckimquyen.ui

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Helpers of LensGroupAdapter.kt that LensGroupRowsTest only touches with a row compared to itself. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensGroupAdapterHelpersTest {

    private companion object {
        const val REDUCED_MOTION_SCALE = 0f
        const val NORMAL_MOTION_SCALE = 1f
        const val FEEDBACK = HapticFeedbackConstants.CONTEXT_CLICK
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun lens(id: String, group: String? = null) = LensWorkspace(id = id, name = id, orderIndex = 0, createdAt = 0L, groupName = group)

    private fun header(group: String?) = LensGroupRow.Header(group, expanded = true, lensCount = 1, position = CardPosition.TOP)

    private fun lensRow(id: String) = LensGroupRow.Lens(lens(id), active = false, position = CardPosition.ONLY)

    // --- stableKey: rows that are different things must have different keys, or DiffUtil would merge them.

    @Test
    fun `two different lenses have different stable keys`() {
        assertNotEquals(lensRow("a").stableKey(), lensRow("b").stableKey())
    }

    @Test
    fun `the same lens keeps its key whatever its active or selected state`() {
        val plain = LensGroupRow.Lens(lens("a"), active = false, position = CardPosition.ONLY)
        val busy = LensGroupRow.Lens(lens("a"), active = true, editing = true, selected = true, position = CardPosition.TOP)
        assertEquals(plain.stableKey(), busy.stableKey())
    }

    @Test
    fun `a header and a lens never share a key even when the names match`() {
        assertNotEquals(header("a").stableKey(), lensRow("a").stableKey())
    }

    @Test
    fun `headers of different groups differ and the ungrouped header has its own key`() {
        assertNotEquals(header("Work").stableKey(), header("Home").stableKey())
        assertNotEquals(header(null).stableKey(), header("Work").stableKey())
        assertEquals(header(null).stableKey(), header(null).stableKey())
    }

    // --- performLensManagerHaptic: never plays when the system asks to reduce motion.

    private fun hapticView(): View = Mockito.mock(View::class.java).also {
        Mockito.`when`(it.context).thenReturn(context)
        Mockito.`when`(it.performHapticFeedback(FEEDBACK)).thenReturn(true)
    }

    private fun withAnimatorScale(scale: Float, block: () -> Unit) {
        val resolver = context.contentResolver
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, NORMAL_MOTION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        try {
            block()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    @Test
    fun `haptic is skipped and never reaches the view under reduced motion`() = withAnimatorScale(REDUCED_MOTION_SCALE) {
        val view = hapticView()
        assertFalse(performLensManagerHaptic(view, FEEDBACK))
        Mockito.verify(view, Mockito.never()).performHapticFeedback(Mockito.anyInt())
    }

    @Test
    fun `haptic is delegated to the view when motion is allowed`() = withAnimatorScale(NORMAL_MOTION_SCALE) {
        val view = hapticView()
        assertTrue(performLensManagerHaptic(view, FEEDBACK))
        Mockito.verify(view).performHapticFeedback(FEEDBACK)
    }
}
