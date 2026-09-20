package com.mckimquyen.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDialogCoordinatorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `COLOR_HEXES and COLOR_NAMES have matching 15 elements`() {
        assertEquals(15, SettingsDialogCoordinator.COLOR_HEXES.size)
        assertEquals(15, SettingsDialogCoordinator.COLOR_NAMES.size)
    }

    @Test
    fun `findColorIndex returns exact index for known hex case-insensitively`() {
        assertEquals(0, SettingsDialogCoordinator.findColorIndex("#FFF50057"))
        assertEquals(0, SettingsDialogCoordinator.findColorIndex("#fff50057"))
        assertEquals(5, SettingsDialogCoordinator.findColorIndex("#FF4CAF50"))
        assertEquals(14, SettingsDialogCoordinator.findColorIndex("#FFFFFFFF"))
    }

    @Test
    fun `findColorIndex falls back to 0 for unknown hex or null`() {
        assertEquals(0, SettingsDialogCoordinator.findColorIndex("#000000"))
        assertEquals(0, SettingsDialogCoordinator.findColorIndex(null))
        assertEquals(0, SettingsDialogCoordinator.findColorIndex(""))
    }

    @Test
    fun `createColorAdapter produces working singlechoice adapter`() {
        val adapter = SettingsDialogCoordinator.createColorAdapter(context)
        assertEquals(15, adapter.count)
        assertEquals("Rose", adapter.getItem(0))
        assertEquals("Light Neutral", adapter.getItem(14))
        val view = adapter.getView(0, null, android.widget.FrameLayout(context))
        assertNotNull(view)
    }
}
