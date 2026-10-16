package com.mckimquyen.ui

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Test

class LensGroupRowsTest {
    private fun lens(id: String, group: String? = null, order: Int = 0) =
        LensWorkspace(id = id, name = id, orderIndex = order, createdAt = order.toLong(), groupName = group)

    @Test
    fun expandedGroupMakesHeaderTopAndLastLensBottom() {
        val rows = buildLensGroupRows(
            lenses = listOf(lens("a", "Work", 0), lens("b", "Work", 1), lens("c", "Work", 2)),
            collapsed = emptySet(), activeLensId = null
        )
        assertEquals(
            listOf(CardPosition.TOP, CardPosition.MIDDLE, CardPosition.MIDDLE, CardPosition.BOTTOM),
            rows.map { it.position }
        )
    }

    @Test
    fun collapsedGroupIsAFullyRoundedSingleCard() {
        val rows = buildLensGroupRows(
            lenses = listOf(lens("a", "Work", 0), lens("b", "Work", 1)),
            collapsed = setOf("Work"), activeLensId = null
        )
        assertEquals(listOf(CardPosition.ONLY), rows.map { it.position })
    }

    @Test
    fun groupWithOneLensIsTopThenBottom() {
        val rows = buildLensGroupRows(listOf(lens("a", "Solo", 0)), emptySet(), null)
        assertEquals(listOf(CardPosition.TOP, CardPosition.BOTTOM), rows.map { it.position })
    }

    @Test
    fun eachSectionRestartsItsCardPositions() {
        val rows = buildLensGroupRows(
            listOf(lens("loose", null, 0), lens("w1", "Work", 1), lens("w2", "Work", 2)),
            emptySet(), "loose"
        )
        assertEquals(
            listOf(CardPosition.TOP, CardPosition.BOTTOM, CardPosition.TOP, CardPosition.MIDDLE, CardPosition.BOTTOM),
            rows.map { it.position }
        )
    }

    @Test
    fun activeAndSelectionAreCarriedOnTheRowSoDiffCanSeeThem() {
        val rows = buildLensGroupRows(
            listOf(lens("a", null, 0), lens("b", null, 1)), emptySet(), "b",
            editing = true, selectedIds = setOf("a")
        ).filterIsInstance<LensGroupRow.Lens>()
        assertEquals(listOf(false, true), rows.map { it.active })
        assertEquals(listOf(true, false), rows.map { it.selected })
        assertEquals(listOf(true, true), rows.map { it.editing })
    }

    @Test
    fun diffTreatsSameLensIdAsSameItemAndSelectionChangeAsContentChange() {
        val before = buildLensGroupRows(listOf(lens("a", null, 0)), emptySet(), "a", editing = true, selectedIds = emptySet())
        val after = buildLensGroupRows(listOf(lens("a", null, 0)), emptySet(), "a", editing = true, selectedIds = setOf("a"))
        val diff = LensGroupRowDiff(before, after)
        val lensIndex = 1
        assertEquals(true, diff.areItemsTheSame(lensIndex, lensIndex))
        assertEquals(false, diff.areContentsTheSame(lensIndex, lensIndex))
        assertEquals(true, diff.areContentsTheSame(0, 0))
    }

    @Test
    fun cardPositionsResolveToSpecificMaterialDrawableResources() {
        assertEquals(com.mckimquyen.R.drawable.bg_lens_card_only, CardPosition.ONLY.backgroundRes())
        assertEquals(com.mckimquyen.R.drawable.bg_lens_card_top, CardPosition.TOP.backgroundRes())
        assertEquals(com.mckimquyen.R.drawable.bg_lens_card_middle, CardPosition.MIDDLE.backgroundRes())
        assertEquals(com.mckimquyen.R.drawable.bg_lens_card_bottom, CardPosition.BOTTOM.backgroundRes())
    }

    @Test
    fun headerIdentityIsGroupNameSoToggleDoesNotRecreateIt() {
        val open = buildLensGroupRows(listOf(lens("a", "Work", 0)), emptySet(), null)
        val closed = buildLensGroupRows(listOf(lens("a", "Work", 0)), setOf("Work"), null)
        val diff = LensGroupRowDiff(open, closed)
        assertEquals(true, diff.areItemsTheSame(0, 0))
        assertEquals(false, diff.areContentsTheSame(0, 0))
    }
}
