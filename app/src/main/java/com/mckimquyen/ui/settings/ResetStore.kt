package com.mckimquyen.ui.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilSettings

/** UI-034: the production [ResetFlow.Store], backed by the app's default SharedPreferences through [UtilSettings]. */
class ResetStore(private val context: Context, private val settings: UtilSettings) : ResetFlow.Store {
    override fun raw(): Map<String, Any?> = PreferenceManager.getDefaultSharedPreferences(context).all

    override fun snapshot(keys: Set<String>): ResetSnapshot = settings.snapshotKeys(keys)

    override fun restore(snapshot: ResetSnapshot): Boolean = settings.restoreKeys(snapshot)

    override fun apply(entries: List<ResetEntry>) = ResetApplier.apply(settings, entries)
}
