package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensGroupPolicyTest {
    private fun lens(
        id: String,
        order: Int,
        created: Long,
        group: String?
    ) = LensWorkspace(id = id, name = id, orderIndex = order, createdAt = created, groupName = group)

    private fun backupLens(id: String, order: Int, group: String?) = LayoutBackupLens(
        id = id,
        name = id,
        orderIndex = order,
        scope = LensAppScope.ALL,
        selectedAppIds = emptySet(),
        frozen = false,
        entries = emptyList(),
        groupName = group
    )

    @Test
    fun normalizeName_trimsAndRejectsBlank() {
        assertEquals("Work", LensGroupPolicy.normalizeName("  Work  "))
        assertNull(LensGroupPolicy.normalizeName("  \t "))
    }

    @Test
    fun canonicalKey_trimsAndCaseFoldsWithRootLocale() {
        assertEquals("work", LensGroupPolicy.canonicalKey("  WoRk "))
        assertEquals("i", LensGroupPolicy.canonicalKey("I"))
    }

    @Test
    fun canonicalize_usesFirstSpellingByRoomOrderAndPreservesInputOrder() {
        val later = lens("later", order = 2, created = 20, group = "work")
        val first = lens("first", order = 1, created = 10, group = "  Work  ")
        val ungrouped = lens("none", order = 3, created = 30, group = null)

        val result = LensGroupPolicy.canonicalize(listOf(later, first, ungrouped))

        assertEquals(listOf("later", "first", "none"), result.map { it.id })
        assertEquals(listOf("Work", "Work", null), result.map { it.groupName })
        assertEquals(listOf(later.name, first.name, ungrouped.name), result.map { it.name })
        assertEquals(listOf(2, 1, 3), result.map { it.orderIndex })
    }

    @Test
    fun canonicalizeBackup_usesFirstSpellingByOrderIndexThenInputIndexAndPreservesOtherFields() {
        val second = backupLens("second", order = 1, group = "work")
        val first = backupLens("first", order = 0, group = " Work ")
        val tied = backupLens("tied", order = 1, group = "WORK")

        val result = LensGroupPolicy.canonicalizeBackup(listOf(second, first, tied))

        assertEquals(listOf("second", "first", "tied"), result.map { it.id })
        assertEquals(listOf("Work", "Work", "Work"), result.map { it.groupName })
        assertEquals(second.copy(groupName = "Work"), result[0])
        assertEquals(first.copy(groupName = "Work"), result[1])
        assertEquals(tied.copy(groupName = "Work"), result[2])
    }

    @Test
    fun canonicalizeBackup_inputIndexBreaksOrderTie() {
        val firstInput = backupLens("a", order = 1, group = " Team ")
        val secondInput = backupLens("b", order = 1, group = "team")

        val result = LensGroupPolicy.canonicalizeBackup(listOf(firstInput, secondInput))

        assertEquals(listOf("Team", "Team"), result.map { it.groupName })
    }

    @Test
    fun sections_putUngroupedFirstThenGroupsByFirstAppearanceInSortedOrder() {
        val travel = lens("travel", order = 3, created = 30, group = "Travel")
        val work2 = lens("work2", order = 2, created = 20, group = "work")
        val none = lens("none", order = 1, created = 10, group = null)
        val work1 = lens("work1", order = 0, created = 0, group = "Work")

        val sections = LensGroupPolicy.sections(listOf(travel, work2, none, work1))

        assertEquals(listOf(null, "Work", "Travel"), sections.map { it.groupName })
        assertEquals(listOf("none"), sections[0].lenses.map { it.id })
        assertEquals(listOf("work1", "work2"), sections[1].lenses.map { it.id })
        assertEquals(listOf("travel"), sections[2].lenses.map { it.id })
    }

    @Test
    fun sections_omitUngroupedSectionWhenEveryLensHasAGroup() {
        val sections = LensGroupPolicy.sections(listOf(lens("a", 0, 0, "A"), lens("b", 1, 1, "B")))

        assertEquals(listOf("A", "B"), sections.map { it.groupName })
    }

    @Test
    fun canonicalKey_isIndependentOfDefaultLocale() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("tr", "TR"))
            // Locale-sensitive lowercase would map "I" to dotless "ı" and split "INBOX"/"inbox".
            assertEquals(LensGroupPolicy.canonicalKey("inbox"), LensGroupPolicy.canonicalKey("INBOX"))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun canonicalize_createdAtBreaksOrderIndexTieForDisplaySpelling() {
        val newer = lens("newer", order = 1, created = 50, group = "work")
        val older = lens("older", order = 1, created = 10, group = "WORK")

        val result = LensGroupPolicy.canonicalize(listOf(newer, older))

        assertEquals(listOf("WORK", "WORK"), result.map { it.groupName })
    }

    @Test
    fun sections_dropGroupOnceItsLastLensIsUngrouped() {
        val before = LensGroupPolicy.sections(listOf(lens("a", 0, 0, "Solo"), lens("b", 1, 1, null)))
        assertEquals(listOf(null, "Solo"), before.map { it.groupName })

        val after = LensGroupPolicy.sections(listOf(lens("a", 0, 0, null), lens("b", 1, 1, null)))

        assertEquals(listOf<String?>(null), after.map { it.groupName })
        assertEquals(listOf("a", "b"), after.single().lenses.map { it.id })
    }

    @Test
    fun sections_ofNoLensesIsEmpty() {
        assertEquals(emptyList<Any>(), LensGroupPolicy.sections(emptyList()))
    }

    @Test
    fun canonicalizeCollapsedNames_ofEmptyInputIsEmpty() {
        assertEquals(emptySet<String>(), LensGroupPolicy.canonicalizeCollapsedNames(emptySet(), listOf(lens("a", 0, 0, "A"))))
    }

    @Test
    fun canonicalizeCollapsedNames_mapsToCanonicalSpellingAndDropsUnknownGroups() {
        val lenses = listOf(
            lens("first", 0, 0, "Work"),
            lens("second", 1, 1, "work"),
            lens("travel", 2, 2, "Travel")
        )

        val result = LensGroupPolicy.canonicalizeCollapsedNames(
            names = setOf(" WORK ", "work", "missing", " "),
            lenses = lenses
        )

        assertEquals(setOf("Work"), result)
    }
}
