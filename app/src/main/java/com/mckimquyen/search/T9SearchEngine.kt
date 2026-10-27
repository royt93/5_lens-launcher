package com.mckimquyen.search

import com.mckimquyen.model.App

/** Pure T9 mapping and ranked matching for local app search. */
object T9SearchEngine {

    private const val LABEL_PREFIX_TIER = 1
    private const val LABEL_WORD_PREFIX_TIER = 2
    private const val LABEL_SUBSTRING_TIER = 3
    private const val ALIAS_PREFIX_TIER = 4
    private const val ALIAS_SUBSTRING_TIER = 5

    @JvmStatic
    fun toT9Digits(text: CharSequence?): String {
        val normalized = AppSearchEngine.normalize(text)
        return buildString(normalized.length) {
            normalized.forEach { char ->
                charToT9Digit(char)?.let(::append)
            }
        }
    }

    /**
     * Ranked T9 matching. Same-tier results are ordered like text search ([AppSearchEngine.search]): favorite,
     * then recent-launch position in [recentKeys], then open count, then label and key. Capped at [limit].
     */
    @JvmStatic
    @JvmOverloads
    fun search(
        candidates: List<App>,
        query: CharSequence?,
        recentKeys: List<String> = emptyList(),
        limit: Int = AppSearchEngine.SEARCH_RESULT_LIMIT
    ): List<App> {
        if (limit <= 0) return emptyList()
        val digitsQuery = query?.toString()?.trim().orEmpty()
        if (digitsQuery.isEmpty() || digitsQuery.any { !it.isDigit() }) return emptyList()

        val recentPositions = buildMap {
            recentKeys.forEachIndexed { index, key -> putIfAbsent(key, index) }
        }
        return candidates.asSequence()
            .filter(App::isVisible)
            .mapNotNull { app ->
                val tier = matchTier(app, digitsQuery) ?: return@mapNotNull null
                app to tier
            }
            .sortedWith(
                compareBy<Pair<App, Int>> { it.second }
                    .thenByDescending { it.first.isFavorite }
                    .thenBy { recentPositions[AppSearchEngine.componentKey(it.first)] ?: Int.MAX_VALUE }
                    .thenByDescending { it.first.openCount }
                    .thenBy { AppSearchEngine.normalize(it.first.label) }
                    .thenBy { AppSearchEngine.componentKey(it.first) }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun matchTier(app: App, query: String): Int? {
        val labelSig = toT9Digits(app.label)
        val labelTier = when {
            labelSig.startsWith(query) -> LABEL_PREFIX_TIER
            labelSig.split('0').any { it.startsWith(query) } -> LABEL_WORD_PREFIX_TIER
            labelSig.contains(query) -> LABEL_SUBSTRING_TIER
            else -> null
        }
        if (labelTier != null) return labelTier

        // SEARCH-012: fallback to Latin package aliases when label is non-Latin or didn't match
        val aliases = AppSearchEngine.normalize(app.packageName)
            .split('.', '-', '_')
            .filter(String::isNotEmpty)
        val aliasSignatures = aliases.map(::toT9Digits).filter(String::isNotEmpty)

        return when {
            aliasSignatures.any { it.startsWith(query) } -> ALIAS_PREFIX_TIER
            aliasSignatures.any { it.contains(query) } -> ALIAS_SUBSTRING_TIER
            else -> null
        }
    }

    private fun charToT9Digit(char: Char): Char? = when (char) {
        in 'a'..'c', '2' -> '2'
        in 'd'..'f', '3' -> '3'
        in 'g'..'i', '4' -> '4'
        in 'j'..'l', '5' -> '5'
        in 'm'..'o', '6' -> '6'
        in 'p'..'s', '7' -> '7'
        in 't'..'v', '8' -> '8'
        in 'w'..'z', '9' -> '9'
        '1' -> '1'
        ' ', '0' -> '0'
        else -> null
    }
}
