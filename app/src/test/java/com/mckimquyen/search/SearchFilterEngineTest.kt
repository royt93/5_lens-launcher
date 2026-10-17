package com.mckimquyen.search

import com.mckimquyen.model.App
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SearchFilterEngineTest {

    private fun app(pkg: String, name: String = "Main", isFavorite: Boolean = false) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = name, isFavorite = isFavorite, isVisible = true)

    private val appA = app("com.example.a", isFavorite = true)
    private val appB = app("com.example.b", isFavorite = false)
    private val appC = app("com.example.c", isFavorite = true)
    private val allApps = listOf(appA, appB, appC)

    @Test
    fun filterCandidates_all_returnsAllApps() {
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.ALL,
            lensApps = listOf(appA),
            hiddenIds = setOf(LensAppScope.identifierOf(appB))
        )
        assertEquals(allApps, result)
        assertNotSame(allApps, result)
    }

    @Test
    fun filterCandidates_thisLens_returnsOnlyLensApps() {
        val lensApps = listOf(appA, appB)
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = lensApps,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appA, appB), result)
    }

    @Test
    fun filterCandidates_thisLens_nullOrEmptyLensAppsReturnsEmpty() {
        val resultNull = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(emptyList<App>(), resultNull)

        val resultEmpty = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = emptyList(),
            hiddenIds = emptySet()
        )
        assertEquals(emptyList<App>(), resultEmpty)
    }

    @Test
    fun filterCandidates_favorites_returnsOnlyFavoriteApps() {
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.FAVORITES,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appA, appC), result)
    }

    @Test
    fun filterCandidates_hidden_returnsOnlyHiddenIdsMatchingInstalled() {
        val hiddenIds = setOf(LensAppScope.identifierOf(appB), "com.orphan-Main")
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.HIDDEN,
            lensApps = null,
            hiddenIds = hiddenIds
        )
        assertEquals(listOf(appB), result)
    }

    @Test
    fun filterCandidates_preservesOrderOfInputApps() {
        val shuffled = listOf(appC, appA, appB)
        val result = SearchFilterEngine.filterCandidates(
            apps = shuffled,
            preset = SearchFilterPreset.FAVORITES,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appC, appA), result)
    }
}
