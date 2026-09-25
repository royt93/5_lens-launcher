package com.mckimquyen.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

// applicationId (not the code namespace com.mckimquyen) - both flavors share it.
internal const val TARGET_PACKAGE = "com.mckimquyen.lenslauncher"
private const val HOME_ACTIVITY = "com.mckimquyen.ui.ActHome"
private const val LENS_VIEW_ID = "lensViews"
private const val LENS_VIEW_TIMEOUT_MS = 10_000L

/**
 * Explicit component on purpose: the zero-arg startActivityAndWait() resolves CATEGORY_LAUNCHER,
 * which in this manifest is ActSettings, not ActHome. An implicit CATEGORY_HOME intent is no
 * better - on a device where this app isn't the default launcher it resolves to the system
 * launcher or a chooser.
 */
internal fun MacrobenchmarkScope.startHomeAndWaitForGrid() {
    pressHome()
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).apply {
            setClassName(TARGET_PACKAGE, HOME_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    )
    device.wait(Until.hasObject(By.res(TARGET_PACKAGE, LENS_VIEW_ID)), LENS_VIEW_TIMEOUT_MS)
    device.waitForIdle()
}
