package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit R2: the lens-share picker listed locked lenses and exported their icons and name without any unlock. */
class LensAccessPolicyShareableTest {

    private fun lens(id: String, order: Int) = LensWorkspace(id = id, name = "Lens $id", orderIndex = order)

    private val lenses = listOf(lens("default", 0), lens("work", 1), lens("private", 2))

    @Test fun lockedLensesAreNotOffered() {
        val offered = LensAccessPolicy.shareable(lenses, locked = setOf("private"))

        assertEquals(listOf("default", "work"), offered.map { it.id })
    }

    @Test fun withNoLocksEveryLensIsOfferedInOrder() {
        // Positive control: the filter is not simply returning less.
        assertEquals(listOf("default", "work", "private"), LensAccessPolicy.shareable(lenses, locked = emptySet()).map { it.id })
    }

    @Test fun aLockOnAnUnknownLensChangesNothing() {
        assertEquals(3, LensAccessPolicy.shareable(lenses, locked = setOf("deleted")).size)
    }

    @Test fun everyLensLockedOffersNothing() {
        assertEquals(emptyList<String>(), LensAccessPolicy.shareable(lenses, locked = lenses.map { it.id }.toSet()).map { it.id })
    }
}
