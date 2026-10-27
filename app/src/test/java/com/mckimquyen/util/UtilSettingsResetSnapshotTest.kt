package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.ui.settings.ResetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsResetSnapshotTest {
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private fun raw() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        raw().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `snapshot records present values by type and absent keys as null`() {
        raw().edit().putBoolean("flag", true).putString("hint", "Find").putFloat("scale", 1.5f).putInt("night", 2)
            .putLong("anim", 250L).putStringSet("set", setOf("a", "b")).commit()

        val snapshot = settings.snapshotKeys(setOf("flag", "hint", "scale", "night", "anim", "set", "missing"))

        assertEquals(true, snapshot.values["flag"])
        assertEquals("Find", snapshot.values["hint"])
        assertEquals(1.5f, snapshot.values["scale"])
        assertEquals(2, snapshot.values["night"])
        assertEquals(250L, snapshot.values["anim"])
        assertEquals(setOf("a", "b"), snapshot.values["set"])
        assertTrue(snapshot.values.containsKey("missing"))
        assertNull(snapshot.values["missing"])
    }

    @Test
    fun `restore puts back old values and removes keys that were absent`() {
        raw().edit().putBoolean("flag", true).putString("hint", "Find").putFloat("scale", 1.5f).commit()
        val snapshot = settings.snapshotKeys(setOf("flag", "hint", "scale", "missing"))

        // The reset overwrites everything and creates the previously absent key.
        raw().edit().putBoolean("flag", false).putString("hint", "").putFloat("scale", 1.0f).putInt("missing", 9).commit()

        assertTrue(settings.restoreKeys(snapshot))

        assertEquals(true, raw().getBoolean("flag", false))
        assertEquals("Find", raw().getString("hint", null))
        assertEquals(1.5f, raw().getFloat("scale", 0f))
        assertFalse("a key that was absent must be absent again", raw().contains("missing"))
    }

    @Test
    fun `restore brings back int long and string set values`() {
        raw().edit().putInt("night", 2).putLong("anim", 250L).putStringSet("set", setOf("a", "b")).commit()
        val snapshot = settings.snapshotKeys(setOf("night", "anim", "set"))
        raw().edit().putInt("night", 1).putLong("anim", 100L).putStringSet("set", setOf("z")).commit()

        assertTrue(settings.restoreKeys(snapshot))

        assertEquals(2, raw().getInt("night", 0))
        assertEquals(250L, raw().getLong("anim", 0L))
        assertEquals(setOf("a", "b"), raw().getStringSet("set", null))
    }

    @Test
    fun `restore leaves every key outside the snapshot alone`() {
        raw().edit().putBoolean("flag", true).commit()
        val snapshot = settings.snapshotKeys(setOf("flag"))
        raw().edit().putBoolean("flag", false).putString("entitlement", "vip").putString("language", "vi")
            .putString("search_history", "x").commit()

        assertTrue(settings.restoreKeys(snapshot))

        assertEquals("vip", raw().getString("entitlement", null))
        assertEquals("vi", raw().getString("language", null))
        assertEquals("x", raw().getString("search_history", null))
    }

    @Test
    fun `restoring an empty snapshot changes nothing`() {
        raw().edit().putBoolean("flag", true).commit()

        assertTrue(settings.restoreKeys(ResetSnapshot(emptyMap())))

        assertEquals(true, raw().getBoolean("flag", false))
    }
}
