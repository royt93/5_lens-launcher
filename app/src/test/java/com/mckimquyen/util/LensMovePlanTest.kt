package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensMovePlanTest {
    private val id = "com.chat-Main"
    private val other = "com.mail-Main"

    private fun state(scope: LensAppScope, selection: Set<String> = emptySet(), hidden: Set<String> = emptySet()) =
        LensState(scope, selection, hidden)

    @Test
    fun `selected to selected moves the id`() {
        val (src, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.SELECTED, setOf(other)))
        )
        assertEquals(state(LensAppScope.SELECTED, setOf(other)), src)
        assertEquals(state(LensAppScope.SELECTED, setOf(other, id)), dst)
    }

    @Test
    fun `selected source left empty becomes all so the lens is never blank`() {
        val (src, _) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id)), state(LensAppScope.SELECTED, setOf(other)))
        )
        assertEquals(LensAppScope.ALL, src.scope)
        assertEquals(emptySet<String>(), src.selection)
    }

    @Test
    fun `all source hides the app and stays all`() {
        val (src, _) = requireNotNull(LensMovePlan.plan(id, state(LensAppScope.ALL), state(LensAppScope.SELECTED, setOf(other))))
        assertEquals(LensAppScope.ALL, src.scope)
        assertEquals(setOf(id), src.hidden)
    }

    @Test
    fun `all target keeps its scope and selection`() {
        val (_, dst) = requireNotNull(LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.ALL)))
        assertEquals(LensAppScope.ALL, dst.scope)
        assertEquals(emptySet<String>(), dst.selection)
    }

    @Test
    fun `moving clears the id from the target hidden set`() {
        val (_, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.ALL), state(LensAppScope.ALL, hidden = setOf(id, other)))
        )
        assertEquals(setOf(other), dst.hidden)
    }

    @Test
    fun `moving clears the id from a selected target hidden set too`() {
        val (_, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.ALL), state(LensAppScope.SELECTED, setOf(other), hidden = setOf(id, other)))
        )
        assertEquals(setOf(other), dst.hidden)
        assertEquals(setOf(other, id), dst.selection)
    }

    @Test
    fun `an id already in the target selection is not duplicated`() {
        val (_, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.SELECTED, setOf(id)))
        )
        assertEquals(setOf(id), dst.selection)
    }

    @Test
    fun `source hidden entries and other ids are preserved`() {
        val (src, _) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.ALL, hidden = setOf(other)), state(LensAppScope.ALL))
        )
        assertEquals(setOf(other, id), src.hidden)
    }

    @Test
    fun `a blank app id is a no-op`() {
        assertNull(LensMovePlan.plan("", state(LensAppScope.ALL), state(LensAppScope.ALL)))
    }
}
