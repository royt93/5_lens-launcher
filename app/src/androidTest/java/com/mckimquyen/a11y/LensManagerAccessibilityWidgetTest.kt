package com.mckimquyen.a11y

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.LensManagementActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** FISH-023: manager rows and header must mirror in RTL, reflow at large font, and keep 48dp targets. */
@RunWith(AndroidJUnit4::class)
class LensManagerAccessibilityWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.deleteAll()
        dao.insertAll(
            listOf(
                LensWorkspace.createDefault().copy(groupName = "Personal"),
                LensWorkspace("work", "A very long lens name that has to wrap or ellipsize safely", 1, 20L, "Personal")
            )
        )
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        dao.deleteAll()
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun rtlContext(activity: LensManagementActivity, fontScale: Float = 1f): android.content.Context {
        val arabic = Locale("ar")
        val config = Configuration(activity.resources.configuration).apply {
            setLocale(arabic)
            setLayoutDirection(arabic)
            this.fontScale = fontScale
        }
        return android.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.AppTheme)
    }

    private fun measure(view: View, widthPx: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test
    fun lensRowMirrorsInRtlSoLeadingIconIsOnTheRight() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val ctx = rtlContext(activity)
                val row = LayoutInflater.from(ctx).inflate(R.layout.item_lens_group_lens, null)
                row.layoutDirection = View.LAYOUT_DIRECTION_RTL
                row.findViewById<TextView>(R.id.tvLensGroupRow).text = "Lens"
                measure(row, 1080)
                val icon = row.findViewById<ImageView>(R.id.ivLensGroupRowIcon)
                val more = row.findViewById<ImageButton>(R.id.btLensGroupMore)
                assertTrue("RTL: lens icon sits right of the overflow button", icon.left > more.left)
            }
        }
    }

    @Test
    fun headerChevronMirrorsInRtl() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val ctx = rtlContext(activity)
                val header = LayoutInflater.from(ctx).inflate(R.layout.item_lens_group_header, null)
                header.layoutDirection = View.LAYOUT_DIRECTION_RTL
                header.findViewById<TextView>(R.id.tvLensGroupHeader).text = "Group"
                measure(header, 1080)
                val chevron = header.findViewById<View>(R.id.tvLensGroupExpanded)
                val count = header.findViewById<View>(R.id.tvLensGroupCount)
                assertTrue("RTL: expand chevron starts on the right, count on the left", chevron.left > count.left)
            }
        }
    }

    @Test
    fun largeFontKeepsRowTargetsAtLeast48dpAndTitleVisible() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val ctx = rtlContext(activity, fontScale = 2.0f)
                val row = LayoutInflater.from(ctx).inflate(R.layout.item_lens_group_lens, null)
                val title = row.findViewById<TextView>(R.id.tvLensGroupRow)
                title.text = "A very long lens name that has to wrap or ellipsize safely"
                row.findViewById<View>(R.id.tvLensGroupActive).visibility = View.VISIBLE
                measure(row, (360 * ctx.resources.displayMetrics.density).toInt())
                val min = (48 * ctx.resources.displayMetrics.density).toInt()
                assertTrue("overflow target >= 48dp", row.findViewById<View>(R.id.btLensGroupMore).measuredHeight >= min)
                assertTrue("title keeps visible width at 200% font", title.measuredWidth > 0)
                assertTrue("title stays within its row", title.right <= row.measuredWidth)
            }
        }
    }

    @Test
    fun activityLabelsAreAccessibleForExpandActiveAndOverflow() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvLensGroups)
                val header = list.findViewHolderForAdapterPosition(0)!!.itemView
                assertNotNull(header.contentDescription)
                val lensRow = list.findViewHolderForAdapterPosition(1)!!.itemView
                assertNotNull(lensRow.contentDescription)
                assertEquals(
                    activity.getString(R.string.lens_group_lens_options),
                    lensRow.findViewById<View>(R.id.btLensGroupMore).contentDescription.toString()
                )
            }
        }
    }
}
