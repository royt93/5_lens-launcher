package com.mckimquyen.search

import android.icu.text.Transliterator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** SEARCH-018: reads a Han label as pinyin syllables so it can be found by typing Latin letters. */
interface PinyinProvider {
    /** Lowercase ASCII syllables of [label] in order, or an empty list when the label has no pinyin. */
    fun syllables(label: String): List<String>
}

/**
 * Pinyin from the platform's own ICU data (API 24+), so no dictionary ships with the app. Results are cached per label
 * because the first transliteration is slow. Any ICU failure means "no pinyin", never a failed search.
 */
object IcuPinyinProvider : PinyinProvider {
    private const val TRANSLITERATOR_ID = "Han-Latin; Latin-ASCII"
    private val WHITESPACE = "\\s+".toRegex()

    private val cache = ConcurrentHashMap<String, List<String>>()
    private val transliterator: Transliterator? by lazy {
        try {
            Transliterator.getInstance(TRANSLITERATOR_ID)
        } catch (error: RuntimeException) {
            null
        }
    }

    override fun syllables(label: String): List<String> {
        if (label.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return emptyList()
        return cache.getOrPut(label) { transliterate(label) }
    }

    private fun transliterate(label: String): List<String> {
        val engine = transliterator ?: return emptyList()
        return try {
            engine.transliterate(label).lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }
        } catch (error: RuntimeException) {
            emptyList()
        }
    }
}
