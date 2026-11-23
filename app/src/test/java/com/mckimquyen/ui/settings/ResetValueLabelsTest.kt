package com.mckimquyen.ui.settings

import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reset preview shows the same words as the Settings screen, not enum constants or English that was built in code. */
class ResetValueLabelsTest {
    private val labels = ResetValueLabels(
        haptic = { "H:${it.name}" },
        sort = { "S:${it.name}" },
        background = { "B:${it.name}" }
    )

    @Test fun hapticIntensityUsesTheTranslatedWordForItsOrdinal() {
        assertEquals("H:LIGHT", labels.labelFor(UtilSettings.KEY_HAPTIC_INTENSITY, HapticIntensity.LIGHT.ordinal))
        assertEquals("H:MEDIUM", labels.labelFor(UtilSettings.KEY_HAPTIC_INTENSITY, HapticIntensity.MEDIUM.ordinal))
        assertEquals("H:STRONG", labels.labelFor(UtilSettings.KEY_HAPTIC_INTENSITY, HapticIntensity.STRONG.ordinal))
    }

    @Test fun sortTypeUsesTheLabelOfTheStoredEnumName() {
        SortType.entries.forEach { assertEquals("S:${it.name}", labels.labelFor(UtilSettings.KEY_SORT_TYPE_NAME, it.name)) }
    }

    @Test fun backgroundModeUsesTheLabelOfTheStoredEnumName() {
        BackgroundMode.entries.forEach { assertEquals("B:${it.name}", labels.labelFor(UtilSettings.KEY_BACKGROUND_MODE, it.name)) }
    }

    @Test fun anUnknownStoredNameOrNumberFallsThroughSoTheFormatterKeepsItsOwnText() {
        assertNull(labels.labelFor(UtilSettings.KEY_SORT_TYPE_NAME, "NOT_A_SORT_TYPE"))
        assertNull(labels.labelFor(UtilSettings.KEY_BACKGROUND_MODE, "NOT_A_MODE"))
        assertNull(labels.labelFor(UtilSettings.KEY_HAPTIC_INTENSITY, HapticIntensity.entries.size))
        assertNull(labels.labelFor(UtilSettings.KEY_HAPTIC_INTENSITY, -1))
    }

    @Test fun otherKeysAreNotTouched() {
        assertNull(labels.labelFor(UtilSettings.KEY_SEARCH_HINT_TEXT, "Find apps"))
        assertNull(labels.labelFor(UtilSettings.KEY_NIGHT_MODE, 2))
    }

    @Test fun theFormatterPrefersTheLabelAndKeepsItsOldBehaviourWhenThereIsNone() {
        val formatter = ResetValueFormatter(on = "On", off = "Off", default = "Default", ms = { "$it ms" }, labelFor = labels::labelFor)
        assertEquals("H:STRONG", formatter.format(UtilSettings.KEY_HAPTIC_INTENSITY, 2))
        assertEquals("S:LABEL_ASCENDING", formatter.format(UtilSettings.KEY_SORT_TYPE_NAME, "LABEL_ASCENDING"))
        assertEquals("B:COLOR", formatter.format(UtilSettings.KEY_BACKGROUND_MODE, "COLOR"))
        assertEquals("On", formatter.format("vibrate_app_hover", true))
        assertEquals("Default", formatter.format(UtilSettings.KEY_SEARCH_HINT_TEXT, ""))
        assertEquals("NOT_A_SORT_TYPE", formatter.format(UtilSettings.KEY_SORT_TYPE_NAME, "NOT_A_SORT_TYPE"))
    }
}
