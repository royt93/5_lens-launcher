package com.mckimquyen.itf

import com.mckimquyen.ui.settings.ResetEntry

interface SettingsInterface {
    fun onDefaultsReset()

    fun onValuesUpdated()

    fun onHomeLauncherChooserStarted()

    /** UI-034: what this tab's reset would write; empty when the tab has no live state to read. */
    fun resetEntries(): List<ResetEntry>

    /** UI-034: refresh visible values and run reset side effects after defaults were applied or undone. */
    fun onResetStateChanged()
}
