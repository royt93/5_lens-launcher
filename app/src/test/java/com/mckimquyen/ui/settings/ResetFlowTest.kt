package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetFlowTest {
    private val label = 1

    private class FakeStore(val prefs: MutableMap<String, Any?> = mutableMapOf()) : ResetFlow.Store {
        var failRestore = false
        override fun raw(): Map<String, Any?> = prefs.toMap()
        override fun snapshot(keys: Set<String>) = ResetSnapshot(keys.associateWith { prefs[it] })
        override fun restore(snapshot: ResetSnapshot): Boolean {
            if (failRestore) return false
            snapshot.values.forEach { (k, v) -> if (v == null) prefs.remove(k) else prefs[k] = v }
            return true
        }
        override fun apply(entries: List<ResetEntry>) { entries.forEach { prefs[it.key] = it.defaultValue } }
    }

    private fun entries(store: FakeStore) = listOf(
        ResetEntry("vibrate", label, false, store.prefs["vibrate"] as? Boolean ?: false),
        ResetEntry("night", label, 2, store.prefs["night"] as? Int ?: 2)
    )

    @Test
    fun `nothing to reset reports already default and writes nothing`() {
        val store = FakeStore(mutableMapOf("vibrate" to false))
        val flow = ResetFlow(store)

        assertNull(flow.prepare(entries(store)))
        assertEquals(mutableMapOf<String, Any?>("vibrate" to false), store.prefs)
        assertFalse(flow.canUndo)
    }

    @Test
    fun `confirm applies defaults and undo restores values including absent keys`() {
        val store = FakeStore(mutableMapOf("vibrate" to true))
        val flow = ResetFlow(store)
        val plan = requireNotNull(flow.prepare(entries(store)))

        flow.confirm(plan, entries(store))

        assertEquals(false, store.prefs["vibrate"])
        assertEquals(2, store.prefs["night"])
        assertTrue(flow.canUndo)

        assertTrue(flow.undo())

        assertEquals(true, store.prefs["vibrate"])
        assertFalse("night was absent before the reset", store.prefs.containsKey("night"))
        assertFalse(flow.canUndo)
    }

    @Test
    fun `undo works only once`() {
        val store = FakeStore(mutableMapOf("vibrate" to true))
        val flow = ResetFlow(store)
        flow.confirm(requireNotNull(flow.prepare(entries(store))), entries(store))

        assertTrue(flow.undo())
        store.prefs["vibrate"] = false

        assertFalse(flow.undo())
        assertEquals(false, store.prefs["vibrate"])
    }

    @Test
    fun `a new reset replaces the previous undo`() {
        val store = FakeStore(mutableMapOf("vibrate" to true))
        val flow = ResetFlow(store)
        flow.confirm(requireNotNull(flow.prepare(entries(store))), entries(store))
        store.prefs["vibrate"] = true
        store.prefs["night"] = 1

        flow.confirm(requireNotNull(flow.prepare(entries(store))), entries(store))
        assertTrue(flow.undo())

        assertEquals(true, store.prefs["vibrate"])
        assertEquals(1, store.prefs["night"])
    }

    @Test
    fun `a failed restore keeps the undo available`() {
        val store = FakeStore(mutableMapOf("vibrate" to true))
        val flow = ResetFlow(store)
        flow.confirm(requireNotNull(flow.prepare(entries(store))), entries(store))
        store.failRestore = true

        assertFalse(flow.undo())
        assertTrue(flow.canUndo)

        store.failRestore = false
        assertTrue(flow.undo())
        assertEquals(true, store.prefs["vibrate"])
    }

    @Test
    fun `clear drops the pending undo`() {
        val store = FakeStore(mutableMapOf("vibrate" to true))
        val flow = ResetFlow(store)
        flow.confirm(requireNotNull(flow.prepare(entries(store))), entries(store))

        flow.clear()

        assertFalse(flow.canUndo)
        assertFalse(flow.undo())
        assertEquals(false, store.prefs["vibrate"])
    }
}
