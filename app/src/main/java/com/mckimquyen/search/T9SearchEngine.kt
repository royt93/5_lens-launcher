package com.mckimquyen.search

import com.mckimquyen.model.App

/** Pure T9 mapping and ranked matching for local app search. */
object T9SearchEngine {

    private const val PREFIX_TIER = 1
    private const val WORD_PREFIX_TIER = 2
    private const val SUBSTRING_TIER = 3

    @JvmStatic
    fun toT9Digits(text: CharSequence?): String {
        val normalized = AppSearchEngine.normalize(text)
        return buildString(normalized.length) {
            normalized.forEach { char -> append(charToT9Digit(char)) }
        }
    }

    @JvmStatic
    fun search(candidates: List<App>, query: CharSequence?): List<App> {
        val digitsQuery = query?.toString()?.trim().orEmpty()
        if (digitsQuery.isEmpty() || digitsQuery.any { !it.isDigit() }) return emptyList()

        return candidates.asSequence()
            .filter(App::isVisible)
            .mapNotNull { app ->
                val signature = toT9Digits(app.label)
                val tier = when {
                    signature.startsWith(digitsQuery) -> PREFIX_TIER
                    signature.split('0').any { it.startsWith(digitsQuery) } -> WORD_PREFIX_TIER
                    signature.contains(digitsQuery) -> SUBSTRING_TIER
                    else -> return@mapNotNull null
                }
                app to tier
            }
            .sortedWith(
                compareBy<Pair<App, Int>> { it.second }
                    .thenByDescending { it.first.isFavorite }
                    .thenByDescending { it.first.openCount }
                    .thenBy { AppSearchEngine.normalize(it.first.label) }
                    .thenBy { it.first.packageName.toString() }
            )
            .map { it.first }
            .toList()
    }

    private fun charToT9Digit(char: Char): Char = when (char) {
        in 'a'..'c', '2' -> '2'
        in 'd'..'f', '3' -> '3'
        in 'g'..'i', '4' -> '4'
        in 'j'..'l', '5' -> '5'
        in 'm'..'o', '6' -> '6'
        in 'p'..'s', '7' -> '7'
        in 't'..'v', '8' -> '8'
        in 'w'..'z', '9' -> '9'
        ' ', '0' -> '0'
        else -> '1'
    }
}
