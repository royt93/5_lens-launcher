package com.mckimquyen.adt

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActSettings
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAdapterNotificationBadgeWidgetTest {

    private fun app(pkg: String, count: Int) =
        App(packageName = pkg, name = pkg, label = pkg, notificationCount = count)

    private fun bindSingleApp(activity: android.app.Activity, count: Int): View {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = AppAdapter(activity, mutableListOf(app("pkg.camera", count)))
        recyclerView.adapter = adapter
        recyclerView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 1080, 400)
        return recyclerView.findViewHolderForAdapterPosition(0)!!.itemView
    }

    @Test
    fun bindingAnAppWithACountShowsTheBadgeWithTheFormattedText() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                val itemView = bindSingleApp(activity, 150)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.VISIBLE, badge.visibility)
                assertEquals("99+", badge.text.toString())
            }
        }
    }

    @Test
    fun bindingAnAppWithZeroCountHidesTheBadge() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                val itemView = bindSingleApp(activity, 0)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.GONE, badge.visibility)
            }
        }
    }

    @Test
    fun settingOffHidesTheBadgeRegardlessOfCount() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
                val itemView = bindSingleApp(activity, 3)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.GONE, badge.visibility)
            }
        }
    }
}
