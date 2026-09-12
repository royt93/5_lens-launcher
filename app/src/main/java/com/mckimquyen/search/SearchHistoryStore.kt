package com.mckimquyen.search

import android.content.Context
import androidx.core.content.edit

/** Small local-only MRU list. No usage data leaves the device. */
class SearchHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun recentKeys(): List<String> = preferences.getString(KEY_RECENT, null)
        ?.split(SEPARATOR)
        ?.filter(String::isNotBlank)
        .orEmpty()

    fun recordLaunch(componentKey: String) {
        if (componentKey.isBlank()) return
        val updated = buildList {
            add(componentKey)
            addAll(recentKeys().filterNot { it == componentKey })
        }.take(MAX_RECENT)
        preferences.edit { putString(KEY_RECENT, updated.joinToString(SEPARATOR)) }
    }

    fun clear() {
        preferences.edit { remove(KEY_RECENT) }
    }

    private companion object {
        const val PREFERENCES = "app_search_history"
        const val KEY_RECENT = "recent_component_keys"
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT = 8
    }
}
