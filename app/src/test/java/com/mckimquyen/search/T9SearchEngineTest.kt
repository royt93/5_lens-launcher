package com.mckimquyen.search

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SearchEngineTest {

    @Test
    fun toT9Digits_mapsStandardLatinAndVietnamese() {
        assertEquals("247663", T9SearchEngine.toT9Digits("Chrome"))
        assertEquals("3664", T9SearchEngine.toT9Digits("Đồng"))
        assertEquals("4664530247663", T9SearchEngine.toT9Digits("Google Chrome"))
        assertEquals("22233344455566677778889999", T9SearchEngine.toT9Digits("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("202", T9SearchEngine.toT9Digits("A B"))
        assertEquals("1", T9SearchEngine.toT9Digits("-"))
    }

    @Test
    fun search_matchesPrefixBeforeWordPrefixBeforeSubstring() {
        val appChrome = App(label = "Chrome", packageName = "com.android.chrome", name = "Main")
        val appGoogleChrome = App(label = "Google Chrome", packageName = "com.google.chrome", name = "Main")
        val appMonochrome = App(label = "Monochrome", packageName = "com.test.mono", name = "Main")
        val appOther = App(label = "YouTube", packageName = "com.google.yt", name = "Main")

        val candidates = listOf(appOther, appMonochrome, appGoogleChrome, appChrome)

        // Query "247" maps to "chr"
        val results = T9SearchEngine.search(candidates, "247")

        assertEquals(3, results.size)
        assertEquals("Chrome", results[0].label) // Prefix match
        assertEquals("Google Chrome", results[1].label) // Word prefix match
        assertEquals("Monochrome", results[2].label) // Substring match
    }

    @Test
    fun search_blankQueryReturnsEmptyList() {
        val app = App(label = "Chrome", packageName = "com.android.chrome", name = "Main")
        assertTrue(T9SearchEngine.search(listOf(app), "").isEmpty())
        assertTrue(T9SearchEngine.search(listOf(app), null).isEmpty())
    }
}
