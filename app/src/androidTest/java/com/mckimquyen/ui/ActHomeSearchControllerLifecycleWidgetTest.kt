package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** ARCH-002 cluster 3: the search controller must release the result adapter when ActHome is destroyed. */
@RunWith(AndroidJUnit4::class)
class ActHomeSearchControllerLifecycleWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun searchResultsAdapterIsHeldWhileAliveAndReleasedOnDestroy() {
        var results: RecyclerView? = null
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                results = activity.findViewById(R.id.rvSearchResults)
                assertNotNull("search results must have an adapter while alive", results!!.adapter)
                assertNotNull("test accessor must expose the controller's adapter", activity.searchResultAdapterForTest)
            }
        }
        assertNull("the adapter must be released on destroy", results!!.adapter)
    }
}
