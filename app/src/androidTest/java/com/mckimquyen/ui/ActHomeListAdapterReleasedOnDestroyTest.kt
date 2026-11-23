package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppAdapter.onViewRecycled dismisses a row's Set-folder dialog, and it only runs when the RecyclerView drops its adapter (setAdapter(null) recycles
 * the attached rows). FrmApps does that; ActHome set its list adapter in setupViews and never cleared it, so an Activity destroyed with that dialog
 * open left the dialog window to the framework's leak handling. The RecyclerView is kept across the close to read the adapter afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeListAdapterReleasedOnDestroyTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test fun theHomeListAdapterIsDroppedWhenTheActivityIsDestroyed() {
        var list: RecyclerView? = null
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { list = it.findViewById(R.id.rvHomeAppList) }
            assertNotNull("positive control: the home list has an adapter while the Activity lives", list?.adapter)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNull("the destroyed Activity must not keep its list adapter attached", list?.adapter)
    }
}
