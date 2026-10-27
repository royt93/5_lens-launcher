package com.mckimquyen.itf

import com.mckimquyen.model.App
import com.mckimquyen.ui.settings.ResetEntry

interface AppsInterface {
    fun onDefaultsReset()

    fun onAppsUpdated(apps: ArrayList<App>?)

    /** UI-034: what this tab's reset would write; empty when the tab has no live state to read. */
    fun resetEntries(): List<ResetEntry>

    /** UI-034: refresh visible values and run reset side effects after defaults were applied or undone. */
    fun onResetStateChanged()
}
