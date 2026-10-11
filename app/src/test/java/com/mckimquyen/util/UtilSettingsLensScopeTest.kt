package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsLensScopeTest {

    private lateinit var context: Context // assigned in @Before, which JUnit runs before every test
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"
    private val default = LensWorkspace.DEFAULT_LENS_ID

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `workspace snapshot preserves exactly app scope selection frozen and active lens values`() {
        settings.saveLensAppScope(default, LensAppScope.SELECTED)
        settings.saveLensAppSelection(default, setOf("pkg-A"))
        settings.saveLensFrozen(default, true)
        settings.saveLensAppScope(work, LensAppScope.ALL)
        settings.saveLensAppSelection(work, setOf("pkg-B"))
        settings.saveLensFrozen(work, false)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, work)
        val snapshot = settings.snapshotWorkspacePreferences()
        settings.restoreWorkspacePreferences(snapshot)
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(default))
        assertEquals(setOf("pkg-A"), settings.getLensAppSelection(default))
        assertTrue(settings.isLensFrozen(default))
        assertEquals(work, settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
    }

    @Test
    fun `replaceWorkspacePreferences clears only app-state keys`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("old"))
        settings.saveLensFrozen(work, true)
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR + "_$work", 5f)
        assertTrue(settings.replaceWorkspacePreferences(
            listOf(LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false)),
            LensWorkspace.DEFAULT_LENS_ID
        ))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(default))
        assertFalse(settings.isLensFrozen(default))
        assertEquals(5f, settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR + "_$work"))
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_LENS_APP_SCOPE}_$work"))
    }

    @Test
    fun `v1 replacement leaves preferences unchanged`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("keep"))
        settings.saveLensFrozen(work, true)
        val before = HashMap(rawPrefs().all)
        assertTrue(settings.replaceWorkspacePreferencesForSchema(1, emptyList(), default))
        assertEquals(before, rawPrefs().all)
    }

    @Test
    fun `keys are stable`() {
        assertEquals("lens_app_scope", UtilSettings.KEY_LENS_APP_SCOPE)
        assertEquals("lens_app_selection", UtilSettings.KEY_LENS_APP_SELECTION)
        assertEquals("lens_frozen", UtilSettings.KEY_LENS_FROZEN)
    }

    @Test
    fun `a fresh lens is ALL, empty and not frozen`() {
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `a non-default lens never inherits the default lens values`() {
        settings.saveLensAppScope(default, LensAppScope.SELECTED)
        settings.saveLensAppSelection(default, setOf("a-A"))
        settings.saveLensFrozen(default, true)

        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `the default lens uses the unsuffixed keys and others the suffixed ones`() {
        settings.saveLensAppScope(default, LensAppScope.SELECTED)
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        assertTrue(rawPrefs().contains("lens_app_scope"))
        assertTrue(rawPrefs().contains("lens_app_scope_$work"))
    }

    @Test
    fun `saving for one lens does not change another`() {
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensAppSelection(personal, setOf("b-B"))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(work))
        assertEquals(setOf("b-B"), settings.getLensAppSelection(personal))
    }

    @Test
    fun `mutating the set passed in later does not change what was stored`() {
        val ids = mutableSetOf("a-A")
        settings.saveLensAppSelection(work, ids)
        ids.add("b-B")
        assertEquals(setOf("a-A"), settings.getLensAppSelection(work))
    }

    @Test
    fun `a wrong-typed stored value degrades to the defaults instead of crashing`() {
        rawPrefs().edit()
            .putInt("lens_app_scope_$work", 7)
            .putString("lens_app_selection_$work", "oops")
            .putString("lens_frozen_$work", "yes")
            .commit()
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }

    @Test
    fun `duplicate copies scope selection and frozen flag`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensFrozen(work, true)

        settings.duplicateLensSettings(work, personal)

        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(personal))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(personal))
        assertTrue(settings.isLensFrozen(personal))
    }

    @Test
    fun `delete removes all three keys so a reused id starts clean`() {
        settings.saveLensAppScope(work, LensAppScope.SELECTED)
        settings.saveLensAppSelection(work, setOf("a-A"))
        settings.saveLensFrozen(work, true)

        settings.deleteLensSettings(work)

        assertEquals(LensAppScope.ALL, settings.getLensAppScope(work))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(work))
        assertFalse(settings.isLensFrozen(work))
    }
}
