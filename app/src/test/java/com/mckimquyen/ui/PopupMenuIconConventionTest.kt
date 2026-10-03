package com.mckimquyen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI-025: the icon popups mixed items with an icon and items without one, so the text column was
 * ragged and the two menus looked unrelated. `setForceShowIcon(true)` only reserves the icon slot;
 * it never forces an item to have one. This guard fails when a popup menu resource gains an item
 * without `android:icon`. The lens-management menu is built in code, so the device widget test
 * covers it instead.
 */
class PopupMenuIconConventionTest {

    private val popupMenuFiles = listOf("menu_app.xml", "menu_search_result.xml")

    private val itemTag = Regex("""<item\b[^>]*>""", RegexOption.DOT_MATCHES_ALL)

    private fun menuDir(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res/menu"),
            File(workingDirectory, "app/src/main/res/menu")
        ).first { it.isDirectory }
    }

    @Test
    fun `the scan really finds the popup menu items`() {
        val items = popupMenuFiles.sumOf { itemTag.findAll(File(menuDir(), it).readText()).count() }
        assertTrue("expected at least the known popup items, found $items", items >= 10)
    }

    @Test
    fun `every popup menu item declares an icon`() {
        val offenders = mutableListOf<String>()
        for (name in popupMenuFiles) {
            for (match in itemTag.findAll(File(menuDir(), name).readText())) {
                if (!match.value.contains("android:icon=")) {
                    val id = Regex("""android:id="@\+id/(\w+)"""").find(match.value)?.groupValues?.get(1)
                    offenders += "$name: $id has no android:icon"
                }
            }
        }
        assertEquals("mixed icon/no-icon popup items render a ragged text column: $offenders", emptyList<String>(), offenders)
    }

    /**
     * A plain `?attr/colorOnSurface` tint never dims, so a disabled item kept a full-contrast icon
     * beside greyed text. The shared selector dims the icon with the item.
     */
    @Test
    fun `every popup item tints its icon with the shared disabled-aware selector`() {
        val offenders = mutableListOf<String>()
        for (name in popupMenuFiles) {
            for (match in itemTag.findAll(File(menuDir(), name).readText())) {
                if (!match.value.contains("app:iconTint=\"@color/popup_menu_icon_tint\"")) {
                    val id = Regex("""android:id="@\+id/(\w+)"""").find(match.value)?.groupValues?.get(1)
                    offenders += "$name: $id"
                }
            }
        }
        assertEquals("popup icons must use @color/popup_menu_icon_tint: $offenders", emptyList<String>(), offenders)
    }

    /** Move earlier/later flip with the grid in RTL, so their arrows must flip too. */
    @Test
    fun `directional popup icons mirror in right-to-left layouts`() {
        val drawableDir = File(menuDir().parentFile, "drawable")
        val notMirrored = listOf("ic_arrow_back_24dp.xml", "ic_arrow_forward_24dp.xml")
            .filterNot { File(drawableDir, it).readText().contains("android:autoMirrored=\"true\"") }
        assertEquals("these icons need android:autoMirrored=\"true\"", emptyList<String>(), notMirrored)
    }
}
