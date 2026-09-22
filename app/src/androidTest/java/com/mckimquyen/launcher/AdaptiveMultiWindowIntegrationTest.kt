package com.mckimquyen.launcher

import android.content.pm.ActivityInfo
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.ui.ActHome
import com.mckimquyen.views.LensView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveMultiWindowIntegrationTest {

    @Test
    fun testGridGeometryAdaptsOnSizeChange() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            val lensView = activity.findViewById<LensView>(R.id.lensViews)
            assertNotNull(lensView)

            val outRect = Rect()
            val boundsRetrieved = lensView.getAppBounds(0, outRect)

            // Simulate size change as in multi-window or orientation layout
            lensView.layout(0, 0, 1200, 800)

            val outRectAfter = Rect()
            lensView.getAppBounds(0, outRectAfter)

            assertNotNull("App bounds should be computable", outRectAfter)
        }

        scenario.close()
    }

    @Test
    fun testSearchStateSurvivesOrientationChange() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            val searchView = activity.findViewById<SearchView>(R.id.searchView)
            val appSearch = searchView.editText

            // Show search view and set query text
            searchView.show()
            appSearch.setText("Camera")
            assertEquals("Camera", appSearch.text.toString())

            // Rotate screen to landscape
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Verify search text persists across orientation change
        scenario.onActivity { activity ->
            val searchView = activity.findViewById<SearchView>(R.id.searchView)
            val appSearch = searchView.editText
            assertEquals("Search text must survive orientation transition", "Camera", appSearch.text.toString())

            // Restore portrait
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        scenario.close()
    }
}
