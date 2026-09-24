package com.mckimquyen.adt

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FEAT-007: pure, Context-free tests for the selection-set logic behind multi-select in
 * the Apps tab — no Android framework/Robolectric needed, same pattern as
 * [AppAdapterLockStateTest].
 */
class AppAdapterSelectionStateTest {

    private fun app(pkg: String) = App(packageName = pkg, name = pkg)

    @Test
    fun `toggling an unselected identifier adds it`() {
        val result = AppAdapter.toggleIdentifier(emptySet(), "a-a")
        assertEquals(setOf("a-a"), result)
    }

    @Test
    fun `toggling an already-selected identifier removes it`() {
        val result = AppAdapter.toggleIdentifier(setOf("a-a", "b-b"), "a-a")
        assertEquals(setOf("b-b"), result)
    }

    @Test
    fun `toggle never mutates the set it was given`() {
        val original = setOf("a-a")
        AppAdapter.toggleIdentifier(original, "a-a")
        assertEquals("input set must stay untouched", setOf("a-a"), original)
    }

    @Test
    fun `clearing means removing every identifier`() {
        // clearSelection() itself just empties the live set; the pure contract it relies on
        // is that toggling every remaining identifier one at a time also reaches empty.
        var current = setOf("a-a", "b-b", "c-c")
        current.toList().forEach { current = AppAdapter.toggleIdentifier(current, it) }
        assertTrue(current.isEmpty())
    }

    @Test
    fun `selectAllIdentifiers returns every app's identifier`() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val result = AppAdapter.selectAllIdentifiers(apps)
        assertEquals(setOf("a-a", "b-b", "c-c"), result)
    }

    @Test
    fun `selectAllIdentifiers of an empty list is empty`() {
        assertTrue(AppAdapter.selectAllIdentifiers(emptyList()).isEmpty())
    }

    @Test
    fun `selectAllIdentifiers de-duplicates repeated identity`() {
        val apps = listOf(app("a"), app("a"), app("b"))
        assertEquals(setOf("a-a", "b-b"), AppAdapter.selectAllIdentifiers(apps))
    }

    @Test
    fun `identifierFor matches AppPersistent's identity convention`() {
        val identifier = AppAdapter.identifierFor(app("com.example.app"))
        assertEquals(
            AppPersistent.generateIdentifier("com.example.app", "com.example.app"),
            identifier
        )
    }
}
