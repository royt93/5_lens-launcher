package com.mckimquyen.search

import com.mckimquyen.model.App
import java.text.Normalizer
import java.util.Locale

/** Pure, deterministic on-device app search and ranking. */
object AppSearchEngine {
    private const val DEFAULT_LIMIT = 24

    @JvmStatic
    fun componentKey(app: App): String = "${app.packageName}/${app.name}"

    @JvmStatic
    fun normalize(value: CharSequence?): String = Normalizer
        .normalize(value?.toString().orEmpty().replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(WHITESPACE, " ")

    @JvmStatic
    @JvmOverloads
    fun search(
        apps: List<App>,
        query: CharSequence?,
        recentKeys: List<String> = emptyList(),
        favoriteKeys: Set<String> = emptySet(),
        limit: Int = DEFAULT_LIMIT
    ): List<App> {
        if (limit <= 0) return emptyList()

        val visibleApps = apps.filter(App::isVisible)
        val normalizedQuery = normalize(query)
        val recentPositions = buildMap {
            recentKeys.forEachIndexed { index, key -> putIfAbsent(key, index) }
        }

        if (normalizedQuery.isEmpty()) {
            return visibleApps
                .filter { componentKey(it) in recentPositions || componentKey(it) in favoriteKeys }
                .sortedWith(
                    compareByDescending<App> { componentKey(it) in favoriteKeys }
                        .thenBy { recentPositions[componentKey(it)] ?: Int.MAX_VALUE }
                        .thenByDescending(App::openCount)
                        .thenBy { normalize(it.label) }
                        .thenBy { componentKey(it) }
                )
                .take(limit)
        }

        val tokens = normalizedQuery.split(WHITESPACE).filter(String::isNotEmpty)
        return visibleApps.mapNotNull { app ->
            val label = normalize(app.label)
            val packageName = normalize(app.packageName)
            val componentName = normalize(app.name)
            val packageAliases = packageName.split('.', '-', '_').filter(String::isNotEmpty)
            var textScore = 0
            for (token in tokens) {
                val tokenScore = when {
                    label == token -> 0
                    label.startsWith(token) -> 10
                    label.split(WHITESPACE).any { it.startsWith(token) } -> 20
                    label.contains(token) -> 30
                    packageAliases.any { it.startsWith(token) } -> 40
                    packageName.contains(token) -> 50
                    componentName.contains(token) -> 60
                    hasFuzzyMatch(label, token) -> 70
                    else -> return@mapNotNull null
                }
                textScore += tokenScore
            }

            RankedApp(
                app = app,
                textScore = textScore,
                favorite = componentKey(app) in favoriteKeys,
                recentPosition = recentPositions[componentKey(app)] ?: Int.MAX_VALUE
            )
        }.sortedWith(
            compareBy<RankedApp> { it.textScore }
                .thenByDescending { it.favorite }
                .thenBy { it.recentPosition }
                .thenByDescending { it.app.openCount }
                .thenBy { normalize(it.app.label) }
                .thenBy { componentKey(it.app) }
        ).take(limit).map(RankedApp::app)
    }

    private data class RankedApp(
        val app: App,
        val textScore: Int,
        val favorite: Boolean,
        val recentPosition: Int
    )

    // SEARCH-001: typo tolerance. Only attempted for tokens long enough that a 1-2 char edit
    // can't turn them into a nonsense match (e.g. "ab" fuzzy-matching half the app list).
    private const val MIN_FUZZY_TOKEN_LENGTH = 3
    private const val SHORT_TOKEN_MAX_LENGTH = 5
    private const val SHORT_TOKEN_EDIT_THRESHOLD = 1
    private const val LONG_TOKEN_EDIT_THRESHOLD = 2

    private fun hasFuzzyMatch(label: String, token: String): Boolean {
        if (token.length < MIN_FUZZY_TOKEN_LENGTH) return false
        val threshold = if (token.length <= SHORT_TOKEN_MAX_LENGTH) {
            SHORT_TOKEN_EDIT_THRESHOLD
        } else {
            LONG_TOKEN_EDIT_THRESHOLD
        }
        return label.split(WHITESPACE).any { word ->
            word.isNotEmpty() && levenshteinDistance(word, token) <= threshold
        }
    }

    /** Pure iterative Levenshtein edit distance, O(n*m) time, O(m) space. */
    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val previousRow = IntArray(b.length + 1) { it }
        val currentRow = IntArray(b.length + 1)
        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,
                    previousRow[j] + 1,
                    previousRow[j - 1] + substitutionCost
                )
            }
            System.arraycopy(currentRow, 0, previousRow, 0, currentRow.size)
        }
        return previousRow[b.length]
    }

    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private val WHITESPACE = "\\s+".toRegex()
}
