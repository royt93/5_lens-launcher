package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensLabelResolverTest {

    private val defaultLens = LensWorkspace(id = "default", name = "Lens 1", orderIndex = 0)
    private val workLens = LensWorkspace(id = "work", name = "Work", orderIndex = 1)
    private val travelLens = LensWorkspace(id = "travel", name = "Travel", orderIndex = 2)

    @Test
    fun emptyList_returnsNull() {
        assertNull(LensLabelResolver.resolveActiveLensName(emptyList(), "work"))
    }

    @Test
    fun matchingActiveId_returnsThatLensName() {
        val lenses = listOf(defaultLens, workLens, travelLens)
        assertEquals("Work", LensLabelResolver.resolveActiveLensName(lenses, "work"))
        assertEquals("Travel", LensLabelResolver.resolveActiveLensName(lenses, "travel"))
    }

    @Test
    fun nullActiveId_fallsBackToFirstLensName() {
        val lenses = listOf(defaultLens, workLens)
        assertEquals("Lens 1", LensLabelResolver.resolveActiveLensName(lenses, null))
    }

    @Test
    fun nonMatchingActiveId_fallsBackToFirstLensName() {
        val lenses = listOf(defaultLens, workLens)
        assertEquals("Lens 1", LensLabelResolver.resolveActiveLensName(lenses, "non-existent-id"))
    }
}
