package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LensShortcutsTest {

    private fun lens(id: String, order: Int) = LensWorkspace(id = id, name = id, orderIndex = order)

    @Test
    fun `leaves room for the static shortcuts`() {
        val lenses = (0 until 6).map { lens("l$it", it) }
        val picked = LensShortcuts.pick(lenses, maxPerActivity = 5)
        assertEquals(5 - LensShortcuts.STATIC_SHORTCUT_COUNT, picked.size)
    }

    @Test
    fun `keeps lens order and ignores input order`() {
        val lenses = listOf(lens("c", 2), lens("a", 0), lens("b", 1))
        assertEquals(listOf("a", "b"), LensShortcuts.pick(lenses, maxPerActivity = 4).map { it.id })
    }

    @Test
    fun `a limit at or below the static count yields nothing`() {
        val lenses = listOf(lens("a", 0))
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(lenses, maxPerActivity = LensShortcuts.STATIC_SHORTCUT_COUNT))
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(lenses, maxPerActivity = 0))
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(lenses, maxPerActivity = -3))
    }

    @Test
    fun `fewer lenses than room returns them all`() {
        val lenses = listOf(lens("a", 0))
        assertEquals(lenses, LensShortcuts.pick(lenses, maxPerActivity = 10))
    }

    @Test
    fun `no lenses yields no shortcuts`() {
        assertEquals(emptyList<LensWorkspace>(), LensShortcuts.pick(emptyList(), maxPerActivity = 10))
    }

    @Test
    fun `the id round trips through the shortcut id`() {
        assertEquals("work-lens", LensShortcuts.lensIdFromShortcutId(LensShortcuts.shortcutIdFor("work-lens")))
    }

    @Test
    fun `an id that is not ours is not a lens shortcut id`() {
        assertNull(LensShortcuts.lensIdFromShortcutId("utilSettings"))
        assertNull(LensShortcuts.lensIdFromShortcutId(""))
        assertNull(LensShortcuts.lensIdFromShortcutId(LensShortcuts.SHORTCUT_ID_PREFIX))
    }

    @Test
    fun `STATIC_SHORTCUT_COUNT matches shortcuts xml`() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val xml = sequenceOf(
            File(workingDirectory, "src/main/res/xml/shortcuts.xml"),
            File(workingDirectory, "app/src/main/res/xml/shortcuts.xml")
        ).first { it.isFile }.readText()
        assertEquals(LensShortcuts.STATIC_SHORTCUT_COUNT, Regex("<shortcut\\b").findAll(xml).count())
    }
}
