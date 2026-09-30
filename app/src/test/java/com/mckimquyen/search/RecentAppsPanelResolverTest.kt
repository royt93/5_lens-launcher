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

    @Test
    fun emptySnapshot_returnsEmptyListRegardlessOfKeys() {
        assertTrue(
            RecentAppsPanelResolver.resolve(listOf(AppSearchEngine.componentKey(camera)), emptyList()).isEmpty()
        )
    }
}
