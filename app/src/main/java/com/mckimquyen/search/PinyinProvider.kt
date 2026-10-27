package com.mckimquyen.search

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** SEARCH-018: reads a Han label as pinyin syllables so it can be found by typing Latin letters. */
interface PinyinProvider {
    /** Lowercase ASCII syllables of [label] in order, or an empty list when the label has no pinyin. */
    fun syllables(label: String): List<String>
}

/**
 * Pinyin from the platform's own ICU data, so no dictionary ships with the app. `android.icu.text.Transliterator` only
 * exists from API 29, so on API 25-28 there is simply no pinyin matching. Results are cached per label because the
 * first transliteration is slow. Any ICU failure means "no pinyin", never a failed search.
 */
object IcuPinyinProvider : PinyinProvider {
    private const val TRANSLITERATOR_ID = "Han-Latin; Latin-ASCII"
    private val WHITESPACE = "\\s+".toRegex()

    private val cache = ConcurrentHashMap<String, List<String>>()

    @Volatile
    private var transliterator: Transliterator? = null

    override fun syllables(label: String): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        if (label.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return emptyList()
        return cache.getOrPut(label) { transliterate(label) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun transliterate(label: String): List<String> = try {
        val engine = transliterator ?: Transliterator.getInstance(TRANSLITERATOR_ID).also { transliterator = it }
        engine.transliterate(label).lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }
    } catch (error: RuntimeException) {
        emptyList()
    }
}
