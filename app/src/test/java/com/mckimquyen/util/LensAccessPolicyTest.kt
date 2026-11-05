package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensAccessPolicyTest {
    private val lenses = listOf(
        LensWorkspace(id = "default", name = "Default", orderIndex = 0),
        LensWorkspace(id = "work", name = "Work", orderIndex = 1),
        LensWorkspace(id = "private", name = "Private", orderIndex = 2)
    )

    @Test
    fun canLockRefusesUnknownAlreadyLockedAndLastUnlockedLens() {
        assertFalse(LensAccessPolicy.canLock("missing", lenses, emptySet()))
        assertFalse(LensAccessPolicy.canLock("work", lenses, setOf("work")))
        assertFalse(LensAccessPolicy.canLock("private", lenses, setOf("default", "work")))
    }

    @Test
    fun canLockAllowsLockingWhenAnotherLensRemainsUnlocked() {
        assertTrue(LensAccessPolicy.canLock("work", lenses, emptySet()))
        assertTrue(LensAccessPolicy.canLock("private", lenses, setOf("work")))
    }

    @Test
    fun canDeleteRefusesLastUnlockedLensButAllowsDeletingLockedLens() {
        assertFalse(LensAccessPolicy.canDelete("default", lenses, setOf("work", "private")))
        assertTrue(LensAccessPolicy.canDelete("work", lenses, setOf("work")))
        assertFalse(LensAccessPolicy.canDelete("missing", lenses, emptySet()))
    }

    @Test
    fun firstUnlockedUsesWorkspaceOrderAndIgnoresStaleLockIds() {
        assertEquals("work", LensAccessPolicy.firstUnlocked(lenses, setOf("default", "stale")))
        assertNull(LensAccessPolicy.firstUnlocked(lenses, setOf("default", "work", "private", "stale")))
    }

    @Test
    fun resolveTargetAllowsOpenBlocksLockedAndRejectsUnknown() {
        assertEquals(LensTargetResolution(LensTargetDecision.ALLOWED, "work"),
            LensAccessPolicy.resolveTarget("work", lenses, setOf("private")))
        assertEquals(LensTargetResolution(LensTargetDecision.BLOCKED, null),
            LensAccessPolicy.resolveTarget("private", lenses, setOf("private")))
        assertEquals(LensTargetResolution(LensTargetDecision.BLOCKED, null),
            LensAccessPolicy.resolveTarget("missing", lenses, emptySet()))
    }

    @Test
    fun resolveActivePreservesUnlockedSavedLensOrFallsBackToFirstUnlocked() {
        assertEquals(LensTargetResolution(LensTargetDecision.ALLOWED, "work"),
            LensAccessPolicy.resolveActive("work", lenses, emptySet()))
        assertEquals(LensTargetResolution(LensTargetDecision.FALLBACK, "work"),
            LensAccessPolicy.resolveActive("private", lenses, setOf("default", "private")))
        assertEquals(LensTargetResolution(LensTargetDecision.FALLBACK, "default"),
            LensAccessPolicy.resolveActive("missing", lenses, emptySet()))
    }

    @Test
    fun emptyWorkspaceAndAllLockedWorkspaceFailClosed() {
        assertNull(LensAccessPolicy.firstUnlocked(emptyList(), emptySet()))
        assertEquals(LensTargetResolution(LensTargetDecision.BLOCKED, null),
            LensAccessPolicy.resolveActive("default", lenses, lenses.map { it.id }.toSet()))
    }

    @Test
    fun policyDoesNotMutateWorkspaceOrLockSet() {
        val lockSet = mutableSetOf("default")
        val workspace = lenses.toMutableList()
        LensAccessPolicy.resolveActive("default", workspace, lockSet)
        assertEquals(listOf("default", "work", "private"), workspace.map { it.id })
        assertEquals(setOf("default"), lockSet)
    }
}
