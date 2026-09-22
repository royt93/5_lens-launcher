package com.mckimquyen.ui

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coverage gap found by whole-codebase audit sweep: ActBase (setContentView's task-description
 * override, updateNightMode's lazy utilSettings init) had zero test at any tier before this.
 * Building ActBase directly under Robolectric hit "You need to use a Theme.AppCompat theme"
 * (real AppCompatDelegate setup needs the real manifest-declared theme) - this project's own
 * convention is to exercise real-Activity/real-theme behavior via androidTest instead, using
 * ActAbout, one of ActBase's real concrete subclasses, rather than adding a test-only manifest
 * entry just to instantiate ActBase directly.
 */
@RunWith(AndroidJUnit4::class)
class ActBaseWidgetTest {

    @Test
    fun setContentView_setsATaskDescriptionMatchingTheAppName() {
        ActivityScenario.launch(ActAbout::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Activity has no public getter for the TaskDescription it set - read it back
                // the way any app can introspect its own current task, permission-free.
                val activityManager =
                    activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val taskDescription = activityManager.appTasks.first().taskInfo?.taskDescription

                assertEquals(activity.getString(R.string.app_name), taskDescription?.label)
            }
        }
    }
}
