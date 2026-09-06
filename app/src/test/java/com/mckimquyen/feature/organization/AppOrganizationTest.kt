package com.mckimquyen.feature.organization

import com.mckimquyen.enums.SortType
import com.mckimquyen.model.App
import com.mckimquyen.model.AppOrganizationRules
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.UtilAppSorter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppOrganizationTest {
    @Test
    fun `folder names are normalized and bounded`() {
        assertEquals("Work", AppOrganizationRules.normalizeFolder("  Wo\u0000rk  "))
        assertEquals(null, AppOrganizationRules.normalizeFolder("   "))
        assertEquals(
            AppOrganizationRules.MAX_FOLDER_LENGTH,
            AppOrganizationRules.normalizeFolder("x".repeat(100))!!.length
        )
        val emojiFolder = AppOrganizationRules.normalizeFolder("😀".repeat(100))!!
        assertEquals(AppOrganizationRules.MAX_FOLDER_LENGTH, emojiFolder.codePointCount(0, emojiFolder.length))
        assertFalse(Character.isHighSurrogate(emojiFolder.last()))
    }

    @Test
    fun `approved zones override base sort while preserving order inside a zone`() {
        val apps = arrayListOf(
            app("alpha", zone = PinnedZone.END),
            app("beta"),
            app("charlie", favorite = true),
            app("delta", zone = PinnedZone.START)
        )

        UtilAppSorter.sort(apps, SortType.LABEL_ASCENDING)

        assertEquals(listOf("delta", "charlie", "beta", "alpha"), apps.map { it.label })
    }

    @Test
    fun `pin favorite explicit order and folder tie breaks are deterministic`() {
        val apps = arrayListOf(
            app("end", zone = PinnedZone.END),
            app("normal", folder = "zeta").copy(orderNumber = 0),
            app("favorite-later", favorite = true).copy(orderNumber = 8),
            app("favorite-first", favorite = true).copy(orderNumber = 2),
            app("start", zone = PinnedZone.START),
            app("folder-b", folder = "Beta"),
            app("folder-a", folder = "alpha")
        )

        UtilAppSorter.sort(apps, SortType.LABEL_DESCENDING)

        assertEquals(
            listOf("start", "favorite-first", "favorite-later", "normal", "folder-a", "folder-b", "end"),
            apps.map { it.label }
        )
    }

    @Test
    fun `stored pinned zones accept known values and default safely`() {
        assertEquals(PinnedZone.START, PinnedZone.fromStored("START"))
        assertEquals(PinnedZone.NONE, PinnedZone.fromStored("NONE"))
        assertEquals(PinnedZone.END, PinnedZone.fromStored("END"))
        assertEquals(PinnedZone.NONE, PinnedZone.fromStored("invalid"))
        assertEquals(PinnedZone.NONE, PinnedZone.fromStored(null))
    }

    private fun app(
        label: String,
        favorite: Boolean = false,
        folder: String? = null,
        zone: PinnedZone = PinnedZone.NONE
    ) = App(
        label = label,
        packageName = "com.example.$label",
        name = "$label.Main",
        isFavorite = favorite,
        folderName = folder,
        pinnedZone = zone
    )
}
