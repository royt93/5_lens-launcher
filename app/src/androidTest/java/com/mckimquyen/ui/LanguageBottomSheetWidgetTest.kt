package com.mckimquyen.ui

import android.view.LayoutInflater
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget Test cho LanguageBottomSheetDialogFragment (Fragment / View Testing)
 */
@RunWith(AndroidJUnit4::class)
class LanguageBottomSheetWidgetTest {

    @Test
    fun testLanguageBottomSheet_inflatesSuccessfully() {
        val scenario = launchFragmentInContainer<LanguageBottomSheetDialogFragment>(
            themeResId = R.style.AppTheme
        )

        scenario.onFragment { fragment ->
            assertNotNull("Fragment should be attached to activity", fragment.activity)
            assertTrue("Fragment should be visible", fragment.isVisible)
        }

        scenario.close()
    }

    @Test
    fun testLanguageBottomSheet_viewsPresent() {
        val scenario = launchFragmentInContainer<LanguageBottomSheetDialogFragment>(
            themeResId = R.style.AppTheme
        )

        scenario.onFragment { fragment ->
            val view = fragment.view
            assertNotNull("Fragment view should not be null", view)

            val tvTitle = view?.findViewById<android.widget.TextView>(R.id.tvTitle)
            val tvSubtitle = view?.findViewById<android.widget.TextView>(R.id.tvSubtitle)
            val rvLanguages = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvLanguages)

            assertNotNull("tvTitle must exist", tvTitle)
            assertNotNull("tvSubtitle must exist", tvSubtitle)
            assertNotNull("rvLanguages must exist", rvLanguages)

            val adapter = rvLanguages?.adapter
            assertNotNull("RecyclerView adapter should be set", adapter)
            assertEquals("Adapter should contain exactly 17 languages", 17, adapter?.itemCount)
        }

        scenario.close()
    }

    @Test
    fun testLanguageBottomSheet_isFragment() {
        val fragment = LanguageBottomSheetDialogFragment()
        assertNotNull(fragment)
        assertTrue(fragment is androidx.fragment.app.Fragment)
    }

    @Test
    fun languagePickerIconsUseConfiguredCompatTints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inflater = LayoutInflater.from(context)

        val dialog = inflater.inflate(R.layout.dialog_language_picker, null)
        val search = dialog.findViewById<ImageView>(R.id.ivSearch)
        assertEquals(
            ContextCompat.getColor(context, R.color.colorAppTint),
            ImageViewCompat.getImageTintList(search)?.defaultColor
        )

        val row = inflater.inflate(R.layout.item_language, null)
        val selected = row.findViewById<ImageView>(R.id.ivSelected)
        assertEquals(
            ContextCompat.getColor(context, R.color.colorPrimary),
            ImageViewCompat.getImageTintList(selected)?.defaultColor
        )
    }
}
