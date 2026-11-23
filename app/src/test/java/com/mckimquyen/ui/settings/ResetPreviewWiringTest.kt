package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reset preview prints the short word for an empty value ("Default"). ActSettings used to pass the whole sentence of the "already at default"
 * snackbar, so a row read "Search hint text: Find apps -> Already at default values". The formatter's own test hard-codes "Default", so it cannot
 * see how ActSettings builds it; this test reads that call site.
 */
class ResetPreviewWiringTest {
    private companion object {
        const val SNACKBAR_SENTENCE_KEY = "R.string.reset_already_default"
        const val SHORT_WORD_KEY = "R.string.setting_search_hint_default"
        const val FORMATTER_CALL = "new ResetValueFormatter("
        const val CALL_WINDOW = 520
        const val LABEL_SOURCE_ARGUMENT = "labels::labelFor"
        const val LABELS_CLASS = "ResetValueLabels("
    }

    private fun actSettingsSource(): String {
        val wd = File(requireNotNull(System.getProperty("user.dir")))
        val file = sequenceOf(File(wd, "src/main/java/com/mckimquyen/ui/ActSettings.java"), File(wd, "app/src/main/java/com/mckimquyen/ui/ActSettings.java")).first { it.isFile }
        return file.readText()
    }

    private fun formatterCall(): String {
        val src = actSettingsSource()
        val at = src.indexOf(FORMATTER_CALL)
        assertTrue("positive control: ActSettings builds a ResetValueFormatter", at >= 0)
        return src.substring(at, at + CALL_WINDOW)
    }

    @Test fun theEmptyValueWordIsTheShortDefaultNotTheSnackbarSentence() {
        val call = formatterCall()
        assertFalse("the reset preview must not print the snackbar sentence for an empty value: $call", call.contains(SNACKBAR_SENTENCE_KEY))
        assertTrue("the reset preview must use the short word for an empty value: $call", call.contains(SHORT_WORD_KEY))
    }

    @Test fun theFormatterReturnsExactlyTheWordItWasGiven() {
        val formatter = ResetValueFormatter(on = "On", off = "Off", default = "Default", ms = { "$it ms" })
        assertEquals("Default", formatter.format("search_hint_text", ""))
    }

    @Test fun theFormatterIsGivenTheSettingsScreenWordsForEnumValues() {
        val src = actSettingsSource()
        assertTrue("ActSettings must build the translated labels the reset preview uses", src.contains(LABELS_CLASS))
        assertTrue("the formatter call must pass the label source, or the preview prints enum constants again: ${formatterCall()}", formatterCall().contains(LABEL_SOURCE_ARGUMENT))
    }
}
