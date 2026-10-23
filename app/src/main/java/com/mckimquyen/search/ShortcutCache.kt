package com.mckimquyen.search

import android.util.LruCache

/**
 * SEARCH-014: per-package app shortcuts for the search results list. Each entry can hold several
 * Drawables, and the adapter lives as long as ActHome, so an unbounded map grew with every package
 * ever searched. Sized in packages (not bytes): a shortcut list is a handful of small icons.
 */
class ShortcutCache(private val maxPackages: Int = DEFAULT_MAX_PACKAGES) {
    private val cache = LruCache<String, List<QuickShortcut>>(maxPackages)

    fun get(packageName: String): List<QuickShortcut>? = cache.get(packageName)

    fun put(packageName: String, shortcuts: List<QuickShortcut>) {
        cache.put(packageName, shortcuts)
    }

    fun size(): Int = cache.size()

    fun clear() = cache.evictAll()

    /** Keeps the most recently used [fraction] of the cache's capacity; used on memory pressure. */
    fun trimToFraction(fraction: Float) {
        cache.trimToSize((maxPackages * fraction.coerceIn(0f, 1f)).toInt())
    }

    companion object {
        /** Enough for the packages a user plausibly compares in one search session. */
        const val DEFAULT_MAX_PACKAGES = 30
    }
}
