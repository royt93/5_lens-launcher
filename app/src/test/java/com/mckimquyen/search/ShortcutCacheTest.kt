package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SEARCH-014: the per-package shortcut cache must stay bounded instead of growing with every search. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ShortcutCacheTest {
    private fun shortcuts(pkg: String) = listOf(QuickShortcut("id-$pkg", pkg, "label-$pkg", null))

    @Test
    fun returnsWhatWasStored() {
        val cache = ShortcutCache(maxPackages = 3)
        val stored = shortcuts("a")
        cache.put("a", stored)
        assertSame(stored, cache.get("a"))
        assertNull(cache.get("missing"))
    }

    @Test
    fun emptyShortcutListIsCachedSoAppsWithoutShortcutsAreNotRequeriedEveryBind() {
        val cache = ShortcutCache(maxPackages = 3)
        cache.put("none", emptyList())
        assertNotNull("an empty result is a valid cached answer", cache.get("none"))
    }

    @Test
    fun exceedingTheLimitEvictsTheLeastRecentlyUsedPackage() {
        val cache = ShortcutCache(maxPackages = 3)
        cache.put("a", shortcuts("a"))
        cache.put("b", shortcuts("b"))
        cache.put("c", shortcuts("c"))
        cache.get("a") // "a" becomes most recent, so "b" is now the oldest
        cache.put("d", shortcuts("d"))

        assertEquals(3, cache.size())
        assertNull("the least recently used entry must be evicted", cache.get("b"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("c"))
        assertNotNull(cache.get("d"))
    }

    @Test
    fun neverHoldsMoreThanTheLimitHoweverManyPackagesAreSearched() {
        val cache = ShortcutCache(maxPackages = 5)
        repeat(100) { cache.put("pkg$it", shortcuts("pkg$it")) }
        assertEquals(5, cache.size())
    }

    @Test
    fun clearDropsEveryEntry() {
        val cache = ShortcutCache(maxPackages = 5)
        repeat(4) { cache.put("pkg$it", shortcuts("pkg$it")) }
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("pkg0"))
    }

    @Test
    fun trimKeepsOnlyTheRequestedFractionOfTheMostRecentEntries() {
        val cache = ShortcutCache(maxPackages = 8)
        repeat(8) { cache.put("pkg$it", shortcuts("pkg$it")) }

        cache.trimToFraction(0.5f)

        assertEquals(4, cache.size())
        assertNull("oldest entries go first", cache.get("pkg0"))
        assertNotNull("newest entries stay", cache.get("pkg7"))
    }

    @Test
    fun trimToZeroOrBelowClearsAndTrimToOneKeepsEverything() {
        val cache = ShortcutCache(maxPackages = 4)
        repeat(4) { cache.put("pkg$it", shortcuts("pkg$it")) }

        cache.trimToFraction(1f)
        assertEquals(4, cache.size())

        cache.trimToFraction(0f)
        assertEquals(0, cache.size())
    }
}
