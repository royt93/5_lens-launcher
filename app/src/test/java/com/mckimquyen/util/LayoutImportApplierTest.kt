package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEAT-006: plan() is the "identity match, skip what's not installed" logic the story's
 * acceptance criteria require - pure given a backup and an installed-app list, so no Room/Android
 * dependency needed beyond what AppPersistent.generateIdentifier already is (a plain string join).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutImportApplierTest {

    private fun entry(pkg: String, name: String, order: Int = 0) = LayoutBackupEntry(
        packageName = pkg,
        name = name,
        orderNumber = order,
        appVisible = true,
        appOpened = true,
        isFavorite = false,
        folderName = null,
        pinnedZone = PinnedZone.NONE.name
    )

    private fun installedApp(pkg: String, name: String) = App(packageName = pkg, name = name)

    @Test
    fun `matches entries against installed apps by component identity, not display label`() {
        val backup = LayoutBackup(1, 0L, listOf(entry("com.a", "com.a.Main")))
        val installed = listOf(installedApp("com.a", "com.a.Main"))

        val plan = LayoutImportApplier.plan(backup, installed)

        assertEquals(1, plan.matched.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `skips entries for apps not currently installed instead of erroring`() {
        val backup = LayoutBackup(
            1, 0L,
            listOf(entry("com.a", "com.a.Main"), entry("com.uninstalled", "com.uninstalled.Main"))
        )
        val installed = listOf(installedApp("com.a", "com.a.Main"))

        val plan = LayoutImportApplier.plan(backup, installed)

        assertEquals(1, plan.matched.size)
        assertEquals(1, plan.skippedCount)
    }

    @Test
    fun `same package different component are not conflated`() {
        val backup = LayoutBackup(1, 0L, listOf(entry("com.a", "com.a.ActivityOne")))
        val installed = listOf(installedApp("com.a", "com.a.ActivityTwo"))

        val plan = LayoutImportApplier.plan(backup, installed)

        assertEquals(0, plan.matched.size)
        assertEquals(1, plan.skippedCount)
    }

    @Test
    fun `empty backup produces an empty plan, not a crash`() {
        val plan = LayoutImportApplier.plan(LayoutBackup(1, 0L, emptyList()), listOf(installedApp("com.a", "com.a.Main")))
        assertEquals(0, plan.matched.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `empty installed list skips every entry`() {
        val backup = LayoutBackup(1, 0L, listOf(entry("com.a", "com.a.Main"), entry("com.b", "com.b.Main")))
        val plan = LayoutImportApplier.plan(backup, emptyList())
        assertEquals(0, plan.matched.size)
        assertEquals(2, plan.skippedCount)
    }
}
