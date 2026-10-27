package com.mckimquyen.util

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.mckimquyen.services.AppEventManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensMoveApplierTest {
    @get:Rule val executor = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private lateinit var applier: LensMoveApplier

    private val source = "default"
    private val target = "work"
    private val id = "com.chat-Main"
    private val other = "com.mail-Main"
    private val sentinel = "ui_sentinel"

    private fun raw() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun keys(lens: String) = listOf(
        settings.lensKey(UtilSettings.KEY_LENS_APP_SCOPE, lens),
        settings.lensKey(UtilSettings.KEY_LENS_APP_SELECTION, lens),
        settings.lensKey(UtilSettings.KEY_LENS_HIDDEN_APP_IDS, lens)
    )

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        raw().edit().clear().commit()
        settings = UtilSettings(context)
        applier = LensMoveApplier(settings)
    }

    @Test
    fun `selected source and selected target swap the id`() {
        settings.saveLensAppScope(source, LensAppScope.SELECTED)
        settings.saveLensAppSelection(source, setOf(id, other))
        settings.saveLensAppScope(target, LensAppScope.SELECTED)
        settings.saveLensAppSelection(target, setOf(other))

        assertTrue(applier.move(id, source, target) != null)

        assertEquals(setOf(other), settings.getLensAppSelection(source))
        assertEquals(setOf(other, id), settings.getLensAppSelection(target))
    }

    @Test
    fun `an all source hides the app and stays all`() {
        applier.move(id, source, target)

        assertEquals(setOf(id), settings.getLensHiddenAppIds(source))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(source))
    }

    @Test
    fun `undo restores all six keys and removes keys that were absent`() {
        settings.saveLensAppScope(source, LensAppScope.SELECTED)
        settings.saveLensAppSelection(source, setOf(id, other))
        val before = (keys(source) + keys(target)).associateWith { raw().all[it] }
        assertTrue("setup: the target keys must start absent", keys(target).none(raw()::contains))

        val result = requireNotNull(applier.move(id, source, target))
        assertTrue(keys(target).any(raw()::contains))
        assertTrue(applier.undo(result.snapshot))

        before.forEach { (key, value) ->
            assertEquals(key, value, raw().all[key])
            assertEquals(key, value != null, raw().contains(key))
        }
    }

    @Test
    fun `an unrelated key is untouched by move and undo`() {
        raw().edit().putString(sentinel, "keep").commit()

        val result = requireNotNull(applier.move(id, source, target))
        assertEquals("keep", raw().getString(sentinel, null))
        applier.undo(result.snapshot)

        assertEquals("keep", raw().getString(sentinel, null))
    }

    @Test
    fun `moving to the same lens writes nothing`() {
        val before = raw().all.toMap()

        assertNull(applier.move(id, target, target))

        assertEquals(before, raw().all.toMap())
    }

    @Test
    fun `an all target with a stale selection stays all and keeps its selection`() {
        settings.saveLensAppSelection(target, setOf(other))
        settings.saveLensAppScope(target, LensAppScope.ALL)

        applier.move(id, source, target)

        assertEquals(LensAppScope.ALL, settings.getLensAppScope(target))
        assertEquals(setOf(other), settings.getLensAppSelection(target))
    }

    @Test
    fun `a non default lens uses its suffixed keys`() {
        settings.saveLensAppScope(target, LensAppScope.SELECTED)
        settings.saveLensAppSelection(target, setOf(other))

        applier.move(id, source, target)

        assertEquals(setOf(other, id), raw().getStringSet("lens_app_selection_work", null))
        assertFalse(raw().contains("lens_app_selection_default"))
    }

    @Test
    fun `move notifies both lenses and undo notifies a refresh`() {
        val seen = mutableListOf<Any?>()
        val observer = Observer<Any?> { seen += it }
        AppEventManager.lensScopeChanged.observeForever(observer)
        try {
            val result = requireNotNull(applier.move(id, source, target))
            assertTrue("the last notified lens must be the target: $seen", seen.contains(target))
            seen.clear()

            applier.undo(result.snapshot)

            assertEquals(1, seen.size)
        } finally {
            AppEventManager.lensScopeChanged.removeObserver(observer)
        }
    }
}
