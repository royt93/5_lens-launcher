package com.mckimquyen.search

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppsPanelResolverTest {

    private val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
    private val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")

    @Test
    fun emptyRecentKeys_returnsEmptyList() {
        assertTrue(RecentAppsPanelResolver.resolve(emptyList(), listOf(camera, notes)).isEmpty())
    }

    @Test
    fun resolvesInRecentKeysOrder_notSnapshotOrder() {
        val keys = listOf(AppSearchEngine.componentKey(notes), AppSearchEngine.componentKey(camera))
        assertEquals(listOf(notes, camera), RecentAppsPanelResolver.resolve(keys, listOf(camera, notes)))
    }

    @Test
    fun keyMissingFromSnapshot_isDroppedNotCrashed() {
        val keys = listOf(
            AppSearchEngine.componentKey(camera),
            "pkg.uninstalled/UninstalledActivity",
            AppSearchEngine.componentKey(notes)
        )
        assertEquals(listOf(camera, notes), RecentAppsPanelResolver.resolve(keys, listOf(camera, notes)))
    }

    // SEARCH-011: an app the user hid (isVisible=false) must not leak through the recent panel.
    @Test
    fun hiddenApp_isDroppedEvenWhenItsKeyIsRecent() {
        val hidden = camera.copy(isVisible = false)
        val keys = listOf(AppSearchEngine.componentKey(hidden), AppSearchEngine.componentKey(notes))

        assertEquals(listOf(notes), RecentAppsPanelResolver.resolve(keys, listOf(hidden, notes)))
    }

    @Test
    fun everyRecentAppHidden_returnsEmptyList() {
        val a = camera.copy(isVisible = false)
        val b = notes.copy(isVisible = false)
        val keys = listOf(AppSearchEngine.componentKey(a), AppSearchEngine.componentKey(b))

        assertTrue(RecentAppsPanelResolver.resolve(keys, listOf(a, b)).isEmpty())
    }

    @Test
    fun hidingAnAppRemovesOnlyThatRow_orderOfTheRestIsKept() {
        val maps = App(label = "Maps", packageName = "pkg.maps", name = "MapsActivity")
        val keys = listOf(maps, notes, camera).map(AppSearchEngine::componentKey)

        val before = RecentAppsPanelResolver.resolve(keys, listOf(camera, notes, maps))
        val after = RecentAppsPanelResolver.resolve(keys, listOf(camera, notes.copy(isVisible = false), maps))

        assertEquals(listOf(maps, notes, camera), before)
        assertEquals(listOf(maps, camera), after)
    }

    @Test
    fun unhidingAnApp_bringsItBackInItsOriginalRecentPosition() {
        val hidden = notes.copy(isVisible = false)
        val keys = listOf(notes, camera).map(AppSearchEngine::componentKey)

        assertEquals(listOf(camera), RecentAppsPanelResolver.resolve(keys, listOf(camera, hidden)))
        assertEquals(listOf(notes, camera), RecentAppsPanelResolver.resolve(keys, listOf(camera, hidden.copy(isVisible = true))))
    }

    @Test
    fun visibleAppsKeepTheirIdentity_noCopyIsMadeForThem() {
        val keys = listOf(AppSearchEngine.componentKey(camera))
        val resolved = RecentAppsPanelResolver.resolve(keys, listOf(camera, notes.copy(isVisible = false)))

        org.junit.Assert.assertSame(camera, resolved.single())
    }

    @Test
    fun emptySnapshot_returnsEmptyListRegardlessOfKeys() {
        assertTrue(
            RecentAppsPanelResolver.resolve(listOf(AppSearchEngine.componentKey(camera)), emptyList()).isEmpty()
        )
    }
}
