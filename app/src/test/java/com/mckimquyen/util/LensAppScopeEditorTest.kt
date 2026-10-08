package com.mckimquyen.util

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.mckimquyen.services.AppEventManager
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensAppScopeEditorTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var settings: UtilSettings // assigned in @Before, which JUnit runs before every test
    private lateinit var editor: LensAppScopeEditor

    private val lens = "work-lens-id"
    private val all = setOf("a-A", "b-B", "c-C")

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
        editor = LensAppScopeEditor(settings)
    }

    @Test
    fun `apply with ids selects them and switches the lens to SELECTED`() {
        assertEquals(LensAppScope.SELECTED, editor.apply(lens, setOf("a-A")))
        assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lens))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `apply with no ids falls back to ALL and clears the selection`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, editor.apply(lens, emptySet()))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope(lens))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(lens))
    }

    @Test
    fun `effectiveIds is every app for ALL and the selection for SELECTED`() {
        assertEquals(all, editor.effectiveIds(lens, all))
        editor.apply(lens, setOf("a-A"))
        assertEquals(setOf("a-A"), editor.effectiveIds(lens, all))
    }

    @Test
    fun `remove on an ALL lens selects everything except the removed apps`() {
        assertEquals(LensAppScope.SELECTED, editor.remove(lens, setOf("b-B"), all))
        assertEquals(setOf("a-A", "c-C"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `remove of the last selected app reverts the lens to ALL`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, editor.remove(lens, setOf("a-A"), all))
        assertEquals(emptySet<String>(), settings.getLensAppSelection(lens))
    }

    @Test
    fun `add extends a SELECTED lens`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.SELECTED, editor.add(lens, setOf("c-C"), all))
        assertEquals(setOf("a-A", "c-C"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `add on an ALL lens keeps every app selected`() {
        assertEquals(LensAppScope.SELECTED, editor.add(lens, setOf("a-A"), all))
        assertEquals(all, settings.getLensAppSelection(lens))
    }

    @Test
    fun `editing one lens leaves another untouched`() {
        editor.apply(lens, setOf("a-A"))
        assertEquals(LensAppScope.ALL, settings.getLensAppScope("other-lens-id"))
    }

    @Test
    fun `every write notifies observers with the edited lens id`() {
        val seen = mutableListOf<Any?>()
        val observer = Observer<Any?> { seen.add(it) }
        AppEventManager.lensScopeChanged.observeForever(observer)
        seen.clear() // LiveData replays the last value to a new observer

        editor.apply(lens, setOf("a-A"))
        editor.add(lens, setOf("b-B"), all)
        editor.remove(lens, setOf("a-A"), all)

        AppEventManager.lensScopeChanged.removeObserver(observer)
        assertEquals(listOf<Any?>(lens, lens, lens), seen)
    }

    @Test
    fun `remove ignores ids the lens never had`() {
        editor.apply(lens, setOf("a-A", "b-B"))
        assertEquals(LensAppScope.SELECTED, editor.remove(lens, setOf("zzz-Z"), all))
        assertEquals(setOf("a-A", "b-B"), settings.getLensAppSelection(lens))
    }

    @Test
    fun `null lens id edits the default lens`() {
        editor.apply(null, setOf("a-A"))
        assertEquals(setOf("a-A"), settings.getLensAppSelection(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID))
    }
}
