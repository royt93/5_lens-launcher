package com.mckimquyen.adt

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.views.LensView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensPagerAdapterLockWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun prefetchedLockedPageNeverRunsAppBindingAndShowsContentFreePlaceholder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var bindCalls = 0
        val adapter = LensPagerAdapter { _, _ -> bindCalls++ }
        adapter.setLockedLensIds(setOf("private"))
        adapter.submitLenses(listOf(LensWorkspace("private", "Private", 0)))
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals("locked prefetch must not call app/lens binding", 0, bindCalls)
        assertTrue("locked page must receive no apps", holder.lensView.displayedApps.isEmpty())
        val placeholder = holder.itemView.findViewById<TextView>(R.id.tvLensLockedPlaceholder)
        assertEquals(View.VISIBLE, placeholder.visibility)
        assertEquals(context.getString(R.string.lens_locked_message), placeholder.text.toString())
    }

    @Test
    fun unlockedPageStillUsesNormalLensBinding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var boundLensId: String? = null
        val adapter = LensPagerAdapter { _, lens -> boundLensId = lens.id }
        adapter.setLockedLensIds(setOf("private"))
        adapter.submitLenses(listOf(LensWorkspace("work", "Work", 0)))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals("work", boundLensId)
        assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.tvLensLockedPlaceholder).visibility)
    }
}
