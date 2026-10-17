package com.mckimquyen.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterPresetTest {

    @Test
    fun fromStored_mapsCorrectEnumValues() {
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("ALL"))
        assertEquals(SearchFilterPreset.THIS_LENS, SearchFilterPreset.fromStored("THIS_LENS"))
        assertEquals(SearchFilterPreset.FAVORITES, SearchFilterPreset.fromStored("FAVORITES"))
        assertEquals(SearchFilterPreset.HIDDEN, SearchFilterPreset.fromStored("HIDDEN"))
    }

    @Test
    fun fromStored_fallsBackToAllOnNullOrUnknown() {
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored(null))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored(""))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("UNKNOWN_PRESET"))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("all"))
    }
}
