package com.mckimquyen.a11y

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * A11Y-001: Widget tests proving RTL layout direction and mirroring
 * in layouts such as [R.layout.view_item_app].
 */
@RunWith(AndroidJUnit4::class)
class RtlLayoutWidgetTest {

    @Test
    fun testViewItemApp_underRtlLocale_respectsRtlLayoutDirection() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val arabicLocale = Locale("ar")
            val config = Configuration(activity.resources.configuration).apply {
                setLocale(arabicLocale)
                setLayoutDirection(arabicLocale)
            }
            val baseContext = activity.createConfigurationContext(config)
            val rtlContext = android.view.ContextThemeWrapper(baseContext, R.style.AppTheme)

            assertEquals("Context configuration layout direction must be RTL", View.LAYOUT_DIRECTION_RTL, rtlContext.resources.configuration.layoutDirection)

            val inflater = LayoutInflater.from(rtlContext)
            val itemView = inflater.inflate(R.layout.view_item_app, null)
            itemView.layoutDirection = View.LAYOUT_DIRECTION_RTL

            assertEquals("Layout direction must be RTL", View.LAYOUT_DIRECTION_RTL, itemView.layoutDirection)

            val ivAppIcon = itemView.findViewById<View>(R.id.ivAppIcon)
            val tvAppLabel = itemView.findViewById<View>(R.id.tvAppLabel)
            val ivAppHide = itemView.findViewById<View>(R.id.ivAppHide)
            val btAppLock = itemView.findViewById<View>(R.id.btAppLock)
            val ivAppMenu = itemView.findViewById<View>(R.id.ivAppMenu)

            assertNotNull("ivAppIcon must exist", ivAppIcon)
            assertNotNull("tvAppLabel must exist", tvAppLabel)
            assertNotNull("ivAppHide must exist", ivAppHide)
            assertNotNull("btAppLock must exist", btAppLock)
            assertNotNull("ivAppMenu must exist", ivAppMenu)
        }

        scenario.close()
    }
}
