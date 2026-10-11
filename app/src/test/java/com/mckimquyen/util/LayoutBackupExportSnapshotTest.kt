package com.mckimquyen.util

import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutBackupExportSnapshotTest {
    @Test
    fun `builds v2 snapshot with all lens layouts and preferences`() {
        val snapshot = LayoutBackupSnapshotFactory.create(
            listOf(LensWorkspace("default", "Home", 0, 1L), LensWorkspace("work", "Work", 1, 2L)),
            listOf(
                AppPersistent(packageName = "com.a", name = "com.a.Main", identifier = "com.a-com.a.Main", lensId = "default"),
                AppPersistent(packageName = "com.b", name = "com.b.Main", identifier = "com.b-com.b.Main", lensId = "work", isFavorite = true)
            ),
            listOf(
                LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup("work", LensAppScope.SELECTED, setOf("com.b-com.b.Main"), true)
            ),
            7L, activeLensId = "work"
        )
        assertEquals(2, snapshot.schemaVersion)
        assertEquals("work", snapshot.activeLensId)
        assertEquals(listOf("default", "work"), snapshot.lenses.map { it.id })
        assertEquals(1, snapshot.lenses[0].entries.size)
        assertEquals(1, snapshot.lenses[1].entries.size)
        assertEquals(LensAppScope.SELECTED, snapshot.lenses[1].scope)
        assertTrue(snapshot.lenses[1].frozen)
        assertEquals(setOf("com.b-com.b.Main"), snapshot.lenses[1].selectedAppIds)
    }
}
