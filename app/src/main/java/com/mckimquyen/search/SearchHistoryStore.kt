package com.mckimquyen.search

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

/**
 * Small local-only MRU list. No usage data leaves the device.
 *
 * SEARCH-016: entries expire [TTL_DAYS] days after their last launch. Timestamps live in a second pref
 * ([KEY_TIMES]) parallel to [KEY_RECENT], so the key list written by older builds stays readable; a key with no
 * usable timestamp is treated as launched "now" the first time it is seen, so pre-TTL history is not wiped on
 * upgrade. The backup rules still exclude this file (device-local).
 */
class SearchHistoryStore @JvmOverloads constructor(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun recentKeys(): List<String> = liveEntries().map { it.first }

    fun recordLaunch(componentKey: String) {
        if (componentKey.isBlank()) return
        val updated = buildList {
            add(componentKey to clock())
            addAll(liveEntries().filterNot { it.first == componentKey })
        }.take(MAX_RECENT)
        write(updated)
    }

    fun removeKey(componentKey: String) {
        write(liveEntries().filterNot { it.first == componentKey })
    }

    fun clear() {
        preferences.edit {
            remove(KEY_RECENT)
            remove(KEY_TIMES)
        }
    }

    /** Keys paired with their last-launch time, expired ones dropped (and removed from disk). */
    private fun liveEntries(): List<Pair<String, Long>> {
        val keys = preferences.getString(KEY_RECENT, null)
            ?.split(SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (keys.isEmpty()) return emptyList()
        val times = preferences.getString(KEY_TIMES, null)?.split(SEPARATOR).orEmpty()
        val now = clock()
        var stampedLegacy = false
        val entries = keys.mapIndexed { index, key ->
            val stamp = times.getOrNull(index)?.toLongOrNull()
            if (stamp == null) stampedLegacy = true
            key to (stamp ?: now)
        }
        val live = entries.filter { now - it.second <= TTL_MILLIS }
        if (live.size != entries.size || stampedLegacy) write(live)
        return live
    }

    private fun write(entries: List<Pair<String, Long>>) {
        preferences.edit {
            if (entries.isEmpty()) {
                remove(KEY_RECENT)
                remove(KEY_TIMES)
            } else {
                putString(KEY_RECENT, entries.joinToString(SEPARATOR) { it.first })
                putString(KEY_TIMES, entries.joinToString(SEPARATOR) { it.second.toString() })
            }
        }
    }

    companion object {
        /** Days a launched app stays in the recents list without being launched again. */
        const val TTL_DAYS = 30L
        private val TTL_MILLIS = TimeUnit.DAYS.toMillis(TTL_DAYS)
        private const val PREFERENCES = "app_search_history"
        private const val KEY_RECENT = "recent_component_keys"
        private const val KEY_TIMES = "recent_component_times"
        private const val SEPARATOR = "\u001F"
        private const val MAX_RECENT = 16 // FEAT-009: was 8, shared by recentHeader and the new panel
    }
}
