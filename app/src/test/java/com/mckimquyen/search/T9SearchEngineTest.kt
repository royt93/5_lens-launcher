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

    @Test
    fun `recent history orders apps within the same t9 match tier`() {
        val recent = App(label = "Chrome", packageName = "com.chrome.recent", name = "Main")
        val older = App(label = "Chrome", packageName = "com.chrome.older", name = "Main")
        val keys = listOf(AppSearchEngine.componentKey(recent), AppSearchEngine.componentKey(older))

        assertEquals(
            listOf(recent, older),
            T9SearchEngine.search(listOf(older, recent), "247", recentKeys = keys)
        )
    }

    @Test
    fun `favorite outranks recency and stronger match tier outranks recent history`() {
        val recentSubstring = App(label = "Monochrome", packageName = "com.mono", name = "Main")
        val prefix = App(label = "Chrome", packageName = "com.chrome", name = "Main")
        val recentNonFavorite = App(label = "Chrome", packageName = "com.chrome.recent", name = "Main")
        val favorite = App(label = "Chrome", packageName = "com.chrome.favorite", name = "Main", isFavorite = true)
        val keys = listOf(AppSearchEngine.componentKey(recentSubstring), AppSearchEngine.componentKey(recentNonFavorite))

        assertEquals(
            listOf(favorite, recentNonFavorite, prefix, recentSubstring),
            T9SearchEngine.search(listOf(recentSubstring, prefix, recentNonFavorite, favorite), "247", recentKeys = keys)
        )
    }

    @Test
    fun `empty history keeps the existing t9 ordering`() {
        val lowOpen = App(label = "Chrome", packageName = "com.chrome.low", name = "Main", openCount = 1)
        val highOpen = App(label = "Chrome", packageName = "com.chrome.high", name = "Main", openCount = 9)

        assertEquals(listOf(highOpen, lowOpen), T9SearchEngine.search(listOf(lowOpen, highOpen), "247"))
    }

    @Test
    fun `results are capped after ranking and a non-positive limit returns empty`() {
        val apps = (0 until 40).map { index ->
            App(label = "Chrome", packageName = "com.chrome.app$index", name = "Main", openCount = index.toLong())
        }

        val capped = T9SearchEngine.search(apps, "247", limit = 7)
        assertEquals(7, capped.size)
        assertEquals(apps.sortedByDescending(App::openCount).take(7), capped)
        assertTrue(T9SearchEngine.search(apps, "247", limit = 0).isEmpty())
        assertTrue(T9SearchEngine.search(apps, "247", limit = -1).isEmpty())
    }

    @Test
    fun `default limit is the shared text search limit`() {
        val apps = (0 until 40).map { index ->
            App(label = "Chrome", packageName = "com.chrome.app$index", name = "Main")
        }

        assertEquals(AppSearchEngine.SEARCH_RESULT_LIMIT, T9SearchEngine.search(apps, "247").size)
    }

    @Test
    fun `alias matches are capped by the same shared result limit`() {
        val apps = (0 until 30).map { index ->
            App(label = "微信", packageName = "com.chrome.alias$index", name = "Main", openCount = index.toLong())
        }

        assertEquals(AppSearchEngine.SEARCH_RESULT_LIMIT, T9SearchEngine.search(apps, "247").size)
    }

    @Test
    fun `work profile history does not rank the personal copy as recent`() {
        val personal = App(label = "Chrome", packageName = "com.chrome", name = "Main", userId = 0)
        val work = App(label = "Chrome", packageName = "com.chrome", name = "Main", userId = 10, isWorkProfile = true)
        val keys = listOf(AppSearchEngine.componentKey(work))

        assertEquals(listOf(work, personal), T9SearchEngine.search(listOf(personal, work), "247", recentKeys = keys))
    }

    @Test
    fun `an earlier recent position beats a later one`() {
        val first = App(label = "Chrome", packageName = "com.chrome.a", name = "Main")
        val second = App(label = "Chrome", packageName = "com.chrome.b", name = "Main")
        val keys = listOf(AppSearchEngine.componentKey(first), AppSearchEngine.componentKey(second))

        assertEquals(listOf(first, second), T9SearchEngine.search(listOf(second, first), "247", recentKeys = keys))
    }
}
