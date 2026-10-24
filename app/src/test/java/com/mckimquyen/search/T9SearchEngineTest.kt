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
        assertEquals("", T9SearchEngine.toT9Digits("-"))
    }


    @Test
    fun unsupportedScriptsDoNotCollapseToDigitOne() {
        listOf("微信", "Яндекс", "ไลน์", "واتساب", "देव").forEach { label ->
            assertEquals(label, "", T9SearchEngine.toT9Digits(label))
        }
        assertEquals("9324280", T9SearchEngine.toT9Digits("WeChat 微信"))
        assertEquals("54630", T9SearchEngine.toT9Digits("LINE ไลน์"))
    }

    @Test
    fun nonLatinLabelsFallBackToLatinPackageAliases() {
        val wechat = App(label = "微信", packageName = "com.tencent.mm", name = "Main")
        val yandex = App(label = "Яндекс", packageName = "ru.yandex.searchplugin", name = "Main")
        val line = App(label = "ไลน์", packageName = "com.linecorp.line", name = "Main")
        val whatsapp = App(label = "واتساب", packageName = "com.whatsapp", name = "Main")

        assertEquals(listOf(wechat), T9SearchEngine.search(listOf(wechat, yandex, line, whatsapp), "836"))
        assertEquals(listOf(yandex), T9SearchEngine.search(listOf(wechat, yandex, line, whatsapp), "926"))
        assertEquals(listOf(line), T9SearchEngine.search(listOf(wechat, yandex, line, whatsapp), "5463"))
        assertEquals(listOf(whatsapp), T9SearchEngine.search(listOf(wechat, yandex, line, whatsapp), "9428"))
    }

    @Test
    fun latinLabelMatchRanksBeforePackageAliasMatch() {
        val packageAliasHit = App(label = "微信", packageName = "com.chrome.fake", name = "Main")
        val labelHit = App(label = "Chrome", packageName = "com.example.other", name = "Main")

        assertEquals(listOf(labelHit, packageAliasHit), T9SearchEngine.search(listOf(packageAliasHit, labelHit), "247"))
    }

    @Test
    fun queryOneMatchesOnlyAnActualDigitOneNotUnsupportedCharacters() {
        val nonLatin = App(label = "微信", packageName = "com.wechat", name = "Main")
        val actualDigit = App(label = "App 1", packageName = "com.example.one", name = "Main")

        assertEquals(listOf(actualDigit), T9SearchEngine.search(listOf(nonLatin, actualDigit), "1"))
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
